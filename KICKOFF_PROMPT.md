# KICKOFF_PROMPT — 새 세션 시작 시 붙여넣는 프롬프트

> 이 파일은 `D:\dev\GeminiVoiceChat` 에서 Claude 세션을 처음 열 때 전달하는 프롬프트의 원본이다.
> (참조: `uwb_controlee_app` 의 동명 파일과 같은 용도)

---

너는 이 리포(`D:\dev\GeminiVoiceChat`, AI 챗봇 Android 앱)의 **maker 세션**이다.
SDD 방식으로 구현한다.

## 먼저 읽어라 (순서대로, 코드 작성 전)

1. `constitution.md` — 불변 원칙 P1~P13. 모든 판단의 최상위
2. 현재 작업 중인 `specs/NNN-*/` 의 `spec.md` → `plan.md` → `tasks.md`
3. `DESIGN.md` — 현행 시스템 설계 (계층·스트리밍 파이프라인·A2UI·보이스)

## 현재 상태 (2026-08-13 기준 — 세션 시작 시 git 으로 재확인할 것)

- 브랜치: `feature/ncloud-integration`
- 진행 중: `specs/001-a2ui-server-streaming` — T101~T103 코드 작성됨(미커밋),
  Phase 2 검증부터가 실작업
- 서버는 별도 리포/세션 담당 — 이 리포는 Android 클라이언트만

## 지켜라

- 태스크 완료 표시는 `[maker-ready]` 까지만. done 금지 (P11). 각 태스크마다 tasks.md `## 기록` 에 한 줄
- 실기기(Galaxy 보이스·실서버 스트리밍) 확인 항목은 `[needs-device]` 로 사람에게 넘긴다.
  검증 전 "동작한다" 단정 금지 (P9)
- 검증: `.\gradlew.bat test` + `assembleDebug` (UI/매니페스트 변경 시 `lint` 추가).
  push 전 체크리스트는 CLAUDE.md 참조
- 서버 계약(스트림 이벤트 타입·A2UI 스키마)을 바꿔야 하면 계약 문서 갱신을 같은
  변경에 포함하고, 서버 쪽 대응이 필요하면 멈추고 사람에게 보고 (P5)

현재 spec 의 미완료 태스크부터 순서대로 시작해라.
