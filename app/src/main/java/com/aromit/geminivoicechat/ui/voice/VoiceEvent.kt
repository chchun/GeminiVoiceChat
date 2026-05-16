package com.aromit.geminivoicechat.ui.voice

sealed interface VoiceEvent {
    data class Partial(val text: String) : VoiceEvent
    data class Final(val text: String) : VoiceEvent
    data class Rms(val value: Float) : VoiceEvent
    data class Error(val message: String) : VoiceEvent
    data object ReadyForSpeech : VoiceEvent
    data object BeginningOfSpeech : VoiceEvent
    data object EndOfSpeech : VoiceEvent
    data object SpeakingStarted : VoiceEvent
    data object SpeakingFinished : VoiceEvent
}
