# 001 — A2UI 서버 연동: 로컬 시나리오 → 서버 스트리밍 엔벨로프

작성 2026-08-13 (SDD 체계 도입과 동시 — 진행 중이던 미커밋 작업을 소급 기록).
**개정 2026-08-14** — 실서버 와이어 계약 확정 반영 (미해결 1 해소). 이전 판이 가정했던
`{"type":"a2ui_create","surface":{...}}` 래퍼는 폐기된 서버(GeminiVoiceChatServer)의
형식이었다. 실서버(saferyn-langgraph, ncloud)는 **SSE `event: a2ui` + A2UI v0.9 표준
엔벨로프 3종**(createSurface / updateComponents / updateDataModel)을 보낸다.
출처: `TODO.md` 항목 9 "A2UI 서버 연동 (현재 로컬 데모만)".

## 확정된 와이어 계약 (2026-08-14, 실서버 curl + saferyn-langgraph 코드 대조)

```
event: token
data: {"text": "..."}                              ← 텍스트 청크

event: a2ui
data: {"version":"v0.9","createSurface":{"surfaceId":..,"catalogId":..,"theme":{"primaryColor":"#.."},"sendDataModel":true}}
data: {"version":"v0.9","updateComponents":{"surfaceId":..,"components":[{"id":"root","component":"<Kind>","value":{"path":"/.."}}]}}
data: {"version":"v0.9","updateDataModel":{"surfaceId":..,"path":"/","value":{...}}}
```

- 종료 이벤트 없음 — 스트림이 닫히면 완료.
- 서버 커스텀 컴포넌트 6종 (모두 루트 1개 + `value.path` 데이터 바인딩):
  `CompletionGaugeCard`(감소대책 이행률·부적합 조치율 공용) / `ActvScoreSummaryCard` /
  `WeeklyScheduleCard` / `PendingApprovalListCard` / `OpertPlanStatusCard` / `OpertStopStatusCard`
- 진실원천: `D:\dev\saferyn-langgraph\app\utils\a2ui.py` (빌더) +
  `app/api/routes.py` (SSE 송출) + `app/graph/saferyn/nodes.py` (인텐트 키워드)
- 참고: '아차사고' 인텐트는 서버에 없음 (`[server]` 요청 필요 — 이 spec 범위 밖)

## 목적

지금까지 A2UI 서피스는 **클라이언트 로컬 키워드 매칭**(`A2UIScenarios.match`)으로만
생성됐다. 이 spec 은 서버가 응답 스트림 안에서 실제 A2UI 엔벨로프를 내려주면
클라이언트가 파싱·렌더링하도록 경로를 서버 주도로 전환한다.

- AI 챗봇 응답 = **텍스트(마크다운) + A2UI 서피스** 의 혼합 스트림이다.
- 텍스트/서피스 구분은 도메인 이벤트(`AiStreamEvent`)로 추상화하고, UI 는 이벤트만 소비한다.

## 범위

### 포함
- `AiRepository.sendMessage` 반환 타입 변경: `Flow<String>` → `Flow<AiStreamEvent>`
  (`TextChunk` | `A2UIEnvelope`) — 도메인 계약 확장. 엔벨로프 payload 는 JSON 문자열
  (도메인 계층의 `a2ui/`·`org.json` 무의존 유지)
- `RemoteAiRepository`: SSE 파싱 — **이벤트명 기반 라우팅** (`token` → `TextChunk`,
  `a2ui` → `A2UIEnvelope`, `error` → 예외, keep-alive 주석 무시), api_key 쿼리 파라미터
- `A2UIEnvelopeApplier`(신설): v0.9 엔벨로프 3종을 기존 `A2UISurface` 에 순차 적용
  (createSurface → 빈 서피스 생성, updateComponents → 컴포넌트 맵 갱신,
  updateDataModel → JSON Pointer 경로에 데이터 반영)
- 서버 커스텀 카드 6종의 Compose 렌더러 (`A2UIComponent.ServerCard(kind, valuePath)` +
  kind 별 카드 UI — 데이터 형태 기반 신규 디자인, 웹 레퍼런스 없음)
- `ChatViewModel`: 로컬 키워드 매칭 분기 **제거**, 엔벨로프 수신 시 서피스 누적 적용 +
  스트리밍 중인 AI 메시지에 `surfaceId` 부착 + `ChatState.surfaces` 갱신
- `MockAiRepository`: 새 계약(`AiStreamEvent`)으로 동기화 (P6)

### 제외
- 서버 쪽 구현 (별도 리포/세션 — '아차사고' 인텐트 추가 등은 `[server]` 요청)
- A2UI 액션의 서버 왕복 (`onA2UIAction` 은 현행 로컬 처리 유지 — 후속 spec)
- `A2UIScenarios`·`A2UISurfaceParser` 파일 삭제 (로컬 데모·테스트 픽스처로 존치, 호출 경로만 제거)
- 폐기된 GeminiVoiceChatServer NDJSON(`{"type":"a2ui_create"}`) 형식 지원 (2026-08-14 사용자 확정: 미사용)
- 오디오 스트리밍 (`streamAudio` 는 계속 미지원)

## 수용 기준

1. `USE_REMOTE=false`(Mock) 빌드에서 기존 텍스트 스트리밍 UX 회귀 없음 (P6)
2. ncloud `/chat/stream` SSE 응답에서 텍스트 청크가 순서대로 누적 렌더링된다 `[needs-device]`
3. "사업장 점수 보여줘" → `ActvScoreSummaryCard` 서피스가 텍스트와 같은 말풍선에
   렌더링된다. 나머지 5종 카드도 해당 인텐트 문구로 렌더링 확인 `[needs-device]`
4. SSE keep-alive 주석·`error` 이벤트가 각각 무시/에러 UI 로 처리되고, 엔벨로프 1건의
   파싱 실패가 텍스트 스트림 전체를 죽이지 않는다
5. `gradlew test` + `assembleDebug` green, UI 계층에 `org.json`/OkHttp import 0건 (P1/P4)
6. `A2UIEnvelopeApplier` JVM 단위 테스트 — 엔벨로프 3종 순차 적용, 커스텀 카드 6종 kind 매핑

## 미해결

- ~~서버 A2UI 엔벨로프의 최종 스키마 확정~~ → 2026-08-14 확정 (위 "확정된 와이어 계약").
  `app/docs/08_a2ui_generative_ui.md` 와이어 스키마 절 추가는 T301 로 추적
- 서버가 basic 카탈로그 표준 컴포넌트(Text/Button 등)를 v0.9 와이어 형식으로 보내는 경우 —
  현재 서버는 커스텀 카드 6종만 사용하므로 applier 는 이 6종 + 미지원 kind 폴백만 구현.
  서버가 표준 컴포넌트를 쓰기 시작하면 applier 확장 (P13: 새 spec 또는 이 spec 개정)
- `deleteSurface`·부분 updateComponents(기존 컴포넌트 수정) — 서버 미사용, 필요 시 확장
