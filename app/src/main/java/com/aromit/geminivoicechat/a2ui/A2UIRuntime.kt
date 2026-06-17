package com.aromit.geminivoicechat.a2ui

// ── JSON Pointer (RFC 6901) helpers ──────────────────────────

@Suppress("UNCHECKED_CAST")
fun getAtPath(model: Map<String, Any?>, path: String): Any? {
    val parts = path.trimStart('/').split('/').filter { it.isNotEmpty() }
    var current: Any? = model
    for (part in parts) {
        current = (current as? Map<*, *>)?.get(part) ?: return null
    }
    return current
}

@Suppress("UNCHECKED_CAST")
fun setAtPath(model: MutableMap<String, Any?>, path: String, value: Any?) {
    val parts = path.trimStart('/').split('/').filter { it.isNotEmpty() }
    if (parts.isEmpty()) return
    var current: MutableMap<String, Any?> = model
    for (i in 0 until parts.size - 1) {
        val existing = current[parts[i]]
        val next: MutableMap<String, Any?> = if (existing is MutableMap<*, *>) {
            existing as MutableMap<String, Any?>
        } else {
            mutableMapOf<String, Any?>().also { current[parts[i]] = it }
        }
        current = next
    }
    current[parts.last()] = value
}

@Suppress("UNCHECKED_CAST")
fun deepCopyModel(model: Map<String, Any?>): MutableMap<String, Any?> =
    model.mapValues { (_, v) ->
        when (v) {
            is Map<*, *> -> deepCopyModel(v as Map<String, Any?>)
            is MutableList<*> -> v.toMutableList()
            is List<*> -> v.toMutableList()
            else -> v
        }
    }.toMutableMap()

// ── FormatString interpolation ───────────────────────────────

fun resolveFormatString(template: String, model: Map<String, Any?>): String =
    Regex("\\$\\{([^}]+)\\}").replace(template) { match ->
        val expr = match.groupValues[1].trim()
        val path = if (expr.startsWith('/')) expr else "/$expr"
        getAtPath(model, path)?.toString() ?: ""
    }

fun resolveValue(value: A2UIValue, model: Map<String, Any?>): String = when (value) {
    is A2UIValue.Static -> value.text
    is A2UIValue.PathBound -> getAtPath(model, value.path)?.toString() ?: ""
    is A2UIValue.FormatStr -> resolveFormatString(value.template, model)
}

// ── Condition evaluation ──────────────────────────────────────

fun evalCondition(condition: A2UICondition, model: Map<String, Any?>): Boolean = when (condition) {
    is A2UICondition.PathRef -> when (val v = getAtPath(model, condition.path)) {
        null -> false
        is Boolean -> v
        is String -> v.isNotBlank()
        is Number -> v.toDouble() != 0.0
        is List<*> -> v.isNotEmpty()
        else -> true
    }
    is A2UICondition.Required -> when (val v = getAtPath(model, condition.valuePath)) {
        null -> false
        is String -> v.isNotBlank()
        is List<*> -> v.isNotEmpty()
        else -> true
    }
    is A2UICondition.Numeric -> {
        val num = (getAtPath(model, condition.valuePath) as? Number)?.toDouble() ?: return false
        (condition.min == null || num >= condition.min) && (condition.max == null || num <= condition.max)
    }
    is A2UICondition.And -> condition.values.all { evalCondition(it, model) }
    is A2UICondition.Or -> condition.values.any { evalCondition(it, model) }
}

fun checksPass(checks: List<A2UICheck>, model: Map<String, Any?>): Boolean =
    checks.all { evalCondition(it.condition, model) }

fun firstFailMessage(checks: List<A2UICheck>, model: Map<String, Any?>): String? =
    checks.firstOrNull { !evalCondition(it.condition, model) }?.message

// ── Context resolution ────────────────────────────────────────

fun resolveContext(
    refs: List<A2UIContextRef>,
    extras: Map<String, String>,
    model: Map<String, Any?>,
): Map<String, Any?> = buildMap {
    putAll(extras)
    for (ref in refs) {
        put(ref.key, if (ref.path != null) getAtPath(model, ref.path) else ref.literal)
    }
}
