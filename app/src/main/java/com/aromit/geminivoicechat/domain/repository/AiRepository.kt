package com.aromit.geminivoicechat.domain.repository

import com.aromit.geminivoicechat.domain.model.AiResponse
import kotlinx.coroutines.flow.Flow

interface AiRepository {
    suspend fun sendMessage(text: String): Flow<String>

    fun streamAudio(audioFlow: Flow<ByteArray>): Flow<AiResponse>
}
