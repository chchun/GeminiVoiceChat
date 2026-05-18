package com.aromit.geminivoicechat.ui.chat

import com.aromit.geminivoicechat.domain.model.ChatMessage
import com.aromit.geminivoicechat.ui.voice.VoiceState

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isAiResponding: Boolean = false,
    val voice: VoiceState = VoiceState(),
    val playingMessageId: String? = null
) {
    val isSendEnabled: Boolean
        get() = inputText.isNotBlank() && !isAiResponding

    val isVoiceInputActive: Boolean
        get() = voice.isActive
}
