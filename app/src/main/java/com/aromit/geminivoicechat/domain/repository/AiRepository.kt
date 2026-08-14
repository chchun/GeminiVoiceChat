package com.aromit.geminivoicechat.domain.repository

import com.aromit.geminivoicechat.domain.model.A2UIActionResult
import com.aromit.geminivoicechat.domain.model.A2UIFile
import com.aromit.geminivoicechat.domain.model.AiResponse
import com.aromit.geminivoicechat.domain.model.AiStreamEvent
import kotlinx.coroutines.flow.Flow

interface AiRepository {
    suspend fun sendMessage(text: String): Flow<AiStreamEvent>

    /** A2UI 버튼 액션을 서버로 전송한다 (spec 002 — `/a2ui/action`, 항상 multipart). */
    suspend fun sendA2UIAction(
        eventName: String,
        surfaceId: String,
        context: Map<String, Any?>,
        files: List<A2UIFile> = emptyList(),
    ): A2UIActionResult

    fun streamAudio(audioFlow: Flow<ByteArray>): Flow<AiResponse>
}
