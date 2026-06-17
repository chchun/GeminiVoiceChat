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
