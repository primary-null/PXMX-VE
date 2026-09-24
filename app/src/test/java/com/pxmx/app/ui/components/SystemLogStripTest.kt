package com.pxmx.app.ui.components

import com.pxmx.app.data.model.ClusterLogEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemLogStripTest {

    @Test
    fun stripLogUser_authSuccessWithQuotes() {
        val msg = "successful auth for user 'root@pam'"
        assertEquals("successful auth", stripLogUser(msg))
    }

    @Test
    fun formatSystemLogStripText_pvedaemonSuccessfulAuth() {
        val entry = ClusterLogEntry(
            tag = "pvedaemon",
            msg = "successful auth for user 'root@pam'",
            pri = 6,
        )
        assertEquals("pvedaemon successful auth", formatSystemLogStripText(entry))
    }

    @Test
    fun formatSystemLogStripText_preservesTagAndStatusWords() {
        val text = formatSystemLogStripText("pvedaemon", "successful auth for user 'root@pam'")
        assertEquals("pvedaemon successful auth", text)
    }

    @Test
    fun formatSystemLogStripText_angleBracketUser() {
        val text = formatSystemLogStripText("pveproxy", "<root@pam> successful auth for user 'root@pam'")
        assertEquals("pveproxy successful auth", text)
    }

    @Test
    fun formatSystemLogStripText_userWithoutRealm() {
        val text = formatSystemLogStripText("pvedaemon", "successful auth for user 'admin'")
        assertEquals("pvedaemon successful auth", text)
    }

    @Test
    fun formatSystemLogStripText_authFailureWithKeyValue() {
        val text = formatSystemLogStripText(
            "pvedaemon",
            "authentication failure; rhost=192.168.1.100 user=root@pam msg=failed",
        )
        assertEquals("pvedaemon authentication failure; rhost=192.168.1.100 msg=failed", text)
    }

    @Test
    fun formatSystemLogStripText_connectionFromUser() {
        val text = formatSystemLogStripText("pvedaemon", "connection from '10.0.0.1' for user 'root@pam'")
        assertEquals("pvedaemon connection from '10.0.0.1'", text)
    }

    @Test
    fun formatSystemLogStripText_regularLogUnchanged() {
        val entry1 = ClusterLogEntry(
            tag = "systemd",
            msg = "Started Proxmox VE replication runner",
            pri = 5,
        )
        assertEquals("systemd Started Proxmox VE replication runner", formatSystemLogStripText(entry1))

        val entry2 = ClusterLogEntry(
            tag = "vzdump",
            msg = "Backup job finished successfully for VM 100",
            pri = 6,
        )
        assertEquals("vzdump Backup job finished successfully for VM 100", formatSystemLogStripText(entry2))
    }

    @Test
    fun formatSystemLogStripText_onlyUserDoesNotBlankLine() {
        // Does not blank the whole line even if message is entirely the user token
        val entry = ClusterLogEntry(tag = "pvedaemon", msg = "user 'root@pam'")
        assertEquals("pvedaemon", formatSystemLogStripText(entry))
    }

    @Test
    fun formatSystemLogStripText_nullAndEmptyEntries() {
        assertEquals("—", formatSystemLogStripText(null))
        assertEquals("sys", formatSystemLogStripText(null, null))
        assertEquals("sys", formatSystemLogStripText("", ""))
        assertEquals("pvedaemon", formatSystemLogStripText("pvedaemon", ""))
    }
}
