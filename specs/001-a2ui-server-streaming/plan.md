# 001 — 구현 계획 (plan)

작성 2026-08-13. 코드가 이미 작성된 상태(미커밋)에서 소급 기록 — 설계 결정의 근거를
남기는 것이 목적이다.
**개정 2026-08-14** — 와이어 계약 확정(R1 현실화)에 따라 D2 폐기·D5~D7 추가.
이전 판의 `{"type":"a2ui_create"}` NDJSON 가정은 폐기된 서버의 형식이었다 (spec 개정 참조).

## 설계 결정

### D1. 도메인 이벤트 = sealed class `AiStreamEvent` (raw JSON 통과 금지)

| 대안 | 평가 |
|---|---|
| **(A) `Flow<AiStreamEvent>` — TextChunk / SurfaceCreate** ✅ | UI·ViewModel 이 와이어 포맷을 모른다 (P4). 이벤트 추가 = sealed class 확장으로 컴파일 타임 검증 |
| (B) `Flow<String>` 유지 + ViewModel 에서 `{"version":"v0.9"...}` 라인 감지 | TODO 9 의 초안. 와이어 지식이 UI 계층에 새고, 청크 경계가 JSON 경계와 어긋나면 깨진다. 탈락 |
| (C) `Flow<ChatMessage>` (완성 메시지 단위) | 스트리밍 누적 UX 소실. 탈락 |

`SurfaceCreate` 의 payload 는 **JSON 문자열**이다 — 도메인 계층이 `a2ui/` 나 `org.json` 에
의존하지 않게 하기 위함 (파싱은 ViewModel 이 `A2UISurfaceParser` 에 위임).

### ~~D2. 와이어 파싱 = SSE·NDJSON 동시 지원~~ → 폐기 (2026-08-14)

NDJSON(`/chat`)을 쓰던 GeminiVoiceChatServer 는 더 이상 사용하지 않는다 (사용자 확정).
SSE 단일 경로로 단순화하고, **라우팅 기준을 data JSON 의 `type` 필드에서 SSE
`event:` 이벤트명으로 교체**한다 — 실서버의 a2ui 엔벨로프에는 `type` 필드가 없어
구 방식으로는 조용히 버려진다 (이번 버그의 직접 원인).

### D5. 엔벨로프 적용 = `A2UIEnvelopeApplier` 순수 함수 (파서 교체 아님)

| 대안 | 평가 |
|---|---|
| **(A) 신설 `A2UIEnvelopeApplier.apply(현재 서피스?, 엔벨로프 JSON) → 새 서피스`** ✅ | v0.9 엔벨로프는 "부분 갱신의 누적"이라 기존 원샷 파서와 모델이 다르다. 순수 함수로 두면 JVM 테스트 가능 (P10) |
| (B) `A2UISurfaceParser` 확장 | 원샷 파싱(로컬 데모 형식)과 누적 적용(서버 형식)이 한 파일에 섞임. 탈락 |
| (C) 서버에 구 형식으로 보내달라 요청 | 서버는 웹 클라이언트와 공용 — 표준 v0.9 를 클라이언트가 따르는 게 맞다. 탈락 |

- createSurface → 빈 서피스 생성 (theme.primaryColor, sendDataModel 반영)
- updateComponents → 컴포넌트 맵에 병합 (현 서버는 root 1개만 보냄)
- updateDataModel → `path`/`value` 를 JSON Pointer 로 반영 (`path:"/"` = 루트 병합)
- 파싱 실패 엔벨로프는 **로그 + 무시** — 텍스트 스트림을 죽이지 않는다 (수용 4)

### D6. 커스텀 카드 = `ServerCard(kind, valuePath)` 단일 타입 + kind 별 Composable

sealed class 에 카드 6종을 개별 타입으로 추가하는 대신 `ServerCard` 하나로 받고,
렌더러에서 kind 문자열로 분기한다. 근거: 6종 모두 "루트 1개 + summary 객체 바인딩"으로
구조가 동일하고, 서버가 카드를 추가할 때 모델 변경 없이 렌더러 분기만 늘리면 된다.
미지원 kind 는 "지원하지 않는 컴포넌트" 안내 카드로 폴백.
렌더링 데이터는 `getAtPath(dataModel, valuePath)` 의 Map 에서 읽는다 (기존 런타임 재사용).

### D7. 이벤트 계약 개명: `SurfaceCreate` → `A2UIEnvelope`

이전 판의 `SurfaceCreate(완성 서피스 JSON)` 는 "한 방에 완성"을 전제한 이름/의미라
누적 엔벨로프와 맞지 않는다. payload 는 계속 JSON 문자열 (도메인 무의존 — D1 유지).

### D3. 서피스 부착 = 메시지 `surfaceId` + `ChatState.surfaces` 맵 (기존 구조 재사용)

로컬 시나리오가 쓰던 구조(`ChatMessage.surfaceId` → `surfaces[id]` → `A2UISurfaceView`)를
그대로 쓴다. 렌더러·런타임(`A2UIRenderer`/`A2UIRuntime`) 0줄 변경.
스트리밍 중 `SurfaceCreate` 수신 시 placeholder 메시지에 `surfaceId` 를 부착하므로
텍스트와 서피스가 **한 말풍선**에 공존한다.

### D4. 로컬 키워드 매칭 제거, `A2UIScenarios` 는 존치

`submitUserPrompt` 의 `A2UIScenarios.match` 분기를 삭제해 모든 입력이 서버로 간다.
파일 자체는 삭제하지 않는다 — Mock 데모·향후 파서 테스트 픽스처로 재사용 가치가 있다.

## 영향 범위 (개정 2026-08-14)

| 파일 | 변경 |
|---|---|
| `domain/model/AiStreamEvent.kt` | 수정 — `SurfaceCreate` → `A2UIEnvelope` (D7) |
| `domain/repository/AiRepository.kt` | 수정 — 반환 타입 (D1, 완료) |
| `data/repository/RemoteAiRepository.kt` | 수정 — SSE 이벤트명 라우팅, NDJSON 제거 (D2 폐기) |
| `data/repository/MockAiRepository.kt` | 수정 — `TextChunk` 로 emit (P6, 완료) |
| `a2ui/A2UIEnvelopeApplier.kt` | **신규** — 엔벨로프 누적 적용 (D5) |
| `a2ui/A2UIModel.kt` | 수정 — `ServerCard` 추가 (D6) |
| `a2ui/A2UIServerCards.kt` | **신규** — 커스텀 카드 6종 Composable (D6) |
| `a2ui/A2UIRenderer.kt` | 수정 — `ServerCard` 분기 1개 추가 |
| `ui/chat/ChatViewModel.kt` | 수정 — 엔벨로프 적용 경로 (D5) |
| `a2ui/A2UISurfaceParser.kt` · `A2UIScenarios.kt` | **0줄** (존치 — 로컬 데모 형식) |

## 검증 전략

- JVM: `A2UIEnvelopeApplier` 단위 테스트 — 엔벨로프 3종 순차 적용, kind 매핑,
  파싱 실패 무시, `path:"/"` 루트 병합 (T104 대체)
- Mock 회귀: `USE_REMOTE=false` 로 텍스트 스트리밍 확인 (수용 1)
- 실기기 + 실서버: "사업장 점수 보여줘" 등 6종 인텐트 문구로 카드 렌더링 (수용 2·3) `[needs-device]`

## 리스크

- ~~**R1.** 서버 엔벨로프 스키마 미확정~~ → 2026-08-14 해소 (spec "확정된 와이어 계약")
- **R2.** SSE `data:` 한 줄이 큰 경우 — 서버는 엔벨로프 1건 = `data:` 한 줄로 보낸다
  (`json.dumps` 한 줄 직렬화 확인). 멀티라인 `data:` 는 계속 미지원 — 서버가 바꾸면 확장
- **R3.** 엔벨로프 파싱 실패 → D5 의 "로그 + 무시"로 완화 (수용 4). 텍스트 스트림 오류는
  기존 runCatching 유지
- **R4.** 카드 6종의 시각 디자인 레퍼런스 부재 (웹 미구현·미push) — 데이터 형태 기반
  신규 디자인. 웹 최종본이 나오면 톤 정합성 재검토 `[human]`
