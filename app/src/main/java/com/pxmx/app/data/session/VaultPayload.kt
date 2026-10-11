package com.pxmx.app.data.session

import kotlinx.serialization.Serializable

/**
 * Typed bucket payload for encrypted vault persistence.
 * Corresponds to standard [android.content.SharedPreferences] value types.
 */
@Serializable
data class VaultPayload(
    val strings: Map<String, String> = emptyMap(),
    val booleans: Map<String, Boolean> = emptyMap(),
    val ints: Map<String, Int> = emptyMap(),
    val longs: Map<String, Long> = emptyMap(),
    val floats: Map<String, Float> = emptyMap(),
    val stringSets: Map<String, Set<String>> = emptyMap(),
)
