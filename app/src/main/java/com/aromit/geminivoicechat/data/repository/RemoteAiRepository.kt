package com.aromit.geminivoicechat.data.repository

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.aromit.geminivoicechat.domain.model.AiResponse
import com.aromit.geminivoicechat.domain.repository.AiRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * FastAPI 서버와 WebSocket으로 통신하는 1단계 구현체.
 *
 * - 같은 WebSocket 세션을 유지해야 서버측 멀티턴 히스토리가 보존됨.
 * - 한 번에 한 턴만 진행한다고 가정 (`ChatViewModel.submitUserPrompt`는 직렬적 호출).
 * - `text_done` 수신 시 Flow가 정상 종료되어 ChatViewModel이 TTS 트리거.
 * - `streamAudio`는 2단계에서 구현. 서버도 1단계에서는 `AUDIO_FORMAT_INVALID`로 응답.
 */
class RemoteAiRepository(
    private val serverUrl: String,
    private val apiKey: String
) : AiRepository {

    private val client = OkHttpClient.Builder()
        // OkHttp 내장 WebSocket PING 프레임(opcode 0x9). Android 네트워크 레이어에서 처리되어
        // Doze/App Standby에도 안정. Cloud Run LB의 idle 강제종료 방지.
        .pingInterval(20, TimeUnit.SECONDS)
        // WebSocket 장기 연결: read timeout 무한대.
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        // default value를 가진 필드(예: TextInput.type)도 직렬화해야 서버가 type 필드를 받음.
        encodeDefaults = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionMutex = Mutex()

    private var webSocket: WebSocket? = null
    private var readyDeferred: CompletableDeferred<Unit>? = null

    // OS가 백그라운드에서 소켓을 abort해도 readyDeferred는 completed 상태로 남아있어
    // 살아있는 것처럼 보인다. 실제 연결 가용성은 이 플래그로만 판단.
    @Volatile
    private var isConnectionAlive = false

    // 진행 중인 한 턴의 수신 채널. listener 스레드와 호출 코루틴이 공유.
    @Volatile
    private var currentTurnChannel: Channel<String>? = null

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            // 앱이 foreground로 복귀 시 연결 prewarm. 첫 ON_START는 앱 최초 기동 시
            // 즉시 발생해 첫 메시지의 connect latency도 함께 흡수한다.
            Log.i(TAG, "ON_START → prewarm reconnect (alive=$isConnectionAlive)")
            scope.launch {
                connectionMutex.withLock {
                    try {
                        ensureConnectedLocked()
                    } catch (t: Throwable) {
                        Log.w(TAG, "Foreground prewarm failed: ${t.message}")
                    }
                }
            }
        }
    }

    init {
        // LifecycleRegistry.addObserver는 main thread 전용.
        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        }
    }

    override suspend fun sendMessage(text: String): Flow<String> {
        val turnChannel = Channel<String>(capacity = Channel.UNLIMITED)
        connectionMutex.withLock {
            ensureConnectedLocked()
            currentTurnChannel = turnChannel
            val payload = json.encodeToString(
                TextInput.serializer(),
                TextInput(text = text),
            )
            val accepted = webSocket?.send(payload) ?: false
            Log.i(TAG, "send text_input accepted=$accepted len=${text.length}")
            if (!accepted) {
                currentTurnChannel = null
                turnChannel.close(IOException("WebSocket send failed"))
            }
        }
        return flow {
            try {
                for (chunk in turnChannel) emit(chunk)
            } finally {
                if (currentTurnChannel === turnChannel) {
                    currentTurnChannel = null
                }
                turnChannel.close()
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun streamAudio(audioFlow: Flow<ByteArray>): Flow<AiResponse> {
        throw UnsupportedOperationException("audio_input은 Phase 2에서 구현됩니다.")
    }

    /** connectionMutex 안에서만 호출. 연결이 없거나 끊겼으면 새로 열고 READY까지 대기. */
    private suspend fun ensureConnectedLocked() {
        if (isConnectionAlive && webSocket != null) {
            return
        }
        closeConnectionLocked()

        val deferred = CompletableDeferred<Unit>()
        readyDeferred = deferred
        val request = Request.Builder()
            .url("$serverUrl?api_key=$apiKey")
            .build()
        Log.i(TAG, "Connecting to $serverUrl")
        webSocket = client.newWebSocket(request, InboundListener())

        try {
            withTimeout(CONNECT_TIMEOUT_MS) { deferred.await() }
        } catch (t: Throwable) {
            Log.w(TAG, "WS connect failed: ${t.message}")
            closeConnectionLocked()
            throw t
        }
        Log.i(TAG, "WS READY")
    }

    /** connectionMutex 안에서 호출. 현재 연결/턴을 모두 정리. */
    private fun closeConnectionLocked() {
        isConnectionAlive = false
        webSocket?.cancel()
        webSocket = null
        readyDeferred?.takeIf { !it.isCompleted }?.cancel()
        readyDeferred = null
        currentTurnChannel?.close(IOException("WebSocket closed"))
        currentTurnChannel = null
    }

    private inner class InboundListener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            handleInbound(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            // 1단계는 텍스트 프레임만 사용
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WS closing code=$code reason=$reason")
            isConnectionAlive = false
            scope.launch { connectionMutex.withLock { closeConnectionLocked() } }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "WS failure: ${t.message}", t)
            isConnectionAlive = false
            readyDeferred?.takeIf { !it.isCompleted }?.completeExceptionally(t)
            currentTurnChannel?.close(t)
            currentTurnChannel = null
            scope.launch { connectionMutex.withLock { closeConnectionLocked() } }
        }
    }

    private fun handleInbound(raw: String) {
        val obj = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to parse inbound frame: $raw", t)
            return
        }
        val type = obj["type"]?.jsonPrimitive?.contentOrNull
        when (type) {
            "status" -> {
                val code = obj["code"]?.jsonPrimitive?.contentOrNull
                Log.i(TAG, "inbound status code=$code")
                if (code == "READY") {
                    isConnectionAlive = true
                    readyDeferred?.takeIf { !it.isCompleted }?.complete(Unit)
                }
                // THINKING / ERROR는 UI 상태가 ChatViewModel에서 관리되므로 별도 처리 없음.
            }
            "text_chunk" -> {
                val chunk = obj["text"]?.jsonPrimitive?.contentOrNull ?: return
                val routed = currentTurnChannel?.trySend(chunk)?.isSuccess == true
                Log.i(TAG, "inbound text_chunk len=${chunk.length} routed=$routed")
            }
            "text_done" -> {
                Log.i(TAG, "inbound text_done")
                currentTurnChannel?.close()
                currentTurnChannel = null
            }
            "error" -> {
                val code = obj["code"]?.jsonPrimitive?.contentOrNull ?: "UNKNOWN"
                val message = obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                Log.w(TAG, "inbound error code=$code msg=$message")
                val err = IOException("Server error [$code] $message")
                currentTurnChannel?.close(err)
                currentTurnChannel = null
            }
            "ping" -> Unit  // 서버측 keepalive ping은 무시 (현재 서버는 미전송, 향후 대비)
            "pong" -> Unit
            else -> Log.w(TAG, "Unhandled inbound type=$type raw=$raw")
        }
    }

    @Serializable
    private data class TextInput(
        val type: String = "text_input",
        val text: String,
    )

    companion object {
        private const val TAG = "RemoteAiRepository"
        private const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
