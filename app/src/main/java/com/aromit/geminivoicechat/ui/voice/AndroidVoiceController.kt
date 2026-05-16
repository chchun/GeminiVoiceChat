package com.aromit.geminivoicechat.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Locale

class AndroidVoiceController(
    context: Context
) : VoiceController {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<VoiceEvent> = _events.asSharedFlow()

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false
    private var pendingSpeech: String? = null

    // 단일 startListening() 호출에 대한 1회 재시도 플래그
    private var retryAttempted: Boolean = false

    // on-device 인식기가 언어 미지원으로 실패한 적이 있으면 이후 세션에서는 바로 네트워크 인식기 사용
    private var onDeviceUnavailable: Boolean = false

    init {
        Log.d(TAG, "init: STT available=${SpeechRecognizer.isRecognitionAvailable(appContext)}")
        initTts()
    }

    private fun initTts() {
        Log.d(TAG, "initTts: requesting TTS engine")
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    val koResult = engine.setLanguage(Locale.KOREAN)
                    Log.d(TAG, "TTS init success. setLanguage(KOREAN)=${ttsLangResult(koResult)}")
                    if (koResult == TextToSpeech.LANG_MISSING_DATA ||
                        koResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        val fallback = engine.setLanguage(Locale.getDefault())
                        Log.w(TAG, "Korean TTS unavailable, fallback to default=${ttsLangResult(fallback)}")
                    }
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            Log.d(TAG, "TTS onStart utteranceId=$utteranceId")
                            _events.tryEmit(VoiceEvent.SpeakingStarted)
                        }
                        override fun onDone(utteranceId: String?) {
                            Log.d(TAG, "TTS onDone utteranceId=$utteranceId")
                            _events.tryEmit(VoiceEvent.SpeakingFinished)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            Log.e(TAG, "TTS onError utteranceId=$utteranceId (legacy)")
                            _events.tryEmit(VoiceEvent.Error("TTS 오류"))
                        }
                        override fun onError(utteranceId: String?, errorCode: Int) {
                            Log.e(TAG, "TTS onError utteranceId=$utteranceId code=$errorCode")
                            _events.tryEmit(VoiceEvent.Error("TTS 오류 ($errorCode)"))
                        }
                    })
                }
                ttsReady = true
                pendingSpeech?.let {
                    Log.d(TAG, "Flushing pending speech (${it.length} chars)")
                    pendingSpeech = null
                    speak(it)
                }
            } else {
                Log.e(TAG, "TTS init failed, status=$status")
                _events.tryEmit(VoiceEvent.Error("TTS 초기화 실패"))
            }
        }
    }

    override fun startListening() {
        mainHandler.post {
            retryAttempted = false
            startListeningInternal()
        }
    }

    private fun startListeningInternal() {
        val available = SpeechRecognizer.isRecognitionAvailable(appContext)
        Log.d(TAG, "startListening: STT available=$available, onDeviceUnavailable=$onDeviceUnavailable")
        if (!available) {
            _events.tryEmit(VoiceEvent.Error("음성 인식을 사용할 수 없습니다"))
            return
        }
        recognizer?.destroy()
        recognizer = createRecognizerSafely()?.apply {
            setRecognitionListener(buildListener())
        }
        if (recognizer == null) {
            _events.tryEmit(VoiceEvent.Error("음성 인식기를 생성할 수 없습니다"))
            return
        }
        recognizer?.startListening(buildRecognitionIntent())
    }

    private fun createRecognizerSafely(): SpeechRecognizer? {
        // on-device 인식기는 사용자가 설정한 기본 인식 서비스(예: 삼성 Bixby)를 우회하므로
        // 시스템 기본이 SpeechRecognizer 표준 호출을 거부하는 단말(특히 갤럭시)에서 효과적이다.
        if (!onDeviceUnavailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                Log.d(TAG, "Created on-device SpeechRecognizer")
                return r
            }.onFailure {
                Log.w(TAG, "On-device recognizer unavailable, falling back: ${it.message}")
                onDeviceUnavailable = true
            }
        }
        Log.d(TAG, "Created default network SpeechRecognizer")
        return SpeechRecognizer.createSpeechRecognizer(appContext)
    }

    private fun buildRecognitionIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // "ko-KR" 형태로 국가 코드까지 명시 (단순 "ko" 보다 안정적)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREA.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }
    }

    override fun stopListening() {
        mainHandler.post {
            Log.d(TAG, "stopListening")
            recognizer?.stopListening()
            recognizer?.destroy()
            recognizer = null
        }
    }

    override fun speak(text: String) {
        if (text.isBlank()) return
        if (!ttsReady) {
            Log.d(TAG, "speak: TTS not ready, queuing ${text.length} chars")
            pendingSpeech = text
            return
        }
        Log.d(TAG, "speak: ${text.length} chars")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    override fun stopSpeaking() {
        Log.d(TAG, "stopSpeaking")
        tts?.stop()
    }

    override fun release() {
        Log.d(TAG, "release")
        mainHandler.post {
            recognizer?.destroy()
            recognizer = null
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private fun buildListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "STT onReadyForSpeech")
            _events.tryEmit(VoiceEvent.ReadyForSpeech)
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "STT onBeginningOfSpeech")
            _events.tryEmit(VoiceEvent.BeginningOfSpeech)
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Intentionally not logged (fires multiple times per second).
            _events.tryEmit(VoiceEvent.Rms(rmsdB))
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "STT onEndOfSpeech")
            _events.tryEmit(VoiceEvent.EndOfSpeech)
        }

        override fun onError(error: Int) {
            val name = sttErrorName(error)
            val msg = sttErrorMessage(error)
            Log.e(TAG, "STT onError code=$error ($name) -> \"$msg\"")

            // on-device 인식기가 언어 미지원/불가를 내면 네트워크 인식기로 폴백 후 재시도
            if (!onDeviceUnavailable && (error == 12 || error == 13)) {
                Log.w(TAG, "On-device language unavailable, falling back to network recognizer")
                onDeviceUnavailable = true
                mainHandler.post { startListeningInternal() }
                return
            }

            // 일시적 에러는 한 세션당 1회 자동 재시도
            if (!retryAttempted && isTransientError(error)) {
                Log.w(TAG, "Transient error ($name), retrying once")
                retryAttempted = true
                mainHandler.post { startListeningInternal() }
                return
            }

            _events.tryEmit(VoiceEvent.Error(msg))
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            Log.d(TAG, "STT onResults: \"$text\"")
            _events.tryEmit(VoiceEvent.Final(text))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) {
                Log.v(TAG, "STT onPartialResults: \"$text\"")
                _events.tryEmit(VoiceEvent.Partial(text))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.v(TAG, "STT onEvent type=$eventType")
        }
    }

    private fun isTransientError(code: Int): Boolean = when (code) {
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> true
        11 -> true // ERROR_SERVER_DISCONNECTED (API 31+)
        else -> false
    }

    private fun sttErrorName(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
        SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
        // 아래 코드는 API 31+ (Android 12+) 추가. minSdk 24에서 참조해도 컴파일 시 상수만 인라인됨.
        10 -> "ERROR_TOO_MANY_REQUESTS"
        11 -> "ERROR_SERVER_DISCONNECTED"
        12 -> "ERROR_LANGUAGE_NOT_SUPPORTED"
        13 -> "ERROR_LANGUAGE_UNAVAILABLE"
        14 -> "ERROR_CANNOT_CHECK_SUPPORT"
        15 -> "ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS"
        else -> "UNKNOWN"
    }

    private fun sttErrorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "오디오 오류"
        SpeechRecognizer.ERROR_CLIENT -> "클라이언트 오류"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요합니다"
        SpeechRecognizer.ERROR_NETWORK -> "네트워크 오류"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 시간 초과"
        SpeechRecognizer.ERROR_NO_MATCH -> "음성을 인식하지 못했습니다"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "인식기 사용 중"
        SpeechRecognizer.ERROR_SERVER -> "서버 오류"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "음성 입력 시간이 초과되었습니다"
        10 -> "요청이 너무 많습니다"
        11 -> "음성 인식 서버 연결이 끊겼습니다"
        12 -> "지원되지 않는 언어입니다"
        13 -> "언어 모델을 사용할 수 없습니다"
        14 -> "언어 지원 여부를 확인할 수 없습니다"
        15 -> "언어 모델 다운로드 상태를 확인할 수 없습니다"
        else -> "음성 인식 오류 ($code)"
    }

    private fun ttsLangResult(code: Int): String = when (code) {
        TextToSpeech.LANG_AVAILABLE -> "AVAILABLE"
        TextToSpeech.LANG_COUNTRY_AVAILABLE -> "COUNTRY_AVAILABLE"
        TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "COUNTRY_VAR_AVAILABLE"
        TextToSpeech.LANG_MISSING_DATA -> "MISSING_DATA"
        TextToSpeech.LANG_NOT_SUPPORTED -> "NOT_SUPPORTED"
        else -> "UNKNOWN($code)"
    }

    companion object {
        private const val TAG = "VoiceController"
        private const val UTTERANCE_ID = "voice-utterance"
    }
}
