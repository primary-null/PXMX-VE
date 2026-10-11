package com.pxmx.app.ui.icons

import org.junit.Assert.assertEquals
import org.junit.Test

class GuestCallsignTest {

    @Test
    fun callsign_extractsFirstTwoAlphanumericCharsUppercase() {
        assertEquals("NO", GuestIcons.callsign("nova"))
        assertEquals("HA", GuestIcons.callsign("haos-prod"))
        assertEquals("WI", GuestIcons.callsign("win-ad"))
        assertEquals("VM", GuestIcons.callsign("vm100"))
        assertEquals("10", GuestIcons.callsign("101-ubuntu"))
        assertEquals("K8", GuestIcons.callsign("k8s-worker"))
    }

    @Test
    fun callsign_namePrecedenceOverVmid() {
        assertEquals("NO", GuestIcons.callsign("nova", 100L))
        assertEquals("HA", GuestIcons.callsign("haos-prod", 200L))
        assertEquals("WI", GuestIcons.callsign("win-ad", 105L))
    }

    @Test
    fun callsign_singleCharacterName() {
        assertEquals("A", GuestIcons.callsign("a"))
        assertEquals("Z", GuestIcons.callsign("z"))
        assertEquals("9", GuestIcons.callsign("9"))
    }

    @Test
    fun callsign_punctuationStripped() {
        assertEquals("PV", GuestIcons.callsign("p_v_e"))
        assertEquals("AB", GuestIcons.callsign("---a---b---"))
        assertEquals("C1", GuestIcons.callsign("...c...1..."))
    }

    @Test
    fun callsign_noAlphanumericChars_fallsBackToVmidZeroPadded() {
        assertEquals("00", GuestIcons.callsign("", 100L))
        assertEquals("05", GuestIcons.callsign("", 105L))
        assertEquals("07", GuestIcons.callsign("", 7L))
        assertEquals("99", GuestIcons.callsign("", 99L))
        assertEquals("00", GuestIcons.callsign("---", 200L))
        assertEquals("34", GuestIcons.callsign("   ", 1234L))
        assertEquals("00", GuestIcons.callsign("!@#$", 0L))
    }

    @Test
    fun callsign_noAlphanumericCharsAndNoVmid_returnsQuestionMark() {
        assertEquals("?", GuestIcons.callsign(""))
        assertEquals("?", GuestIcons.callsign("---"))
        assertEquals("?", GuestIcons.callsign("   "))
        assertEquals("?", GuestIcons.callsign("!@#$%"))
        assertEquals("?", GuestIcons.callsign("", null))
        assertEquals("?", GuestIcons.callsign("---", null))
    }
}
