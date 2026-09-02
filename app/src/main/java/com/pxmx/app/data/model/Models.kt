package com.pxmx.app.data.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Standard Proxmox JSON envelope. */
@Serializable
data class PveResponse<T>(
    val data: T? = null,
    val errors: Map<String, String>? = null,
    val message: String? = null,
)

@Serializable
data class TicketData(
    val ticket: String? = null,
    @SerialName("CSRFPreventionToken") val csrfPreventionToken: String? = null,
    val username: String? = null,
    val cap: Map<String, @Contextual Any>? = null,
    @SerialName("clustername") val clusterName: String? = null,
)

@Serializable
data class VersionInfo(
    val version: String? = null,
    val release: String? = null,
    val repoid: String? = null,
) {
    /** Major version number (8, 9, …) when parseable. */
    val major: Int?
        get() = version?.substringBefore('.')?.toIntOrNull()

    val display: String
        get() = listOfNotNull(version, release).joinToString("-").ifBlank { "unknown" }
}

/**
 * Whether this is a real multi-node cluster or a standalone node.
 * Proxmox still exposes cluster API paths on standalone installs —
 * that does not mean the UI should say "Cluster".
 */
@Serializable
data class SiteInfo(
    val isCluster: Boolean,
    /** Cluster name when clustered; otherwise null. */
    val clusterName: String? = null,
    /** Local / primary node name. */
    val nodeName: String? = null,
    val nodeCount: Int = 1,
) {
    /** Top bar title: cluster name, or the node name for standalone. */
    val title: String
        get() = when {
            isCluster && !clusterName.isNullOrBlank() -> clusterName
            isCluster -> "Cluster"
            !nodeName.isNullOrBlank() -> nodeName
            else -> "Proxmox"
        }

    val subtitleKind: String
        get() = if (isCluster) "Cluster" else "Standalone"
}

/**
 * Unified resource row for the home list.
 * Built from /cluster/resources and/or enriched node endpoints
 * (PVE 9.x cluster/resources can return sparse "unknown" status rows).
 *
 * type: node | qemu | lxc | storage | pool | sdn | …
 */
@Serializable
data class ClusterResource(
    val id: String? = null,
    val type: String? = null,
    val node: String? = null,
    val vmid: Long? = null,
    val name: String? = null,
    val status: String? = null,
    val uptime: Long? = null,
    val cpu: Double? = null,
    val maxcpu: Int? = null,
    /** Node QEMU/LXC list uses `cpus` for vCPU count. */
    val cpus: Int? = null,
    val mem: Long? = null,
    val maxmem: Long? = null,
    val disk: Long? = null,
    val maxdisk: Long? = null,
    val netin: Long? = null,
    val netout: Long? = null,
    val template: Int? = null,
    val tags: String? = null,
    /** From guest config (enriched); e.g. l26, win11. Not on list API alone. */
    val ostype: String? = null,
    /** From guest config: start at boot (0/1). */
    val onboot: Int? = null,
    val pool: String? = null,
    val storage: String? = null,
    val plugintype: String? = null,
    val content: String? = null,
    val shared: Int? = null,
    val level: String? = null,
    val hastate: String? = null,
    val active: Int? = null,
    val enabled: Int? = null,
) {
    val isGuest: Boolean get() = type == "qemu" || type == "lxc"
    val isRunning: Boolean get() = status == "running" || status == "online"

    /** True when guest is paused / suspended / frozen (not fully off, not fully on). */
    val isPausedOrSuspended: Boolean
        get() = when (status?.lowercase()) {
            "paused", "suspended", "frozen", "prelaunch" -> true
            else -> false
        }

    /**
     * Home-list grouping rank for guests (lower = higher on list).
     * Running → paused/suspended → templates → stopped → everything else.
     */
    val guestStatusRank: Int
        get() = when {
            !isGuest -> 50
            isRunning -> 0
            isPausedOrSuspended -> 1
            template == 1 -> 3
            status.equals("stopped", true) || status.equals("offline", true) -> 2
            else -> 4
        }

    /** Section label for status-grouped guest lists. */
    val guestStatusSection: String
        get() = when (guestStatusRank) {
            0 -> "RUNNING"
            1 -> "PAUSED / SUSPENDED"
            2 -> "STOPPED"
            3 -> "TEMPLATES"
            else -> "OTHER"
        }

    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: storage?.takeIf { it.isNotBlank() }
            ?: vmid?.let { "$type $it" }
            ?: id
            ?: type
            ?: "?"

    val cpuPercent: Double?
        get() = cpu?.let { it * 100.0 }

    val memPercent: Double?
        get() {
            val used = mem ?: return null
            val max = maxmem ?: return null
            if (max <= 0) return null
            // Balloon can report mem slightly above maxmem on PVE.
            return (used.toDouble() / max.toDouble() * 100.0).coerceAtMost(100.0)
        }

    val diskPercent: Double?
        get() {
            val used = disk ?: return null
            val max = maxdisk ?: return null
            if (max <= 0) return null
            return (used.toDouble() / max.toDouble() * 100.0).coerceAtMost(100.0)
        }

    /** Free bytes when capacity is known (storage / disk). */
    val freeBytes: Long?
        get() {
            val max = maxdisk ?: return null
            val used = disk ?: 0L
            return (max - used).coerceAtLeast(0L)
        }

    /**
     * Live utilization score for “most usage” sorts (0…200-ish).
     * Combines CPU% + RAM% (and disk% when present) so hot guests float up.
     */
    val usageScore: Double
        get() {
            val c = cpuPercent ?: 0.0
            val m = memPercent ?: 0.0
            val d = diskPercent ?: 0.0
            // Weight live CPU/RAM higher; disk fills slowly.
            return c + m + d * 0.25
        }

    val vcpuCount: Int? get() = maxcpu ?: cpus
}

/** GET /nodes/{node}/storage row (note: `type` here is plugin type, not resource type). */
@Serializable
data class NodeStorageEntry(
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

@Serializable
data class NodeStatus(
    val uptime: Long? = null,
    val cpu: Double? = null,
    @SerialName("wait") val ioWait: Double? = null,
    val memory: MemoryInfo? = null,
    val rootfs: DiskInfo? = null,
    val loadavg: List<String>? = null,
    val kversion: String? = null,
    val pveversion: String? = null,
)

@Serializable
data class MemoryInfo(
    val total: Long? = null,
    val used: Long? = null,
    val free: Long? = null,
)

@Serializable
data class DiskInfo(
    val total: Long? = null,
    val used: Long? = null,
    val free: Long? = null,
    val avail: Long? = null,
)

@Serializable
data class GuestStatus(
    val status: String? = null,
    val uptime: Long? = null,
    val cpu: Double? = null,
    val cpus: Int? = null,
    val mem: Long? = null,
    val maxmem: Long? = null,
    val disk: Long? = null,
    val maxdisk: Long? = null,
    val netin: Long? = null,
    val netout: Long? = null,
    val name: String? = null,
    val pid: Long? = null,
    val qmpstatus: String? = null,
    val ha: Map<String, @Contextual Any>? = null,
    val clipboard: String? = null,
    val lock: String? = null,
    val tags: String? = null,
    val template: Int? = null,
    val vmid: Long? = null,
)

@Serializable
data class TaskStatus(
    val status: String? = null,
    val exitstatus: String? = null,
    val type: String? = null,
    val id: String? = null,
    val user: String? = null,
    val node: String? = null,
    val pid: Long? = null,
    val pstart: Long? = null,
    val starttime: Long? = null,
    val upid: String? = null,
) {
    val isRunning: Boolean get() = status == "running"
    val isOk: Boolean get() = exitstatus == "OK"
}

@Serializable
enum class GuestType(val path: String, val label: String) {
    QEMU("qemu", "VM"),
    LXC("lxc", "CT"),
    NODE("node", "NODE");

    companion object {
        fun fromResourceType(type: String?): GuestType? = when (type) {
            "qemu" -> QEMU
            "lxc" -> LXC
            "node" -> NODE
            else -> null
        }
    }
}

@Serializable
enum class GuestAction(val apiName: String, val label: String) {
    START("start", "Start"),
    SHUTDOWN("shutdown", "Shutdown"),
    STOP("stop", "Stop"),
    REBOOT("reboot", "Reboot"),
    RESET("reset", "Reset"),
    SUSPEND("suspend", "Suspend"),
    RESUME("resume", "Resume"),
}

@Serializable
enum class AuthMode {
    PASSWORD,
    API_TOKEN,
}

@Serializable
data class ServerConfig(
    val host: String,
    val port: Int = 8006,
    val authMode: AuthMode = AuthMode.PASSWORD,
    val username: String = "",
    /** Password, or empty when using token. */
    val password: String = "",
    /** Full token form: USER@REALM!TOKENID=UUID */
    val apiToken: String = "",
    /** Opt-in only — disables cert/hostname verification (lab self-signed). */
    val trustSelfSigned: Boolean = false,
    val realm: String = "pam",
) {
    val baseUrl: String
        get() {
            val h = host.trim().removePrefix("https://").removePrefix("http://")
                .substringBefore('/')
                .substringBefore(':')
            return "https://$h:$port/api2/json/"
        }

    val displayHost: String
        get() {
            val h = host.trim().removePrefix("https://").removePrefix("http://")
                .substringBefore('/')
            return if (h.contains(":")) h else "$h:$port"
        }
}

@Serializable
data class SessionState(
    val config: ServerConfig,
    val ticket: String? = null,
    val csrf: String? = null,
    val username: String? = null,
    val version: VersionInfo? = null,
)

sealed interface LoginOutcome {
    data class Success(val session: SessionState) : LoginOutcome
    data class NeedsTfa(
        val partialTicket: String,
        val config: ServerConfig,
        val saveCredentials: Boolean = true,
        val profileId: String? = null,
        val enableAutoConnect: Boolean? = null,
        val label: String = "",
        val forceNewProfile: Boolean = false,
    ) : LoginOutcome
    data class Failed(val error: Throwable) : LoginOutcome
}

@Serializable
data class ServerProbe(
    val host: String,
    val version: String?,
    val running: Int,
    val stopped: Int,
    val guests: List<Pair<String, String>>
)

/**
 * Single entry from GET /cluster/log?max=N
 * Syslog priority: 0=emerg, 1=alert, 2=crit, 3=err, 4=warning, 5=notice, 6=info, 7=debug
 */
@Serializable
data class ClusterLogEntry(
    val time: Long? = null,
    val node: String? = null,
    val tag: String? = null,
    val msg: String? = null,
    val pri: Int? = null,
    val user: String? = null,
    val pid: Long? = null,
    val id: String? = null,
) {
    val stableKey: String
        get() = id ?: "${time ?: 0}_${node.orEmpty()}_${pid ?: 0}_${tag.orEmpty()}_${msg.orEmpty().hashCode()}"
}
