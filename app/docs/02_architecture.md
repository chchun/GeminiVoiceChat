# 기술 아키텍처 및 구현 가이드 - v1.2

## 1. 핵심 아키텍처 원칙: 의존성 역전 (DIP)
- **UI 및 ViewModel은 절대 구체적인 데이터 소스(Mock 클래스 등)를 직접 참조해서는 안 된다.**
- 모든 데이터와 스트리밍 제어는 오직 `domain/repository/`에 정의된 **인터페이스(Interface)**를 통해서만 이루어져야 한다.
- 이 제약 조건은 향후 클라이언트 UI 코드를 단 한 줄도 수정하지 않고 `Mock`에서 `실제 서버(WebSocket)`로 통신 레이어를 완전히 교체(Swap)하기 위함이다.

---

## 2. 레이어별 설계 지침

### 2.1 Domain Layer (인터페이스 및 비즈니스 규격)
- `domain/repository/AiRepository.kt` 인터페이스를 먼저 생성한다.
    - 텍스트 메시지 송수신: `suspend fun sendMessage(text: String): Flow<String>` (스트리밍 답변 반영을 위해 Flow 반환 권장)
    - 음성 스트리밍 송수신: `fun streamAudio(audioFlow: Flow<ByteArray>): Flow<AiResponse>`

### 2.2 Data Layer (구현체 및 시뮬레이션)
- **현재 단계 (`MockAiRepository.kt`):**
    - `AiRepository` 인터페이스를 상속받아 가짜 비즈니스 로직을 구현한다.
    - 내부적으로 `delay()`를 활용하여 네트워크 딜레이를 정밀하게 묘사하고, 타이핑 효과를 위해 단어/글자 단위로 `emit()`을 발생시키는 코루틴 Flow를 구현한다.
- **Phase 1 (`RemoteAiRepository.kt`) — 구현 완료 (2026-05-17):**
    - OkHttp WebSocket 클라이언트로 `text_input` 송신 / `text_chunk` 수신 / `text_done`에서 Flow 종료. 통신 규격은 `03_server_api.md` 참조.
    - 세션 단위 연결 유지로 서버측 멀티턴 히스토리 보존. `connectionMutex`로 직렬화.
    - **Keepalive**: OkHttp `pingInterval(20s)` 프로토콜 레벨 PING. 앱 레벨 JSON ping 미사용. 상세 `05_websocket_keepalive_fix.md`.
    - **백그라운드 재연결**: `ProcessLifecycleOwner.ON_START` 옵저버 + `@Volatile isConnectionAlive` 플래그. OS가 백그라운드에서 소켓을 abort해도 foreground 복귀 시 자동 재연결. 단, 멀티턴 히스토리는 서버측 메모리이므로 새 세션은 초기화됨 (spec 동작).
    - **JSON 직렬화**: `Json { encodeDefaults = true }`. default value 필드(`TextInput.type = "text_input"`)가 누락되지 않도록 필수.
- **Phase 2 (예정):** `streamAudio` 본체 구현, Gemini Live API 모델 전환, 서버 측 PCM 청크 처리. 현재 `UnsupportedOperationException` 던지는 스텁만 존재.

### 2.3 UI & Presentation Layer (Jetpack Compose & ViewModel)
- `ChatViewModel`은 생성자 파라미터로 오직 `AiRepository` 인터페이스만 주입(Inject)받는다.
- ViewModel 내부에서 `MockAiRepository`라는 단어를 직접 언급하거나 인스턴스를 직접 생성(`val repo = MockAiRepository()`)하는 행위를 절대 금지한다.

---

## 3. 의존성 주입 (DI) 및 프로젝트 구조 강제

### 3.1 Hilt를 사용할 경우의 규칙
- `di/AppModule.kt`를 생성하여 `AiRepository` 인터페이스에 어떤 구현체를 바인딩할지 결정한다.
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAiRepository(
        mockRepository: MockAiRepository // 나중에 RemoteAiRepository로 여기만 바꿈!
    ): AiRepository
}
```

### 3.2 수동 DI를 사용할 경우의 규칙 (Hilt 미도입 시)
앱의 진입점(Application class 등)에서 Container 패턴을 사용해 AiRepository 인스턴스를 하나만 생성하고, 이를 ViewModelFactory를 통해 뷰모델에 넘겨준다.

### 3.3 추천 패키지 디렉토리 구조
```Plaintext
com.example.geminivoicechat/
├── di/                     # 의존성 주입 모듈 (수동 container 또는 Hilt)
├── domain/
│   ├── model/              # ChatMessage 등 순수 데이터 모델
│   └── repository/         # AiRepository 인터페이스
├── data/
│   └── repository/         # MockAiRepository 구현체 (향후 Remote 추가 위치)
└── ui/
├── chat/               # ChatScreen, ChatViewModel, ChatState
└── theme/              # Color, Type 등 Compose 테마 설정을 위한 폴더
```






