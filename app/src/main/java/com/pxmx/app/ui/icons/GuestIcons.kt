package com.pxmx.app.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Window
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

    fun styleFor(resource: ClusterResource): GuestIconStyle {
        return when (resource.type) {
            "node" -> GuestIconStyle(GuestKind.NODE, Icons.Default.Dns, Orange, "Node")
            "storage" -> GuestIconStyle(GuestKind.STORAGE, Icons.Default.Storage, Cyan, "Storage")
            "lxc" -> containerStyle(resource)
            "qemu" -> qemuStyle(resource)
            else -> GuestIconStyle(GuestKind.GENERIC_VM, Icons.Default.Laptop, Grey, "Guest")
        }
    }

    private fun containerStyle(r: ClusterResource): GuestIconStyle {
        if (r.template == 1) {
            return GuestIconStyle(GuestKind.TEMPLATE, Icons.Default.GridView, Purple, "CT template")
        }
        return GuestIconStyle(GuestKind.CONTAINER, Icons.Default.Inventory2, Green, "Container")
    }

    private fun qemuStyle(r: ClusterResource): GuestIconStyle {
        if (r.template == 1) {
            return GuestIconStyle(GuestKind.TEMPLATE, Icons.Default.GridView, Purple, "VM template")
        }

        val ostype = r.ostype?.lowercase()?.trim().orEmpty()
        val name = r.name?.lowercase().orEmpty()
        val tags = r.tags?.lowercase().orEmpty()
        val blob = "$name $tags $ostype"

        when {
            looksLikeHomeAssistant(blob) ->
                return GuestIconStyle(GuestKind.HOME_ASSISTANT, Icons.Default.Home, Color(0xFF41BDF5), "Home Assistant")
            looksLikeWindows(name, tags, ostype) ->
                return GuestIconStyle(GuestKind.WINDOWS, Icons.Default.Window, WinBlue, "Windows")
        }

        when {
            ostype.startsWith("win") || ostype in setOf("wxp", "w2k", "w2k3", "w2k8", "wvista") ->
                return GuestIconStyle(GuestKind.WINDOWS, Icons.Default.Window, WinBlue, "Windows")
            ostype in setOf("l24", "l26", "linux", "other") || ostype.startsWith("l") ->
                return GuestIconStyle(GuestKind.LINUX, Icons.Default.Terminal, Blue, "Linux")
            ostype.contains("solaris") ->
                return GuestIconStyle(GuestKind.LINUX, Icons.Default.Terminal, Orange, "Solaris")
        }

        if (looksLikeLinux(blob)) {
            return GuestIconStyle(GuestKind.LINUX, Icons.Default.Terminal, Blue, "Linux")
        }

        return GuestIconStyle(GuestKind.GENERIC_VM, Icons.Default.Laptop, Grey, "VM")
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
