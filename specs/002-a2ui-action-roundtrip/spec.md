# 002 — A2UI 액션 서버 왕복: 폼 제출 → `/a2ui/action`

작성 2026-08-14. spec 001 의 명시 제외 범위("A2UI 액션의 서버 왕복")를 구현한다.
서버 계약은 2026-08-14 사용자(서버 개발자 회신 중계) + saferyn-langgraph 코드로 확정.

## 목적

아차사고/안전제안 폼의 제출 버튼(`register_safety_report`)이 실제 등록되게 한다.
현재는 로컬 임시 응답("아직 등록되지 않았어요")만 표시된다 (spec 001 T110).

## 확정된 계약 (`POST {서버베이스}/a2ui/action`)

| 항목 | 값 (근거) |
|---|---|
| 인증 | **불필요** — 미전송 시 서버가 자체 토큰 발급 (`routes.py`: `authorization if ... else get_saferyn_token()`) |
| `X-Tenant-Id`/`X-Bplc-Id` | **불필요** — 서버 상수 기본값 사용 (사용자: "나중에 테넌트/사업장 설정 시 변경, 지금은 고정") |
| 요청 형식 | **항상 multipart/form-data** — `action` 파트에 `{name(필수), surfaceId?, context{}, ...}` JSON 문자열 (개정 2026-08-14: JSON 바디는 서버 500 — 실기기 검증에서 확인, 사용자 회신) |
| 파일 첨부 시 | 같은 multipart 에 `files` 파트(복수) 추가. **포함으로 개정** (2026-08-14 사용자 착수 지시 — spec 001 T111 을 이 spec T105 로 편입). 서버는 무변경 — `form.getlist("files")` → `post_opinion(files=...)` 경로 기구현 확인 |
| 응답 | `{ok: bool, result: {...}, md: "마크다운"}` — `ok:false` 도 `md` 포함 (미연결 액션 안내) |
| 제출 후 폼 처리 | 서버 정책 없음 → **클라이언트 결정** (2026-08-14 사용자 위임): 폼 유지 + 제출 버튼을 "✅ 등록 완료" 텍스트로 치환해 중복 등록 방지 |

## 범위

### 포함
- `AiRepository.sendA2UIAction(eventName, surfaceId, context): A2UIActionResult` — 도메인 계약 확장
- `A2UIActionResult(ok, markdown)` 도메인 모델
- `RemoteAiRepository`: 액션 URL 을 `SERVER_URL` 에서 유도 (`/a2ui/action` resolve), JSON POST
- `MockAiRepository`: 성공 md 모사 (P6)
- `ChatViewModel`: `register_safety_report` → 서버 왕복. "등록 중" 스트리밍 placeholder →
  응답 `md` 를 AI 말풍선으로 표시. `ok:true` 면 해당 서피스의 제출 버튼(같은 eventName 의
  ButtonComp)을 완료 텍스트로 치환. 실패 시 에러 메시지 + 버튼 유지(재시도 가능)

### 포함 (2026-08-14 편입 — T105)
- `FileUpload` 컴포넌트 렌더링 — Android Photo Picker(권한 불필요)로 사진 선택,
  선택 목록 표시/제거, `maxFiles`·`maxSizeMb` 제한 검사
- 선택 파일을 multipart `files` 파트로 전송 — 도메인 계약은
  `sendA2UIAction(..., files: List<A2UIFile>)` (UI 는 OkHttp 무지 유지 — P1/P4)
- action JSON 의 `context.photos` 에는 파일명 목록만 (bytes 비노출)

### 제외
- `register_safety_report` 외 액션의 서버 왕복 — 서버가 "미연결" md 를 주므로 로컬 데모
  액션(create_incident 등)은 현행 유지
- 로그인/테넌트 설정 화면 — 헤더가 필수가 되는 시점에 별도 spec

## 수용 기준

1. 폼 제출 → 서버 201 등록 → "✅ 아차사고 등록이 완료되었습니다..." md 가 말풍선에 렌더링 `[needs-device]`
2. 제출 성공 후 같은 폼의 제출 버튼이 "✅ 등록 완료" 텍스트로 바뀌어 중복 등록 불가 `[needs-device]`
3. 서버 오류/네트워크 실패 시 에러 안내 + 버튼 유지 (재시도 가능)
4. Mock(`USE_REMOTE=false`) 에서도 제출 흐름이 성공 md 로 동작 (P6)
5. `gradlew test` + `assembleDebug` green, UI 계층 OkHttp/org.json import 0건 (P1/P4)

## 미해결

- 서버 `/a2ui/action` 의 api_key 검사 없음 — 운영 전환 시 인증 정책 확정 필요 `[server]`
- 등록 성공 후 서버가 결과 서피스(접수번호 카드 등)를 줄 계획이 있는지 — 주면 md 대신 렌더링
