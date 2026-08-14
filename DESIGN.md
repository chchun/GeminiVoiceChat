# DESIGN.md — GeminiVoiceChat 시스템 설계 (현행)

갱신 2026-08-13. 이 문서는 **현재 구현된 시스템의 설계도**다 — 제품이 무엇이고,
데이터가 어떻게 흐르는지를 한 파일에서 답한다. 신규 기능의 설계는 `specs/NNN-*/plan.md`,
불변 원칙은 `constitution.md`, 상세 계약은 `app/docs/` 가 담당한다.

## 1. 제품 정의

**AI 챗봇 Android 앱** — ncloud 서버의 AI 에이전트와 대화한다.

- 입력: 텍스트 타이핑, 인라인 음성 받아쓰기, 전체화면 보이스 모드 (로컬 STT)
- 출력: **마크다운 텍스트 스트리밍 + A2UI v0.9 생성형 UI 서피스** — 에이전트가
  텍스트와 함께(또는 대신) 인터랙티브 폼·대시보드·카드 목록을 채팅 말풍선 안에 렌더링
- 보이스 모드 응답은 로컬 TTS 로 발화 (서버는 텍스트만 반환 — TTS 정책은
  `app/docs/03_server_api.md` 상단 참조)

## 2. 계층 구조 (constitution P1~P3)

```
┌─ ui/chat/  ChatScreen (마크다운 + A2UISurfaceView), ChatViewModel, ChatState
├─ ui/voice/ VoiceOverlay + VoiceController(인터페이스) ← AndroidVoiceController(구현)
├─ a2ui/     A2UIModel(타입) · A2UIRuntime(바인딩·조건) · A2UIRenderer(Compose)
│            · A2UISurfaceParser(서버 JSON → 모델) · A2UIScenarios(로컬 데모, 현재 미호출)
├─ domain/   ChatMessage · AiStreamEvent · AiRepository(인터페이스)
├─ data/     RemoteAiRepository(HTTP 스트리밍) · MockAiRepository(오프라인)
└─ di/       AppContainer — 유일한 배선 지점 (BuildConfig.USE_REMOTE 분기)
```

## 3. 응답 스트리밍 파이프라인 (핵심 흐름)

```
사용자 입력 (텍스트 or 음성 받아쓰기)
  → ChatViewModel.submitUserPrompt()
      USER 메시지 + AI placeholder(isStreaming=true) 를 한 번의 상태 갱신으로 추가
  → AiRepository.sendMessage(prompt): Flow<AiStreamEvent>
      RemoteAiRepository:
        POST {SERVER_URL}?api_key=...  body={session_id, message}
        SSE 를 줄 단위 파싱, 라우팅은 이벤트명 기준 —
          event: token → TextChunk / event: a2ui → A2UIEnvelope(v0.9 엔벨로프 JSON)
          event: error → IOException / ":" keep-alive 무시 / 종료 이벤트 없음
  → ChatViewModel.streamAiResponse() collect
      TextChunk    → placeholder 텍스트에 누적 (마크다운 렌더링)
      A2UIEnvelope → A2UIEnvelopeApplier.apply() 로 서피스 누적 구성
                     (createSurface → updateComponents → updateDataModel)
                     첫 서피스를 메시지에 surfaceId 로 부착 + ChatState.surfaces[id] 갱신
  → 완료 시 finalizeMessage (isStreaming=false); 보이스 모드면 전체 텍스트 TTS 발화
```

세션: `RemoteAiRepository` 인스턴스당 UUID `session_id` 1개 (프로세스 생존 동안 유지).

## 4. A2UI 생성형 UI (상세: `app/docs/08_a2ui_generative_ui.md` §8 = 서버 와이어 계약)

- 서피스 = `surfaceId` + 컴포넌트 맵(트리) + `dataModel` + 테마(primaryColor 등)
- 기본 컴포넌트 14종: Card/Column/Row/Text/Icon/Divider/Button/TextField/CheckBox/
  ChoicePicker/Slider/Switch/TagInput/Select/RiskCardList (로컬 데모 형식 — `A2UISurfaceParser`)
- **서버 커스텀 카드 6종** (`ServerCard(kind, valuePath)` → `A2UIServerCards.kt` 렌더링):
  CompletionGauge/ActvScoreSummary/WeeklySchedule/PendingApprovalList/
  OpertPlanStatus/OpertStopStatus — 서버(saferyn-langgraph)가 v0.9 엔벨로프로 내려줌
- 값 바인딩: Static / PathBound(JSON Pointer) / FormatStr(`${/path}` 보간)
- 유효성: Required/Numeric/PathRef/And/Or — Button `checks` 불충족 시 비활성 + 메시지
- 사용자 상호작용:
  - `onA2UIData(surfaceId, path, value)` — 입력 위젯 → dataModel 갱신 (deep copy + setAtPath)
  - `onA2UIAction(surfaceId, eventName, context)` — 버튼 → 현재 **로컬 처리**
    (서버 왕복은 후속 spec — specs/001 제외 범위)
- 렌더링 위치: `ChatScreen.AiMessage` 안에서 `MarkdownText` 아래 `A2UISurfaceView`

## 5. 보이스 설계

보이스는 같은 `sendMessage` 경로의 **대체 입력 수단**이다 (P7). STT/TTS 는 단말 로컬.

```
마이크 버튼 → RECORD_AUDIO 권한 → startVoiceMode() → VoiceController.startListening()
LISTENING (RMS 가 오브 스케일 구동)
  → EndOfSpeech → THINKING (AI 스트리밍 동안 펄스)
  → 스트림 완료 → speak(전체 텍스트) → SPEAKING
  → SpeakingFinished → LISTENING 자동 재개
X 버튼 → endVoiceMode() — STT/TTS 모두 정지, 대화 이력 보존
```

Samsung 하드닝(P8): 온디바이스 인식기 우선(`createOnDeviceSpeechRecognizer`, API 31+),
`EXTRA_PREFER_OFFLINE`, ko-KR 고정, 에러 2/4/8/11 세션당 1회 무음 재시도,
12/13 은 네트워크 인식기 폴백 후 세션 동안 기억. 디버깅은 logcat 태그
`VoiceController` / `ChatViewModel` (RMS 는 의도적으로 미로깅).

## 6. UI 세부 결정

- **스마트 자동 스크롤** (`AutoScrollEffect`): ① `lastUserMessageId` 키 — 사용자 전송 시
  무조건 최하단, ② `messageCount`+`lastMessageText` 키 — `isUserAtBottom` 일 때만.
  스트리밍이 히스토리를 읽는 사용자와 싸우지 않게 하는 장치.
- 전송 시 키보드 dismiss (`LocalSoftwareKeyboardController.hide()` + `clearFocus()`),
  IME `onSend` = 전송 버튼과 동일 콜백.

## 7. 빌드 구성 / 서버 전환

`local.properties` → `BuildConfig` (커밋 금지 값):

| 키 | 기본값 | 의미 |
|---|---|---|
| `USE_REMOTE` | `false` | false = Mock (서버 불필요 — P6 보장) |
| `SERVER_URL` | `ws://10.0.2.2:8000/ws` | HTTP POST 엔드포인트로 교체해 사용 |
| `WS_API_KEY` | 빈 문자열 | 쿼리 파라미터 `?api_key=` 로 전달 |

개발 LAN 은 cleartext 허용 중 (`usesCleartextTraffic="true"`) — 운영 전환 시 https + 해제.

## 8. 이력·계약 문서 지도 (`app/docs/`)

| 문서 | 내용 |
|---|---|
| `01_requirements.md` | PRD — 데이터 모델, UX 상태, STT/TTS 파이프라인 |
| `02_architecture.md` | DIP 규칙·패키지 설계 (제정 당시 기준 — 현행은 이 DESIGN.md) |
| `03_server_api.md` | 서버 계약 — ⚠ WebSocket 시절 기준, HTTP POST/SSE 개정 필요 (specs/001 T301) |
| `04~06_*.md` | 서버 핸드오프·keepalive fix·오디오 핸드오프 (이력) |
| `07_ui_markdown_voice_inline.md` | 마크다운 + 인라인 받아쓰기 |
| `08_a2ui_generative_ui.md` | A2UI v0.9 타입 시스템·시나리오 (계약) |

주의: 전송 계층은 WebSocket(9d57eae 이전) → **HTTP POST 스트리밍** 으로 이행했다.
`03_server_api.md` 와 `TODO.md` 항목 3 의 WebSocket 서술은 이력이다 — 현행 와이어
계약의 실체는 `RemoteAiRepository` 파서와 specs/001 이 기준.
