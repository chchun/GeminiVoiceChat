# Changelog

본 프로젝트의 모든 주목할 만한 변경 사항을 기록한다.
형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 기반이며 버전 체계는 [Semantic Versioning](https://semver.org/lang/ko/)을 따른다.

---

## [1.1.0] - 2026-05-17

Phase 1 — 실제 FastAPI 서버(WebSocket)와의 end-to-end 통합. Mock-only였던 v1.0.0에서 처음으로 외부 서버 응답이 단말까지 도달.

### Added

- **`data/repository/RemoteAiRepository.kt`** — OkHttp WebSocket 기반 `AiRepository` 구현체.
  - 세션 단위 WebSocket 연결 유지 (서버 멀티턴 히스토리 보존).
  - `text_input` 송신 / `status` / `text_chunk` / `text_done` / `error` 수신 처리.
  - `connectionMutex`로 한 턴 직렬화. `streamAudio`는 Phase 2 스텁(`UnsupportedOperationException`).
- **`DefaultAppContainer` USE_REMOTE 분기** — `BuildConfig.USE_REMOTE` true 시 `RemoteAiRepository`, false 시 `MockAiRepository`. default false.
- **BuildConfig 필드 3종** — `app/build.gradle.kts`에서 `local.properties`의 `USE_REMOTE` / `SERVER_URL` / `WS_API_KEY`를 컴파일 타임에 노출.
- **`AndroidManifest.xml`** — `INTERNET` 권한, 개발용 cleartext traffic 허용.
- **의존성 추가**
  - `com.squareup.okhttp3:okhttp:4.12.0`
  - `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3`
  - `org.jetbrains.kotlin.plugin.serialization`
  - `androidx.lifecycle:lifecycle-process` (백그라운드 재연결용)
- **백그라운드 → foreground 자동 재연결** — `ProcessLifecycleOwner.ON_START` 옵저버에서 `ensureConnectedLocked()` 트리거. Samsung One UI가 백그라운드 소켓을 강제 abort하는 동작에 대응.
- **`@Volatile isConnectionAlive` 플래그** — `READY` 수신 시 true, `onFailure`/`onClosing`/`closeConnectionLocked`에서 false. 좀비 소켓을 정확히 감지하여 reconnect 분기.
- **진단 로그 (`Log.i`)** — `ON_START` 발화, `send text_input accepted=...`, `inbound status/text_chunk/text_done/error` 등. 운영 노이즈 관리는 후속 작업(TODO).
- **문서 신규 3건**
  - `app/docs/03_server_api.md` — 클라이언트↔서버 WebSocket 메시지 규격 (Single Source of Truth).
  - `app/docs/04_server_handoff.md` — Phase 1 통합 가이드 (엔드포인트, API 키 동기화, 통합 스모크 시나리오).
  - `app/docs/05_websocket_keepalive_fix.md` — Keepalive 처방 및 Samsung 백그라운드 reaping 후속 조치.

### Fixed

- **(Critical) `kotlinx.serialization` `encodeDefaults = true`** — `TextInput.type = "text_input"` 같은 default value 필드가 직렬화 시 누락되어 서버가 `{"text":"..."}`만 수신, `INTERNAL_ERROR: Unknown type: None`을 반환하던 문제. **이 fix 전까지 Android 클라이언트는 한 번도 정상 응답을 받은 적이 없었다.** 서버 단독 `ws_smoke.py` (Python)는 통과한 상태였기에 발견이 늦었음.
- **WebSocket idle keepalive** — 코루틴 기반 30초 JSON ping(`{"type":"ping"}`)을 OkHttp `pingInterval(20, SECONDS)` 프로토콜 레벨 PING 프레임(opcode 0x9)으로 교체. Android Doze/App Standby에서 throttle되지 않고 네트워크 레이어가 처리. Cloud Run LB의 idle 종료 방지. 상세는 `05_websocket_keepalive_fix.md`.
- **재연결 가용성 판정 오류** — 기존 `readyDeferred.isCompleted` 체크는 최초 READY 도달 이후 영구 true라서 OS가 죽인 좀비 소켓을 감지하지 못했음. `isConnectionAlive` 플래그로 분리.

### Changed

- **`03_server_api.md` §2-4 `ping`** — "Android는 30초 간격으로 전송 권장" 문구 삭제. 클라이언트는 JSON ping 미사용. (양식은 서버측 향후 활용 대비로 유지)
- **`02_architecture.md`** — `RemoteAiRepository`를 "향후 단계"에서 "Phase 1 구현 완료"로 갱신. 백그라운드 재연결 패턴 명시.
- **`TODO.md` 항목 3번** — Phase 1 end-to-end 검증 결과 및 후속 조치(reconnect, encodeDefaults) 추가 기록.

### Removed

- `RemoteAiRepository`의 `pingJob: Job?` 필드, 코루틴 ping 루프, `PING_INTERVAL_MS` / `PING_PAYLOAD` 상수.

---

## [1.0.0] - 2026-05-16

최초 릴리스. Mock-only Phase 0 — 외부 서버 없이도 UI/UX 완성도를 검증할 수 있는 클라이언트.

### Added

- Jetpack Compose 기반 채팅 화면 (`ui/chat/`).
- 보이스 오버레이 + STT/TTS 상태머신 (`ui/voice/`, `AndroidVoiceController`).
- DIP 강제 구조 — `domain/repository/AiRepository` 인터페이스 + `MockAiRepository` 구현 + `DefaultAppContainer` 수동 DI.
- Samsung 디바이스의 `ERROR_SERVER_DISCONNECTED (11)` 자동 1회 재시도 / on-device → network recognizer 폴백.
- 스마트 자동 스크롤 (`AutoScrollEffect`) — 사용자 메시지는 강제 스크롤, AI 스트리밍은 사용자가 하단에 있을 때만 따라감.
- 문서: `01_requirements.md` (PRD), `02_architecture.md` (DIP/레이어 가이드).
