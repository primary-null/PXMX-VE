package com.pxmx.app.data.repo

import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.model.FirewallAlias
import com.pxmx.app.data.model.FirewallRule
import com.pxmx.app.data.model.FirewallSnapshot
import com.pxmx.app.data.model.SdnStatusInfo
import com.pxmx.app.data.model.SdnVnetInfo
import com.pxmx.app.data.model.SdnZoneInfo

/**
 * Handles Software Defined Networking (SDN) and Datacenter / Node Firewall rules and settings.
 */
class NetworkRepository(
    private val pveClient: PveClient,
    private val discoverNodeNames: suspend (ProxmoxApi) -> List<String>,
) {

    /** Optional SDN zones (may fail with 501 if SDN is not configured). */
    suspend fun listSdnZones(): Result<List<SdnZoneInfo>> = pveClient.apiCall { api ->
        api.sdnZones().data.orEmpty().map { SdnZoneInfo.fromMap(it) }
    }

    suspend fun listSdnVnets(): Result<List<SdnVnetInfo>> = pveClient.apiCall { api ->
        api.sdnVnets().data.orEmpty().map { SdnVnetInfo.fromMap(it) }
    }

    suspend fun createSdnZone(zone: String, type: String): Result<Unit> = pveClient.apiCall { api ->
        api.createSdnZone(zone, type)
    }

    suspend fun deleteSdnZone(zone: String): Result<Unit> = pveClient.apiCall { api ->
        api.deleteSdnZone(zone)
    }

    suspend fun createSdnVnet(vnet: String, zone: String, alias: String? = null): Result<Unit> = pveClient.apiCall { api ->
        api.createSdnVnet(vnet, zone, alias)
    }

    suspend fun deleteSdnVnet(vnet: String): Result<Unit> = pveClient.apiCall { api ->
        api.deleteSdnVnet(vnet)
    }

    suspend fun listSdnStatus(): Result<List<SdnStatusInfo>> = pveClient.apiCall { api ->
        val nodes = discoverNodeNames(api)
        val allStatuses = mutableListOf<SdnStatusInfo>()
        for (node in nodes) {
            val statusMap = runCatching { api.nodeSdnZones(node).data.orEmpty() }
                .getOrDefault(emptyList())
            allStatuses.addAll(statusMap.map { SdnStatusInfo.fromMap(it).copy(node = node) })
        }
        allStatuses
    }

    suspend fun applySdn(): Result<String> = pveClient.apiCall { api ->
        api.applySdn().data ?: "OK"
    }

    /** Datacenter firewall options + rules. */
    suspend fun loadClusterFirewall(): Result<FirewallSnapshot> = pveClient.apiCall { api ->
        val options = api.clusterFirewallOptions().data.orEmpty()
        val rules = api.clusterFirewallRules().data.orEmpty()
            .map { FirewallRule.fromMap(it) }
        val aliases = runCatching { api.clusterFirewallAliases().data.orEmpty() }
            .getOrDefault(emptyList())
            .map { FirewallAlias.fromMap(it) }
        FirewallSnapshot(scope = "cluster", options = options, rules = rules, aliases = aliases)
    }

    suspend fun setClusterFirewallEnable(
        enable: Boolean,
        digest: String? = null,
    ): Result<Unit> = pveClient.apiCall { api ->
        api.setClusterFirewallOptions(if (enable) 1 else 0, digest)
        Unit
    }

    suspend fun loadNodeFirewall(node: String): Result<FirewallSnapshot> = pveClient.apiCall { api ->
        val options = api.nodeFirewallOptions(node).data.orEmpty()
        val rules = runCatching { api.nodeFirewallRules(node).data.orEmpty() }
            .getOrDefault(emptyList())
            .map { FirewallRule.fromMap(it) }
        FirewallSnapshot(scope = "node/$node", options = options, rules = rules, aliases = emptyList())
    }

    suspend fun setNodeFirewallEnable(
        node: String,
        enable: Boolean,
        digest: String? = null,
    ): Result<Unit> = pveClient.apiCall { api ->
        api.setNodeFirewallOptions(node, if (enable) 1 else 0, digest)
        Unit
    }
}
