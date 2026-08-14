package com.aromit.geminivoicechat.a2ui

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * A2UI v0.9 엔벨로프(createSurface / updateComponents / updateDataModel)를
 * [A2UISurface]에 순차 적용하는 순수 함수 모음.
 *
 * 서버(saferyn-langgraph)는 서피스를 한 번에 완성해 보내지 않고 엔벨로프 여러 건으로
 * 나눠 보낸다 — 이 적용기가 부분 갱신을 누적한다. 원샷 서피스 JSON 파싱(로컬 데모
 * 형식)은 [A2UISurfaceParser] 몫이며 이 파일과 형식이 다르다.
 */
object A2UIEnvelopeApplier {

    /** 엔벨로프가 대상으로 하는 surfaceId. 파싱 불가 시 null. */
    fun surfaceIdOf(envelopeJson: String): String? = runCatching {
        val obj = JSONObject(envelopeJson)
        for (key in listOf("createSurface", "updateComponents", "updateDataModel")) {
            obj.optJSONObject(key)?.let { return it.optString("surfaceId").ifEmpty { null } }
        }
        null
    }.getOrNull()

    /**
     * 엔벨로프 1건을 적용한 새 서피스를 반환한다.
     * createSurface 이전에 update 가 오거나 파싱에 실패하면 [current]를 그대로 반환한다
     * (엔벨로프 1건의 실패가 스트림을 죽이지 않는다 — spec 001 수용 4).
     */
    fun apply(current: A2UISurface?, envelopeJson: String): A2UISurface? = runCatching {
        val obj = JSONObject(envelopeJson)
        obj.optJSONObject("createSurface")?.let { return@runCatching createSurface(it) }
        val surface = current ?: return@runCatching null
        obj.optJSONObject("updateComponents")?.let { return@runCatching updateComponents(surface, it) }
        obj.optJSONObject("updateDataModel")?.let { return@runCatching updateDataModel(surface, it) }
        surface
    }.getOrDefault(current)

    private fun createSurface(op: JSONObject): A2UISurface {
        val theme = op.optJSONObject("theme")
        return A2UISurface(
            surfaceId = op.getString("surfaceId"),
            primaryColor = parseColor(theme?.optString("primaryColor") ?: ""),
            // 서버는 agentDisplayName 을 theme 안에 넣는다 (예: "Safety Report Bot")
            agentDisplayName = theme?.optString("agentDisplayName")?.ifEmpty { null }
                ?: op.optString("agentDisplayName", "AI"),
            components = emptyMap(),
            dataModel = mutableMapOf(),
            sendDataModel = op.optBoolean("sendDataModel", true),
        )
    }

    private fun updateComponents(surface: A2UISurface, op: JSONObject): A2UISurface {
        val arr = op.optJSONArray("components") ?: return surface
        val merged = surface.components.toMutableMap()
        for (i in 0 until arr.length()) {
            val comp = parseComponent(arr.getJSONObject(i)) ?: continue
            merged[comp.id] = comp
        }
        return surface.copy(components = merged)
    }

    /**
     * v0.9 와이어 컴포넌트 → 모델 (T110).
     * basic catalog 표준 컴포넌트는 기존 [A2UIComponent] 타입으로 매핑하고
     * (아차사고/안전제안 폼이 이 형식 — 서버 `build_safety_report_form_a2ui`),
     * 그 외 kind(커스텀 카드 6종 + FileUpload 등 미지원)는 [A2UIComponent.ServerCard]로
     * 담아 렌더러가 카드/폴백을 그리게 한다.
     *
     * 로컬 데모 형식(`A2UISurfaceParser`)과 와이어 필드가 다르다:
     * `component`(타입명) · `text` 직접값 · `value:{path}` 바인딩 ·
     * `checks:[{call,args,message}]` · `action:{event:{name,context}}`.
     */
    private fun parseComponent(obj: JSONObject): A2UIComponent? {
        val id = obj.optString("id").ifEmpty { return null }
        val kind = obj.optString("component").ifEmpty { return null }
        val valuePath = wireValuePath(obj)
        return when (kind) {
            "Card" -> A2UIComponent.Card(id, obj.optString("child"))
            "Column" -> A2UIComponent.Column(
                id,
                strings(obj.optJSONArray("children")),
                obj.optString("justify", "start"),
                obj.optString("align", "start"),
            )

            "Row" -> A2UIComponent.Row(
                id,
                strings(obj.optJSONArray("children")),
                obj.optString("justify", "start"),
                obj.optString("align", "center"),
            )

            "Text" -> A2UIComponent.TextComp(
                id,
                if (valuePath.isNotEmpty()) A2UIValue.PathBound(valuePath)
                else A2UIValue.Static(obj.optString("text")),
                obj.optString("variant", "body"),
            )

            "Divider" -> A2UIComponent.Divider(id)

            "TextField" -> A2UIComponent.TextFieldComp(
                id = id,
                label = obj.optString("label"),
                valuePath = valuePath,
                variant = obj.optString("variant", "shortText"),
                placeholder = obj.optString("placeholder", ""),
                checks = wireChecks(obj.optJSONArray("checks")),
                readonly = obj.optBoolean("readonly", false),
            )

            "CheckBox" -> A2UIComponent.CheckBoxComp(id, obj.optString("label"), valuePath)

            "ChoicePicker" -> A2UIComponent.ChoicePickerComp(
                id = id,
                options = wireOptions(obj.optJSONArray("options")),
                valuePath = valuePath,
                variant = obj.optString("variant", "mutuallyExclusive"),
            )

            "FileUpload" -> A2UIComponent.FileUploadComp(
                id = id,
                label = obj.optString("label", "파일"),
                valuePath = valuePath,
                accept = strings(obj.optJSONArray("accept")),
                maxFiles = obj.optInt("maxFiles", 5),
                maxSizeMb = obj.optInt("maxSizeMb", 50),
            )

            "Button" -> A2UIComponent.ButtonComp(
                id = id,
                label = A2UIValue.Static(obj.optString("text")),
                variant = obj.optString("variant", "primary"),
                action = wireAction(obj.optJSONObject("action")),
                checks = wireChecks(obj.optJSONArray("checks")),
            )

            else -> A2UIComponent.ServerCard(id = id, kind = kind, valuePath = valuePath)
        }
    }

    private fun wireValuePath(obj: JSONObject): String =
        obj.optJSONObject("value")?.optString("path") ?: ""

    /** `[{call:"required"|"numeric", args:{value:{path},min?,max?}, message}]` → A2UICheck 목록. */
    private fun wireChecks(arr: JSONArray?): List<A2UICheck> {
        if (arr == null) return emptyList()
        val checks = mutableListOf<A2UICheck>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val args = o.optJSONObject("args")
            val path = args?.optJSONObject("value")?.optString("path") ?: continue
            val condition = when (o.optString("call")) {
                "required" -> A2UICondition.Required(path)
                "numeric" -> A2UICondition.Numeric(
                    path,
                    if (args.has("min")) args.getDouble("min") else null,
                    if (args.has("max")) args.getDouble("max") else null,
                )

                else -> continue
            }
            checks += A2UICheck(condition, o.optString("message"))
        }
        return checks
    }

    /** `{event:{name, context:{키:{path}|{literal}|원시값}}}` → A2UIAction. */
    private fun wireAction(obj: JSONObject?): A2UIAction? {
        val event = obj?.optJSONObject("event") ?: return null
        val name = event.optString("name").ifEmpty { return null }
        val refs = mutableListOf<A2UIContextRef>()
        event.optJSONObject("context")?.let { ctx ->
            for (key in ctx.keys()) {
                val v = ctx.opt(key)
                refs += if (v is JSONObject) {
                    A2UIContextRef(
                        key = key,
                        path = v.optString("path").ifEmpty { null },
                        literal = v.optString("literal").ifEmpty { null },
                    )
                } else {
                    A2UIContextRef(key = key, literal = v?.toString())
                }
            }
        }
        return A2UIAction(eventName = name, contextRefs = refs)
    }

    private fun wireOptions(arr: JSONArray?): List<A2UIOption> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { A2UIOption(it.optString("label"), it.optString("value")) }
        }
    }

    private fun strings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }

    private fun updateDataModel(surface: A2UISurface, op: JSONObject): A2UISurface {
        val path = op.optString("path", "/")
        val value = toKotlin(op.opt("value"))
        val model = deepCopyModel(surface.dataModel)
        if (path == "/" || path.isEmpty()) {
            (value as? Map<*, *>)?.forEach { (k, v) -> model[k.toString()] = v }
        } else {
            setAtPath(model, path, value)
        }
        return surface.copy(dataModel = model)
    }

    private fun parseColor(hex: String): Color {
        val clean = hex.trimStart('#')
        val argb = when (clean.length) {
            6 -> ("FF$clean").toLong(16)
            8 -> clean.toLong(16)
            else -> DEFAULT_COLOR_ARGB
        }
        return Color(argb.toInt())
    }

    private fun toKotlin(v: Any?): Any? = when (v) {
        is JSONObject -> {
            val map = mutableMapOf<String, Any?>()
            for (key in v.keys()) map[key] = toKotlin(v.get(key))
            map
        }

        is JSONArray -> (0 until v.length()).map { toKotlin(v.get(it)) }.toMutableList()
        JSONObject.NULL -> null
        else -> v
    }

    private const val DEFAULT_COLOR_ARGB = 0xFF888888L
}
