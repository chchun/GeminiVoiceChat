# TODO

현재 구현된 범위와 별개로, 나중에 이어서 작업할 항목들을 모아둔 문서.

권위 있는 사양은 여전히 `app/docs/01_requirements.md` / `app/docs/02_architecture.md` 이며, 본 문서는 그 사양 중 **미구현분과 추가 개선안**만 트래킹한다.

---

## PRD 잔여 (요구사항 문서에 명시되어 있으나 미구현)

### 1. 배지인(Barge-in) — TTS 중 사용자 발화 감지
- **출처:** PRD 4.2 — "보이스 모드가 종료되거나 유저가 중간에 말을 시작하면 현재 출력 중인 TTS 음성을 즉시 stop() 해야 함."
- **현재 상태:** TTS 재생 중에는 STT가 멈춰 있어 사용자가 끼어들면 X 버튼 또는 TTS 종료까지 대기해야 한다.
- **구현 옵션:**
  - (a) `AudioRecord`로 별도 마이크 스트림을 열어 RMS만 모니터링하다가 임계치 초과 시 `voiceController.stopSpeaking()` + `startListening()` 호출.
  - (b) TTS 재생 중에도 SpeechRecognizer를 병행 실행 (오디오 포커스 충돌 가능, 디바이스 의존성 큼).
- **추천:** (a). Phase 1에서는 미구현으로 명시했으니 별도 작업 분기로 진행.

### 2. 문장 단위 TTS 청크 발화
- **출처:** PRD 4.2 — "AI 스트리밍 답변이 완료되거나, 혹은 문장 단위(`. `, `? `)로 청크가 완성될 때마다 해당 텍스트를 즉시 TTS 음성으로 출력해야 함."
- **현재 상태:** 응답 스트리밍 완료 후 한 번에 발화. 체감 지연이 있음.
- **구현 메모:**
  - `ChatViewModel.submitUserPrompt` 의 chunk collect 루프에서 누적 버퍼에 `. ` / `? ` / `! ` / `.\n` 패턴이 등장하면 그 지점까지의 문장을 `voiceController.speak(..., queueAdd = true)` 로 큐잉.
  - `VoiceController.speak` 시그니처에 `queueAdd: Boolean` 추가 필요 (현재는 QUEUE_FLUSH 고정).
  - `UTTERANCE_ID` 를 청크별로 다르게 부여해서 `SpeakingFinished` 가 마지막 발화에서만 발생하도록 보정.

---

## 아키텍처/Phase 2 준비

### 3. `RemoteAiRepository` — ✅ WebSocket 구현 완료 (2026-05-17) → ✅ HTTP POST 전환 완료 (2026-06-17)
- **결과:**
  - `data/repository/RemoteAiRepository.kt` 구현 (OkHttp WebSocket, 세션 유지, READY 대기, `text_chunk` 라우팅, `text_done`에서 Flow 종료).
  - `DefaultAppContainer`가 `BuildConfig.USE_REMOTE`로 분기 (default false → Mock).
  - `app/build.gradle.kts`에 BuildConfig 필드(`USE_REMOTE` / `SERVER_URL` / `WS_API_KEY`) + kotlinx-serialization 플러그인 + okhttp/serialization 의존성 추가.
  - `AndroidManifest.xml`에 `INTERNET` 권한 + `android:usesCleartextTraffic="true"` (개발 LAN 접속용 — 운영 전환 시 `wss://`로 교체하고 cleartext 해제).
- **Phase 1 end-to-end 검증 (2026-05-17):**
  - **클라이언트 버그 수정 — `kotlinx.serialization` `encodeDefaults = true`**: default value를 가진 필드(`TextInput.type = "text_input"`)가 직렬화 시 누락되어 서버가 `Unknown type: None`을 반환하던 문제. 이 fix 없이 클라이언트는 한 번도 정상 동작한 적이 없었음 (서버 단독 smoke test는 Python `ws_smoke.py`로 통과한 상태였음).
  - **WebSocket keepalive 강화**: `pingInterval(20s)` 프로토콜 레벨 PING + 코루틴 JSON ping 제거. 상세는 `app/docs/05_websocket_keepalive_fix.md`.
  - **백그라운드 재진입 자동 재연결**: Samsung One UI가 화면 OFF 시 소켓을 강제 abort하므로 `pingInterval`만으로 부족. `ProcessLifecycleOwner` `ON_START` 옵저버로 foreground 복귀 시 자동 reconnect. `@Volatile isConnectionAlive` 플래그로 좀비 소켓 정확히 감지. `lifecycle-process` 의존성 추가.
- **잔여:**
  - `streamAudio`는 `UnsupportedOperationException("Phase 2")`. Live 오디오 인입 시 시그니처 재검토는 항목 4와 함께.
  - 진단용 `Log.i` 인바운드/송신 로그가 운영 시 노이즈가 될 수 있음 — Phase 2 진입 전 `BuildConfig.DEBUG` 가드 또는 Timber 도입 검토.
- **사용법:** `local.properties`에 아래 3줄 추가 후 리빌드.
  ```properties
  USE_REMOTE=true
  SERVER_URL=ws://10.0.2.2:8000/ws
  WS_API_KEY=<서버 .env의 WS_API_KEY와 동일 값>
  ```
- **상세 핸드오프:** `app/docs/04_server_handoff.md`, `app/docs/05_websocket_keepalive_fix.md`.

### 4. 서버용 `VoiceController` 추상화 검토
- 현재 `VoiceController` 인터페이스는 디바이스 로컬 STT/TTS를 전제. 서버가 audio in/out을 직접 처리하는 Live 모드로 가면 인터페이스 분리(`LocalVoiceController` vs `StreamingVoiceController`) 가 필요할 수 있음.
- Phase 2 착수 시점에 재설계.

---

## 품질/안정성

### 5. ChatViewModel 단위 테스트
- **범위:**
  - 텍스트 전송 → 스트리밍 누적 → finalize 동작
  - 보이스 상태머신 (LISTENING → THINKING → SPEAKING → LISTENING 재개)
  - 에러 이벤트 시 `voice.isActive = false` 전이
- **도구:** `kotlinx-coroutines-test`, `Turbine` (StateFlow 검증). `MockAiRepository` 는 그대로 사용 가능. `VoiceController` 는 테스트용 Fake 작성.

### 6. 보이스 모드 프리워밍 (선택)
- **목적:** 첫 startListening 시 발생하는 `ERROR_SERVER_DISCONNECTED (11)` 로그 노이즈 제거. 현재는 자동 재시도로 UX는 정상이지만 logcat에 매번 ERROR가 찍힘.
- **구현:** `startVoiceMode` 진입 직후 빈 `startListening()` + 즉시 `stopListening()` 호출로 서비스 바인딩만 깨운 뒤 실제 세션 시작.
- **트레이드오프:** 진입 지연 100~200ms 정도 추가될 수 있음. 현재 동작이 만족스러우면 보류.

---

## UX/제품

### 7. 대화 영속화
- 앱 종료 후 재실행 시 이전 대화 이력 유지.
- 후보: Room 기반 `ConversationDao`, 또는 단순 `DataStore<List<ChatMessage>>`.
- 도메인 모델에 `conversationId` 추가 여부 검토.

### 8. 추가 UI 수정 (사용자 피드백 미수령)
- 사용자가 이전에 "수정사항이 조금 있다"고 언급한 항목 중 키보드/스크롤 외 잔여분은 아직 공유되지 않음.
- 다음 세션에서 사용자에게 확인 필요.

---

## A2UI / 생성형 UI

### 9. A2UI 서버 연동 — ✅ 구현 완료 (2026-08-14, `specs/001-a2ui-server-streaming/`)
- SSE `event: a2ui` + v0.9 엔벨로프 3종 적용 (`A2UIEnvelopeApplier`) + 커스텀 카드 6종 렌더러.
- 잔여: 실기기 검증(T203/T204), 서버에 '아차사고' 인텐트 요청(T304). 아래 원문은 이력.
- **현재 상태:** 키워드 매칭 → 로컬 시나리오 응답 (서버 호출 없음).
- **목표:** 서버가 실제 A2UI 엔벨로프(JSONL)를 응답하면 클라이언트가 파싱·렌더링.
- **구현 메모:** HTTP POST 응답 스트림에서 `\n` 구분 줄이 `{"version":"v0.9",...}` 형태면 서피스 업데이트, 일반 텍스트면 기존 청크 누적. `ChatViewModel.streamAiResponse`에 분기 로직 추가.

### 10. A2UI DataTable — 모바일 최적화 개선
- **현재 상태:** 위험성평가 결과를 `RiskCardList` (카드 목록)으로 표시. 웹앱의 19열 DataTable은 모바일에서 불가.
- **개선 옵션:**
  - (a) 가로 스크롤 테이블 (`LazyRow` + `LazyColumn` 조합).
  - (b) 카드 상세 보기 모달 — 카드 탭 시 전체 필드 표시.
  - (c) 현재 카드 방식 유지 + 편집 모드 추가 (위험도·담당자 변경).

### 11. A2UI 시나리오 확장
- 추가 가능한 시나리오: 회의 예약, 승인 결재, KPI 대시보드, 작업 지시서, 안전 점검 체크리스트 등.
- `A2UIScenarios.ALL` 리스트에 `Scenario` 항목 추가로 확장 가능.

## 문서

### 12. 빌드 트러블슈팅 메모
- Windows + 신규 셸에서 `JAVA_HOME` 미설정 시 `./gradlew` 실패. CLAUDE.md에 명시는 했지만, 신규 개발자용으로 `app/docs/` 에 환경 셋업 문서를 추가하는 것도 고려.
