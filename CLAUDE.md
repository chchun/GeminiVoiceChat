# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

ncloud 서버의 AI 에이전트와 대화하는 **AI 챗봇 Android 앱** (Kotlin + Jetpack Compose).
응답은 마크다운 텍스트 스트리밍 + **A2UI v0.9 생성형 UI 서피스**, 입력은 텍스트 + 로컬 STT 보이스.

## 반드시 먼저 읽을 것 (순서대로 — 2026-08-13 SDD 체계 도입)

1. `constitution.md` — **불변 원칙 (P1~P13). 모든 판단의 최상위.** 충돌하면 constitution 이 이긴다.
2. 현재 작업 중인 `specs/NNN-*/` 의 `spec.md` → `plan.md` → `tasks.md`
3. `DESIGN.md` — 현행 시스템 설계도 (계층·스트리밍 파이프라인·A2UI·보이스)
4. 계약 문서: `app/docs/08_a2ui_generative_ui.md` (A2UI 타입 시스템),
   `app/docs/03_server_api.md` (⚠ WebSocket 시절 기준 — 현행 와이어 계약은 specs/001 참조)

`TODO.md` 는 잔여 개선안 백로그다. 신규 착수는 백로그 항목이라도 specs/ 부터 만든다.

## SDD 문서 3종 (spec → plan → tasks)

feature 디렉터리(`specs/NNN-*/`)는 **항상 세 문서를 모두 갖는다.** 하나라도 없으면 코드 작성 전에 만든다.

| 문서 | 답하는 질문 |
|---|---|
| `spec.md` | 무엇을·왜 — 범위, 계약, 수용 기준 |
| `plan.md` | 어떻게 — 설계 결정(D1..), 영향 파일 표, 검증 전략, 리스크. **버린 대안과 이유를 남긴다** |
| `tasks.md` | 순서 — 실행 태스크 + `## 기록` 절 |

- 대화로 들어온 요청도 문서에 남긴다. 기존 spec 범위 안이면 해당 `tasks.md` `## 기록` 에 한 줄,
  범위 밖이면 새 `specs/NNN-*/` 를 만든다. plan 없이 tasks 부터 쓰지 않는다.
- 원칙과 충돌하면 constitution 개정(개정일 + 근거 spec 번호)이 코드보다 먼저다.
- maker 는 태스크를 `[maker-ready]` 까지만, 실기기 필요 항목은 `[needs-device]` (P9/P11).

## 푸시 전 체크리스트

1. `CHANGELOG.md` 갱신 (같은 날짜 절이 있으면 덧붙임)
2. 해당 `specs/NNN-*/tasks.md` `## 기록` 에 태스크별 한 줄
3. 서버 계약(스트림 이벤트·A2UI 스키마)을 건드렸으면 계약 문서 동일 커밋 반영 (P5)
4. `gradlew test` + `assembleDebug` (UI/매니페스트/리소스 변경 시 `lint` 추가)

## 빌드·검증 명령

Windows PowerShell 기준. 신규 셸이 `java` 를 못 찾으면
`JAVA_HOME = C:\Program Files\Android\Android Studio1\jbr` 설정 후 실행
(2026-08-14 확인 — `Android Studio\jbr` 는 빈 껍데기, `Studio1` 쪽이 실제 JDK).

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
.\gradlew.bat lint
.\gradlew.bat installDebug        # 연결된 기기/에뮬레이터
```

서버 연동 실행: `local.properties` 에 `USE_REMOTE=true` / `SERVER_URL` / `WS_API_KEY`
(상세: `DESIGN.md` §7). 기본은 Mock — 서버 없이 항상 빌드·실행 가능해야 한다 (P6).

**SDK:** compileSdk 36, minSdk 24, targetSdk 36, Kotlin 2.2.10, Compose BOM 2026.02.01,
Java 11. Application id: `com.aromit.geminivoicechat`.

## 함정 목록 (이전 세션에서 확인된 것들)

- **kotlinx.serialization `encodeDefaults = true` 필수** — default 값 필드가 직렬화에서
  누락돼 서버가 `Unknown type` 을 반환한 전력 (Phase 1 최대 삽질)
- Samsung One UI 는 화면 OFF 시 소켓을 강제 abort — 백그라운드 복귀 재연결 이력은
  `app/docs/05_websocket_keepalive_fix.md` (WebSocket 시절이지만 HTTP 스트리밍도 동일 주의)
- Galaxy 첫 보이스 호출 `ERROR_SERVER_DISCONNECTED(11)` — `AndroidVoiceController` 하드닝이
  흡수한다. **실기기 검증 없이 완화 금지** (P8). 디버깅: logcat 태그 `VoiceController` / `ChatViewModel`
- 전송 계층은 WebSocket → HTTP POST 스트리밍으로 이행했다 — `03_server_api.md` 등
  옛 문서의 WebSocket 서술을 현행으로 오독하지 말 것 (`DESIGN.md` §8)
- SSE `data:` 파싱은 줄 단위 — 서버가 대형 payload 를 여러 `data:` 줄로 쪼개는 경우 미지원
  (specs/001 plan R2)

## 주요 코드 위치

- 배선(유일한 DI 지점): `di/AppContainer.kt` — `BuildConfig.USE_REMOTE` 분기
- 도메인 계약: `domain/repository/AiRepository.kt` + `domain/model/AiStreamEvent.kt`
- 서버 스트리밍 파서: `data/repository/RemoteAiRepository.kt`
- 채팅 상태·시퀀스: `ui/chat/ChatViewModel.kt` / 화면: `ui/chat/ChatScreen.kt`
- A2UI: `a2ui/` (Model·Runtime·Renderer·SurfaceParser·Scenarios)
- 보이스: `ui/voice/AndroidVoiceController.kt` (Samsung 하드닝 포함)

패키지 전체 구조·데이터 흐름은 `DESIGN.md` §2~§5 참조.
