package com.aromit.geminivoicechat.domain.model

/** A2UI 액션에 첨부하는 파일 (multipart `files` 파트 1개). */
data class A2UIFile(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is A2UIFile && other.name == name && other.mimeType == mimeType &&
            other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = 31 * (31 * name.hashCode() + mimeType.hashCode()) +
        bytes.contentHashCode()
}
