package com.aromit.geminivoicechat.data.repository

import android.util.Log
import com.aromit.geminivoicechat.domain.model.AiResponse
import com.aromit.geminivoicechat.domain.repository.AiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class RemoteAiRepository(
    private val serverUrl: String,
    @Suppress("UNUSED_PARAMETER") private val apiKey: String = "",
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

    override suspend fun sendMessage(text: String): Flow<String> = flow {
        val bodyJson = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(session_id = sessionId, message = text),
        )
        val requestBody = bodyJson
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .build()

        Log.i(TAG, "POST $serverUrl  session=$sessionId  msg=${text.take(80)}")

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw IOException("서버 연결 실패: ${e.message}", e)
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}: ${resp.message}")
            }
            val source = resp.body?.source()
                ?: throw IOException("응답 본문이 비어 있습니다.")

            val buf = Buffer()
            while (!source.exhausted()) {
                source.read(buf, 8192)
                val chunk = buf.readUtf8()
                if (chunk.isNotEmpty()) emit(chunk)
            }
        }
    }.flowOn(Dispatchers.IO)

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