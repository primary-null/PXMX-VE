package com.pxmx.app.ui.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeNotifyPermissionTest {
    @Test
    fun needsPrompt_api32_returnsFalse() {
        assertFalse(RuntimeNotifyPermission.needsPrompt(32, false))
        assertFalse(RuntimeNotifyPermission.needsPrompt(32, true))
    }

    @Test
    fun needsPrompt_api33Denied_returnsTrue() {
        assertTrue(RuntimeNotifyPermission.needsPrompt(33, false))
    }

    @Test
    fun needsPrompt_api33Granted_returnsFalse() {
        assertFalse(RuntimeNotifyPermission.needsPrompt(33, true))
    }

    @Test
    fun needsPrompt_higherApis() {
        assertTrue(RuntimeNotifyPermission.needsPrompt(34, false))
        assertFalse(RuntimeNotifyPermission.needsPrompt(34, true))
        assertTrue(RuntimeNotifyPermission.needsPrompt(36, false))
        assertFalse(RuntimeNotifyPermission.needsPrompt(36, true))
    }
}
