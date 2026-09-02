package com.pxmx.app.data.model

import com.pxmx.app.data.model.MapParse.flag
import com.pxmx.app.data.model.MapParse.int
import com.pxmx.app.data.model.MapParse.long
import com.pxmx.app.data.model.MapParse.str
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

// ---- Guest / storage bundles (repo return types) ----

@Serializable
data class GuestBundle(
    val status: GuestStatus?,
    val config: ParsedGuestConfig,
    val snapshots: List<SnapshotInfo>,
    val backups: List<BackupVolume>,
    val hostUsbs: List<HostUsbDevice>,
    val backupStorages: List<String>,
)

@Serializable
data class StorageDetail(
    val node: String,
    val storage: String,
    val status: StorageStatus,
    val content: List<StorageContentItem>,
)

// ---- Network / SDN ----

@Serializable
data class NodeNetworkSnapshot(
    val node: String,
    val interfaces: List<NetworkIface>,
)

@Serializable
data class NetworkIface(
    val iface: String?,
    val type: String?,
    val method: String?,
    val address: String?,
    val netmask: String?,
    val gateway: String?,
    val cidr: String?,
    val bridgePorts: String?,
    val active: Boolean,
    val autostart: Boolean,
    val comments: String?,
) {
    val title: String get() = iface ?: "?"
    val detailLine: String
        get() = buildString {
            type?.let { append(it) }
            method?.let { append(if (isEmpty()) it else " · $it") }
            val addr = cidr ?: listOfNotNull(address, netmask).joinToString("/")
            if (addr.isNotBlank()) append(if (isEmpty()) addr else " · $addr")
            gateway?.let { append(" · gw $it") }
            bridgePorts?.let { append(" · ports $it") }
        }

    companion object {
        fun fromMap(m: Map<String, Any>): NetworkIface = NetworkIface(
            iface = str(m, "iface"),
            type = str(m, "type"),
            method = str(m, "method", "method6"),
            address = str(m, "address", "address6"),
            netmask = str(m, "netmask", "netmask6"),
            gateway = str(m, "gateway", "gateway6"),
            cidr = str(m, "cidr", "cidr6"),
            bridgePorts = str(m, "bridge_ports", "slaves", "bond-slaves"),
            active = flag(m, "active"),
            autostart = flag(m, "autostart"),
            comments = str(m, "comments"),
        )
    }
}

@Serializable
data class SdnZoneInfo(
    val zone: String?,
    val type: String?,
    val ipam: String?,
    val dns: String?,
    val mtu: String?,
    val bridge: String? = null,
    val tag: String? = null,
    val peers: String? = null,
) {
    companion object {
        fun fromMap(m: Map<String, Any>): SdnZoneInfo = SdnZoneInfo(
            zone = str(m, "zone", "name"),
            type = str(m, "type"),
            ipam = str(m, "ipam"),
            dns = str(m, "dns"),
            mtu = str(m, "mtu"),
            bridge = str(m, "bridge"),
            tag = str(m, "tag"),
            peers = str(m, "peers", "peer"),
        )
    }
}

@Serializable
data class SdnVnetInfo(
    val vnet: String?,
    val zone: String?,
    val type: String?,
    val tag: String?,
    val vlanaware: Boolean,
    val alias: String?,
) {
    companion object {
        fun fromMap(m: Map<String, Any>): SdnVnetInfo = SdnVnetInfo(
            vnet = str(m, "vnet", "name"),
            zone = str(m, "zone"),
            type = str(m, "type"),
            tag = str(m, "tag"),
            vlanaware = flag(m, "vlanaware"),
            alias = str(m, "alias"),
        )
    }
}

// ---- Apt / updates ----

@Serializable
data class NodeUpdateSnapshot(
    val node: String,
    val updates: List<AptPackageUpdate>,
    val versions: List<AptPackageVersion>,
) {
    val updateCount: Int get() = updates.size
}

@Serializable
data class AptPackageUpdate(
    val packageName: String?,
    val title: String?,
    val description: String?,
    val priority: String?,
    val section: String?,
    val origin: String?,
    val oldVersion: String?,
    val version: String?,
    val arch: String?,
) {
    companion object {
        fun fromMap(m: Map<String, Any>): AptPackageUpdate = AptPackageUpdate(
            packageName = str(m, "Package", "package"),
            title = str(m, "Title", "title"),
            description = str(m, "Description", "description"),
            priority = str(m, "Priority", "priority"),
            section = str(m, "Section", "section"),
            origin = str(m, "Origin", "origin"),
            oldVersion = str(m, "OldVersion", "old-version"),
            version = str(m, "Version", "version"),
            arch = str(m, "Arch", "arch"),
        )
    }
}

@Serializable
data class AptPackageVersion(
    val packageName: String?,
    val version: String?,
    val oldVersion: String?,
    val currentState: String?,
    val runningVersion: String?,
) {
    companion object {
        fun fromMap(m: Map<String, Any>): AptPackageVersion = AptPackageVersion(
            packageName = str(m, "Package", "package"),
            version = str(m, "Version", "version"),
            oldVersion = str(m, "OldVersion"),
            currentState = str(m, "CurrentState", "current-state"),
            runningVersion = str(m, "RunningKernel", "RunningVersion"),
        )
    }
}

// ---- Node ops ----

@Serializable
data class NodeBundle(
    val node: String,
    val status: NodeStatus?,
    val services: List<NodeServiceInfo>,
    val tasks: List<NodeTaskInfo>,
)

@Serializable
data class NodeServiceInfo(
    val name: String?,
    val desc: String?,
    val state: String?,
    val unitState: String?,
    val activeState: String?,
) {
    val isActive: Boolean
        get() = state.equals("running", true) ||
            activeState.equals("active", true) ||
            unitState.equals("active", true)

    companion object {
        fun fromMap(m: Map<String, Any>): NodeServiceInfo = NodeServiceInfo(
            name = str(m, "name", "service"),
            desc = str(m, "desc", "description"),
            state = str(m, "state"),
            unitState = str(m, "unit-state", "unit_state"),
            activeState = str(m, "active-state", "active_state"),
        )
    }
}

@Serializable
data class NodeTaskInfo(
    val upid: String?,
    val type: String?,
    val status: String?,
    val user: String?,
    val starttime: Long?,
    val endtime: Long?,
    val id: String?,
) {
    val isRunning: Boolean get() = status.equals("running", true)

    companion object {
        fun fromMap(m: Map<String, Any>): NodeTaskInfo = NodeTaskInfo(
            upid = str(m, "upid"),
            type = str(m, "type"),
            status = str(m, "status"),
            user = str(m, "user"),
            starttime = long(m, "starttime"),
            endtime = long(m, "endtime"),
            id = str(m, "id"),
        )
    }
}

// ---- Firewall ----

@Serializable
data class FirewallSnapshot(
    val scope: String,
    val options: Map<String, @Contextual Any>,
    val rules: List<FirewallRule>,
    val aliases: List<FirewallAlias>,
) {
    val enabled: Boolean
        get() = when (val e = options["enable"] ?: options["enabled"]) {
            is Number -> e.toInt() != 0
            is Boolean -> e
            is String -> e == "1" || e.equals("true", ignoreCase = true)
            else -> false
        }
}

@Serializable
data class FirewallRule(
    val pos: Int?,
    val type: String?,
    val action: String?,
    val enable: Boolean,
    val source: String?,
    val dest: String?,
    val proto: String?,
    val dport: String?,
    val sport: String?,
    val comment: String?,
    val macro: String?,
    val iface: String?,
    val log: String? = null,
) {
    val hasLog: Boolean
        get() = log != null && !log.equals("nolog", ignoreCase = true) && !log.equals("none", ignoreCase = true)

    val summary: String
        get() = buildString {
            action?.let { append(it.uppercase()) }
            type?.let { append(if (isEmpty()) it else " · $it") }
            proto?.let { append(" · $it") }
            dport?.let { append(" :$it") }
            source?.let { append("  src $it") }
            dest?.let { append("  dst $it") }
            macro?.let { append("  [$it]") }
        }

    companion object {
        fun fromMap(m: Map<String, Any>): FirewallRule = FirewallRule(
            pos = int(m, "pos"),
            type = str(m, "type"),
            action = str(m, "action"),
            enable = flag(m, "enable", default = true),
            source = str(m, "source"),
            dest = str(m, "dest"),
            proto = str(m, "proto"),
            dport = str(m, "dport"),
            sport = str(m, "sport"),
            comment = str(m, "comment"),
            macro = str(m, "macro"),
            iface = str(m, "iface"),
            log = str(m, "log"),
        )
    }
}

@Serializable
data class FirewallAlias(
    val name: String?,
    val cidr: String?,
    val comment: String?,
) {
    companion object {
        fun fromMap(m: Map<String, Any>): FirewallAlias = FirewallAlias(
            name = str(m, "name"),
            cidr = str(m, "cidr"),
            comment = str(m, "comment"),
        )
    }
}

@Serializable
data class SdnStatusInfo(
    val name: String,
    val type: String?,
    val status: String?,
    val controller: String?,
) {
    val isOk: Boolean
        get() = status.equals("ok", ignoreCase = true) || status.equals("running", ignoreCase = true)

    companion object {
        fun fromMap(m: Map<String, Any>): SdnStatusInfo = SdnStatusInfo(
            name = str(m, "zone", "name", "id") ?: "unknown",
            type = str(m, "type"),
            status = str(m, "status", "state"),
            controller = str(m, "controller"),
        )
    }
}
