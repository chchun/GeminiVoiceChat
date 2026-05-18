# Gemini Voice Chat

Gemini Live 스타일의 **실시간 대화형 AI 안드로이드 앱** 클라이언트.
텍스트와 음성 두 가지 방식으로 AI와 대화할 수 있고, 백엔드 없이도 동작하는 Mock 모드로 완전한 UI/UX를 체험할 수 있습니다.

> **이 저장소를 처음 보는 분께**: 이 문서는 단순한 사용법 안내를 넘어, Android 모던 아키텍처(MVVM + DIP + Jetpack Compose + Coroutines/Flow)를 학습할 수 있도록 구조와 핵심 개념을 코드 위치와 함께 설명합니다.

---

## 목차

1. [무엇을 하는 앱인가](#1-무엇을-하는-앱인가)
2. [기술 스택](#2-기술-스택)
3. [빠른 시작](#3-빠른-시작)
4. [프로젝트 구조 살펴보기](#4-프로젝트-구조-살펴보기)
5. [핵심 개념 학습 가이드](#5-핵심-개념-학습-가이드)
6. [동작 흐름 깊이 보기](#6-동작-흐름-깊이-보기)
7. [확장하기 — Mock에서 실제 서버로](#7-확장하기--mock에서-실제-서버로)
8. [디버깅과 트러블슈팅](#8-디버깅과-트러블슈팅)
9. [더 읽을거리](#9-더-읽을거리)

---

## 1. 무엇을 하는 앱인가

### 사용자 입장
- **텍스트 채팅**: 카카오톡처럼 메시지를 입력하면 AI가 한 글자씩 타자 치듯 답변합니다.
- **음성 대화**: 마이크 버튼을 누르면 입력창이 파형 애니메이션으로 전환되어 말을 인식합니다. 인식 완료 시 자동으로 전송되고 AI 답변이 마크다운으로 표시됩니다 (v1.2.0~).

### 개발자 입장
- **Mock 모드** (기본): 가짜 AI가 동작합니다. 외부 서버 없이 UI/UX의 완성도를 검증할 수 있습니다.
- **Remote 모드** (v1.1.0~): FastAPI 서버와 WebSocket으로 연결, 실제 Gemini 응답을 스트리밍합니다. `local.properties`의 한 줄(`USE_REMOTE=true`)로 전환. [§7 확장하기](#7-확장하기--mock에서-실제-서버로) 참조.
- 두 모드는 **UI/ViewModel 코드를 한 줄도 안 바꾸고** 교체됩니다. 이 책임 분리를 가능하게 하는 원칙이 DIP — 의존성 역전 원칙.

---

## 2. 기술 스택

| 영역 | 사용 기술 | 한 줄 설명 |
|---|---|---|
| 언어 | **Kotlin 2.2.10** | Android 공식 권장 언어. Java보다 간결하고 안전. |
| UI | **Jetpack Compose** (BOM 2026.02.01) | XML이 아닌 Kotlin 코드로 UI를 선언적으로 작성하는 최신 프레임워크. |
| 상태 관리 | **ViewModel + StateFlow** | 화면 회전이나 재구성에도 안전한 비동기 상태 보관 + 관찰. |
| 비동기 | **Coroutines + Flow** | 콜백 지옥 없이 비동기/스트리밍 코드를 동기 코드처럼 작성. |
| 음성 인식 | **`android.speech.SpeechRecognizer`** | 안드로이드 OS 내장 STT. 네트워크/온디바이스 둘 다 지원. |
| 음성 합성 | **`android.speech.tts.TextToSpeech`** | 안드로이드 OS 내장 TTS. |
| 마크다운 | **compose-markdown 0.5.7** | AI 답변의 **/**제목/리스트/표/코드 마크다운 렌더링. |
| 디자인 시스템 | **Material 3** | 구글 표준 디자인 가이드의 최신 버전. |
| 빌드 | **Gradle 9.x (Kotlin DSL)** | `build.gradle.kts` |
| 최소 지원 | minSdk 24 (Android 7.0) / targetSdk 36 | |

---

## 3. 빠른 시작

### 사전 준비

- **Android Studio** Hedgehog 이상 (Kotlin 2.x 플러그인 지원 버전)
- **JDK 11+** — Android Studio에 번들된 JBR을 그대로 써도 됩니다 (Windows 기준 경로: `C:\Program Files\Android\Android Studio\jbr`)
- **Android 7.0 이상 실기기 또는 에뮬레이터** — 보이스 모드 테스트는 **실기기** 권장 (에뮬레이터에서 STT/TTS가 불안정)

### 빌드 & 실행

```bash
# Windows에서 셸이 JAVA를 못 찾을 때
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 디버그 APK 빌드
./gradlew assembleDebug

# 연결된 디바이스에 설치
./gradlew installDebug

# Android Studio에서는 그냥 Run(▶) 버튼이면 충분합니다.
```

### 처음 실행했을 때
1. 텍스트 입력창에 아무 메시지나 보내보면, AI가 한국어 더미 응답을 한 단어씩 타이핑하듯 출력합니다.
2. 좌하단 **마이크 버튼** 탭 → 마이크 권한 허용 → 입력창이 파형으로 전환되면 말을 시작하세요. 인식 완료 후 자동 전송됩니다.

### 갤럭시(특히 S24)에서 음성 인식이 안 될 때
- 설정 → 일반 → 언어 및 키보드 → **온디바이스 음성 인식 → 한국어 추가**
- 설정 → 앱 → 기본 앱 선택 → **디지털 어시스턴트 앱을 Google로 변경**
- 자세한 가이드: [§8 디버깅과 트러블슈팅](#8-디버깅과-트러블슈팅)

---

## 4. 프로젝트 구조 살펴보기

### 4.1 최상위 디렉토리

```
GeminiVoiceChat/
├── app/                          # 안드로이드 앱 모듈 (이 프로젝트의 본체)
├── gradle/                       # Gradle 래퍼와 버전 카탈로그
│   └── libs.versions.toml        # 의존성 버전을 한곳에서 관리
├── build.gradle.kts              # 루트 프로젝트 빌드 스크립트
├── settings.gradle.kts           # 모듈 등록
├── gradlew / gradlew.bat         # Gradle 래퍼 실행 스크립트
├── README.md                     # 이 파일
├── CHANGELOG.md                  # 버전별 변경 이력 (Keep a Changelog 형식)
├── CLAUDE.md                     # AI 코딩 어시스턴트(Claude Code)용 가이드
└── TODO.md                       # 미구현/추후 작업 항목
```

### 4.2 `app/` 모듈

```
app/
├── docs/
│   ├── 01_requirements.md            # PRD (제품 요구사항 정의서)
│   ├── 02_architecture.md            # 기술 아키텍처 가이드
│   ├── 03_server_api.md              # 서버 WebSocket 통신 규격 (SSoT)
│   ├── 04_server_handoff.md          # Phase 1 서버 통합 가이드
│   └── 05_websocket_keepalive_fix.md # Keepalive + 백그라운드 재연결 처방
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml   # 앱 메타데이터, 권한, 컴포넌트 선언
│   │   ├── java/com/aromit/geminivoicechat/  # ⭐ 실제 소스 코드
│   │   └── res/                  # 리소스 (문자열, 색상, 아이콘)
│   ├── test/                     # 단위 테스트
│   └── androidTest/              # 계측 테스트 (실디바이스 필요)
├── build.gradle.kts              # 앱 모듈 빌드 스크립트
└── proguard-rules.pro            # 릴리스 빌드 코드 난독화 규칙
```

### 4.3 ⭐ 핵심 소스 디렉토리 (`app/src/main/java/com/aromit/geminivoicechat/`)

이 프로젝트의 **모든 비즈니스 로직과 UI 코드**는 이 안에 있습니다. **레이어별로 폴더가 나뉘어** 있으니, 학습 순서대로 따라가세요.

```
com.aromit.geminivoicechat/
│
├── domain/                       # 🟦 도메인 레이어 — 순수 비즈니스 규약
│   ├── model/
│   │   ├── ChatMessage.kt        # 대화 메시지 데이터 모델 (불변)
│   │   ├── SenderType.kt         # USER / AI 열거형
│   │   └── AiResponse.kt         # 보이스 응답 sealed interface (서버 연동용)
│   └── repository/
│       └── AiRepository.kt       # AI 통신 인터페이스 — 구현체는 모름
│
├── data/                         # 🟩 데이터 레이어 — 인터페이스 구현체
│   └── repository/
│       ├── MockAiRepository.kt   # 가짜 AI 구현 (delay + Flow emit으로 시뮬레이션)
│       └── RemoteAiRepository.kt # FastAPI 서버 WebSocket 구현 (v1.1.0~)
│
├── di/                           # 🟨 의존성 주입(DI) 모듈
│   └── AppContainer.kt           # 어떤 구현체를 인터페이스에 바인딩할지 결정
│
├── ui/                           # 🟥 UI/Presentation 레이어
│   ├── chat/
│   │   ├── ChatScreen.kt         # 메인 채팅 화면 Composable
│   │   ├── ChatViewModel.kt      # 화면 상태와 비즈니스 로직
│   │   └── ChatState.kt          # 화면이 가져야 할 상태(데이터)
│   ├── voice/
│   │   ├── VoiceMode.kt          # LISTENING / THINKING / SPEAKING 열거형
│   │   ├── VoiceState.kt         # 보이스 모드의 상태(데이터)
│   │   ├── VoiceEvent.kt         # 보이스 컨트롤러가 발생시키는 이벤트 sealed
│   │   ├── VoiceController.kt    # 마이크/스피커 추상화 인터페이스
│   │   └── AndroidVoiceController.kt  # 실제 구현 (SpeechRecognizer + TTS)
│   └── theme/
│       ├── Color.kt              # 색상 팔레트
│       ├── Theme.kt              # 라이트/다크 테마 설정
│       └── Type.kt               # 폰트/타이포그래피
│
├── GeminiVoiceChatApp.kt         # 🚀 Application 클래스 — AppContainer 소유
└── MainActivity.kt               # 🚀 진입점 Activity — ChatScreen을 띄움
```

### 4.4 색상으로 보는 4계층 구조

```
┌─────────────────────────────────────────────────────────────┐
│  🟥 UI Layer (ui/)                                          │
│     - Compose 화면, ViewModel, 상태                          │
│     - 오직 인터페이스만 안다 (AiRepository, VoiceController) │
└─────────────────────────────────────────────────────────────┘
                          ▲ (인터페이스를 통해서만 의존)
                          │
┌─────────────────────────────────────────────────────────────┐
│  🟦 Domain Layer (domain/)                                  │
│     - 인터페이스(AiRepository)와 순수 모델(ChatMessage)     │
│     - 안드로이드 SDK에 의존하지 않음                         │
└─────────────────────────────────────────────────────────────┘
                          ▲ (Domain의 인터페이스를 구현)
                          │
┌─────────────────────────────────────────────────────────────┐
│  🟩 Data Layer (data/)                                      │
│     - AiRepository 구현 (지금은 Mock, 나중에 Remote)         │
└─────────────────────────────────────────────────────────────┘

  🟨 DI(di/)는 "어떤 구현체를 쓸지" 결정하는 스위치 역할.
     UI는 DI 컨테이너에게서 인터페이스 인스턴스를 받아 쓸 뿐.
```

이 그림이 **이 프로젝트 아키텍처의 핵심**입니다. 외운다는 생각으로 보세요.

---

## 5. 핵심 개념 학습 가이드

이 절은 코드를 읽기 전에 알아두면 좋은 개념들을 **이 프로젝트의 실제 코드 위치**와 함께 정리한 것입니다.

### 5.1 DIP — 의존성 역전 원칙 (Dependency Inversion Principle)

> **High-level 모듈(UI/ViewModel)이 Low-level 모듈(데이터 소스)에 직접 의존해서는 안 된다. 둘 다 추상(인터페이스)에 의존해야 한다.**

이 프로젝트에서:
- `ChatViewModel` ([ui/chat/ChatViewModel.kt](app/src/main/java/com/aromit/geminivoicechat/ui/chat/ChatViewModel.kt))은 `AiRepository` **인터페이스**만 안다.
- 실제로 호출되는 건 `MockAiRepository` ([data/repository/MockAiRepository.kt](app/src/main/java/com/aromit/geminivoicechat/data/repository/MockAiRepository.kt))지만, ViewModel은 그 존재조차 모름.
- 누가 인터페이스와 구현체를 연결하는가? → `DefaultAppContainer` ([di/AppContainer.kt](app/src/main/java/com/aromit/geminivoicechat/di/AppContainer.kt))의 단 한 줄:

  ```kotlin
  override val aiRepository: AiRepository by lazy { MockAiRepository() }
  ```

  나중에 이 한 줄만 `RemoteAiRepository()`로 바꾸면 UI/ViewModel 코드는 그대로 둔 채 백엔드가 교체됩니다. **이게 DIP의 보상.**

### 5.2 Jetpack Compose — 선언적 UI

기존 안드로이드는 XML에 UI를 그리고 Java/Kotlin으로 조작했습니다. Compose는 **Kotlin 함수가 곧 UI**입니다.

```kotlin
@Composable
fun Greeting(name: String) {
    Text(text = "Hello, $name!")
}
```

핵심 규칙:
- **`@Composable` 함수는 UI를 "그리는 방법"을 기술**합니다. 직접 그리지 않습니다.
- 함수가 받은 데이터(`name`)가 바뀌면 Compose가 자동으로 다시 호출(**recomposition**)해서 화면을 갱신합니다.
- 이 프로젝트에서 가장 큰 Composable은 [`ChatScreen.kt`](app/src/main/java/com/aromit/geminivoicechat/ui/chat/ChatScreen.kt)입니다 — 작은 Composable들(`MessageBubble`, `InputBar`, `MessageList`)로 분해되어 있으니 순서대로 읽으면 좋습니다.

### 5.3 Kotlin Coroutines + Flow

`suspend fun`은 **함수 실행을 일시 중단했다가 재개할 수 있는 비동기 함수**입니다. 콜백 없이 동기 코드처럼 비동기를 다룰 수 있습니다.

`Flow<T>`는 **시간차를 두고 여러 값을 emit하는 비동기 스트림**입니다. 이 프로젝트의 핵심 활용 예:

[`MockAiRepository.sendMessage`](app/src/main/java/com/aromit/geminivoicechat/data/repository/MockAiRepository.kt):
```kotlin
override suspend fun sendMessage(text: String): Flow<String> = flow {
    delay(800)                       // 네트워크 대기 시뮬레이션
    for (word in response.split(" ")) {
        emit(" $word")               // 단어 하나씩 흘려보냄
        delay(80)                    // 타이핑 효과
    }
}.flowOn(Dispatchers.IO)             // I/O 스레드에서 실행
```

소비 측 ([ChatViewModel.streamAiResponse](app/src/main/java/com/aromit/geminivoicechat/ui/chat/ChatViewModel.kt)):
```kotlin
aiRepository.sendMessage(prompt).collect { chunk ->
    appendChunkToMessage(aiMessageId, chunk)   // chunk가 emit될 때마다 호출
}
```

**학습 포인트**: `Flow`는 "결과를 한 번에 받는 함수(`return`)" 와 "결과를 여러 번 받는 함수(`emit`)" 의 차이라고 이해하면 빠릅니다.

### 5.4 ViewModel + StateFlow — 단방향 데이터 흐름

**ViewModel**은 화면 회전이나 액티비티 재생성에도 살아남는 객체입니다. UI 상태와 비즈니스 로직을 보관합니다.

**StateFlow**는 "항상 현재 값을 가진 Flow"입니다. UI가 구독하면 값이 바뀔 때마다 자동으로 recomposition.

[ChatViewModel](app/src/main/java/com/aromit/geminivoicechat/ui/chat/ChatViewModel.kt)의 패턴:

```kotlin
private val _state = MutableStateFlow(ChatState())   // ViewModel만 쓰기 가능
val state: StateFlow<ChatState> = _state.asStateFlow() // UI는 읽기만

fun onSendClicked() {
    _state.update { it.copy(inputText = "") }        // 상태 변경 = copy로 새 인스턴스
}
```

[ChatScreen](app/src/main/java/com/aromit/geminivoicechat/ui/chat/ChatScreen.kt)의 구독:
```kotlin
val state by viewModel.state.collectAsStateWithLifecycle()
// 이제 state.messages, state.inputText 등을 그냥 읽으면 자동으로 recomposition
```

**핵심 원칙(단방향)**:
1. UI는 사용자 액션을 ViewModel에 알린다 (`viewModel.onSendClicked()`).
2. ViewModel은 상태를 변경한다 (`_state.update { ... }`).
3. 상태 변경이 UI로 흘러나간다 (Compose가 자동 재렌더링).

이 흐름은 **절대 역방향으로 흐르지 않아야** 합니다. 이게 UI 버그를 압도적으로 줄여줍니다.

### 5.5 수동 DI — `DefaultAppContainer` 패턴

이 프로젝트는 Hilt 같은 DI 프레임워크 없이 **수동 DI**로 시작합니다. 구조가 단순해서 처음 배우기 좋습니다.

[GeminiVoiceChatApp](app/src/main/java/com/aromit/geminivoicechat/GeminiVoiceChatApp.kt)이 앱 생애주기 동안 유일한 컨테이너를 보유:

```kotlin
class GeminiVoiceChatApp : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        container = DefaultAppContainer(applicationContext)
    }
}
```

[MainActivity](app/src/main/java/com/aromit/geminivoicechat/MainActivity.kt)가 컨테이너에서 의존성을 꺼내 ViewModelFactory로 전달:

```kotlin
val app = application as GeminiVoiceChatApp
val viewModelFactory = ChatViewModel.Factory(
    aiRepository = app.container.aiRepository,
    voiceController = app.container.voiceController
)
```

**왜 이렇게?** ViewModel은 자기가 사용할 의존성을 스스로 만들지 않아야(=직접 `new MockAiRepository()` 하지 않아야) DIP가 지켜집니다. Factory가 외부에서 주입해주죠.

### 5.6 Permission API & Composable 라이프사이클

런타임 권한(마이크) 요청은 [`rememberLauncherForActivityResult`](app/src/main/java/com/aromit/geminivoicechat/ui/chat/ChatScreen.kt) 로 Compose 안에서 처리합니다:

```kotlin
val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) viewModel.startVoiceMode()
    else Toast.makeText(context, "마이크 권한이 필요합니다.", ...).show()
}
```

권한이 이미 있는지는 `ContextCompat.checkSelfPermission(...)`로 동기 확인 후, 없으면 `permissionLauncher.launch(...)`.

---

## 6. 동작 흐름 깊이 보기

### 6.1 텍스트 채팅 — 메시지 전송부터 스트리밍까지

```
[사용자]
   │ TextField에 "안녕" 입력 후 ▶ 전송 버튼 탭
   ▼
[ChatScreen.kt]
   │ onSend 람다 실행
   │   ├─ viewModel.onSendClicked()
   │   ├─ keyboardController.hide()        ← 키보드 닫기
   │   └─ focusManager.clearFocus()
   ▼
[ChatViewModel.onSendClicked]
   │ ① USER 메시지 + 빈 AI placeholder(isStreaming=true)를 messages에 동시 추가
   │ ② inputText 비우기
   │ ③ submitUserPrompt("안녕", speakResult=false) 호출
   ▼
[ChatViewModel.submitUserPrompt]
   │ viewModelScope.launch { ... }
   │   └─ streamAiResponse("안녕", aiPlaceholder.id)
   ▼
[ChatViewModel.streamAiResponse]
   │ aiRepository.sendMessage("안녕").collect { chunk ->
   │     appendChunkToMessage(id, chunk)   ← 매 chunk마다 메시지 텍스트 누적
   │ }
   │ finalizeMessage(id)                   ← isStreaming = false
   ▼
[MockAiRepository.sendMessage]
   │ flow {
   │   delay(800)                          ← 네트워크 시뮬레이션
   │   for (word in canned.split(" ")) {
   │     emit(" $word")
   │     delay(80)                         ← 타이핑 시뮬레이션
   │   }
   │ }.flowOn(Dispatchers.IO)
   ▼
[상태 변경 → UI 자동 갱신]
   │ _state.update { ... } 호출 시마다
   │ ChatScreen의 collectAsStateWithLifecycle이 새 상태 받아 recomposition
   ▼
[ChatScreen.MessageList]
   │ LazyColumn이 messages를 다시 렌더
   │ AutoScrollEffect가 마지막 USER 메시지 id 변경을 감지 → 자동 스크롤
```

### 6.2 음성 받아쓰기 — STT → 자동 전송 (v1.2.0~)

```
[사용자] 마이크 버튼 탭
   ▼
[ChatScreen.onMicClicked]
   │ ContextCompat.checkSelfPermission(RECORD_AUDIO)
   │   ├─ 권한 있음 → viewModel.startVoiceMode()
   │   └─ 권한 없음 → permissionLauncher.launch(...)
   ▼
[ChatViewModel.startVoiceMode]
   │ state.voice = VoiceState(isActive=true, mode=LISTENING)
   │ voiceController.startListening()
   ▼
[InputBar → 파형 모드 전환]
   │ TextFiled 자리가 파형 애니메이션으로 전환
   │ 앱바 우측에 초록 마이크 인디케이터 표시
   │
   ├─ onRmsChanged → Rms emit → amplitude 정규화 → 파형 막대 높이 갱신
   ├─ onPartialResults → Partial emit (내부 상태 추적용)
   ├─ onEndOfSpeech → mode = THINKING
   │
   └─ onResults → Final emit
          │
          ▼
[ChatViewModel.onSpeechRecognized]
   │ state.voice 즉시 초기화 (isActive = false)
   │ InputBar → 키보드 모드 복귀
   │ submitUserPrompt(recognizedText)
   ▼
[AI 응답 스트리밍 → 마크다운 렌더링]
   │ 메시지 헤더 스파클 아이콘 회전(isStreaming=true)
   │ 스트리밍 완료 → 아이콘 정지, TTS 스피커 버튼 활성화
   ▼
[사용자가 스피커 아이콘 탭 시 → TTS 재생 / 재탭 → 정지]
```

**[■ 정지 버튼]** 음성 입력 중 취소 시 `endVoiceMode()` → 전송 없이 키보드 모드 복귀.

### 6.3 스마트 자동 스크롤 ([ChatScreen.kt:AutoScrollEffect](app/src/main/java/com/aromit/geminivoicechat/ui/chat/ChatScreen.kt))

두 개의 `LaunchedEffect`가 협력합니다:

```kotlin
// (1) 사용자가 메시지를 보내면 무조건 최하단으로
LaunchedEffect(lastUserMessageId) {
    listState.animateScrollToItem(messageCount - 1)
}

// (2) AI가 스트리밍 중일 때는 "사용자가 하단을 보고 있는 경우에만" 따라감
LaunchedEffect(messageCount, lastMessageText) {
    if (isUserAtBottom) listState.animateScrollToItem(messageCount - 1)
}
```

**왜 이렇게?**
- 사용자가 자기가 보낸 메시지를 못 보는 건 답답함 → (1) 강제 스크롤.
- 사용자가 위로 올려서 옛날 대화 읽는 중에 AI 응답이 화면을 끌어내리면 짜증남 → (2) 조건부.

### 6.4 첫 시도 ERROR_SERVER_DISCONNECTED 자동 복구

갤럭시(특히 S24)에서 음성 인식 첫 호출이 즉시 11번 에러로 실패하는 경우가 잦습니다. `AndroidVoiceController.onError`가 이를 처리:

```kotlin
override fun onError(error: Int) {
    if (!retryAttempted && isTransientError(error)) {
        retryAttempted = true
        mainHandler.post { startListeningInternal() }   // 새 recognizer로 1회 재시도
        return                                           // ← 토스트 없이 조용히 복구
    }
    _events.tryEmit(VoiceEvent.Error(msg))              // 2번째 실패 시에만 UI에 전파
}
```

---

## 7. 확장하기 — Mock에서 실제 서버로

> v1.1.0부터 `RemoteAiRepository`가 구현되어 있어 **새 파일을 만들 필요가 없습니다.** `local.properties`에서 플래그만 켜면 즉시 서버 모드로 전환됩니다.

### 7.1 가장 짧은 경로 (서버는 별도 저장소에서 실행 중이라 가정)

**Step 1.** `local.properties`에 3줄 추가:

```properties
USE_REMOTE=true
SERVER_URL=ws://10.0.2.2:8000/ws        # 에뮬레이터용. 실기기는 PC LAN IP
WS_API_KEY=<서버 .env의 WS_API_KEY와 동일 값>
```

**Step 2.** `./gradlew installDebug` — `BuildConfig.USE_REMOTE`가 true가 되어 `DefaultAppContainer`가 `RemoteAiRepository`를 바인딩합니다.

**Step 3.** 끝. `ChatViewModel`, `ChatScreen`, `ChatState`는 손대지 않습니다.

### 7.2 작동 흐름 (v1.1.0)

```
ChatViewModel ──sendMessage("안녕")──▶ AiRepository (interface)
                                              │
                                  USE_REMOTE? ─┴─ true ─▶ RemoteAiRepository
                                              │                │
                                              └─ false ─▶ MockAiRepository
                                                               │
                       Flow<String> ◀──── word-by-word emit ────┘
```

`RemoteAiRepository`는 다음을 알아서 처리합니다:
- WebSocket 세션 유지 (서버 멀티턴 히스토리 보존)
- `text_chunk` 수신 → `Flow.emit` 라우팅, `text_done` 수신 → Flow 종료
- OkHttp `pingInterval(20s)` 프로토콜 PING + `ProcessLifecycleOwner.ON_START` 기반 백그라운드 자동 재연결
- `error` 프레임 수신 시 Flow에 예외 전파

상세 통신 규격: [`app/docs/03_server_api.md`](app/docs/03_server_api.md).
서버 통합 가이드: [`app/docs/04_server_handoff.md`](app/docs/04_server_handoff.md).
백그라운드 안정성 처방: [`app/docs/05_websocket_keepalive_fix.md`](app/docs/05_websocket_keepalive_fix.md).

### 7.3 Mock으로 돌아가기

`local.properties`에서 `USE_REMOTE=false` (또는 해당 줄 삭제) → 리빌드. ViewModel/UI 코드 변경 없음.

**이것이 DIP의 가장 강력한 보상이자, 본 프로젝트 아키텍처의 존재 이유입니다.**

### 7.4 Phase 2 (예정)

- 오디오 청크를 서버로 직접 송신 (`streamAudio` 본체 구현).
- Gemini Live API 모델로 전환 — 서버 측 STT 통합.
- `VoiceController` 인터페이스 분리 (`LocalVoiceController` vs `StreamingVoiceController`).

상세는 [`TODO.md`](TODO.md) 항목 4.

---

## 8. 디버깅과 트러블슈팅

### 8.1 Logcat 활용

Android Studio Logcat에서 다음 태그로 필터:

| 태그 | 무엇이 찍히는가 |
|---|---|
| `VoiceController` | STT/TTS 플랫폼 이벤트 (인식 시작/종료, 에러, TTS 초기화 등) |
| `ChatViewModel` | 상태머신 전환 (LISTENING/THINKING/SPEAKING), 인식 결과 |

CLI에서:
```bash
adb logcat -s VoiceController:* ChatViewModel:*
```

RMS 이벤트(초당 수십 번 발생)는 의도적으로 로깅하지 않습니다.

### 8.2 자주 보는 에러 코드

| 코드 | 이름 | 의미 / 대응 |
|---|---|---|
| 7 | `ERROR_NO_MATCH` | 인식 못함. 다시 말하기. |
| 9 | `ERROR_INSUFFICIENT_PERMISSIONS` | 권한 없음. 설정 확인. |
| 11 | `ERROR_SERVER_DISCONNECTED` | 인식 서비스 연결 끊김. **자동 1회 재시도**됨. |
| 12 | `ERROR_LANGUAGE_NOT_SUPPORTED` | 해당 언어 미지원. 자동으로 네트워크 인식기로 폴백. |
| 13 | `ERROR_LANGUAGE_UNAVAILABLE` | 언어 모델 없음. 시스템 설정에서 한국어 모델 다운로드 필요. |

### 8.3 갤럭시 S24 셋업 가이드 (음성 인식이 안 될 때)

1. **디지털 어시스턴트를 Google로**: 설정 → 앱 → 기본 앱 선택 → 디지털 어시스턴트 앱 → Google
2. **한국어 온디바이스 모델 설치**: 설정 → 일반 → 언어 및 키보드 → 온디바이스 음성 인식 → 한국어 추가
3. **Google 앱 활성화 확인**: 설정 → 앱 → Google → 활성 상태 + 최신 버전
4. 그래도 안 되면 → 본 프로젝트의 자동 폴백 로직이 네트워크 인식기로 전환 시도 (logcat에서 `Falling back to network recognizer` 확인)

### 8.4 빌드가 안 될 때

| 증상 | 원인 / 해결 |
|---|---|
| `JAVA_HOME is not set` | Windows 셸에서 `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` |
| `Compose compiler version mismatch` | Kotlin 버전과 Compose Compiler 버전 호환 확인. `libs.versions.toml`에서 `kotlin` 버전 일치 여부 점검. |
| 첫 빌드가 매우 느림 | 정상. 의존성 다운로드와 Gradle 데몬 워밍업 때문. 두 번째부터 빠름. |

### 8.5 서버 모드(USE_REMOTE=true) 트러블슈팅

logcat 필터: `adb logcat -s RemoteAiRepository:*`

| 증상 | 원인 / 해결 |
|---|---|
| `WS connect failed` 또는 HTTP 403 | `WS_API_KEY` 불일치. 서버 `.env`의 값과 `local.properties` 값이 정확히 같은지 확인. |
| `WS failure: Software caused connection abort` (백그라운드 진입 시) | **정상 동작.** OS가 백그라운드 소켓을 abort하는 것으로, `ON_START` 옵저버가 foreground 복귀 시 자동 재연결합니다. `app/docs/05_websocket_keepalive_fix.md` 참조. |
| `inbound error code=INTERNAL_ERROR msg=Unknown type: None` | `Json { encodeDefaults = true }`가 빠진 경우 발생 — v1.1.0에서 수정됨. 같은 증상 재발 시 RemoteAiRepository의 `json` 설정 점검. |
| 메시지를 보내도 응답 없음 | logcat에서 `send text_input accepted=...` 와 그 다음 `inbound status` 가 보이는지 확인. accepted=false면 연결 문제, status 후 멈춤이면 서버측 Gemini 호출 문제. |
| 멀티턴 히스토리가 안 됨 (앱 백그라운드 후 이름 회상 실패) | 의도된 동작. 서버는 WebSocket 세션 단위로 히스토리를 메모리에 유지하고, OS가 소켓을 죽이면 새 세션으로 시작합니다 (`04_server_handoff.md §5.2 항목 4`). |

---

## 9. 더 읽을거리

### 프로젝트 내부 문서
- **[app/docs/01_requirements.md](app/docs/01_requirements.md)** — 제품 요구사항 (PRD). 어떤 기능이 왜 필요한지.
- **[app/docs/02_architecture.md](app/docs/02_architecture.md)** — 아키텍처 원칙과 패키지 규약.
- **[app/docs/03_server_api.md](app/docs/03_server_api.md)** — 서버 WebSocket 통신 규격 (SSoT).
- **[app/docs/04_server_handoff.md](app/docs/04_server_handoff.md)** — Phase 1 서버 통합 가이드 (엔드포인트, API 키 동기화, 스모크 테스트).
- **[app/docs/05_websocket_keepalive_fix.md](app/docs/05_websocket_keepalive_fix.md)** — Keepalive 및 백그라운드 재연결 처방 (Samsung 디바이스 실측 결과 포함).
- **[app/docs/07_ui_markdown_voice_inline.md](app/docs/07_ui_markdown_voice_inline.md)** — v1.2.0 UI 변경 사양 (마크다운 렌더링, TTS 토글, 인라인 음성 입력 흐름).
- **[CHANGELOG.md](CHANGELOG.md)** — 버전별 변경 이력.
- **[TODO.md](TODO.md)** — 미구현 항목 (배지인, 문장단위 TTS, Phase 2 등).
- **[CLAUDE.md](CLAUDE.md)** — AI 코딩 어시스턴트용 컨텍스트.

### 외부 학습 리소스
- [Android Developers — Modern App Architecture](https://developer.android.com/topic/architecture)
- [Jetpack Compose 공식 가이드](https://developer.android.com/jetpack/compose)
- [Kotlin Coroutines 가이드](https://kotlinlang.org/docs/coroutines-guide.html)
- [SpeechRecognizer 레퍼런스](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [TextToSpeech 레퍼런스](https://developer.android.com/reference/android/speech/tts/TextToSpeech)

### 학습 순서 추천 (이 코드베이스를 처음 읽는 분께)

1. `domain/model/ChatMessage.kt` — 가장 단순한 데이터 클래스부터.
2. `domain/repository/AiRepository.kt` — 인터페이스 한 장.
3. `data/repository/MockAiRepository.kt` — Coroutines/Flow 실전 예제.
4. `ui/chat/ChatState.kt` → `ChatViewModel.kt` → `ChatScreen.kt` 순으로 — MVVM + StateFlow + Compose 흐름.
5. `di/AppContainer.kt` + `GeminiVoiceChatApp.kt` + `MainActivity.kt` — DI 와이어링.
6. `ui/voice/` 전체 — 플랫폼 추상화 + 상태머신 패턴 (조금 어려움, 마지막에).

---

**즐거운 코딩 되세요!** 🎙️✨

질문/버그/개선 제안은 이슈로 남겨주세요.
