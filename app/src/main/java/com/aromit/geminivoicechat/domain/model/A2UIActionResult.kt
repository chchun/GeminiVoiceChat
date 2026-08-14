package com.aromit.geminivoicechat.domain.model

/** A2UI 액션(`/a2ui/action`) 왕복 결과. [markdown] 은 사용자에게 보여줄 응답 본문. */
data class A2UIActionResult(
    val ok: Boolean,
    val markdown: String,
)
