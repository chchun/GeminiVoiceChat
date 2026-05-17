# 04. Server Handoff — 1단계 MVP 통합 가이드

FastAPI 서버(`D:\dev\GeminiVoiceChatServer`)의 1단계 구현이 완료되었다. 이 문서는 Android 측 `RemoteAiRepository` 구현 및 서버 통합 테스트에 필요한 모든 정보를 한 곳에 모은 핸드오프 노트다.

본 문서의 통신 규격은 항상 `03_server_api.md`(Single Source of Truth)를 따른다. 충돌 시 `03_server_api.md` 우선.

> **상태 (2026-05-17 갱신):** Android 클라이언트 end-to-end 통합 검증 완료. §5.2 시나리오 1~3 통과. 검증 과정에서 발견된 추가 이슈/수정사항은 `05_websocket_keepalive_fix.md` 및 본 저장소의 `CHANGELOG.md` v1.1.0 항목 참조.

---

## 1. 서버 상태 (2026-05-17 기준)

| 항목 | 값 |
|---|---|
| 단계 | 1단계 MVP (`INFRA-TEXT-STREAM`) |
| SDK | `google-genai` (신규 공식) |
| 기본 모델 | `gemini-2.5-flash-lite` |
| 검증 | 서버 단독 스모크 테스트 **5/5 PASS** |
| 서버측 TTFT | **~910ms** (KPI < 1000ms, 마진 작음 — 운영 모니터링 필요) |

### 검증된 동작

- 잘못된 `api_key` → HTTP 403 으로 핸드셰이크 거부
- 정상 `api_key` 연결 시 즉시 `status(READY)` 송신
- `ping` → `pong` 즉시 응답
- `text_input` → `status(THINKING)` → `text_chunk × N` → `text_done` 흐름
- 동일 WebSocket 세션 내 **멀티턴 대화 히스토리** 유지 (연결 종료 시 폐기, 외부 영속화 없음)

### 1단계에서 의도적으로 미지원

| 메시지 | 1단계 서버 동작 |
|---|---|
| `audio_input` | `error(code=AUDIO_FORMAT_INVALID)` + `status(ERROR)` 응답, 세션은 유지. 2단계에서 활성화 예정. |
| `mode_change` | 수신은 하지만 세션 모드 상태만 갱신, 실제 처리 로직 차이는 없음(ACK 없음 — 스펙대로). |

→ **Phase 1 (현재)**: STT/TTS 모두 Android 단말이 처리. 서버는 텍스트 in/out만 다룬다.

---

## 2. 접속 정보

### 2.1 엔드포인트 패턴

```
ws://<HOST>:<PORT>/ws?api_key=<WS_API_KEY>
```

| 변수 | 환경별 값 |
|---|---|
| `<HOST>` (에뮬레이터) | `10.0.2.2` (Android 에뮬레이터에서 호스트 PC 가리키는 특수 IP) |
| `<HOST>` (실기기) | PC의 LAN IP (예 `192.168.x.x`) — `ipconfig` 로 확인 |
| `<PORT>` | `8000` (서버 `.env`의 `SERVER_PORT`) |
| `<WS_API_KEY>` | **§3 참조 — 서버와 정확히 같은 값** |

### 2.2 PC IP 확인 (실기기 테스트용)

PowerShell:
```powershell
ipconfig | findstr IPv4
```
"무선 LAN 어댑터 Wi-Fi"의 IPv4 주소를 사용한다.

### 2.3 Windows 방화벽 (실기기 테스트 시 필수)

처음 8000번 포트로 외부 접속 시 Windows Defender 방화벽이 차단할 수 있다. PowerShell **관리자 권한**으로 한 번만 실행:

```powershell
New-NetFirewallRule -DisplayName "GeminiVoiceChat Dev Server 8000" `
  -Direction Inbound -LocalPort 8000 -Protocol TCP -Action Allow -Profile Private
```

(에뮬레이터만 쓸 거면 불필요 — 같은 PC 내 루프백)

---

## 3. `WS_API_KEY` 동기화 (간접 방식)

> 본 문서에는 실제 키 값을 기재하지 않는다. 서버 `.env`와 Android `local.properties` 양쪽을 사용자가 직접 동기화한다.

### 3.1 서버 측 키 위치

```
D:\dev\GeminiVoiceChatServer\.env
```

해당 파일의 `WS_API_KEY=...` 줄의 값을 **그대로 복사**한다. (이 파일은 git에 커밋되지 않는다 — `.gitignore` 등록됨)

### 3.2 Android 측 키 주입 위치

```
D:\dev\GeminiVoiceChat\local.properties
```

`local.properties`에 다음 3줄을 추가한다 (`local.properties`는 Android 표준에 의해 git에 커밋되지 않는다):

```properties
USE_REMOTE=true
SERVER_URL=ws://10.0.2.2:8000/ws
WS_API_KEY=<서버 .env에서 복사한 동일 값>
```

> 에뮬레이터로 시작하길 권장. 실기기 테스트 시 `SERVER_URL` 호스트만 LAN IP로 교체.

### 3.3 키가 다를 때 증상

- 서버 로그: `WebSocket auth failed: bad api_key`
- 클라이언트: WebSocket 핸드셰이크가 HTTP 403으로 즉시 거부됨 (메시지 한 번도 못 받음)

---

## 4. Android 측 작업 체크리스트

### 4.1 `app/build.gradle.kts` — BuildConfig 필드 추가

`local.properties`의 값을 컴파일 타임에 BuildConfig로 노출한다.

```kotlin
import java.util.Properties

android {
    defaultConfig {
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        buildConfigField(
            "boolean",
            "USE_REMOTE",
            localProps.getProperty("USE_REMOTE", "false"),
        )
        buildConfigField(
            "String",
            "SERVER_URL",
            "\"${localProps.getProperty("SERVER_URL", "ws://10.0.2.2:8000/ws")}\"",
        )
        buildConfigField(
            "String",
            "WS_API_KEY",
            "\"${localProps.getProperty("WS_API_KEY", "")}\"",
        )
    }
    buildFeatures {
        buildConfig = true
    }
}
```

### 4.2 의존성

Kotlin coroutine + WebSocket + JSON 직렬화에 OkHttp + kotlinx-serialization 조합 권장 (이미 일부 사용 중이면 그대로 활용).

```kotlin
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
```
(버전은 프로젝트 정책에 맞게 조정)

### 4.3 `RemoteAiRepository.kt` 구현 가이드

위치: `app/src/main/java/com/aromit/geminivoicechat/data/repository/RemoteAiRepository.kt`

인터페이스 충족 사항 (`domain/repository/AiRepository.kt`):
- `suspend fun sendMessage(text: String): Flow<String>` — **1단계에서 구현 본체**
- `fun streamAudio(audioFlow: Flow<ByteArray>): Flow<AiResponse>` — **1단계에서는 `throw UnsupportedOperationException("Stage 2")`** (서버도 미지원)

**`sendMessage` 동작 사양:**

1. WebSocket 연결: `${BuildConfig.SERVER_URL}?api_key=${BuildConfig.WS_API_KEY}`
2. 서버의 첫 패킷 `status(code=READY)` 수신 대기
3. `{"type":"text_input","text":"..."}` 송신
4. `status(code=THINKING)` 수신 (UI 로딩 트리거에 활용)
5. `text_chunk` 수신 시 그때마다 `emit(textChunk.text)`
6. `text_done` 수신 시 Flow 종료 (정상 `close`)
7. `error` 패킷 수신 시 Flow에 예외 throw (또는 별도 sealed result로 표현)

**핵심 제약:**
- WebSocket 연결 라이프사이클을 **세션 단위**로 관리할 것. 서버는 같은 WebSocket 세션 내에서만 멀티턴 히스토리를 유지한다. 매 메시지마다 연결을 새로 열면 매번 첫 턴으로 취급된다.
- 한 곳에서 한 번에 한 흐름만 처리 (텍스트 한 턴이 진행 중일 때 새 메시지를 보내지 말 것 — 1단계 동시성 가정).
- 30초 간격 `{"type":"ping"}` 송신 권장 (idle keep-alive).

**Flow가 종료되어야 `ChatViewModel.submitUserPrompt`의 collect 루프가 끝나고 Android TTS가 시작됨** — 이 시점 트리거가 `text_done` 수신이다.

### 4.4 `DefaultAppContainer.kt` — `USE_REMOTE` 분기

현재(2026-05-17 기준):
```kotlin
override val aiRepository: AiRepository by lazy { MockAiRepository() }
```

변경 후:
```kotlin
override val aiRepository: AiRepository by lazy {
    if (BuildConfig.USE_REMOTE) {
        RemoteAiRepository(
            serverUrl = BuildConfig.SERVER_URL,
            apiKey = BuildConfig.WS_API_KEY,
        )
    } else {
        MockAiRepository()
    }
}
```

→ `TODO.md` 항목 3번이 이 단계에서 닫힌다.

---

## 5. 통합 스모크 테스트 시나리오

### 5.1 사전 준비
1. PC에서 서버 기동:
   ```powershell
   cd D:\dev\GeminiVoiceChatServer
   venv\Scripts\activate
   uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
   ```
2. Android에서 `USE_REMOTE=true`로 빌드/설치.

### 5.2 검증 항목

| # | 시나리오 | 기대 동작 | 어디서 확인 |
|---|---|---|---|
| 1 | 앱 실행 후 첫 화면 진입 | 서버 로그 `WS connected trace_id=...` | 서버 콘솔 |
| 2 | "안녕" 입력 | 서버 응답 텍스트 받아서 단말 TTS 발화 | 단말 + 서버 콘솔의 `turn_latency` JSON |
| 3 | 멀티턴: "내 이름은 민수야" → "내 이름이 뭐였지?" | 두 번째 응답에 "민수" 포함 | 단말 화면/TTS |
| 4 | 앱 백그라운드 → 재진입 | 새 세션은 이전 히스토리를 모름 (메모리 폐기됨) | 동일 |
| 5 | Wi-Fi off → 메시지 송신 | 적절한 에러 노출 (1단계에선 최소한 크래시 없음) | 단말 |

### 5.3 서버 로그에서 봐야 할 키 라인

- `WS connected trace_id=<uuid>` — 연결 성공
- `{"event":"turn_latency","trace_id":"...","ttft_ms":<N>,...}` — 한 턴 완료 (TTFT 확인)
- `WS disconnected trace_id=<uuid>` — 연결 종료
- `WebSocket auth failed: bad api_key` — 키 불일치 (있으면 즉시 §3 점검)

---

## 6. 자주 발생할 이슈와 해결

| 증상 | 원인 / 조치 |
|---|---|
| Android 빌드 후 접속이 즉시 끊김 (HTTP 403) | `WS_API_KEY` 불일치 — §3 동기화 재확인 |
| 에뮬레이터에서 접속 불가 | `SERVER_URL`이 `10.0.2.2`가 아니거나 서버가 `127.0.0.1`에만 bind. 서버는 `--host 0.0.0.0`으로 띄울 것 |
| 실기기에서 접속 불가 | (1) PC와 같은 Wi-Fi인지 (2) Windows 방화벽 인바운드 (§2.3) (3) 학교/회사망의 클라이언트 격리 정책 |
| `CLEARTEXT communication ... not permitted` | Android 9+ 기본 차단. `AndroidManifest.xml`에 `android:usesCleartextTraffic="true"` 또는 `network_security_config.xml`에 LAN IP를 cleartext 허용 도메인으로 등록 |
| 멀티턴이 안 됨 (이름 회상 실패) | 매 메시지마다 WebSocket을 새로 여는 구현. 한 세션을 유지하도록 `RemoteAiRepository` 재설계 (§4.3) |
| TTFT 1초 초과 | (1) 네트워크 RTT 측정 (2) Gemini 응답 자체 지연 — 서버 `turn_latency` 로그의 `ts_llm_req → ts_llm_ttft` 구간 확인. 필요시 `.env`의 `GEMINI_MODEL=gemini-flash-latest` 시도 |
| `AUDIO_FORMAT_INVALID` 에러 수신 | 1단계는 `audio_input` 미지원이 정상. 마이크 입력은 단말 STT(`SpeechRecognizer`)로 처리해서 `text_input`으로 전송해야 함 |

---

## 7. 1단계 범위에서 다루지 않는 것 (Phase 2 백로그)

다음 항목은 **1단계에서 손대지 말 것** — 2단계 진입 시점에 일괄 재설계한다.

- `streamAudio(...)` 본체 구현
- `VoiceController` 인터페이스 분리 (`LocalVoiceController` vs `StreamingVoiceController` — TODO.md 4번)
- Gemini Live API 모델 전환 (`gemini-2.0-flash-live-exp` 류)
- 서버 측 PCM 청크 디코딩 / STT 결합

---

## 8. 참고

- 서버 저장소: `D:\dev\GeminiVoiceChatServer`
- 서버 요구사항/아키텍처: `D:\dev\GeminiVoiceChatServer\docs\01_server_requirements.md`, `02_server_architecture.md`
- 서버 스모크 테스트: `D:\dev\GeminiVoiceChatServer\scripts\ws_smoke.py`
- 통신 스펙(SSoT): `03_server_api.md` (본 폴더)
- Android Phase 1 TODO: `D:\dev\GeminiVoiceChat\TODO.md` 항목 3, 4
