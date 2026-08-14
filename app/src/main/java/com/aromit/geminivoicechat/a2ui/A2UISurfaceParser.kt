package com.aromit.geminivoicechat.a2ui

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

object A2UISurfaceParser {

    fun parse(json: String): A2UISurface {
        val obj = JSONObject(json)
        val componentsObj = obj.getJSONObject("components")
        val components = mutableMapOf<String, A2UIComponent>()
        for (key in componentsObj.keys()) {
            components[key] = parseComponent(componentsObj.getJSONObject(key))
        }
        return A2UISurface(
            surfaceId = obj.getString("surfaceId"),
            primaryColor = parseColor(obj.getString("primaryColor")),
            agentDisplayName = obj.getString("agentDisplayName"),
            rootId = obj.optString("rootId", "root"),
            components = components,
            dataModel = toMap(obj.optJSONObject("dataModel") ?: JSONObject()),
        )
    }

    private fun parseColor(hex: String): Color {
        val clean = hex.trimStart('#')
        val argb = when (clean.length) {
            6 -> ("FF$clean").toLong(16)
            8 -> clean.toLong(16)
            else -> 0xFF888888L
        }
        return Color(argb.toInt())
    }

    private fun parseComponent(obj: JSONObject): A2UIComponent {
        val id = obj.getString("id")
        return when (obj.getString("type")) {
            "Card" -> A2UIComponent.Card(id, obj.getString("child"))
            "Column" -> A2UIComponent.Column(
                id,
                strings(obj.getJSONArray("children")),
                obj.optString("justify", "start"),
                obj.optString("align", "start"),
            )
            "Row" -> A2UIComponent.Row(
                id,
                strings(obj.getJSONArray("children")),
                obj.optString("justify", "start"),
                obj.optString("align", "center"),
            )
            "Text" -> A2UIComponent.TextComp(id, parseValue(obj.getJSONObject("value")),
                obj.optString("variant", "body"))
            "Icon" -> A2UIComponent.IconComp(id, obj.getString("name"), obj.optInt("size", 20))
            "Divider" -> A2UIComponent.Divider(id)
            "Button" -> A2UIComponent.ButtonComp(
                id,
                parseValue(obj.getJSONObject("label")),
                obj.optString("variant", "primary"),
                obj.optJSONObject("action")?.let { parseAction(it) },
                obj.optJSONArray("checks")?.let { parseChecks(it) } ?: emptyList(),
            )
            "TextField" -> A2UIComponent.TextFieldComp(
                id,
                obj.getString("label"),
                obj.getString("valuePath"),
                obj.optString("variant", "shortText"),
                obj.optString("placeholder", ""),
                obj.optJSONArray("checks")?.let { parseChecks(it) } ?: emptyList(),
            )
            "CheckBox" -> A2UIComponent.CheckBoxComp(id, obj.getString("label"),
                obj.getString("valuePath"))
            "ChoicePicker" -> A2UIComponent.ChoicePickerComp(
                id,
                options(obj.getJSONArray("options")),
                obj.getString("valuePath"),
                obj.optString("variant", "mutuallyExclusive"),
            )
            "Slider" -> A2UIComponent.SliderComp(
                id,
                obj.getString("label"),
                obj.getString("valuePath"),
                obj.getDouble("min").toFloat(),
                obj.getDouble("max").toFloat(),
                obj.getDouble("step").toFloat(),
            )
            "Switch" -> A2UIComponent.SwitchComp(id, obj.getString("label"),
                obj.getString("valuePath"))
            "TagInput" -> A2UIComponent.TagInputComp(
                id,
                obj.getString("label"),
                obj.getString("valuePath"),
                obj.optJSONArray("suggestions")?.let { strings(it) } ?: emptyList(),
                obj.optString("placeholder", ""),
            )
            "Select" -> A2UIComponent.SelectComp(
                id,
                obj.getString("label"),
                obj.getString("valuePath"),
                options(obj.getJSONArray("options")),
                obj.optString("placeholder", ""),
            )
            "RiskCardList" -> A2UIComponent.RiskCardList(id, obj.getString("rowsPath"))
            else -> A2UIComponent.Divider(id)
        }
    }

    private fun parseValue(obj: JSONObject): A2UIValue = when (obj.getString("valueType")) {
        "static" -> A2UIValue.Static(obj.getString("text"))
        "pathBound" -> A2UIValue.PathBound(obj.getString("path"))
        "formatStr" -> A2UIValue.FormatStr(obj.getString("template"))
        else -> A2UIValue.Static("")
    }

    private fun parseCondition(obj: JSONObject): A2UICondition = when (obj.getString("condType")) {
        "required" -> A2UICondition.Required(obj.getString("valuePath"))
        "numeric" -> A2UICondition.Numeric(
            obj.getString("valuePath"),
            if (obj.has("min")) obj.getDouble("min") else null,
            if (obj.has("max")) obj.getDouble("max") else null,
        )
        "pathRef" -> A2UICondition.PathRef(obj.getString("path"))
        "and" -> A2UICondition.And(condList(obj.getJSONArray("values")))
        "or" -> A2UICondition.Or(condList(obj.getJSONArray("values")))
        else -> A2UICondition.Required("/never")
    }

    private fun condList(arr: JSONArray): List<A2UICondition> =
        (0 until arr.length()).map { parseCondition(arr.getJSONObject(it)) }

    private fun parseChecks(arr: JSONArray): List<A2UICheck> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            A2UICheck(parseCondition(o.getJSONObject("condition")), o.getString("message"))
        }

    private fun parseAction(obj: JSONObject): A2UIAction {
        val refs = (obj.optJSONArray("contextRefs") ?: JSONArray()).let { arr ->
            (0 until arr.length()).map { i ->
                val r = arr.getJSONObject(i)
                A2UIContextRef(
                    key = r.getString("key"),
                    path = r.takeUnless { it.isNull("path") }?.optString("path"),
                    literal = r.takeUnless { it.isNull("literal") }?.optString("literal"),
                )
            }
        }
        val extras = mutableMapOf<String, String>()
        (obj.optJSONObject("extraLiterals") ?: JSONObject()).let { e ->
            for (k in e.keys()) extras[k] = e.getString(k)
        }
        return A2UIAction(obj.getString("eventName"), refs, extras)
    }

    private fun options(arr: JSONArray): List<A2UIOption> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            A2UIOption(o.getString("label"), o.getString("value"))
        }

    private fun strings(arr: JSONArray): List<String> =
        (0 until arr.length()).map { arr.getString(it) }

    @Suppress("UNCHECKED_CAST")
    private fun toMap(obj: JSONObject): MutableMap<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (key in obj.keys()) map[key] = toKotlin(obj.get(key))
        return map
    }

    private fun toKotlin(v: Any?): Any? = when (v) {
        is JSONObject -> toMap(v)
        is JSONArray -> (0 until v.length()).map { toKotlin(v.get(it)) }.toMutableList()
        JSONObject.NULL -> null
        else -> v
    }
}
