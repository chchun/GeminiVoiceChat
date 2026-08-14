# 08. A2UI v0.9 — 생성형 UI (Generative UI)

AI 에이전트가 채팅 메시지 안에서 인터랙티브 UI 서피스를 직접 생성·조작하는 레이어.
웹앱([AIAgent_TUI](https://github.com/chchun/AIAgent_TUI))의 A2UI v0.9 구현을 Android Compose 네이티브로 포팅.

---

## 1. 개념: A2UI란?

**Agent-to-UI** — 에이전트가 텍스트 응답 대신(또는 함께) 구조화된 UI 정의를 전송하면,
클라이언트가 그것을 인터랙티브 컴포넌트로 렌더링하는 프로토콜.

```
사용자: "인시던트 보고해줘"
  ↓
에이전트: { createSurface } + { updateComponents } + { updateDataModel }
  ↓
클라이언트: 폼 렌더링 (ChoicePicker + TextField + Button)
  ↓
사용자: 폼 작성 후 "인시던트 생성" 버튼 탭
  ↓
에이전트: "✅ INC-1234 접수 완료" 텍스트 응답
```

채팅 UI가 정적 텍스트에 머물지 않고, 에이전트 의도에 따라 **동적으로 UI를 구성**한다.

---

## 2. 핵심 구성 요소

### 2.1 패키지 구조

```
a2ui/
├── A2UIModel.kt       — 타입 시스템 (sealed class 컴포넌트 트리, 서피스 등)
├── A2UIRuntime.kt     — 실행 엔진 (JSON Pointer, formatString, 조건 평가)
├── A2UIScenarios.kt   — 4개 데모 시나리오 + 키워드 매칭
└── A2UIRenderer.kt    — Compose 렌더러 (A2UINode recursive composable)
```

### 2.2 데이터 흐름

```
ChatViewModel
    │
    ├─ submitUserPrompt()
    │      └─ A2UIScenarios.match(text) → Scenario?
    │              ├─ match: 로컬 시나리오 → A2UISurface 생성 → ChatState.surfaces에 저장
    │              └─ no match: 서버 HTTP POST 스트리밍
    │
    ├─ onA2UIData(surfaceId, path, value)
    │      └─ deepCopyModel() → setAtPath() → 새 surface → ChatState 갱신
    │
    └─ onA2UIAction(surfaceId, eventName, context)
           └─ 이벤트별 AI 응답 메시지 생성 (+ 결과 서피스)

ChatScreen
    └─ AiMessage()
           ├─ MarkdownText(message.text)
           └─ A2UISurfaceView(surface, onData, onAction)   ← A2UI 렌더링
```

---

## 3. A2UIModel — 타입 레퍼런스

### 3.1 A2UIValue (값 바인딩)

| 타입 | 예시 | 설명 |
|---|---|---|
| `Static(text)` | `"인시던트 보고"` | 정적 문자열 |
| `PathBound(path)` | `"/status/rps"` | 데이터 모델에서 직접 읽기 |
| `FormatStr(template)` | `"${/deploy/canary}% 전환"` | `${}` 보간 — 데이터 변경 시 자동 갱신 |

### 3.2 A2UICondition (유효성 검사)

| 타입 | 동작 |
|---|---|
| `Required(valuePath)` | 해당 경로 값이 비어있지 않으면 통과 (String, List, Boolean 지원) |
| `Numeric(valuePath, min?, max?)` | 숫자 범위 검사 |
| `PathRef(path)` | truthy 값이면 통과 |
| `And(values)` | 모두 통과해야 통과 |
| `Or(values)` | 하나라도 통과하면 통과 |

Button의 `checks` 리스트에 넣으면 조건 불충족 시 버튼 비활성화 + 오류 메시지 표시.

### 3.3 A2UIComponent 목록

| 컴포넌트 | 주요 props | 비고 |
|---|---|---|
| `Card` | `child: String` | 단일 자식 래퍼. elevation 2dp. |
| `Column` | `children`, `justify`, `align` | `justify`: start/end/center/spaceBetween |
| `Row` | `children`, `justify`, `align` | 같음 |
| `TextComp` | `text: A2UIValue`, `variant` | variant: h1/h2/caption/body |
| `IconComp` | `name`, `size` | alert/activity/chart/check/settings/search 등 |
| `Divider` | — | `HorizontalDivider` |
| `ButtonComp` | `label`, `variant`, `action`, `checks` | variant: primary/secondary/borderless |
| `TextFieldComp` | `label`, `valuePath`, `variant` | variant: shortText/longText |
| `CheckBoxComp` | `label`, `valuePath` | — |
| `ChoicePickerComp` | `options`, `valuePath`, `variant` | variant: mutuallyExclusive/multiple → FilterChip |
| `SliderComp` | `label`, `valuePath`, `min`, `max`, `step` | — |
| `SwitchComp` | `label`, `valuePath` | — |
| `TagInputComp` | `label`, `valuePath`, `suggestions` | 제안 칩 + 커스텀 입력 + InputChip 태그 |
| `SelectComp` | `label`, `valuePath`, `options` | ExposedDropdownMenu |
| `RiskCardList` | `rowsPath` | 모바일용 위험성평가 카드 리스트 (DataTable 대체) |

### 3.4 A2UISurface

```kotlin
data class A2UISurface(
    val surfaceId: String,           // 고유 ID (시나리오명 + 타임스탬프)
    val primaryColor: Color,         // 테마 색상 (버튼, 아이콘, 체크박스 등에 적용)
    val agentDisplayName: String,    // 서피스 생성 에이전트 이름 (현재 미표시, 향후 헤더용)
    val components: Map<String, A2UIComponent>,  // 컴포넌트 ID → 컴포넌트 트리
    val rootId: String = "root",     // 렌더링 진입점
    val dataModel: MutableMap<String, Any?>,     // JSON Pointer 네비게이션 가능 중첩 맵
    val sendDataModel: Boolean = true,           // 버튼 액션 시 전체 모델 포함 여부
)
```

---

## 4. A2UIRuntime — 실행 엔진

### 4.1 JSON Pointer 경로 해석

```kotlin
// 읽기: /incident/summary → map["incident"]["summary"]
getAtPath(model, "/incident/summary")

// 쓰기: /deploy/canary = 25
setAtPath(model, "/deploy/canary", 25)
```

중간 경로가 없으면 자동 생성 (`mutableMapOf`).

### 4.2 FormatString 보간

```kotlin
resolveFormatString("신버전으로 \${/deploy/canary}% 전환합니다.", model)
// → "신버전으로 25% 전환합니다."
```

`A2UINode`는 `TextComp(FormatStr(...))`를 렌더링할 때마다 호출되므로,
Slider 조작 → 데이터 모델 변경 → surface 교체 → recomposition → 텍스트 자동 갱신.

### 4.3 조건 평가

```kotlin
// 배포 버튼 예시: healthOk=true AND canary >= 1
val condition = A2UICondition.And(listOf(
    A2UICondition.PathRef("/deploy/healthOk"),
    A2UICondition.Numeric("/deploy/canary", min = 1.0),
))
evalCondition(condition, model)  // true/false
```

`checksPass(checks, model)` 가 false면 버튼 `enabled=false`.
`firstFailMessage(checks, model)` 가 오류 문자열 반환 → 버튼 아래 빨간 텍스트 표시.

### 4.4 상태 불변성

```kotlin
// ViewModel에서 데이터 업데이트 시
val newModel = deepCopyModel(surface.dataModel)   // deep copy
setAtPath(newModel, path, value)                  // 변경
val newSurface = surface.copy(dataModel = newModel)
state.copy(surfaces = state.surfaces + (surfaceId to newSurface))
```

`MutableMap` 레퍼런스를 그대로 바꾸면 Compose가 변경을 감지 못함.
`deepCopyModel`로 새 인스턴스 생성 후 교체해야 recomposition 발동.

---

## 5. 4개 데모 시나리오

### 5.1 인시던트 보고 폼

**트리거 키워드:** `인시던트`, `incident`, `장애 보고`, `보고서 폼`, `인시던트 생성`

**데이터 모델 초기값:**
```json
{ "incident": { "severity": "P2", "service": "order-service", "summary": "", "customerImpact": false } }
```

**컴포넌트 트리:**
```
Card
└─ Column
   ├─ Row: Icon(alert) + Text("인시던트 보고", h2)
   ├─ Text("심각도", caption)
   ├─ ChoicePicker(P1/P2/P3, mutuallyExclusive) → /incident/severity
   ├─ TextField("영향 서비스") → /incident/service
   ├─ TextField("증상 요약", longText, required) → /incident/summary
   ├─ CheckBox("고객 영향") → /incident/customerImpact
   └─ Button("인시던트 생성", primary, required check)
         action: create_incident { service, severity, summary, customerImpact }
```

**액션 응답:** `✅ INC-XXXX 인시던트 생성 + 상세 정보`

---

### 5.2 카나리 배포 승인

**트리거 키워드:** `배포`, `deploy`, `롤아웃`, `카나리`, `canary`, `배포 승인`

**데이터 모델 초기값:**
```json
{ "deploy": { "canary": 10, "healthOk": false } }
```

**컴포넌트 트리:**
```
Card
└─ Column
   ├─ Text("카나리 배포 승인", h2)
   ├─ Text("payment-service · v2.4.1 → production", caption)
   ├─ Slider(0~100, step=5) → /deploy/canary
   ├─ Text(FormatStr: "신버전으로 ${/deploy/canary}% 전환")  ← 실시간 갱신
   ├─ Divider
   ├─ CheckBox("헬스체크 확인") → /deploy/healthOk
   └─ Row
      ├─ Button("취소", borderless) → cancel_deploy
      └─ Button("배포 시작", primary, And(healthOk, canary≥1))
            action: start_deploy { service, version, canary, healthOk }
```

**핵심:** Slider 움직일 때마다 FormatStr 텍스트가 즉시 갱신됨.

---

### 5.3 서비스 상태 카드

**트리거 키워드:** `상태 카드`, `서비스 상태`, `status card`, `상태 대시보드`, `헬스 카드`

**데이터 모델 초기값:**
```json
{ "status": { "range": "1h", "p99": 842, "errorRate": 1.3, "rps": "2.4k" } }
```

**컴포넌트 트리:**
```
Card
└─ Column
   ├─ Row: Icon(activity) + Text("payment-service 상태", h2)
   ├─ ChoicePicker(15분/1시간/24시간) → /status/range
   ├─ Divider
   ├─ Row(spaceBetween)
   │  ├─ Column: Text("${/status/p99}ms", h1) + Text("p99 지연", caption)
   │  ├─ Column: Text("${/status/errorRate}%", h1) + Text("에러율", caption)
   │  └─ Column: Text(/status/rps, h1) + Text("RPS", caption)
   └─ Button("새로고침", secondary)
         action: refresh_status { range }
```

**특징:** 표시 전용 바인딩 (입력 없음). 기간 선택 + 새로고침 액션만 존재.

---

### 5.4 위험성평가 자동 생성

**트리거 키워드:** `위험성평가`, `위험 평가`, `리스크 평가`, `안전 점검`, `작업 위험`

**입력 폼 데이터 모델:**
```json
{ "risk": { "trades": [], "location": "일반", "equip": "", "count": "5" } }
```

**입력 폼 컴포넌트:**
```
Card
└─ Column
   ├─ Text("위험성평가 자동 생성", h2)
   ├─ TagInput("공종 선택", suggestions=[배관,용접,비계,전기,굴착,도장]) → /risk/trades
   ├─ Select("작업 장소", [일반/지상/고소/지하/밀폐공간]) → /risk/location
   ├─ TextField("사용 장비") → /risk/equip
   ├─ Select("공종당 항목 수", [3/5/8/10]) → /risk/count
   └─ Button("AI 위험성평가 생성", primary, Required(/risk/trades))
         action: generate_risk { trades, count, location, equip }
```

**generate_risk 액션 처리:**
1. `A2UIScenarios.buildRiskResultSurface(trades, count)` 호출
2. 내장 `HAZARD_DB`에서 공종별 위험 항목 조회
3. 결과 서피스를 새 AI 메시지에 첨부

**결과 서피스:**
```
Card
└─ Column
   ├─ Text("N개 공종 · 총 M건 · 고위험 K건", caption)
   ├─ Divider
   └─ RiskCardList  ← 위험 항목을 카드 리스트로 표시
```

**RiskRowCard 구조 (항목당):**
```
Card
└─ Column
   ├─ Row: "N. 공종명" + 위험도 배지 (상=빨강/중=주황/하=초록)
   ├─ Text: 작업 내용
   ├─ Text: 재해 원인 (회색, 2줄)
   └─ Text: 개선 대책 (초록)
```

---

## 6. 키워드 매칭 로직

```kotlin
// ChatViewModel.submitUserPrompt()
val scenario = A2UIScenarios.match(prompt)
if (scenario != null) {
    val surface = scenario.surface()           // 서피스 생성
    val aiMsg = ChatMessage(
        text = scenario.replyMd,               // 안내 텍스트
        senderType = SenderType.AI,
        surfaceId = surface.surfaceId,         // 서피스 연결
    )
    _state.update {
        it.copy(
            messages = it.messages + userMsg + aiMsg,
            surfaces = it.surfaces + (surface.surfaceId to surface),
        )
    }
    return   // 서버 호출 없음
}
// 키워드 미매칭 → 서버 HTTP POST 스트리밍
```

---

## 7. 확장 가이드

### 새 시나리오 추가

```kotlin
// A2UIScenarios.kt
val ALL = listOf(
    // ... 기존 4개 ...
    Scenario(
        keys = listOf("새 키워드"),
        replyMd = "안내 텍스트 (**마크다운** 지원)",
        surface = ::myNewSurface,
    ),
)

fun myNewSurface(): A2UISurface = A2UISurface(
    surfaceId = "my_surface_${System.currentTimeMillis()}",
    primaryColor = Color(0xFF...),
    agentDisplayName = "My Bot",
    components = mapOf( /* 컴포넌트 트리 */ ),
    dataModel = mutableMapOf( /* 초기값 */ ),
)
```

### 새 액션 추가

```kotlin
// ChatViewModel.onA2UIAction()
"my_action" -> {
    val value = context["key"]?.toString() ?: ""
    addAiReply("처리 결과: $value")
}
```

### 새 컴포넌트 추가

1. `A2UIModel.kt`의 `A2UIComponent` sealed class에 data class 추가.
2. `A2UIRenderer.kt`의 `A2UINode` when 절에 렌더링 코드 추가.

---

## 8. 서버 와이어 계약 (2026-08-14 확정 — specs/001)

> 이 절이 서버 연동의 현행 계약이다. 진실원천: saferyn-langgraph 리포
> `app/utils/a2ui.py`(빌더) + `app/api/routes.py`(송출). 아래 8-1 이전의
> "서버 연동 확장 (향후)" 초안은 폐기됐다 (구 GeminiVoiceChatServer NDJSON 형식).

### 8-1. 전송 — SSE (`POST /chat/stream?api_key=...`)

```
event: token
data: {"text": "..."}                    ← 텍스트 청크 (마크다운 누적)

event: a2ui
data: {"version":"v0.9", <엔벨로프 1건>}   ← 아래 3종 중 하나, 한 줄 직렬화

(종료 이벤트 없음 — 스트림이 닫히면 완료. event: error 시 data.message 가 오류 메시지)
```

### 8-2. A2UI v0.9 엔벨로프 3종 (송출 순서 고정)

```json
{"version":"v0.9","createSurface":{"surfaceId":"...","catalogId":"...","theme":{"primaryColor":"#RRGGBB"},"sendDataModel":true}}
{"version":"v0.9","updateComponents":{"surfaceId":"...","components":[{"id":"root","component":"<Kind>","value":{"path":"/<modelKey>"}}]}}
{"version":"v0.9","updateDataModel":{"surfaceId":"...","path":"/","value":{"<modelKey>":{...summary...},"raw":{...원본 API 응답...}}}}
```

클라이언트 적용기: `a2ui/A2UIEnvelopeApplier.kt` (누적 적용, 실패 시 로그+무시).

### 8-3. 서버 커스텀 카드 kind 목록 (렌더러: `a2ui/A2UIServerCards.kt`)

| kind | modelKey | 트리거 인텐트 (서버 키워드) |
|---|---|---|
| `CompletionGaugeCard` | riskMeasure / checkMeasure | "감소대책 이행율" / "부적합 조치율" |
| `ActvScoreSummaryCard` | activityScore | "사업장 활동 점수", "활동 점수" |
| `WeeklyScheduleCard` | weeklySchedule | "주간 일정", "이번 주 일정" |
| `PendingApprovalListCard` | pendingApprovals | (결재 대기 문서 조회) |
| `OpertPlanStatusCard` | opertPlan | "작업계획서" |
| `OpertStopStatusCard` | opertStop | "작업중지" |
| `AccidentListCard` | accidents | "재해 발생 내역", "사고 현황" (서버 반영 2026-08-14) |

미지원 kind 는 "지원하지 않는 컴포넌트" 폴백 카드로 렌더링된다.

### 8-4. 표준(basic catalog) 컴포넌트 와이어 형식 — 아차사고/안전제안 폼 (서버 `b8356ec`~)

서버의 `build_safety_report_form_a2ui` 는 커스텀 카드가 아니라 **표준 컴포넌트 조합**을
보낸다. 클라이언트 applier(T110)가 아래 형식을 기존 `A2UIComponent` 로 매핑한다:

```json
{"id":"root","component":"Card","child":"form"}
{"id":"form","component":"Column","children":["title","kind",...]}
{"id":"title","component":"Text","text":"아차사고 등록","variant":"h2"}
{"id":"kind","component":"ChoicePicker","variant":"mutuallyExclusive","value":{"path":"/report/kind"},"options":[{"label":..,"value":..}]}
{"id":"receipt_dt","component":"TextField","label":"청취일시","variant":"datetime","value":{"path":"/report/receiptDateTime"},
 "checks":[{"call":"required","args":{"value":{"path":"/report/receiptDateTime"}},"message":"..."}]}
{"id":"registrant","component":"TextField","readonly":true, ...}
{"id":"photos","component":"FileUpload","label":"사진","value":{"path":"/report/photos"},
 "accept":[".jpg",".gif",".png",".webp"],"maxFiles":5,"maxSizeMb":50}   ← Photo Picker 렌더링 (spec 002 T105)
{"id":"submit","component":"Button","text":"아차사고 등록","variant":"primary","checks":[...],
 "action":{"event":{"name":"register_safety_report","context":{"kind":{"path":"/report/kind"}, ...}}}}
```

주의점 (로컬 데모 형식과의 차이):
- 타입 필드가 `type` 이 아니라 `component`, 값 바인딩은 `value:{path}` 축약형
- `Text` 는 `text` 직접 문자열, `Button` 은 `text` + `action.event.{name,context}`
- `checks` 는 `{call:"required"|"numeric", args:{value:{path},min?,max?}, message}` 형태
- `theme.agentDisplayName` 이 createSurface 의 theme 안에 있음
- 버튼 액션의 서버 왕복은 §8-5 참조 (specs/002 에서 연동 완료).

### 8-5. 액션 왕복 — `POST /a2ui/action` (specs/002, 계약 확정 2026-08-14)

```
요청:  POST {서버베이스}/a2ui/action   (Content-Type: multipart/form-data — 파일이 없어도!)
       action = {"name":"register_safety_report","surfaceId":"safety-report-form","context":{...}}  (JSON 문자열 파트)
       files  = <첨부 파일들>  (선택, 복수 가능)
응답:  {"ok": true|false, "result": {...}, "md": "✅ **아차사고 등록이 완료되었습니다.** ..."}
```

⚠ JSON 바디(`application/json`)로 보내면 서버가 **500** 을 반환한다 — 반드시 multipart
(개정 2026-08-14, 실기기 검증에서 발견).

- 인증·`X-Tenant-Id`/`X-Bplc-Id` 헤더 **불필요** — 미전송 시 서버가 자체 토큰·상수
  기본값으로 처리 (운영 전환 시 정책 재확정 필요). api_key 검사 없음.
- 파일 첨부: 같은 multipart 에 `files` 파트(복수)로 전송 (spec 002 T105). action JSON 의
  `context.photos` 에는 `[{name}]` 파일명 목록만 실린다 (bytes 는 files 파트에만)
- 클라이언트 동작: `md` 를 AI 말풍선에 마크다운 렌더링. `ok:true` 면 해당 서피스의
  제출 버튼(같은 eventName 의 Button)을 "✅ 등록이 완료되었습니다." 텍스트로 치환해
  중복 등록 방지. 실패(네트워크/HTTP 오류) 시 버튼 유지 → 재시도 가능.
- `register_safety_report` 만 서버 왕복하며, 로컬 데모 액션(create_incident 등)은 로컬 처리 유지.

## 9. (폐기) 구 서버 연동 초안 — GeminiVoiceChatServer NDJSON

현재는 데모 시나리오만 지원 (로컬 키워드 매칭).
실제 서버가 A2UI 엔벨로프를 반환하도록 확장하려면:

1. HTTP POST 응답 스트림에서 `\n` 구분 JSONL 파싱 추가.
2. 각 라인이 `{"version":"v0.9","createSurface":{...}}` 형태면 서피스 상태 업데이트.
3. 일반 텍스트 청크는 기존대로 `appendChunkToMessage` 처리.

---

## 9. 버전 이력

| 버전 | 날짜 | 내용 |
|---|---|---|
| 1.0 | 2026-06-17 | 최초 작성. A2UI v0.9 Kotlin/Compose 포트, 4개 데모 시나리오 포함. |
