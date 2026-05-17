# WebSocket Keepalive Fix — Handoff

**대상 파일:** `app/src/main/java/com/aromit/geminivoicechat/data/repository/RemoteAiRepository.kt`

> **상태 (2026-05-17):** ✅ 적용 완료. 단, **이 fix 단독으로는 Samsung One UI의 백그라운드 socket reaping을 막지 못함이 실측으로 확인되어** 후속 조치를 추가했다. 본 문서 하단 **§ Addendum** 참조.

## 문제

Cloud Run 배포 환경에서 앱이 백그라운드로 전환되었다가 돌아올 때 WebSocket 연결이 끊어짐.

```
WS failure: Software caused connection abort
java.net.SocketException: Software caused connection abort
```

**원인:**
- 현재 코드는 `pingInterval(0)` (비활성) + 코루틴 기반 JSON ping (30초) 사용
- Android Doze/App Standby 모드에서 백그라운드 코루틴이 throttle되어 ping이 중단됨
- Cloud Run 로드밸런서가 idle 연결을 TCP RST로 강제 종료

## 수정 내용

### 1. OkHttpClient — pingInterval 활성화 (라인 47~52)

**Before:**
```kotlin
private val client = OkHttpClient.Builder()
    // 앱 레벨 ping(JSON)을 사용. OkHttp 내장 ping/pong은 끔.
    .pingInterval(0, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.MILLISECONDS)
    .build()
```

**After:**
```kotlin
private val client = OkHttpClient.Builder()
    .pingInterval(20, TimeUnit.SECONDS)  // 프로토콜 레벨 WebSocket PING 프레임 (opcode 0x9)
    .readTimeout(0, TimeUnit.MILLISECONDS)
    .build()
```

OkHttp 내장 pingInterval은 JSON 텍스트가 아닌 WebSocket 프로토콜 레벨 PING 프레임(opcode 0x9)을 전송합니다. 이는 Android 네트워크 레이어에서 처리되므로 앱이 백그라운드일 때도 동작하며, Cloud Run 로드밸런서가 이를 인식하여 연결을 유지합니다.

---

### 2. pingJob 관련 코드 전체 제거

OkHttp가 keepalive를 담당하므로 앱 레벨 pingJob은 불필요합니다.

#### 2-1. 필드 선언 제거

```kotlin
// 제거 대상
private var pingJob: Job? = null
```

#### 2-2. ensureConnectedLocked() 내 pingJob 실행 블록 제거 (라인 128~134)

```kotlin
// 제거 대상
pingJob?.cancel()
pingJob = scope.launch {
    while (true) {
        delay(PING_INTERVAL_MS)
        val active = webSocket ?: break
        active.send(PING_PAYLOAD)
    }
}
```

#### 2-3. closeConnectionLocked() 내 pingJob 정리 코드 제거 (라인 138~139)

```kotlin
// 제거 대상
pingJob?.cancel()
pingJob = null
```

#### 2-4. companion object 상수 제거 (라인 216~217)

```kotlin
// 제거 대상
private const val PING_INTERVAL_MS = 30_000L
private const val PING_PAYLOAD = """{"type":"ping"}"""
```

---

### 3. handleInbound() — ping 수신 처리 추가 (라인 172~205)

서버가 `{"type":"ping"}`을 보낼 경우를 대비해 명시적으로 처리합니다.

**Before:**
```kotlin
"pong" -> Unit
else -> Log.d(TAG, "Unhandled inbound: $raw")
```

**After:**
```kotlin
"ping" -> Unit  // 서버 keepalive ping 무시
"pong" -> Unit
else -> Log.d(TAG, "Unhandled inbound: $raw")
```

---

## 수정 후 구조 요약

| 항목 | 변경 전 | 변경 후 |
|------|--------|--------|
| Keepalive 방식 | 코루틴 JSON ping (30s) | OkHttp PING 프레임 (20s) |
| 동작 레이어 | Application layer | WebSocket protocol layer |
| 백그라운드 안정성 | Android Doze에 취약 | 네트워크 레이어 처리로 안정 |
| pingJob 필드 | 필요 | 제거 |
| PING_INTERVAL_MS 상수 | 필요 | 제거 |
| PING_PAYLOAD 상수 | 필요 | 제거 |

## 참고

- 서버(GeminiVoiceChatServer)의 30초 text ping 코드는 이미 제거되었습니다.
- OkHttp의 pingInterval에 대한 서버 응답(PONG)은 Starlette/uvicorn이 자동 처리합니다. 별도 서버 수정 불필요.

---

## Addendum (2026-05-17) — 실측 결과 및 후속 조치

본 문서의 처방(`pingInterval(20s)`)을 적용한 뒤 Samsung Galaxy S24 (SM-S928N, One UI on Android 16) 실측에서 다음 사실이 확인되었다.

### 관측된 동작

- 앱이 백그라운드로 가거나 화면이 꺼지면 **수 초 내에 `WS failure: Software caused connection abort` 로그 발생**.
- 스택: `socketRead0` (Native) → `ConscryptEngineSocket$SSLInputStream` — OkHttp가 사용 중인 SSL 소켓을 **OS/커널이 로컬에서 abort**한 것 (ECONNABORTED).
- `pingInterval`이 무용해서가 아니라, 네트워크 idle보다 **OS의 백그라운드 프로세스 socket reaping이 더 빠르게 일어남**. 본 문서의 진단(Cloud Run LB idle 종료) 외에 Samsung 디바이스 측의 별개 원인이 추가로 존재.

### 추가 조치 — `ProcessLifecycleOwner` 기반 자동 재연결

원래의 keepalive 처방은 그대로 유지하면서, 다음을 함께 적용:

1. **`@Volatile var isConnectionAlive: Boolean` 플래그 신설**
   - `handleInbound`에서 `READY` 수신 시 `true`, `onFailure`/`onClosing`/`closeConnectionLocked`에서 `false`.
   - 기존 `readyDeferred.isCompleted` 체크는 한 번 READY 도달하면 영구 `true`라서 좀비 소켓 감지 불가 → 이 플래그가 진짜 가용성 판단 기준.
   - `ensureConnectedLocked()`의 early-return 조건도 `isConnectionAlive && webSocket != null`로 변경.

2. **`ProcessLifecycleOwner` 옵저버 등록**
   - `init {}`에서 `Dispatchers.Main`로 dispatch하여 `addObserver`.
   - `DefaultLifecycleObserver.onStart`에서 `ensureConnectedLocked()` 호출 → foreground 복귀 시 자동 prewarm.
   - 의존성: `androidx.lifecycle:lifecycle-process` (버전은 `lifecycle-runtime-ktx`와 공유).

### 부가로 발견된 결정적 버그 — `encodeDefaults`

검증 도중 별개로 발견된 **클라이언트 첫날부터의 잠재 버그**:

- `kotlinx.serialization`의 `Json`은 기본적으로 `encodeDefaults = false`. 즉 default value를 가진 필드는 직렬화에서 누락된다.
- `TextInput(type: String = "text_input", text: String)`이 `{"text":"..."}`로만 인코딩되어 서버가 `Unknown type: None`으로 거부.
- 이 fix(`encodeDefaults = true`) 없이는 RemoteAiRepository가 실제로 한 번도 응답을 받은 적이 없었다 (서버 단독 `ws_smoke.py`는 통과했기에 발견이 늦었음).

### 정리: 최종 keepalive 전략

| 레이어 | 메커니즘 | 책임 |
|---|---|---|
| 네트워크 idle 방지 | OkHttp `pingInterval(20s)` 프로토콜 PING | Cloud Run LB / 중간 NAT의 idle disconnect 방지 |
| 백그라운드 socket 사망 복구 | `ProcessLifecycleOwner.ON_START` → 자동 reconnect | OS의 background socket reaping 보상 |
| 송신 직전 안전망 | `sendMessage` 내 `ensureConnectedLocked()` | 둘 다 실패했을 경우 lazy 복구 |

세 층이 직교한다. Phase 2에서 streaming audio가 들어와도 본 구조는 그대로 유효.
