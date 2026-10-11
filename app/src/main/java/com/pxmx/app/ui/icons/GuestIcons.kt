package com.pxmx.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.pxmx.app.data.model.ClusterResource

/**
 * Guest/node/storage icons for the resource list.
 *
 * Priority for VMs:
 * 1. resource type (lxc / node / storage)
 * 2. Proxmox `ostype` from config
 * 3. name / tags heuristics (helps when ostype is wrong, e.g. WIN-TEST as l26)
 */
enum class GuestKind {
    NODE,
    STORAGE,
    CONTAINER,
    WINDOWS,
    LINUX,
    FIREWALL,
    DATABASE,
    MEDIA,
    HOME_ASSISTANT,
    TEMPLATE,
    GENERIC_VM,
}

data class GuestIconStyle(
    val kind: GuestKind,
    val icon: ImageVector,
    /** Accent when guest is running/available; status still tints when offline. */
    val accent: Color,
    val label: String,
)

object GuestIcons {

    private val Blue = Color(0xFF42A5F5)
    private val Green = Color(0xFF66BB6A)
    private val Orange = Color(0xFFFFA726)
    private val Purple = Color(0xFFAB47BC)
    private val Cyan = Color(0xFF26C6DA)
    private val Grey = Color(0xFFBDBDBD)
    private val WinBlue = Color(0xFF00A4EF)
    private val RedOrange = Color(0xFFFF7043)
    private val Amber = Color(0xFFFFB300)

    fun callsign(displayName: String, vmid: Long? = null): String {
        val chars = displayName.filter { it.isLetterOrDigit() }
        if (chars.isNotEmpty()) {
            return chars.take(2).uppercase()
        }
        if (vmid != null) {
            val lastTwo = (kotlin.math.abs(vmid) % 100).toInt()
            return "%02d".format(lastTwo)
        }
        return "?"
    }

    fun styleFor(resource: ClusterResource): GuestIconStyle {
        return when (resource.type) {
            "node" -> GuestIconStyle(GuestKind.NODE, GuestGlyphs.Node, Orange, "Node")
            "storage" -> GuestIconStyle(GuestKind.STORAGE, GuestGlyphs.Storage, Cyan, "Storage")
            "lxc" -> containerStyle(resource)
            "qemu" -> qemuStyle(resource)
            else -> GuestIconStyle(GuestKind.GENERIC_VM, GuestGlyphs.GenericVm, Grey, "Guest")
        }
    }

    private fun containerStyle(r: ClusterResource): GuestIconStyle {
        if (r.template == 1) {
            return GuestIconStyle(GuestKind.TEMPLATE, GuestGlyphs.Template, Purple, "CT template")
        }
        return GuestIconStyle(GuestKind.CONTAINER, GuestGlyphs.Container, Green, "Container")
    }

    private fun qemuStyle(r: ClusterResource): GuestIconStyle {
        if (r.template == 1) {
            return GuestIconStyle(GuestKind.TEMPLATE, GuestGlyphs.Template, Purple, "VM template")
        }

        val ostype = r.ostype?.lowercase()?.trim().orEmpty()
        val name = r.name?.lowercase().orEmpty()
        val tags = r.tags?.lowercase().orEmpty()
        val blob = "$name $tags $ostype"

        when {
            looksLikeHomeAssistant(blob) ->
                return GuestIconStyle(GuestKind.HOME_ASSISTANT, GuestGlyphs.HomeAssistant, Color(0xFF41BDF5), "Home Assistant")
            looksLikeFirewall(blob) ->
                return GuestIconStyle(GuestKind.FIREWALL, GuestGlyphs.Firewall, RedOrange, "Firewall")
            looksLikeMedia(blob) ->
                return GuestIconStyle(GuestKind.MEDIA, GuestGlyphs.Media, Amber, "Media")
            looksLikeDatabase(blob) ->
                return GuestIconStyle(GuestKind.DATABASE, GuestGlyphs.Database, Purple, "Database")
            looksLikeWindows(name, tags, ostype) ->
                return GuestIconStyle(GuestKind.WINDOWS, GuestGlyphs.Windows, WinBlue, "Windows")
        }

        when {
            ostype.startsWith("win") || ostype in setOf("wxp", "w2k", "w2k3", "w2k8", "wvista") ->
                return GuestIconStyle(GuestKind.WINDOWS, GuestGlyphs.Windows, WinBlue, "Windows")
            ostype in setOf("l24", "l26", "linux", "other") || ostype.startsWith("l") ->
                return GuestIconStyle(GuestKind.LINUX, GuestGlyphs.Linux, Blue, "Linux")
            ostype.contains("solaris") ->
                return GuestIconStyle(GuestKind.LINUX, GuestGlyphs.Linux, Orange, "Solaris")
        }

        if (looksLikeLinux(blob)) {
            return GuestIconStyle(GuestKind.LINUX, GuestGlyphs.Linux, Blue, "Linux")
        }

        return GuestIconStyle(GuestKind.GENERIC_VM, GuestGlyphs.GenericVm, Grey, "VM")
    }

    private fun looksLikeFirewall(blob: String): Boolean {
        val keys = listOf(
            "pfsense", "opnsense", "firewall", "vyos", "openwrt",
            "freebsd", "openbsd", "netbsd", "bsd", "router",
        )
        return keys.any { blob.contains(it) }
    }

    private fun looksLikeMedia(blob: String): Boolean {
        val keys = listOf("plex", "jellyfin", "emby")
        return keys.any { blob.contains(it) }
    }

    private fun looksLikeDatabase(blob: String): Boolean {
        val keys = listOf("postgres", "postgresql", "mariadb", "mongodb", "redis")
        return keys.any { blob.contains(it) }
    }

    private fun looksLikeWindows(name: String, tags: String, ostype: String): Boolean {
        if (ostype.startsWith("win")) return true
        val keys = listOf(
            "win", "windows", "w11", "w10", "w2k", "server20", "dc0", "dc1", "ad-",
            "domain", "mssql", "iis",
        )
        val blob = "$name $tags"
        return keys.any { blob.contains(it) }
    }

    private fun looksLikeLinux(blob: String): Boolean {
        val keys = listOf(
            "ubuntu", "debian", "centos", "rhel", "rocky", "alma", "fedora",
            "arch", "suse", "linux", "kl-", "kl_", "ub-", "nix", "docker",
            "k8s", "kube", "pve", "proxmox", "pdm",
        )
        return keys.any { blob.contains(it) }
    }

    private fun looksLikeHomeAssistant(blob: String): Boolean {
        return listOf("haos", "hass", "homeassistant", "home-assistant", "home_assistant")
            .any { blob.contains(it) }
    }
}
