package com.pxmx.app.data

import com.pxmx.app.data.api.DemoShell
import com.pxmx.app.data.model.GuestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoShellTest {

    @Test
    fun execute_help_returnsCommandList() {
        val output = DemoShell.execute("help", node = "alpha")
        assertTrue(output.contains("Available demo shell commands"))
        assertTrue(output.contains("uptime"))
        assertTrue(output.contains("ls"))
        assertTrue(output.contains("pveversion"))
        assertTrue(output.contains("date"))
        assertTrue(output.contains("whoami"))
        assertTrue(output.contains("apt list --upgradable"))
        assertTrue(output.contains("systemctl status pve-cluster"))
    }

    @Test
    fun execute_uptime_returnsUptime() {
        val output = DemoShell.execute("uptime", node = "alpha")
        assertTrue(output.contains("up 14 days"))
        assertTrue(output.contains("load average"))
        assertTrue(output.contains("(demo)"))
    }

    @Test
    fun execute_ls_returnsDirectoryListing() {
        val output = DemoShell.execute("ls", node = "alpha")
        assertTrue(output.contains("backup"))
        assertTrue(output.contains("cluster-config.yaml"))
        assertTrue(output.contains("templates"))
    }

    @Test
    fun execute_pveversion_returnsVersion() {
        val output = DemoShell.execute("pveversion", node = "alpha")
        assertTrue(output.contains("pve-manager/8.3.0"))
        assertTrue(output.contains("6.8.12-2-pve"))
    }

    @Test
    fun execute_date_returnsDate() {
        val output = DemoShell.execute("date", node = "alpha")
        assertTrue(output.contains("2026"))
        assertTrue(output.contains("(demo)"))
    }

    @Test
    fun execute_whoami_returnsRoot() {
        val output = DemoShell.execute("whoami", node = "alpha")
        assertEquals("root", output)
    }

    @Test
    fun execute_aptListUpgradable_returnsPackages() {
        val output = DemoShell.execute("apt list --upgradable", node = "alpha")
        assertTrue(output.contains("Listing... Done"))
        assertTrue(output.contains("corosync"))
        assertTrue(output.contains("pve-manager"))
    }

    @Test
    fun execute_systemctlStatusPveCluster_returnsServiceStatus() {
        val output = DemoShell.execute("systemctl status pve-cluster", node = "alpha")
        assertTrue(output.contains("pve-cluster.service"))
        assertTrue(output.contains("Active: active (running)"))
        assertTrue(output.contains("pmxcfs"))
    }

    @Test
    fun execute_trimmedAndCaseInsensitive() {
        val output1 = DemoShell.execute("  UPTIME  ", node = "alpha")
        assertTrue(output1.contains("load average"))

        val output2 = DemoShell.execute("  APT LIST --UPGRADABLE  ", node = "alpha")
        assertTrue(output2.contains("Listing... Done"))
    }

    @Test
    fun execute_unknownCommand_returnsFallback() {
        val output = DemoShell.execute("nonexistent-cmd", node = "alpha")
        assertEquals("nonexistent-cmd: command not found (demo shell)", output)
    }

    @Test
    fun execute_emptyCommand_returnsBlank() {
        val output = DemoShell.execute("   ", node = "alpha")
        assertEquals("", output)
    }

    @Test
    fun generateHtml_containsInteractiveTerminalElements() {
        val htmlUri = DemoShell.generateHtml("alpha", GuestType.NODE, 0L, "alpha")
        assertTrue(htmlUri.startsWith("data:text/html;charset=utf-8,"))

        val decoded = java.net.URLDecoder.decode(
            htmlUri.removePrefix("data:text/html;charset=utf-8,"),
            "UTF-8",
        )
        assertTrue(decoded.contains("root@alpha:~#"))
        assertTrue(decoded.contains("term-input"))
        assertTrue(decoded.contains("handleFormSubmit"))
        assertTrue(decoded.contains("pveversion"))
    }
}
