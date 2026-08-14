package com.aromit.geminivoicechat.data.repository

import android.util.Log
import com.aromit.geminivoicechat.domain.model.A2UIActionResult
import com.aromit.geminivoicechat.domain.model.A2UIFile
import com.aromit.geminivoicechat.domain.model.AiResponse
import com.aromit.geminivoicechat.domain.model.AiStreamEvent
import com.aromit.geminivoicechat.domain.repository.AiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class RemoteAiRepository(
    private val serverUrl: String,
    private val apiKey: String = "",
) : AiRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val sessionId: String = UUID.randomUUID().toString()

    override suspend fun sendMessage(text: String): Flow<AiStreamEvent> = flow {
        val bodyJson = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(session_id = sessionId, message = text),
        )
        val url = if (apiKey.isNotBlank()) "$serverUrl?api_key=$apiKey" else serverUrl
        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        Log.i(TAG, "POST $serverUrl  session=$sessionId  msg=${text.take(80)}")

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw IOException("서버 연결 실패: ${e.message}", e)
        }

        response.use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${resp.message}")
            val source = resp.body?.source() ?: throw IOException("응답 본문이 비어 있습니다.")

            // SSE (ncloud /chat/stream): "event: <name>" + "data: {...}" 쌍.
            // 종료 이벤트 없이 스트림이 닫히면 완료. 라우팅은 이벤트명 기준 —
            //   token → 텍스트 청크, a2ui → v0.9 엔벨로프 1건, error → 예외.
            var sseEvent = "message"
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                when {
                    line.startsWith("event:") -> {
                        sseEvent = line.removePrefix("event:").trim()
                    }

                    line.startsWith("data:") -> {
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isEmpty() || payload == "[DONE]") continue
                        emitSseData(sseEvent, payload)
                    }

                    line.startsWith(":") -> Unit // SSE 주석/keep-alive
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<AiStreamEvent>.emitSseData(event: String, payload: String) {
        when (event) {
            "token", "message" -> {
                val text = JSONObject(payload).optString("text")
                if (text.isNotEmpty()) emit(AiStreamEvent.TextChunk(text))
            }

            "a2ui" -> emit(AiStreamEvent.A2UIEnvelope(payload))

            "error" -> throw IOException(
                "서버 오류: ${JSONObject(payload).optString("message", payload)}",
            )

            "done" -> Unit
            else -> Log.w(TAG, "알 수 없는 SSE 이벤트 무시: $event")
        }
    }

    /**
     * A2UI 버튼 액션 전송 (spec 002 — `POST /a2ui/action`).
     * 인증·테넌트 헤더는 현재 불필요 — 미전송 시 서버가 기본값으로 처리한다 (계약 확정 2026-08-14).
     */
    override suspend fun sendA2UIAction(
        eventName: String,
        surfaceId: String,
        context: Map<String, Any?>,
        files: List<A2UIFile>,
    ): A2UIActionResult = withContext(Dispatchers.IO) {
        val actionUrl = serverUrl.toHttpUrl().resolve("/a2ui/action")
            ?: throw IOException("액션 URL 을 만들 수 없습니다: $serverUrl")

        val actionJson = JSONObject()
            .put("name", eventName)
            .put("surfaceId", surfaceId)
            .put("context", toJson(context))
            .toString()

        // 파일이 없어도 항상 multipart 로 전송한다 — JSON 경로는 서버가 500 을 반환
        // (계약 개정 2026-08-14, spec 002).
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("action", actionJson)
            .apply {
                for (file in files) {
                    addFormDataPart(
                        "files",
                        file.name,
                        file.bytes.toRequestBody(file.mimeType.toMediaType()),
                    )
                }
            }
            .build()

        Log.i(TAG, "POST $actionUrl  action=$eventName  surface=$surfaceId  files=${files.size} (multipart)")

        val request = Request.Builder()
            .url(actionUrl)
            .post(body)
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw IOException("서버 연결 실패: ${e.message}", e)
        }

        response.use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${resp.message}")
            val obj = JSONObject(resp.body?.string() ?: throw IOException("응답 본문이 비어 있습니다."))
            A2UIActionResult(
                ok = obj.optBoolean("ok", false),
                markdown = obj.optString("md", "처리 결과를 받지 못했습니다."),
            )
        }
    }

    private fun toJson(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().also { obj ->
            value.forEach { (k, v) -> obj.put(k.toString(), toJson(v)) }
        }

        is List<*> -> org.json.JSONArray().also { arr -> value.forEach { arr.put(toJson(it)) } }
        else -> value
    }

    override fun streamAudio(audioFlow: Flow<ByteArray>): Flow<AiResponse> {
        throw UnsupportedOperationException("오디오 스트리밍은 현재 지원하지 않습니다.")
    }

    @Serializable
    private data class ChatRequest(
        val session_id: String,
        val message: String,
    )

    companion object {
        private const val TAG = "RemoteAiRepository"
    }
}
