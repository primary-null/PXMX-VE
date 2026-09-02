package com.pxmx.app.data.model

/**
 * Tiny helpers for PVE JSON maps that aren't fully typed yet.
 * Keeps fromMap() companions short and consistent.
 */
internal object MapParse {
    fun str(m: Map<String, Any>, key: String): String? =
        m[key]?.toString()?.takeIf { it.isNotBlank() }

    fun str(m: Map<String, Any>, vararg keys: String): String? {
        for (key in keys) {
            str(m, key)?.let { return it }
        }
        return null
    }

    fun flag(m: Map<String, Any>, key: String, default: Boolean = false): Boolean =
        when (val v = m[key]) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> v == "1" || v.equals("true", ignoreCase = true)
            else -> default
        }

    fun long(m: Map<String, Any>, key: String): Long? =
        when (val v = m[key]) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull()
            else -> null
        }

    fun int(m: Map<String, Any>, key: String): Int? =
        when (val v = m[key]) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull()
            else -> null
        }
}
