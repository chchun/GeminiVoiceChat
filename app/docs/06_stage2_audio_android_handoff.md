# Stage 2 Android Handoff — 음성 입력 (오디오 → STT → LLM)

> **작성일:** 2026-05-18  
> **대상:** Android Claude 세션 (이전 컨텍스트 없음)  
> **서버 담당 문서:** `D:\dev\GeminiVoiceChatServer\docs\03_stage2_stt_server_guide.md`  
> **이 문서의 목적:** Android 클라이언트에서 Stage 2 음성 입력 구현에 필요한 모든 정보 제공

---

## 1. 전체 아키텍처

```
Android 단말                     FastAPI 서버 (Cloud Run)
─────────────────────────────    ─────────────────────────────────
🎤 마이크                          WebSocket Router (main.py)
   │ PCM 16kHz 16bit Mono            │
   ▼                                │──► SttSession (stt_service.py)
AudioRecord                         │       │ gRPC
   │ 100ms 청크                      │       ▼
   ▼                                │    Google Cloud STT API
Base64 인코딩                        │       │ transcript
   │                                │◄──────┘
   ▼                                │
WebSocket                          │──► GeminiSession (gemini_service.py)
audio_input 패킷 전송 ──────────────►│       │ HTTPS
   │  (is_final=false × N)          │       ▼
   │  (is_final=true × 1)           │    Google Gemini API
   │                                │       │ text_chunk × N
   ▼                                │◄──────┘
text_chunk 수신 ◄───────────────────│
text_done 수신 ◄────────────────────│
   │
   ▼
TextToSpeech → 🔊 스피커
```

**핵심: Android는 STT API를 직접 호출하지 않습니다.**  
오디오 PCM 데이터를 WebSocket으로 서버에 전송하면 서버가 Google STT API를 호출합니다.  
이유: Android APK에 STT API 키가 포함되면 리버스엔지니어링으로 탈취 가능.

---

## 2. WebSocket 프로토콜 변경 사항

### 2-1. 기존 패킷 (Stage 1 — 변경 없음)

```json
// 클라이언트 → 서버
{"type": "text_input", "text": "안녕하세요"}
{"type": "ping"}
{"type": "mode_change", "mode": "TEXT"}

// 서버 → 클라이언트
{"type": "status", "code": "READY"}
{"type": "status", "code": "THINKING"}
{"type": "text_chunk", "text": "안녕"}
{"type": "text_done"}
{"type": "pong"}
{"type": "error", "code": "...", "message": "..."}
```

### 2-2. Stage 2 신규 패킷

```json
// 클라이언트 → 서버: 음성 모드 전환
{"type": "mode_change", "mode": "VOICE"}

// 클라이언트 → 서버: 오디오 청크 전송 (반복)
{"type": "audio_input", "payload": "<Base64 PCM>", "is_final": false}

// 클라이언트 → 서버: 마지막 청크 (STT 트리거)
{"type": "audio_input", "payload": "<Base64 PCM>", "is_final": true}
```

### 2-3. audio_input 이후 서버 응답 흐름

```
is_final=true 수신 후 서버가 STT 처리 → Gemini 호출:
{"type": "status", "code": "THINKING"}
{"type": "text_chunk", "text": "..."}   × N
{"type": "text_done"}
```

> **주의:** is_final=false 청크에는 서버가 응답하지 않습니다.  
> is_final=true 이후부터 text_chunk가 흐릅니다.

---

## 3. 오디오 포맷 규격 (서버와 합의된 값)

| 항목 | 값 | 비고 |
|------|-----|------|
| 인코딩 | PCM LINEAR16 | 16-bit signed integer, little-endian |
| 샘플레이트 | **16,000 Hz** | 반드시 준수 |
| 채널 | **Mono (1채널)** | 스테레오 불가 |
| 청크 크기 | 100ms 단위 | 1,600 샘플 = 3,200 bytes raw |
| 전송 포맷 | Base64 인코딩 문자열 | `audio_input.payload` 필드 |

---

## 4. Android 구현 가이드

### 4-1. 필요한 권한 (`AndroidManifest.xml`)

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

런타임 권한 요청도 필요합니다 (Android 6.0+):

```kotlin
ActivityCompat.requestPermissions(
    this,
    arrayOf(Manifest.permission.RECORD_AUDIO),
    REQUEST_RECORD_AUDIO
)
```

### 4-2. AudioRecord 설정

```kotlin
val SAMPLE_RATE = 16000
val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
val CHUNK_DURATION_MS = 100
val CHUNK_SIZE_BYTES = SAMPLE_RATE * 2 * CHUNK_DURATION_MS / 1000  // 3200 bytes

val bufferSize = maxOf(
    AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
    CHUNK_SIZE_BYTES * 2
)

val audioRecord = AudioRecord(
    MediaRecorder.AudioSource.MIC,
    SAMPLE_RATE,
    CHANNEL_CONFIG,
    AUDIO_FORMAT,
    bufferSize
)
```

### 4-3. 녹음 및 전송 루프 (Coroutine)

```kotlin
/**
 * 녹음 시작 → 100ms 청크를 audio_input 패킷으로 전송 → 녹음 중지 시 is_final=true
 */
suspend fun startVoiceInput(
    webSocket: WebSocket,
    stopSignal: MutableStateFlow<Boolean>
) = withContext(Dispatchers.IO) {
    audioRecord.startRecording()

    val buffer = ByteArray(CHUNK_SIZE_BYTES)

    while (!stopSignal.value) {
        val bytesRead = audioRecord.read(buffer, 0, buffer.size)
        if (bytesRead > 0) {
            val payload = Base64.encodeToString(
                buffer.copyOf(bytesRead),
                Base64.NO_WRAP
            )
            val packet = buildJsonObject {
                put("type", "audio_input")
                put("payload", payload)
                put("is_final", false)
            }
            webSocket.send(packet.toString())
        }
    }

    // 마지막 청크 — is_final=true 로 STT 트리거
    val finalBytes = audioRecord.read(buffer, 0, buffer.size)
    val finalPayload = if (finalBytes > 0) {
        Base64.encodeToString(buffer.copyOf(finalBytes), Base64.NO_WRAP)
    } else {
        ""
    }
    val finalPacket = buildJsonObject {
        put("type", "audio_input")
        put("payload", finalPayload)
        put("is_final", true)
    }
    webSocket.send(finalPacket.toString())

    audioRecord.stop()
}
```

### 4-4. 모드 전환 패킷

```kotlin
// 음성 모드로 전환 (녹음 시작 전)
webSocket.send("""{"type":"mode_change","mode":"VOICE"}""")

// 텍스트 모드로 복귀 (필요 시)
webSocket.send("""{"type":"mode_change","mode":"TEXT"}""")
```

### 4-5. RemoteAiRepository 수정 포인트

현재 `RemoteAiRepository.kt`의 `sendMessage(text: String)`만 있는 구조에서  
아래 메서드를 추가합니다:

```kotlin
/**
 * 음성 입력 세션: AudioRecord에서 읽은 PCM 청크를 스트리밍 전송.
 * 최종 텍스트 응답은 기존 text_chunk Flow로 수신.
 */
suspend fun sendAudioChunk(pcmBase64: String, isFinal: Boolean) {
    connectionMutex.withLock {
        ensureConnectedLocked()
        val payload = buildJsonObject {
            put("type", "audio_input")
            put("payload", pcmBase64)
            put("is_final", isFinal)
        }
        webSocket?.send(payload.toString())
    }
}
```

> `isFinal=true` 패킷을 보낸 후에는 기존 `sendMessage()`와 동일하게  
> `text_chunk` / `text_done` 패킷이 수신됩니다.  
> 별도 수신 채널 구현 불필요 — 기존 `currentTurnChannel` 재사용 가능.

---

## 5. UI/UX 권장 흐름

```
사용자 액션              Android UI 상태          서버 상태
─────────────────────────────────────────────────────────
🎤 버튼 누름             [녹음 중...]              audio_input 수신 중
    ↓
🎤 버튼 놓음             [인식 중...]              STT 처리 + Gemini 호출
    ↓
                         [AI 응답 중...]           text_chunk 전송
    ↓
                         [TTS 재생 중]             text_done 수신 후 TTS
```

---

## 6. 에러 처리

서버에서 오디오 처리 실패 시 수신할 수 있는 에러 패킷:

```json
{"type": "error", "code": "AUDIO_FORMAT_INVALID", "message": "..."}
{"type": "error", "code": "INTERNAL_ERROR", "message": "STT failed: ..."}
{"type": "status", "code": "ERROR"}
```

> 에러 수신 후 세션은 유지됩니다. 재연결 불필요.  
> `onMessage`의 `"error"` 분기에서 처리 (기존 코드 재사용).

---

## 7. 테스트 시나리오

| # | 시나리오 | 기대 동작 |
|---|----------|-----------|
| 1 | 한국어 음성 입력 후 is_final=true | text_chunk 스트림 수신 |
| 2 | 무음 상태로 is_final=true | 서버에서 응답 없음 (무시) |
| 3 | audio_input 중 앱 백그라운드 | 재연결 후 재시도 (기존 keepalive 로직 활용) |
| 4 | 잘못된 Base64 payload | `AUDIO_FORMAT_INVALID` 에러 수신 |
| 5 | mode_change(VOICE) 후 text_input 전송 | 정상 동작 (서버는 두 타입 모두 처리) |

---

## 8. 서버 환경 정보 (현재 운영 중)

```
엔드포인트: wss://gemini-voice-chat-server-401388719515.asia-northeast3.run.app/ws
WS_API_KEY: dev-local-key-change-me   (실서비스 전 변경 필요)
```

`local.properties`:
```properties
USE_REMOTE=true
SERVER_URL=wss://gemini-voice-chat-server-401388719515.asia-northeast3.run.app/ws
WS_API_KEY=dev-local-key-change-me
```

---

## 9. 관련 문서

- 서버 Stage 2 구현 가이드: `D:\dev\GeminiVoiceChatServer\docs\03_stage2_stt_server_guide.md`
- WebSocket 프로토콜 스펙: `D:\dev\GeminiVoiceChat\app\docs\03_server_api.md`
- WebSocket Keepalive Fix: `D:\dev\GeminiVoiceChat\app\docs\05_websocket_keepalive_fix.md`
