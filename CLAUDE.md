# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Clean build artifacts
./gradlew clean
```

On Windows the Gradle JDK is the one bundled with Android Studio. If a fresh shell can't find `java`, set `JAVA_HOME` to `C:\Program Files\Android\Android Studio\jbr` before running `gradlew.bat`.

**SDK config:** compileSdk 36, minSdk 24, targetSdk 36, Kotlin 2.2.10, Compose BOM 2026.02.01, Java 11. Application id: `com.aromit.geminivoicechat`.

## Architecture

Gemini Live-style real-time conversational AI Android app. The architecture enforces Dependency Inversion so the data and platform layers can be swapped without touching UI code.

### Layers

```
com.aromit.geminivoicechat/
├── domain/
│   ├── model/          ChatMessage, SenderType, AiResponse
│   └── repository/     AiRepository (interface)
├── data/
│   └── repository/     MockAiRepository (impl)
├── di/                 AppContainer (manual DI)
├── ui/
│   ├── chat/           ChatScreen, ChatViewModel, ChatState
│   ├── voice/          VoiceOverlay + VoiceController (interface) + AndroidVoiceController (impl)
│   └── theme/          Material3 theme
├── GeminiVoiceChatApp  Application — owns AppContainer
└── MainActivity        Hosts ChatScreen, builds ChatViewModel.Factory from AppContainer
```

### DIP rules (do not break)

`ChatViewModel`, `ChatScreen`, and all other UI code reference **only** these abstractions:
- `AiRepository` (data layer) — bound to `MockAiRepository` today, will swap to `RemoteAiRepository` for Phase 2
- `VoiceController` (platform layer) — bound to `AndroidVoiceController` today, will swap to a server-driven audio controller when Gemini Live audio streaming lands

`DefaultAppContainer` is the single place that knows which concrete implementations to wire. Never construct repositories or controllers from a Composable or ViewModel directly, and never import classes from `data/` or `ui/voice/AndroidVoiceController` outside `di/`.

### Text streaming flow

1. `ChatViewModel.onSendClicked()` adds a USER `ChatMessage` and an empty AI placeholder with `isStreaming=true` in a single state update.
2. `aiRepository.sendMessage(prompt)` returns `Flow<String>`; each emitted chunk is appended to the placeholder message text.
3. When the flow completes, the placeholder is finalized (`isStreaming=false`).
4. `MockAiRepository` simulates this with an 800 ms initial delay then word-by-word emits at ~80 ms intervals on `Dispatchers.IO`.

### Voice flow

Voice mode is **just an alternate input method** for the same `sendMessage` path. STT/TTS are local (Android built-in) — they are NOT part of `AiRepository`.

1. Mic button → permission check (`RECORD_AUDIO`) → `viewModel.startVoiceMode()` → `VoiceController.startListening()`.
2. State machine, driven by `VoiceEvent`s emitted from a `SharedFlow`:
   - `LISTENING` (RMS amplitude drives orb scale) → user speaks
   - `EndOfSpeech` → `THINKING` (orb gently pulses while AI streams)
   - On AI stream completion → `VoiceController.speak(fullText)` → `SPEAKING`
   - `SpeakingFinished` → auto-resume `LISTENING`
3. X button → `endVoiceMode()` stops both STT and TTS, conversation history preserved.

### Voice quirks on Samsung devices

`AndroidVoiceController` is hardened against the Galaxy "first-call ERROR_SERVER_DISCONNECTED (code 11)" pattern:
- Prefers `SpeechRecognizer.createOnDeviceSpeechRecognizer()` (API 31+) to bypass the device's default RecognitionService (Bixby on Samsung) which often rejects standard SpeechRecognizer calls.
- Sets `EXTRA_PREFER_OFFLINE = true` and `EXTRA_LANGUAGE = "ko-KR"`.
- On transient errors (2/4/8/11) auto-retries **once per session** without surfacing the error to UI.
- On on-device language errors (12/13) falls back to network recognizer and remembers the fallback for the session.

When debugging voice issues, filter logcat by tag `VoiceController` (platform events) and `ChatViewModel` (state transitions). RMS events are intentionally not logged to avoid spam.

### Smart auto-scroll

`AutoScrollEffect` in `ChatScreen.kt` has two `LaunchedEffect`s:
- Keyed on `lastUserMessageId` → **always** scrolls to bottom when the user sends (their own message must be visible regardless of prior scroll position).
- Keyed on `messageCount` + `lastMessageText` → scrolls only when `isUserAtBottom`, so AI streaming doesn't fight a user who scrolled up to read history.

On send, the keyboard is dismissed via `LocalSoftwareKeyboardController.hide()` + `LocalFocusManager.clearFocus()`. The IME `onSend` action shares the same callback as the send button.

### Specs

Authoritative specs live in `app/docs/`:
- `01_requirements.md` — PRD (data model, UX states, STT/TTS pipeline)
- `02_architecture.md` — DIP enforcement, layer responsibilities, package layout

Pending work is tracked in `TODO.md` at the project root.
