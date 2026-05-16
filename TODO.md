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

### 3. `RemoteAiRepository` 스캐폴딩
- **목적:** 실제 Gemini Live(WebSocket) 백엔드 연동 진입점을 미리 정의해 두기. 구현 본체는 서버 준비 후 채움.
- **작업:**
  - `data/repository/RemoteAiRepository.kt` 빈 클래스 생성 (현재는 미사용, `@Suppress` 또는 `TODO()` 본문).
  - `DefaultAppContainer` 에 `BuildConfig.USE_REMOTE` 플래그 분기 추가 (default false → Mock 유지).
  - `streamAudio` 의 입력/출력 시그니처 검토. 현재 인터페이스: `streamAudio(audioFlow: Flow<ByteArray>): Flow<AiResponse>` — Live API와 정합한지 재확인 필요.

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

## 문서

### 9. 빌드 트러블슈팅 메모
- Windows + 신규 셸에서 `JAVA_HOME` 미설정 시 `./gradlew` 실패. CLAUDE.md에 명시는 했지만, 신규 개발자용으로 `app/docs/` 에 환경 셋업 문서를 추가하는 것도 고려.
