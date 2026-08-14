package com.aromit.geminivoicechat.domain.model

sealed class AiStreamEvent {
    data class TextChunk(val text: String) : AiStreamEvent()

    /** A2UI v0.9 엔벨로프 1건 (createSurface/updateComponents/updateDataModel) — raw JSON. */
    data class A2UIEnvelope(val envelopeJson: String) : AiStreamEvent()
}
