# 03. Server API Contract

Android 클라이언트(`RemoteAiRepository`)와 FastAPI 서버 간의 WebSocket 통신 규격.

> **TTS 정책**: 음성 합성(TTS)은 **Android 단말 내장 엔진**(`AndroidVoiceController`)이 처리한다.
> 서버는 텍스트 토큰만 반환하면 되며, 오디오 출력 스트림은 구현하지 않는다.

---

## 1. 연결

| 항목 | 값 |
|---|---|
| 프로토콜 | WebSocket (`ws://` / `wss://`) |
| 엔드포인트 | `ws://<host>/ws` |
| 인증 | 연결 시 쿼리 파라미터 `?api_key=<KEY>` |
| 메시지 포맷 | UTF-8 JSON (양방향 모두) |

```
ws://localhost:8000/ws?api_key=YOUR_KEY
```

연결 직후 서버는 즉시 `status` 메시지(`code: READY`)를 송신한다.

---

## 2. 클라이언트 → 서버 (Upstream)

### 2-1. `text_input` — 텍스트 채팅 전송

```json
{
  "type": "text_input",
  "text": "오늘 날씨 어때?"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `type` | string | `"text_input"` 고정 |
| `text` | string | 사용자가 입력한 텍스트 |

**서버 응답 흐름:** `status(THINKING)` → `text_chunk` × N → `text_done`

---

### 2-2. `audio_input` — 음성 오디오 청크 전송

```json
{
  "type": "audio_input",
  "payload": "base64_encoded_pcm_bytes",
  "is_final": false
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `type` | string | `"audio_input"` 고정 |
| `payload` | string | Base64 인코딩된 PCM 오디오 데이터 |
| `is_final` | boolean | 발화 종료 시 `true` (마지막 청크임을 알림) |

**오디오 포맷 (Android `AudioRecord` 기본값)**

| 항목 | 값 |
|---|---|
| 샘플레이트 | 16,000 Hz |
| 채널 | Mono |
| 인코딩 | PCM 16-bit Little-Endian |
| 청크 크기 | 4,096 bytes (권장) |

`is_final: true` 수신 후 서버는 Gemini에 전체 오디오를 전달하고 텍스트 응답을 스트리밍한다.

**서버 응답 흐름:** `status(THINKING)` → `text_chunk` × N → `text_done`

---

### 2-3. `mode_change` — 채팅 모드 전환 알림

```json
{
  "type": "mode_change",
  "mode": "VOICE"
}
```

| 필드 | 타입 | 값 |
|---|---|---|
| `type` | string | `"mode_change"` 고정 |
| `mode` | string | `"TEXT"` 또는 `"VOICE"` |

서버 측 세션 상태를 갱신하는 신호. 서버는 응답을 반환하지 않는다 (ACK 없음).

---

### 2-4. `ping` — 연결 유지

```json
{
  "type": "ping"
}
```

서버는 즉시 `pong`으로 응답한다. Android는 30초 간격으로 전송 권장.

---

## 3. 서버 → 클라이언트 (Downstream)

### 3-1. `text_chunk` — AI 답변 토큰 스트리밍

```json
{
  "type": "text_chunk",
  "text": "오늘 서울은"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `type` | string | `"text_chunk"` 고정 |
| `text` | string | 스트리밍 토큰 (단어 또는 문장 단편) |

---

### 3-2. `text_done` — 스트리밍 완료

```json
{
  "type": "text_done"
}
```

모든 `text_chunk` 전송 후 서버가 1회 송신. **Android는 이 이벤트를 수신한 시점에 `VoiceController.speak(fullText)`를 호출한다.**

---

### 3-3. `status` — 시스템 상태 알림

```json
{
  "type": "status",
  "code": "THINKING"
}
```

| `code` 값 | 의미 |
|---|---|
| `READY` | 연결 완료, 입력 대기 |
| `THINKING` | Gemini API 처리 중 |
| `ERROR` | 오류 발생 (`error` 메시지 직후 전송) |

---

### 3-4. `error` — 오류 알림

```json
{
  "type": "error",
  "code": "GEMINI_API_ERROR",
  "message": "API 할당량 초과"
}
```

| `code` 값 | 의미 |
|---|---|
| `AUTH_FAILED` | API 키 인증 실패 |
| `AUDIO_FORMAT_INVALID` | 오디오 포맷 불일치 |
| `GEMINI_API_ERROR` | Gemini API 호출 오류 |
| `INTERNAL_ERROR` | 서버 내부 오류 |

오류 발생 시 서버는 `error` → `status(ERROR)` 순으로 송신한다.

---

### 3-5. `pong` — Ping 응답

```json
{
  "type": "pong"
}
```

---

## 4. 통신 흐름 다이어그램

### 텍스트 채팅 흐름

```
Android                          FastAPI Server
   |                                  |
   |--- text_input ------------------>|
   |                                  |-- Gemini API 호출
   |<-- status(THINKING) ------------|
   |<-- text_chunk("오늘 서울은") ----|
   |<-- text_chunk(" 맑고") ---------|
   |<-- text_chunk(" 따뜻해요.") ----|
   |<-- text_done -------------------|
   |                                  |
   | [Android TTS 시작: "오늘 서울은 맑고 따뜻해요."]
```

### 음성 채팅 흐름

**Phase 1 (현재)** — Android 단말 STT가 텍스트를 변환하여 `text_input`으로 전송.
`audio_input` 미사용. 흐름은 텍스트 채팅과 동일.

```
Android (단말 STT 처리)          FastAPI Server
   |                                  |
   | [마이크 → SpeechRecognizer]       |
   | [인식된 텍스트 획득]               |
   |--- text_input("날씨 어때?") ----->|
   |                                  |-- Gemini 텍스트 API 호출
   |<-- status(THINKING) ------------|
   |<-- text_chunk × N --------------|
   |<-- text_done -------------------|
   | [Android TTS 시작]               |
```

**Phase 2 (예정)** — 오디오 청크를 서버로 전송, Gemini Live API가 STT + 응답 일괄 처리.
별도 STT 서비스 불필요 (`gemini-2.0-flash-live-exp` 모델이 오디오 직접 이해).

```
Android                          FastAPI Server
   |                                  |
   |--- mode_change(VOICE) ---------->|
   |--- audio_input(is_final:false) ->|  (청크 반복)
   |--- audio_input(is_final:false) ->|
   |--- audio_input(is_final:true) -->|
   |                                  |-- Gemini Live API 호출
   |                                  |   (STT + 응답 동시 처리)
   |<-- status(THINKING) ------------|
   |<-- text_chunk × N --------------|
   |<-- text_done -------------------|
   | [Android TTS 시작]               |
   | [SPEAKING → 완료 → LISTENING]    |
```

---

## 5. Android 연동 포인트

### `RemoteAiRepository.kt` (TODO.md 항목 3번)

```kotlin
// AiRepository 인터페이스 구현체
class RemoteAiRepository(private val serverUrl: String) : AiRepository {

    // 텍스트: text_input 전송 → text_chunk 수집 → text_done 시 완료
    override suspend fun sendMessage(text: String): Flow<String>

    // 음성: audio_input 청크 전송 → text_chunk 수집
    override fun streamAudio(audioFlow: Flow<ByteArray>): Flow<AiResponse>
}
```

`text_done` 이벤트가 수신되면 `Flow`를 `close()`하여 `ChatViewModel.submitUserPrompt`의 collect 루프가 종료되고 TTS가 시작된다.

### `DefaultAppContainer.kt` 분기

```kotlin
override val aiRepository: AiRepository by lazy {
    if (BuildConfig.USE_REMOTE)
        RemoteAiRepository(BuildConfig.SERVER_URL)
    else
        MockAiRepository()
}
```

`BuildConfig.USE_REMOTE` (default `false`) 플래그로 Mock ↔ Remote 전환.

---

## 6. FastAPI 서버 스켈레톤 (참고)

```python
# main.py
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Query
import json

app = FastAPI()

@app.websocket("/ws")
async def websocket_endpoint(
    websocket: WebSocket,
    api_key: str = Query(...)
):
    if api_key != VALID_KEY:
        await websocket.close(code=1008)
        return

    await websocket.accept()
    await websocket.send_json({"type": "status", "code": "READY"})

    try:
        while True:
            data = json.loads(await websocket.receive_text())
            msg_type = data.get("type")

            if msg_type == "text_input":
                await handle_text_input(websocket, data["text"])
            elif msg_type == "audio_input":
                await handle_audio_input(websocket, data)
            elif msg_type == "ping":
                await websocket.send_json({"type": "pong"})
            elif msg_type == "mode_change":
                pass  # 서버 세션 상태 갱신

    except WebSocketDisconnect:
        pass


async def handle_text_input(websocket: WebSocket, text: str):
    await websocket.send_json({"type": "status", "code": "THINKING"})
    # Gemini API 스트리밍 호출
    async for chunk in call_gemini_stream(text):
        await websocket.send_json({"type": "text_chunk", "text": chunk})
    await websocket.send_json({"type": "text_done"})
```

---

## 7. 버전 이력

| 버전 | 날짜 | 변경 내용 |
|---|---|---|
| 1.0 | 2026-05-16 | 최초 작성. TTS 단말 처리 정책 반영. |
