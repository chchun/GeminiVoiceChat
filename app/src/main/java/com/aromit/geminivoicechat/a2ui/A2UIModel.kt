package com.aromit.geminivoicechat.a2ui

import androidx.compose.ui.graphics.Color

// ── Value binding types ──────────────────────────────────────
sealed class A2UIValue {
    data class Static(val text: String) : A2UIValue()
    data class PathBound(val path: String) : A2UIValue()
    data class FormatStr(val template: String) : A2UIValue()
}

// ── Validation conditions ────────────────────────────────────
sealed class A2UICondition {
    data class PathRef(val path: String) : A2UICondition()
    data class Required(val valuePath: String) : A2UICondition()
    data class Numeric(val valuePath: String, val min: Double? = null, val max: Double? = null) : A2UICondition()
    data class And(val values: List<A2UICondition>) : A2UICondition()
    data class Or(val values: List<A2UICondition>) : A2UICondition()
}

data class A2UICheck(val condition: A2UICondition, val message: String)

// ── Actions ──────────────────────────────────────────────────
data class A2UIContextRef(
    val key: String,
    val path: String? = null,
    val literal: String? = null,
)

data class A2UIAction(
    val eventName: String,
    val contextRefs: List<A2UIContextRef> = emptyList(),
    val extraLiterals: Map<String, String> = emptyMap(),
)

// ── Picker option ────────────────────────────────────────────
data class A2UIOption(val label: String, val value: String)

// ── Component tree ───────────────────────────────────────────
sealed class A2UIComponent {
    abstract val id: String

    data class Card(override val id: String, val child: String) : A2UIComponent()
    data class Column(
        override val id: String,
        val children: List<String>,
        val justify: String = "start",
        val align: String = "start",
    ) : A2UIComponent()
    data class Row(
        override val id: String,
        val children: List<String>,
        val justify: String = "start",
        val align: String = "center",
    ) : A2UIComponent()
    data class TextComp(override val id: String, val text: A2UIValue, val variant: String = "body") : A2UIComponent()
    data class IconComp(override val id: String, val name: String, val size: Int = 20) : A2UIComponent()
    data class Divider(override val id: String) : A2UIComponent()
    data class ButtonComp(
        override val id: String,
        val label: A2UIValue,
        val variant: String = "primary",
        val action: A2UIAction?,
        val checks: List<A2UICheck> = emptyList(),
    ) : A2UIComponent()
    data class TextFieldComp(
        override val id: String,
        val label: String,
        val valuePath: String,
        val variant: String = "shortText",
        val placeholder: String = "",
        val checks: List<A2UICheck> = emptyList(),
        val readonly: Boolean = false,
    ) : A2UIComponent()
    data class CheckBoxComp(override val id: String, val label: String, val valuePath: String) : A2UIComponent()
    data class ChoicePickerComp(
        override val id: String,
        val options: List<A2UIOption>,
        val valuePath: String,
        val variant: String = "mutuallyExclusive",
    ) : A2UIComponent()
    data class SliderComp(
        override val id: String,
        val label: String,
        val valuePath: String,
        val min: Float,
        val max: Float,
        val step: Float,
    ) : A2UIComponent()
    data class SwitchComp(override val id: String, val label: String, val valuePath: String) : A2UIComponent()
    data class TagInputComp(
        override val id: String,
        val label: String,
        val valuePath: String,
        val suggestions: List<String> = emptyList(),
        val placeholder: String = "",
    ) : A2UIComponent()
    data class SelectComp(
        override val id: String,
        val label: String,
        val valuePath: String,
        val options: List<A2UIOption>,
        val placeholder: String = "",
    ) : A2UIComponent()
    data class RiskCardList(override val id: String, val rowsPath: String) : A2UIComponent()

    /** 사진 첨부 (spec 002 T105). 선택 결과는 valuePath 에 List<A2UIPickedFile> 로 저장. */
    data class FileUploadComp(
        override val id: String,
        val label: String,
        val valuePath: String,
        val accept: List<String> = emptyList(),
        val maxFiles: Int = 5,
        val maxSizeMb: Int = 50,
    ) : A2UIComponent()

    /**
     * 서버(saferyn-langgraph)가 v0.9 엔벨로프로 내려주는 커스텀 카탈로그 컴포넌트.
     * kind = 서버의 component 필드 (예: "ActvScoreSummaryCard"),
     * valuePath = dataModel 안의 summary 객체 위치. 렌더링은 A2UIServerCards.kt.
     */
    data class ServerCard(
        override val id: String,
        val kind: String,
        val valuePath: String,
    ) : A2UIComponent()
}

/**
 * FileUpload 로 선택된 사진 1장. bytes 는 선택 직후 UI 계층에서 읽는다 (spec 002 plan D5).
 * 제출 시 ViewModel 이 도메인 [com.aromit.geminivoicechat.domain.model.A2UIFile] 로 변환하고,
 * action JSON 의 context 에는 파일명만 남긴다.
 */
data class A2UIPickedFile(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is A2UIPickedFile && other.name == name && other.sizeBytes == sizeBytes

    override fun hashCode(): Int = 31 * name.hashCode() + sizeBytes.hashCode()
}

// ── Surface ──────────────────────────────────────────────────
data class A2UISurface(
    val surfaceId: String,
    val primaryColor: Color,
    val agentDisplayName: String,
    val components: Map<String, A2UIComponent>,
    val rootId: String = "root",
    val dataModel: MutableMap<String, Any?> = mutableMapOf(),
    val sendDataModel: Boolean = true,
)
