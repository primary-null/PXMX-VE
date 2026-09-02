package com.pxmx.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SnapshotInfo(
    val name: String? = null,
    val description: String? = null,
    val snaptime: Long? = null,
    val vmstate: Int? = null,
    val running: Int? = null,
    val parent: String? = null,
    val digest: String? = null,
) {
    val isCurrent: Boolean get() = name == "current"
}

@Serializable
data class BackupVolume(
    val volid: String? = null,
    val content: String? = null,
    val format: String? = null,
    val size: Long? = null,
    val ctime: Long? = null,
    val vmid: Long? = null,
    val notes: String? = null,
    val subtype: String? = null,
)

@Serializable
data class HostUsbDevice(
    val busnum: Int? = null,
    val devnum: Int? = null,
    val port: Int? = null,
    val level: Int? = null,
    @SerialName("class") val deviceClass: Int? = null,
    val vendid: String? = null,
    val prodid: String? = null,
    val manufacturer: String? = null,
    val product: String? = null,
    val speed: String? = null,
    val usbpath: String? = null,
) {
    val hostId: String?
        get() {
            val v = vendid ?: return null
            val p = prodid ?: return null
            return "${v.lowercase()}:${p.lowercase()}"
        }

    /** USB hubs (class 9) are rarely useful to passthrough. */
    val isHub: Boolean get() = deviceClass == 9

    val displayName: String
        get() = product?.trim()?.takeIf { it.isNotBlank() }
            ?: manufacturer?.trim()?.takeIf { it.isNotBlank() }
            ?: "USB device"

    val display: String
        get() = buildString {
            append(displayName)
            manufacturer?.trim()?.takeIf { it.isNotBlank() && it != displayName }
                ?.let { append(" ($it)") }
            hostId?.let { append(" · $it") }
        }

    val detailLine: String
        get() = buildString {
            busnum?.let { append("bus $it") }
            devnum?.let {
                if (isNotEmpty()) append(" · ")
                append("dev $it")
            }
            speed?.let {
                if (isNotEmpty()) append(" · ")
                append("${it} Mb/s")
            }
            deviceClass?.let {
                if (isNotEmpty()) append(" · ")
                append("class $it")
            }
            if (isHub) {
                if (isNotEmpty()) append(" · ")
                append("hub")
            }
        }
}

/** Generic storage content row (images, iso, backup, vztmpl, snippets, rootdir). */
@Serializable
data class StorageContentItem(
    val volid: String? = null,
    val content: String? = null,
    val format: String? = null,
    val size: Long? = null,
    val used: Long? = null,
    val ctime: Long? = null,
    val vmid: Long? = null,
    val notes: String? = null,
    val parent: String? = null,
    val subtype: String? = null,
    val path: String? = null,
)

@Serializable
data class StorageStatus(
    val storage: String? = null,
    val type: String? = null,
    val content: String? = null,
    val active: Int? = null,
    val enabled: Int? = null,
    val shared: Int? = null,
    val total: Long? = null,
    val used: Long? = null,
    val avail: Long? = null,
)

/** Response from vncproxy / termproxy. */
@Serializable
data class ConsoleProxyData(
    val port: String? = null,
    val ticket: String? = null,
    val user: String? = null,
    val cert: String? = null,
    val upid: String? = null,
)

/**
 * Everything needed to open the official Proxmox console UI in a WebView.
 * Uses real server tickets — same path the web UI uses.
 */
@Serializable
data class ConsoleSession(
    val pageUrl: String,
    val cookieHostUrl: String,
    val pveAuthCookie: String,
    val guestType: GuestType,
    val node: String,
    val vmid: Long,
    val name: String,
)

@Serializable
data class ConfigDisk(
    val key: String,
    val raw: String,
    val storage: String? = null,
    val volume: String? = null,
    val size: String? = null,
    val media: String? = null,
    val cache: String? = null,
    val iothread: Boolean = false,
    val discard: Boolean = false,
    val ssd: Boolean = false,
    val backup: Boolean? = null,
    val extra: Map<String, String> = emptyMap(),
) {
    val isCdrom: Boolean get() = media == "cdrom" || raw.contains("media=cdrom")
    val isEfi: Boolean get() = key.startsWith("efidisk")
    val isTpm: Boolean get() = key.startsWith("tpmstate")
    val label: String
        get() = when {
            isEfi -> "EFI Disk"
            isTpm -> "TPM State"
            isCdrom -> "CD/DVD"
            else -> "Hard Disk"
        }
}

@Serializable
data class ConfigNet(
    val key: String,
    val raw: String,
    val model: String? = null,
    val mac: String? = null,
    val bridge: String? = null,
    val tag: String? = null,
    val firewall: Boolean = false,
    val rate: String? = null,
    val extra: Map<String, String> = emptyMap(),
)

@Serializable
data class ConfigUsb(
    val key: String,
    val raw: String,
    /** host=vvvv:pppp or spice or mapped device */
    val host: String? = null,
    val usb3: Boolean = false,
    val resolvedName: String? = null,
) {
    val display: String
        get() = resolvedName?.let { "$it ($raw)" } ?: raw
}

@Serializable
data class ConfigPci(
    val key: String,
    val raw: String,
)

@Serializable
data class ParsedGuestConfig(
    val raw: Map<String, String>,
    val name: String? = null,
    val cores: String? = null,
    val sockets: String? = null,
    val cpu: String? = null,
    val memory: String? = null,
    val balloon: String? = null,
    val ostype: String? = null,
    val machine: String? = null,
    val bios: String? = null,
    val scsihw: String? = null,
    val boot: String? = null,
    val onboot: Boolean? = null,
    val agent: String? = null,
    val tags: String? = null,
    val description: String? = null,
    val numa: String? = null,
    val digest: String? = null,
    val disks: List<ConfigDisk> = emptyList(),
    val nets: List<ConfigNet> = emptyList(),
    val usbs: List<ConfigUsb> = emptyList(),
    val pcis: List<ConfigPci> = emptyList(),
    val other: List<Pair<String, String>> = emptyList(),
) {
    val vcpus: String?
        get() {
            val c = cores?.toIntOrNull()
            val s = sockets?.toIntOrNull()
            return when {
                c != null && s != null -> "${c * s} (${s}×$c)"
                c != null -> c.toString()
                else -> null
            }
        }
}
