# 002 — 태스크 (tasks)

plan.md (D1~D4) 실행 순서. maker 는 `[maker-ready]` 까지만 (P11).

## Phase 1 — 구현

- [x] T101 `[maker-ready]` `A2UIActionResult` 도메인 모델 + `AiRepository.sendA2UIAction`
      + Mock 구현 (D1, P6)
- [x] T102 `[maker-ready]` `RemoteAiRepository.sendA2UIAction` — URL 유도(D2), JSON POST
      `{name, surfaceId, context}` (중첩 Map/List → JSON 변환 포함), 응답 `{ok, md}` 파싱
- [x] T103 `[maker-ready]` `ChatViewModel` — register_safety_report 서버 왕복 (D4),
      성공 시 같은 eventName 버튼 → "✅ 등록이 완료되었습니다." 치환 (D3),
      실패 시 재시도 안내 + 버튼 유지

## Phase 2 — 검증

- [x] T201 `[maker-ready]` `gradlew test` + `assembleDebug` green (2026-08-14)
- [x] T202 `[maker-ready]` 배포 서버 `/a2ui/action` 실존 확인 — 무해한 미지 액션 프로브로
      `{"ok":false,"md":"...연결되지 않았습니다"}` HTTP 200 수신 (실등록 curl 은 더미
      데이터 생성 방지 위해 미수행 — plan R3, 실검증은 T203)
- [x] T203 폼 제출 → 등록 md 표시 + 버튼 치환 **사용자 실기기 확인 (2026-08-14)** (수용 1~2).
      서버 500 (백엔드 JSON 미지원) → multipart 개정 + 서버 post_opinion 수정으로 해결

## Phase 3 — 문서·마감

- [x] T301 `[maker-ready]` `app/docs/08_a2ui_generative_ui.md` §8-5 액션 왕복 계약 추가 (P5)
- [x] T302 `[maker-ready]` CHANGELOG 1.5.0 절에 반영
- [ ] T303 `[human]` 커밋 판단 (spec 001 분과 함께)

- [x] T104 `[maker-ready]` **계약 개정 — 항상 multipart 전송** (2026-08-14 실기기 검증 회신:
      JSON 바디는 서버 500). `MultipartBody` 로 `action` 파트 전송, multipart 프로브 200 확인.
      spec·08 §8-5 개정, build green
- [x] T105 `[maker-ready]` **FileUpload 사진 첨부** (spec 001 T111 편입) —
      `FileUploadComp` 모델·applier 매핑, Photo Picker 렌더러(선택/제거 칩,
      maxFiles·maxSizeMb 검사, 초과 스킵 안내), `A2UIFile`/`A2UIPickedFile` +
      `sendA2UIAction(files)` multipart `files` 파트, VM 에서 context 의 bytes 분리
      (JSON 에는 파일명만). build green, Galaxy 설치 완료
- [x] T106 사진 첨부 제출 — **사용자 실기기 확인 (2026-08-14, "실기기로 잘 돼")**

## 기록

- 2026-08-14 spec 신설 — 서버 개발자 회신(사용자 중계)으로 계약 확정: 인증·테넌트 헤더
  불필요(서버 기본값), FileUpload 선택 항목(`post_opinion` 코드 확인), 제출 후 폼 처리는
  클라이언트 위임 → "버튼 완료 치환" 채택.
- 2026-08-14 실기기 제출에서 HTTP 500 발견 (사용자) — JSON 바디 경로가 서버에서 실패.
  파일 유무와 무관하게 **항상 multipart** 로 개정 (T104). 서버 routes.py 는 JSON 도 받는
  것처럼 보였으나 실동작은 multipart 만 정상 — 코드 독해보다 실검증이 우선한 사례 (P9).
