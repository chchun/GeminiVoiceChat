package com.aromit.geminivoicechat.domain.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val senderType: SenderType,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)
