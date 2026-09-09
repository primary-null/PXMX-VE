package com.pxmx.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapParseTest {

    @Test
    fun str_blankAndMissing() {
        assertNull(MapParse.str(emptyMap(), "k"))
        assertNull(MapParse.str(mapOf("k" to "  "), "k"))
        assertEquals("ok", MapParse.str(mapOf("k" to "ok"), "k"))
    }

    @Test
    fun str_firstMatchingKey() {
        val m = mapOf("b" to "two", "c" to "three")
        assertEquals("two", MapParse.str(m, "a", "b", "c"))
        assertNull(MapParse.str(m, "x", "y"))
    }

    @Test
    fun flag_boolNumberString() {
        assertFalse(MapParse.flag(emptyMap(), "on"))
        assertTrue(MapParse.flag(emptyMap(), "on", default = true))
        assertTrue(MapParse.flag(mapOf("on" to true), "on"))
        assertFalse(MapParse.flag(mapOf("on" to false), "on"))
        assertTrue(MapParse.flag(mapOf("on" to 1), "on"))
        assertFalse(MapParse.flag(mapOf("on" to 0), "on"))
        assertTrue(MapParse.flag(mapOf("on" to "1"), "on"))
        assertTrue(MapParse.flag(mapOf("on" to "TRUE"), "on"))
        assertFalse(MapParse.flag(mapOf("on" to "no"), "on"))
    }

    @Test
    fun longAndInt() {
        assertEquals(42L, MapParse.long(mapOf("n" to 42), "n"))
        assertEquals(42L, MapParse.long(mapOf("n" to "42"), "n"))
        assertNull(MapParse.long(mapOf("n" to "x"), "n"))
        assertEquals(7, MapParse.int(mapOf("n" to 7L), "n"))
        assertEquals(7, MapParse.int(mapOf("n" to "7"), "n"))
        assertNull(MapParse.int(emptyMap(), "n"))
    }
}
