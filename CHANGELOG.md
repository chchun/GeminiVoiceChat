# Changelog

본 프로젝트의 모든 주목할 만한 변경 사항을 기록한다.
형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 기반이며 버전 체계는 [Semantic Versioning](https://semver.org/lang/ko/)을 따른다.

---

## [1.5.0] - 2026-08-14

A2UI 서버 연동 — 로컬 키워드 데모를 실서버(saferyn-langgraph, ncloud) 스트리밍으로 전환 (specs/001).
SDD 하네스 도입 (constitution.md / DESIGN.md / specs/ / CLAUDE.md 리팩토링 — 2026-08-13).

### Added

- **`domain/model/AiStreamEvent.kt`** — 스트림 이벤트 sealed class (`TextChunk` | `A2UIEnvelope`).
  `AiRepository.sendMessage` 반환 타입을 `Flow<String>` → `Flow<AiStreamEvent>` 로 확장.
- **`a2ui/A2UIEnvelopeApplier.kt`** — A2UI v0.9 엔벨로프 3종(createSurface /
  updateComponents / updateDataModel) 누적 적용기. 파싱 실패는 로그+무시 (스트림 보호).
- **`a2ui/A2UIServerCards.kt`** — 서버 커스텀 카드 6종 Compose 렌더러:
  `CompletionGaugeCard` / `ActvScoreSummaryCard` / `WeeklyScheduleCard` /
  `PendingApprovalListCard` / `OpertPlanStatusCard` / `OpertStopStatusCard` + 미지원 kind 폴백.
- **`A2UIEnvelopeApplierTest`** — JVM 단위 테스트 5건 (`org.json:json` 테스트 의존성 추가).
- SDD 하네스: `constitution.md`(P1~P13) / `DESIGN.md` / `specs/001-a2ui-server-streaming/` /
  `KICKOFF_PROMPT.md` / `app/docs/guide/sdd_하네스_전환_가이드.md`.

### Added (2026-08-14 후속 — specs/001 T110/T112, specs/002)

- **표준(basic catalog) 컴포넌트 v0.9 와이어 파싱** — 아차사고/안전제안 폼
  (Card/Column/Row/Text/ChoicePicker/TextField/Button + readonly, Row 균등 분배) 렌더링.
- **`AccidentListCard`** 렌더러 — 재해 발생 내역 (서버 빌더 반영에 대응).
- **A2UI 액션 서버 왕복** (specs/002) — `AiRepository.sendA2UIAction` →
  `POST /a2ui/action` (파일 유무와 무관하게 항상 multipart — JSON 바디는 서버 500).
  폼 제출 시 실제 등록, 응답 `md` 말풍선 표시, 성공 시 제출 버튼을 완료 텍스트로 치환.
- **FileUpload 사진 첨부** (specs/002 T105) — Photo Picker(권한 불필요) 선택/제거 칩,
  maxFiles·maxSizeMb 제한, multipart `files` 파트 전송 (context 에는 파일명만).

### Changed

- **`RemoteAiRepository`** — SSE 라우팅을 data JSON `type` 필드 → **SSE 이벤트명 기준**으로
  교체 (`token`/`a2ui`/`error`). 실서버 a2ui 엔벨로프가 조용히 버려지던 버그 수정.
- **`ChatViewModel`** — A2UI 로컬 키워드 매칭 분기 제거 (모든 입력이 서버로),
  엔벨로프 수신 시 서피스 누적 적용 + 첫 서피스를 메시지에 부착.
- `app/docs/08_a2ui_generative_ui.md` §8 — 확정 서버 와이어 계약 문서화 (구 초안은 §9 폐기).

### Removed

- GeminiVoiceChatServer NDJSON(`{"type":"a2ui_create"}`) 형식 지원 — 해당 서버 미사용 확정.
  (`A2UISurfaceParser`/`A2UIScenarios` 는 로컬 데모·픽스처로 존치, 호출 경로만 제거)

---

## [1.4.0] - 2026-06-17

A2UI v0.9 — AI 에이전트가 채팅 안에서 인터랙티브 UI 서피스를 직접 생성하는 생성형 UI 레이어 추가.

### Added

- **`a2ui/` 패키지** — A2UI v0.9 런타임 전체를 Kotlin/Compose 네이티브로 구현.
  - **`A2UIModel.kt`** — A2UI 전체 타입 시스템 정의.
    - `A2UIValue` — `Static` / `PathBound` / `FormatStr` 3종 값 바인딩 타입.
    - `A2UICondition` — `Required` / `Numeric` / `And` / `Or` 조건 트리 (버튼 유효성 검사용).
    - `A2UIComponent` (sealed) — `Card`, `Column`, `Row`, `TextComp`, `IconComp`, `Divider`, `ButtonComp`, `TextFieldComp`, `CheckBoxComp`, `ChoicePickerComp`, `SliderComp`, `SwitchComp`, `TagInputComp`, `SelectComp`, `RiskCardList` 14종.
    - `A2UISurface` — 서피스 상태 (컴포넌트 트리 + 데이터 모델 + 테마).
  - **`A2UIRuntime.kt`** — A2UI 실행 엔진.
    - JSON Pointer(RFC 6901) 경로 해석 (`getAtPath` / `setAtPath`).
    - `formatString` 보간 — `${/deploy/canary}%` 형태의 템플릿을 데이터 모델 값으로 실시간 치환.
    - 조건 평가 (`evalCondition`) — 중첩 `And/Or/Numeric/Required` 재귀 평가.
    - `deepCopyModel` — 상태 업데이트 시 불변성 보장을 위한 데이터 모델 deep copy.
  - **`A2UIScenarios.kt`** — 4개 데모 시나리오 정의 및 키워드 자동 매칭.
    - **인시던트 보고 폼** (`인시던트`, `incident`, `장애 보고` 등) — ChoicePicker(P1/P2/P3) + TextField(서비스/증상) + CheckBox(고객 영향) + required 유효성 버튼.
    - **카나리 배포 승인** (`배포`, `deploy`, `카나리` 등) — Slider(트래픽 %) + formatString 실시간 텍스트 + CheckBox(헬스체크) + `And(healthOk, numeric(canary, min=1))` 복합 조건 버튼.
    - **서비스 상태 카드** (`상태 카드`, `서비스 상태` 등) — FormatStr 바인딩 메트릭(p99/에러율/RPS) + ChoicePicker 기간 선택 + 새로고침 액션.
    - **위험성평가 자동 생성** (`위험성평가`, `위험 평가` 등) — TagInput(공종) + Select(장소/항목 수) + 생성 후 결과 카드 리스트.
    - 6개 공종(배관·용접·비계·전기·굴착·도장) × 5개 항목 샘플 위험성평가 데이터 내장.
  - **`A2UIRenderer.kt`** — `A2UINode` Composable 재귀 렌더러 (웹앱 `a2ui.jsx`의 Compose 포트).
    - `A2UISurfaceView` — 서피스 진입점 Composable.
    - 모든 14종 컴포넌트 Compose 구현 (`FilterChip`, `Slider`, `ExposedDropdownMenu`, `InputChip`, `FlowRow` 등 Material3 활용).
    - `RiskRowCard` — DataTable 대신 모바일 최적화 리스크 카드 (위험도별 색상 배지).

- **`ChatMessage.surfaceId: String?`** — AI 메시지가 A2UI 서피스를 선택적으로 보유할 수 있도록 필드 추가.
- **`ChatState.surfaces: Map<String, A2UISurface>`** — 활성 서피스 상태를 ViewModel이 중앙 관리.
- **`ChatViewModel.onA2UIData()`** — 폼 입력 변경 시 JSON Pointer 경로로 데이터 모델 업데이트. deep copy로 불변성 보장.
- **`ChatViewModel.onA2UIAction()`** — 버튼 액션 이벤트 처리.
  - `create_incident` → 인시던트 번호 채번 + 접수 확인 메시지.
  - `start_deploy` → 카나리 배포 시작 메시지.
  - `cancel_deploy` → 취소 메시지.
  - `refresh_status` → 지표 재조회 메시지.
  - `generate_risk` → 공종별 위험성평가 결과 서피스 생성 후 채팅에 삽입.
- **`ChatScreen.AiMessage()`** — 마크다운 아래에 `A2UISurfaceView` 조건부 렌더링 추가.
- **문서 추가**: `app/docs/08_a2ui_generative_ui.md` — A2UI 프로토콜, 컴포넌트 레퍼런스, 데이터 바인딩, 시나리오별 동작 흐름.

### Changed

- **`ChatViewModel.submitUserPrompt()`** — 키워드 감지 시 서버 호출 없이 로컬 시나리오 응답으로 단락. 매칭 없는 일반 메시지는 기존 HTTP POST 스트리밍 유지.
- **`ChatScreen.MessageList()`** — `surfaces`, `onA2UIData`, `onA2UIAction` 파라미터 추가.

---

## [1.3.0] - 2026-06-17

ncloud 연동 — 서버 통신 프로토콜을 WebSocket에서 HTTP POST 청크 스트리밍으로 전환. `feature/ncloud-integration` 브랜치.

### Added

- **`local.properties` 설정값 추가** — `SERVER_URL=http://223.130.159.179:8000/chat/stream`. `WS_API_KEY` 는 현재 미사용(서버 인증 없음).

### Changed

- **`RemoteAiRepository.kt`** — WebSocket(OkHttp `WebSocketListener`) → HTTP POST 청크 스트리밍으로 전면 재작성.
  - 요청: `POST /chat/stream` · `Content-Type: application/json` · body `{"session_id": "<uuid>", "message": "<text>"}`.
  - 응답: chunked transfer-encoding 텍스트 스트림. `okio.Buffer`로 8 KB 단위 읽기 → `Flow.emit`.
  - 세션 ID는 앱 실행 동안 고정 UUID — 서버 측 멀티턴 대화 컨텍스트 유지.
  - `connectTimeout` 10 s / `readTimeout` 120 s (스트리밍 응답 대비).
  - WebSocket 관련 import(`WebSocketListener`, `WebSocket`, `response.handleText` 등) 전량 제거.
  - `@Suppress("UNUSED_PARAMETER") apiKey` 파라미터 보존 — 향후 인증 도입 대비 인터페이스 시그니처 유지.
- **`app/docs/03_server_api.md`** — ncloud HTTP POST 규격 섹션 추가 (v1.3).

### Removed

- WebSocket keepalive (`pingInterval`, `ProcessLifecycleOwner` 옵저버, `connectionMutex`, `isConnectionAlive` 플래그) — HTTP POST는 연결 유지가 불필요하므로 전량 제거.
- `kotlinx.serialization`의 `TextInput` / `InboundMessage` 봉인 클래스 — HTTP POST 응답은 평문 텍스트 청크이므로 JSON 파싱 불필요.

---

## [1.2.0] - 2026-05-18

Phase 2 UI — AI 답변 마크다운 렌더링, 메시지별 TTS 토글, 인라인 음성 받아쓰기.

### Added

- **AI 메시지 마크다운 렌더링** — AI 답변이 말풍선 없이 풀폭으로 표시되며 `compose-markdown` 라이브러리를 통해 **굵게 / 기울임 / 제목 / 리스트 / 인라인 코드 / 표** 등 표준 마크다운을 렌더링.
- **스파클(✦) 아이콘 회전** — AI가 응답을 스트리밍하는 동안 메시지 상단 좌측의 스파클 아이콘이 회전. 완료 시 정지.
- **메시지별 TTS 토글** — AI 메시지 우측 상단에 스피커(🔊) 아이콘 추가. 탭하면 해당 메시지를 TTS 재생, 재생 중 탭하면 정지(아이콘이 ■로 전환). 다른 메시지의 스피커를 탭하면 이전 재생이 자동 중단.
- **인라인 음성 입력** — 마이크 버튼 탭 시 별도 화면 이동 없이 InputBar가 파형 애니메이션으로 전환. 음성 인식이 완료되면 자동으로 텍스트가 전송되고 키보드 모드로 복귀 (한 번만 받아쓰기).
- **음성 모드 활성 인디케이터** — 음성 입력 중 TopAppBar 우측에 초록 원형 마이크 칩 표시 (탭 동작 없음).
- **`ChatState.playingMessageId`** — 현재 TTS 재생 중인 메시지 ID를 추적하는 상태 필드.
- **의존성 추가**
  - `com.github.jeziellago:compose-markdown:0.5.7` (JitPack)
- **문서 추가**: `app/docs/07_ui_markdown_voice_inline.md` — 변경 사양, 상태 전환 흐름, API 변경 요점.

### Changed

- **`ChatViewModel` 이벤트 수집** — `voiceEventsJob` 패턴(startVoiceMode 시 시작, endVoiceMode 시 취소) → `init {}` 블록에서 항상 수집으로 변경. TTS 이벤트(`SpeakingStarted`/`SpeakingFinished`)를 음성 모드 밖에서도 처리하기 위함.
- **음성 입력 흐름** — 기존 `LISTENING → THINKING → SPEAKING → LISTENING` 자동 루프 → 한 번만 받아쓰기 후 자동 전송. `speakResult` 플래그 및 `resumeListeningIfActive()` 제거.
- **`settings.gradle.kts`** — `dependencyResolutionManagement.repositories`에 JitPack 저장소 추가.

### Removed

- **`VoiceOverlay.kt`** — 전체화면 오버레이 방식 폐기. 인라인 파형으로 대체.

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
