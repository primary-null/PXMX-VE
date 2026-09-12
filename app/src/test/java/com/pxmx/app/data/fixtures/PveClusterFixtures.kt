package com.pxmx.app.data.fixtures

import com.pxmx.app.data.model.ClusterLogEntry
import com.pxmx.app.data.model.FirewallRule
import com.pxmx.app.data.model.FirewallSnapshot
import com.pxmx.app.data.model.NetworkIface
import com.pxmx.app.data.model.NodeNetworkSnapshot
import com.pxmx.app.data.model.SdnStatusInfo
import com.pxmx.app.data.model.SdnVnetInfo
import com.pxmx.app.data.model.SdnZoneInfo

/**
 * Realistic test fixtures modeled after a live Proxmox VE 8.x cluster
 * (e.g. multi-node setup with ZFS, SDN, firewall, and bridges).
 */
object PveClusterFixtures {

    val liveClusterFirewallSnapshot = FirewallSnapshot(
        scope = "cluster",
        options = mapOf(
            "enable" to 1,
            "policy_in" to "DROP",
            "policy_out" to "ACCEPT",
            "digest" to "a1b2c3d4e5f67890",
        ),
        rules = listOf(
            FirewallRule.fromMap(
                mapOf(
                    "pos" to 0,
                    "type" to "in",
                    "action" to "ACCEPT",
                    "enable" to 1,
                    "proto" to "tcp",
                    "dport" to "8006",
                    "comment" to "PVE Web GUI & API",
                )
            ),
            FirewallRule.fromMap(
                mapOf(
                    "pos" to 1,
                    "type" to "in",
                    "action" to "ACCEPT",
                    "enable" to 1,
                    "proto" to "tcp",
                    "dport" to "22",
                    "comment" to "SSH cluster administration",
                )
            ),
            FirewallRule.fromMap(
                mapOf(
                    "pos" to 2,
                    "type" to "in",
                    "action" to "ACCEPT",
                    "enable" to 1,
                    "macro" to "PVEWebAdmin",
                    "comment" to "PVE Web Admin macro",
                )
            ),
            FirewallRule.fromMap(
                mapOf(
                    "pos" to 3,
                    "type" to "in",
                    "action" to "ACCEPT",
                    "enable" to 1,
                    "proto" to "tcp",
                    "dport" to "5900:5999",
                    "comment" to "VNC console range",
                )
            ),
            FirewallRule.fromMap(
                mapOf(
                    "pos" to 4,
                    "type" to "out",
                    "action" to "ACCEPT",
                    "enable" to 1,
                    "comment" to "Allow all outbound traffic",
                )
            ),
        ),
        aliases = emptyList(),
    )

    val liveNodeNetworkSnapshot = NodeNetworkSnapshot(
        node = "pve5",
        interfaces = listOf(
            NetworkIface.fromMap(
                mapOf(
                    "iface" to "eno1",
                    "type" to "eth",
                    "method" to "manual",
                    "active" to true,
                    "autostart" to true,
                )
            ),
            NetworkIface.fromMap(
                mapOf(
                    "iface" to "vmbr0",
                    "type" to "bridge",
                    "method" to "static",
                    "address" to "10.0.0.55",
                    "netmask" to "255.255.255.0",
                    "gateway" to "10.0.0.1",
                    "cidr" to "10.0.0.55/24",
                    "bridge_ports" to "eno1",
                    "active" to true,
                    "autostart" to true,
                    "comments" to "Management bridge",
                )
            ),
            NetworkIface.fromMap(
                mapOf(
                    "iface" to "vmbr1",
                    "type" to "bridge",
                    "method" to "static",
                    "address" to "192.168.100.1",
                    "netmask" to "255.255.255.0",
                    "cidr" to "192.168.100.1/24",
                    "active" to true,
                    "autostart" to true,
                    "comments" to "Internal private bridge",
                )
            ),
        ),
    )

    val liveSdnZones: List<SdnZoneInfo> = listOf(
        SdnZoneInfo.fromMap(mapOf("zone" to "localnet", "type" to "simple", "mtu" to "1500", "dns" to "10.0.0.1")),
        SdnZoneInfo.fromMap(mapOf("zone" to "vlan_dmz", "type" to "vlan", "bridge" to "vmbr0", "tag" to "100")),
        SdnZoneInfo.fromMap(mapOf("zone" to "vxlan_mesh", "type" to "vxlan", "peers" to "10.0.0.55,10.0.0.56")),
    )

    val liveSdnVnets: List<SdnVnetInfo> = listOf(
        SdnVnetInfo.fromMap(mapOf("vnet" to "vnet100", "zone" to "vlan_dmz", "tag" to "100", "alias" to "DMZ Segment")),
        SdnVnetInfo.fromMap(mapOf("vnet" to "vnet200", "zone" to "localnet", "alias" to "Isolated Lab")),
    )

    val liveSdnStatuses: List<SdnStatusInfo> = listOf(
        SdnStatusInfo.fromMap(mapOf("name" to "localnet", "type" to "zone", "status" to "ok")),
        SdnStatusInfo.fromMap(mapOf("name" to "vlan_dmz", "type" to "zone", "status" to "ok")),
        SdnStatusInfo.fromMap(mapOf("name" to "vxlan_mesh", "type" to "zone", "status" to "running")),
    )

    val liveClusterLogs = listOf(
        ClusterLogEntry(
            id = "pve5_1001",
            node = "pve5",
            time = 1715000000L,
            pri = 6,
            tag = "systemd",
            user = "root@pam",
            msg = "Started Proxmox VE replication runner",
        ),
        ClusterLogEntry(
            id = "pve5_1002",
            node = "pve5",
            time = 1715000060L,
            pri = 5,
            tag = "pve-firewall",
            user = "root@pam",
            msg = "rules updated: 5 rules loaded",
        ),
        ClusterLogEntry(
            id = "pve5_1003",
            node = "pve5",
            time = 1715000120L,
            pri = 6,
            tag = "pvedaemon",
            user = "root@pam",
            msg = "successful auth for user 'root@pam'",
        ),
    )
}
