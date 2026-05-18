# v1.2.0 UI 변경 가이드 — 마크다운 렌더링 + 인라인 음성 입력

## 변경 요약

| 항목 | 이전 (v1.1.0) | 이후 (v1.2.0) |
|---|---|---|
| AI 답변 렌더링 | 말풍선 (`Surface`) + 일반 텍스트 | 말풍선 없음, 풀폭 마크다운 |
| AI 로딩 인디케이터 | 말풍선 안 점 3개 펄스 | 헤더 스파클(✦) 아이콘 회전 |
| TTS 재생 | 음성 모드 자동 루프 전용 | 메시지별 스피커 버튼 (토글) |
| 음성 입력 UI | 전체화면 VoiceOverlay | InputBar 인라인 파형 전환 |
| 음성 입력 흐름 | LISTENING→THINKING→SPEAKING 루프 | 한 번만 받아쓰기 후 자동 전송 |

---

## 1. AI 메시지 마크다운 렌더링

### 라이브러리

`dev.jeziellago:compose-markdown:0.5.7` (JitPack).

```toml
# gradle/libs.versions.toml
composeMarkdown = "0.5.7"
compose-markdown = { group = "com.github.jeziellago", name = "compose-markdown", version.ref = "composeMarkdown" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.compose.markdown)
```

```kotlin
// settings.gradle.kts — JitPack 저장소 추가
maven { url = uri("https://jitpack.io") }
```

### 메시지 레이아웃 구조 (`AiMessage` Composable)

```
┌──────────────────────────────────────────────┐
│ ✦ (스파클 아이콘, 스트리밍 중 회전)  [🔊 스피커]│
│                                              │
│  마크다운 본문 (MarkdownText)                │
│  - **굵게**, *기울임*                         │
│  - # 제목 / - 리스트 / ` 코드 `              │
└──────────────────────────────────────────────┘
```

- `isStreaming=true` → 스파클 아이콘이 1.4초 주기 무한 회전. 본문이 아직 비어있으면 점 3개 펄스 표시.
- `isStreaming=false` → 스파클 아이콘 정지. 스피커 버튼 활성화.

### TTS 토글 동작

| 상태 | 아이콘 | 탭 결과 |
|---|---|---|
| 미재생 | 🔊 (VolumeUp) | 이 메시지 TTS 재생 시작 |
| 재생 중 | ■ (Stop), primary 컬러 | TTS 정지 |
| 다른 메시지 재생 중 | 🔊 | 이전 재생 중단 후 이 메시지 재생 |

`ChatState.playingMessageId: String?`로 재생 중인 메시지를 추적.  
`VoiceEvent.SpeakingFinished` 수신 시 자동으로 `null`로 초기화.

---

## 2. 인라인 음성 입력 (한 번만 받아쓰기)

### 상태 전환

```
[마이크 버튼 탭]
    │
    ▼ state.voice.isActive = true
[InputBar → 파형 모드]
    │  ┌──────────────────────────────────────┐
    │  │  ░▌▌▌▌│▌▌▌▌│▌▌▌▌│▌▌▌▌░  [■ 정지]  │
    │  └──────────────────────────────────────┘
    │  앱바 우측: ● (초록 마이크 인디케이터)
    │
    ├─ [VoiceEvent.Rms] → 파형 막대 높이 갱신 (amplitude 기반)
    ├─ [VoiceEvent.Partial] → 인식 중 텍스트 (내부 상태, UI에 미표시)
    ├─ [VoiceEvent.EndOfSpeech] → mode = THINKING (파형 유지)
    │
    └─ [VoiceEvent.Final]
           │
           ▼ state.voice 즉시 초기화 (isActive = false)
       [InputBar → 키보드 모드 복귀]
           │
           ▼ submitUserPrompt(recognizedText)
       [AI 응답 스트리밍 → 마크다운으로 렌더링]
```

### 정지 버튼 동작

- 사용자가 ■ 버튼을 누르면 `endVoiceMode()` 호출 → `voiceController.stopListening()` + 상태 초기화.
- 인식 결과 없이 취소되므로 아무 메시지도 전송되지 않음.

### 앱바 인디케이터

음성 모드가 활성화된 동안 TopAppBar 우측에 초록 원형(#34A853) 안에 마이크 아이콘이 표시됨. 탭 동작 없음, 순수 상태 표시용.

---

## 3. 제거된 컴포넌트

| 파일 | 이유 |
|---|---|
| `ui/voice/VoiceOverlay.kt` | 전체화면 오버레이 방식 폐기. 인라인 파형으로 대체. |

---

## 4. ChatViewModel 변경 요점

### 이벤트 수집 구조 변경

이전에는 `voiceEventsJob`을 `startVoiceMode()`에서 시작하고 `endVoiceMode()`에서 취소했음. 이제 `init { }` 블록에서 항상 수집. 이유: 메시지별 TTS 토글(음성 모드 밖에서도 동작)과 `SpeakingFinished`/`SpeakingStarted` 이벤트를 항상 처리해야 하기 때문.

### 자동 루프 제거

`resumeListeningIfActive()` + `SpeakingFinished → startListening()` 루프 제거. 받아쓰기(한 번) → 전송 → 종료.

### 새 공개 API

| 메서드 | 설명 |
|---|---|
| `onMessageTtsToggled(message)` | 메시지 TTS 재생/정지 토글 |
| `startVoiceMode()` | 인라인 음성 입력 시작 (기존과 동일한 이름 유지) |
| `endVoiceMode()` | 음성 입력 취소 |
