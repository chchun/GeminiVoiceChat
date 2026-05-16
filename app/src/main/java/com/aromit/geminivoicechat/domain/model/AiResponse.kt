package com.aromit.geminivoicechat.domain.model

sealed interface AiResponse {
    data class Transcript(val text: String, val isFinal: Boolean) : AiResponse
    data class AudioChunk(val bytes: ByteArray) : AiResponse {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AudioChunk) return false
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = bytes.contentHashCode()
    }
    data object EndOfTurn : AiResponse
}
