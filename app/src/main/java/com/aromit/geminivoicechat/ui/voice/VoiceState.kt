package com.aromit.geminivoicechat.ui.voice

data class VoiceState(
    val isActive: Boolean = false,
    val mode: VoiceMode = VoiceMode.LISTENING,
    val amplitude: Float = 0f,
    val partialText: String = "",
    val errorMessage: String? = null
)
