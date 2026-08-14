# 001 — 태스크 (tasks)

plan.md (D1~D7) 실행 순서. maker 는 `[maker-ready]` 까지만 (P11).
개정 2026-08-14 — 와이어 계약 확정에 따라 Phase 1 재편 (T105~T109 추가).

## Phase 1 — 도메인 계약 + 클라이언트 구현

- [x] T101 `[maker-ready]` `AiStreamEvent` sealed class 신설 + `AiRepository.sendMessage`
      반환 타입 변경 + `MockAiRepository` 동기화 (D1, P6)
- [x] T102 `[maker-ready]` ~~NDJSON/구형 SSE 파서~~ → T105 로 대체 (구 서버 형식 폐기)
- [x] T103 `[maker-ready]` `A2UISurfaceParser` 신설 + `ChatViewModel` 키워드 분기 제거
      (파서는 로컬 데모 형식용으로 존치 — D4)
- [x] T105 `[maker-ready]` `AiStreamEvent.SurfaceCreate` → `A2UIEnvelope` 개명 (D7)
- [x] T106 `[maker-ready]` `RemoteAiRepository` — SSE 이벤트명 라우팅 (`token`/`a2ui`/`error`),
      NDJSON 분기 제거 (D2 폐기)
- [x] T107 `[maker-ready]` `A2UIEnvelopeApplier` 신설 — createSurface/updateComponents/
      updateDataModel 누적 적용, 파싱 실패 로그+무시 (D5)
- [x] T108 `[maker-ready]` `A2UIModel.ServerCard` + `A2UIServerCards.kt` 커스텀 카드 6종
      Composable + 렌더러 분기 (D6) + 미지원 kind 폴백
- [x] T109 `[maker-ready]` `ChatViewModel` — 엔벨로프 수신 → 서피스 누적 적용 + surfaceId 부착 (D5)
- [x] T104 `[maker-ready]` `A2UIEnvelopeApplier` JVM 단위 테스트 5건 (수용 6) —
      `testImplementation org.json:json` 추가 (android.jar 스텁 대체)
- [x] T110 `[maker-ready]` **표준 컴포넌트 v0.9 와이어 파싱** — applier 가 `component`
      값에 따라 기존 `A2UIComponent`(Card/Column/Row/Text/Divider/ChoicePicker/TextField/
      CheckBox/Button)로 매핑, 그 외 kind 는 `ServerCard`(커스텀 카드/폴백).
      와이어 차이 흡수: `child`/`children`, `text` 직접값, `value:{path}`,
      `checks:{call,args,message}`, `action:{event:{name,context}}`,
      `theme.agentDisplayName`. 부가 대응: `TextFieldComp.readonly` 신설(렌더러 enabled),
      Row 안 입력형 자식 weight(1f) 균등 분배 (2필드 Row 레이아웃),
      `register_safety_report` 임시 로컬 응답("아직 등록 안 됨" 명시 — 서버 왕복은 spec 002)
- [x] T111 ~~`FileUpload` 컴포넌트~~ → **specs/002 T105 로 편입·완료** (2026-08-14 —
      파일 전송은 액션 왕복의 일부라서 spec 002 범위가 맞음)
- [x] T112 `[maker-ready]` `AccidentListCard` 렌더러 — 서버 반영(2026-08-14) 확인 후 추가.
      유형 칩 + 사업장·일시 + title/content 조건부 표시 + "외 N건". 분기 1줄 + Composable
      1개 (D6 설계 의도 검증됨 — 모델·applier 무변경)

## Phase 2 — 검증

- [x] T201 `[maker-ready]` `gradlew test` + `assembleDebug` **green** (2026-08-14),
      UI 계층 `org.json`/OkHttp import 0건 확인 (수용 5)
- [ ] T202 `[needs-device]` Mock 회귀 — `USE_REMOTE=false` 텍스트 스트리밍 UX 확인 (수용 1).
      정적 확인은 완료(Mock 은 TextChunk 만 emit, 분기 무변경) — 실행 확인만 남음
- [x] T203 실서버 SSE — 텍스트 청크 누적 렌더링 **사용자 실기기 확인 (2026-08-14)** (수용 2)
- [x] T204 실서버 카드·폼 렌더링 — 아차사고 폼(표준 컴포넌트·입력·제출)·AccidentListCard 등
      **사용자 실기기 확인 (2026-08-14)** (수용 3). 6종 카드 개별 문구 전수 확인은 잔여

## Phase 3 — 문서·마감

- [x] T301 `[maker-ready]` 확정 와이어 스키마를 `app/docs/08_a2ui_generative_ui.md` §8 에
      추가, 구 초안은 §9 로 폐기 표시 (P5). DESIGN.md §3·§4 현행화 동반
- [x] T302 `[maker-ready]` `CHANGELOG.md` 1.5.0 절 추가 + `TODO.md` 항목 9 종결 처리
- [ ] T303 `[human]` 커밋·푸시 판단
- [ ] T304 `[server]` 서버 요청 사항 전달 (Confluence v2 — 2026-08-14 재점검 반영)
      - ~~① '아차사고 등록' 인텐트/폼 추가~~ → **서버 `b8356ec` 에서 해결됨**
      - ~~② 결재 대기 카드 트리거 문구 확인~~ → **철회** (`API_TOOL_KEYWORDS` 에 이미 존재:
        `내 할 일`/`결재 대기`/`승인할` 등 — 초판 확인 누락)
      - ~~③ 재해(`call_get_accidents`) a2ui 빌더 누락~~ → **서버 반영 완료 (2026-08-14).**
        제안대로 `AccidentListCard` kind 채택, summary = {title, subtitle, total, shown,
        items:[{id,siteId,siteName,date,type,title,content}]}. 클라이언트 렌더러 추가함 (T112)
      - ④ **신규**: `/a2ui/action` 엔드포인트 계약 확인 (인증·헤더·multipart·응답 md·
        제출 후 폼 처리 정책) + `FileUpload` 필수 여부

## 기록

- 2026-08-13 SDD 체계 도입과 함께 spec 신설. T101~T103 은 이미 작성돼 있던 미커밋
  코드를 소급 반영한 것 — 자동/실기기 검증은 아직 없음 (P9), Phase 2 부터가 실작업.
- 2026-08-14 사용자 리포트 "'아차사고등록해줘' 시 A2UI 미표시" 조사 — 실서버 curl 재현
  ("사업장 점수 보여줘")으로 와이어 계약 확정: SSE `event: a2ui` + v0.9 엔벨로프 3종.
  구 가정(`type:"a2ui_create"` 래퍼)은 폐기된 GeminiVoiceChatServer 형식이었음 (사용자
  확정: 미사용). spec/plan 개정, T105~T109 추가. '아차사고' 인텐트는 서버에 없음 → T304.
- 2026-08-14 사용자: 웹앱(AIAgent_TUI) 최종 소스 미push — 카드 디자인 레퍼런스 없이
  데이터 형태 기반 신규 디자인으로 진행 (plan R4).
- 2026-08-14 T104~T109 구현 + `gradlew test`/`assembleDebug` green (자동 검증만 — P9,
  실서버 렌더링 T203/T204 는 실기기 몫). JDK 경로 변동 발견: `Android Studio1\jbr` 가
  실제 JDK (CLAUDE.md 갱신). 계약 문서(08 §8)·DESIGN.md 동반 갱신 (T301).
- 2026-08-14 "재해 발생 내역" curl 확인 — 서버가 비엔벨로프 raw JSON 을 a2ui 이벤트로
  송출 (서버 빌더 누락 → T304 ③). 클라이언트는 surfaceId 부재로 무시, 텍스트만 표시.
- 2026-08-14 T304 요청 사항을 Confluence 로 서버 개발자에게 전달 —
  https://s-cloud.atlassian.net/wiki/spaces/SAFETYSAAS/pages/1184235521
  AccidentListCard 엔벨로프 제안 스키마 포함. 서버 회신 대기.
- 2026-08-14 **서버 최신 소스(`b8356ec`) 재점검** — 초판 조사가 구버전 기준이라는
  지적 반영. 결과: ①아차사고 해결됨 ②결재문구 요청은 저희 오류로 철회 ③재해 빌더
  누락은 유효(코드+배포서버 재확인) ④`/a2ui/action` 신규 엔드포인트 발견.
  Confluence v2 로 정정 게시.
- 2026-08-14 T110 구현 완료 — 표준 컴포넌트 파싱 + readonly + Row 균등분배 +
  register_safety_report 임시 응답 + 폼 파싱 단위 테스트 추가.
  `gradlew test`/`assembleDebug` green (자동 검증만 — P9). 계약 문서 08 §8-4 추가.
  아차사고 폼 실기기 렌더링·입력 확인은 T204 에 포함.
- 2026-08-14 **중대 발견 — 표준 컴포넌트 미지원 (T110 신설).** `b8356ec` 의 아차사고
  폼은 커스텀 카드가 아니라 basic catalog 표준 컴포넌트 조합
  (Card/Column/Row/Text/ChoicePicker/TextField/FileUpload/Button)이다. 현재 applier 는
  모든 컴포넌트를 `ServerCard` 로 담으므로 폼 전체가 "지원하지 않는 컴포넌트" 폴백으로
  렌더링된다 — spec 001 미해결 항목("서버가 표준 컴포넌트를 쓰기 시작하면 applier 확장")이
  현실화. 와이어 형식도 로컬 데모 형식과 다름 (`component`/`child`/`text` 직접값/
  `value.path`/`checks.call` 구조). 액션 서버 왕복은 spec 001 명시 제외 범위 → spec 002 필요.
