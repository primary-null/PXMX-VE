package com.pxmx.app.data

import com.pxmx.app.data.api.CertUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SecurityHardeningTest {

    @Test
    fun testNormalizeFingerprint() {
        val raw = "aa:bb:cc:dd:ee:ff:00:11:22:33:44:55:66:77:88:99:aa:bb:cc:dd:ee:ff:00:11:22:33:44:55:66:77:88:99"
        val normalized = CertUtils.normalizeFingerprint(raw)
        assertEquals("AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899", normalized)

        val spaced = "AA BB CC DD EE FF 00 11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF 00 11 22 33 44 55 66 77 88 99"
        assertEquals(normalized, CertUtils.normalizeFingerprint(spaced))
    }

    @Test
    fun testBackupFilenameSanitizationRules() {
        val validFilenames = listOf(
            "vzdump-qemu-100-2026_08_31.vma.zst",
            "backup_123-abc.tar.gz",
            "node.backup.01.tar"
        )
        for (name in validFilenames) {
            assert(name.matches(Regex("^[A-Za-z0-9._-]+$")) && !name.contains(".."))
        }

        val invalidFilenames = listOf(
            "../etc/passwd",
            "../../backup.tar",
            "foo/bar.tar",
            "foo\\bar.tar",
            "backup;rm -rf /",
            "backup`whoami`",
            "backup\$id",
            ""
        )
        for (name in invalidFilenames) {
            val isInvalid = name.isEmpty() || name.contains('/') || name.contains('\\') || name.contains("..") || !name.matches(Regex("^[A-Za-z0-9._-]+$"))
            assert(isInvalid)
        }
    }

    @Test
    fun testAppToastFormatting() {
        assertEquals("Connected", com.pxmx.app.ui.util.AppToast.CONNECTED.text())
        assertEquals("web01: start sent", com.pxmx.app.ui.util.AppToast.GUEST_STARTED.text("web01"))
        assertEquals("Guest web01 (VMID 100) created", com.pxmx.app.ui.util.AppToast.DEPLOY_CREATED.text("web01", 100))
        assertEquals("Deploy failed: timeout", com.pxmx.app.ui.util.AppToast.ACTION_FAILED.text("Deploy", "timeout"))
        assertEquals(21, com.pxmx.app.ui.util.AppToast.entries.size)
    }
}
