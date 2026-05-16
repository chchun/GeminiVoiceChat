package com.aromit.geminivoicechat.ui.voice

import kotlinx.coroutines.flow.Flow

/**
 * 마이크/스피커 플랫폼 추상화. AiRepository와 동일한 DIP 원칙을 따른다.
 * 향후 Gemini Live 서버 기반 streamAudio로 교체할 때도 이 인터페이스만 다른 구현으로 바인딩하면 된다.
 */
interface VoiceController {
    val events: Flow<VoiceEvent>
    fun startListening()
    fun stopListening()
    fun speak(text: String)
    fun stopSpeaking()
    fun release()
}
