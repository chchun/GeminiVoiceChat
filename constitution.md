# Constitution — GeminiVoiceChat

이 문서는 모든 feature, 모든 세션에 적용되는 **불변 원칙**이다.
spec/plan/tasks 는 바뀔 수 있으나 이 문서의 규칙은 위반할 수 없다.
원칙과 충돌하는 변경은 **이 문서 개정(개정일 + 근거 spec 번호 병기)이 코드보다 먼저다.**

> 제정 2026-08-13 (SDD 체계 도입). 그전까지 이 규칙들은 `CLAUDE.md` 에 흩어져 있었다 —
> 여기로 승격했고, 충돌 시 이 문서가 이긴다.

## 1. 계층 구조 — DIP (위반 금지)

```
UI (Compose — ChatScreen, VoiceOverlay, A2UIRenderer)
   ↑ StateFlow<ChatState> 만 구독
ChatViewModel (상태·시퀀스 조정)
   ↑ 추상 인터페이스만 참조
domain/ (AiRepository, VoiceController — 인터페이스 + 모델)
   ↑ 구현
data/ (RemoteAiRepository, MockAiRepository) · ui/voice/AndroidVoiceController
```

- **P1.** UI·ViewModel 은 **추상만 참조한다**: `AiRepository`(데이터), `VoiceController`(플랫폼).
  `data/` 패키지와 `AndroidVoiceController` 의 import 는 `di/` 전용이다.
- **P2.** 구현체 배선은 `DefaultAppContainer` 한 곳에서만 한다. Composable·ViewModel 에서
  리포지토리/컨트롤러를 직접 생성하지 않는다.
- **P3.** 상태는 `StateFlow<ChatState>` 단방향. UI 이벤트는 ViewModel 함수 호출로만 전달한다.

## 2. 스트림 계약 (서버 응답 = 텍스트 + A2UI)

- **P4.** `AiRepository.sendMessage` 의 반환은 `Flow<AiStreamEvent>` 다
  (`TextChunk` | `SurfaceCreate`). 서버 이벤트 타입이 늘면 **sealed class 를 확장**하고,
  raw JSON·와이어 포맷(SSE/NDJSON)이 리포지토리 경계 밖으로 새지 않게 한다.
  와이어 파싱은 `data/`, A2UI 서피스 JSON 파싱은 `a2ui/A2UISurfaceParser` 가 담당한다.
- **P5.** 서버 API 계약의 진실원천은 `app/docs/03_server_api.md`(전송)와
  `app/docs/08_a2ui_generative_ui.md`(A2UI 타입 시스템)다. 계약이 바뀌면
  **문서 갱신이 같은 변경(커밋)에 포함**되어야 한다.
- **P6.** `MockAiRepository` 는 항상 인터페이스와 동기화한다 — 서버 없이
  (`USE_REMOTE=false`) 빌드·실행이 되는 상태를 깨지 않는다.

## 3. 보이스 규칙

- **P7.** 보이스 모드는 **같은 `sendMessage` 경로의 대체 입력 수단**일 뿐이다.
  STT/TTS 는 단말 로컬(`VoiceController`)이며 `AiRepository` 의 일부가 아니다.
  서버 오디오 스트리밍(Live)이 들어오면 별도 spec 으로 인터페이스를 재설계한다.
- **P8.** `AndroidVoiceController` 의 Samsung 하드닝(온디바이스 인식기 우선,
  에러 2/4/8/11 세션당 1회 자동 재시도, 12/13 네트워크 폴백)은 **실기기 검증 없이
  완화하지 않는다.**

## 4. 검증 규율

- **P9.** 최소 검증 = `gradlew test` + `assembleDebug`. UI/매니페스트/리소스 변경은 `lint` 추가.
  자동 검증 결과와 실기기 검증 결과를 **구분해 보고**하고, 실기기 필요 항목은
  `[needs-device]` 태그로 사람에게 넘긴다. 실기기(특히 Galaxy 보이스·실서버 스트리밍)
  검증 전에는 "동작한다"고 단정하지 않는다.
- **P10.** 테스트가 통과하도록 테스트를 약화시키지 않는다. 테스트 수정은 spec 변경이 선행된다.
- **P11.** maker 세션은 태스크를 `[maker-ready]` 까지만 표시한다 (done 금지).
  완료 판정은 fresh 세션 checker 또는 사용자 몫 — 기준은 해당 tasks.md 의 수용 기준.

## 5. 범위 규율

- **P12.** 기술 스택 고정: Kotlin / Jetpack Compose, 수동 DI(`AppContainer` — Hilt 등
  DI 프레임워크 금지), coroutine + StateFlow, OkHttp. compileSdk 36 / minSdk 24.
  외부 라이브러리는 spec 에 근거가 있을 때만 추가한다.
- **P13.** feature 는 `specs/NNN-*/` 에 spec → plan → tasks 3종을 먼저 갖춘 뒤 코드를
  작성한다. plan 없이 tasks 부터 쓰지 않는다. 대화로 들어온 요청도 기존 spec 범위 안이면
  해당 `tasks.md` `## 기록` 에 한 줄, 범위 밖이면 새 spec 을 만든다.
