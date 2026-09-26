package com.pxmx.app.data.ssh

import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.model.ClusterStatusEntry
import com.pxmx.app.data.model.MapParse
import com.pxmx.app.data.model.NetworkIface
import com.pxmx.app.data.repo.PveException
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl

/**
 * Representation of a network address reported by a node (e.g. from GET nodes/{node}/network).
 */
data class NodeReportedAddress(
    val address: String? = null,
    val netmask: String? = null,
    val cidr: String? = null,
) {
    /**
     * The host part of this reported interface address (without CIDR prefix length or brackets).
     */
    val hostPart: String?
        get() {
            val raw = cidr?.substringBefore('/')?.takeIf { it.isNotBlank() }
                ?: address?.substringBefore('/')?.takeIf { it.isNotBlank() }
            return raw?.trim()?.removeSurrounding("[", "]")
        }

    /**
     * Checks if this interface's CIDR or address+netmask contains the given target IP.
     * Does NOT invent a /24 if neither CIDR prefix nor netmask is reported.
     */
    fun contains(targetIp: String): Boolean {
        val target = targetIp.trim().removeSurrounding("[", "]")
        val host = hostPart?.trim()?.removeSurrounding("[", "]") ?: return false

        val prefixStr = cidr?.substringAfter('/', "")?.takeIf { it.isNotBlank() }
            ?: if (address?.contains('/') == true) address.substringAfter('/') else null
            ?: netmask?.takeIf { it.isNotBlank() && !it.contains('.') }

        val netmaskDotted = netmask?.takeIf { it.isNotBlank() && it.contains('.') }

        val host4 = parseIpv4(host)
        val target4 = parseIpv4(target)
        if (host4 != null && target4 != null) {
            val mask4 = when {
                netmaskDotted != null -> parseIpv4(netmaskDotted) ?: return false
                prefixStr != null -> {
                    val p = prefixStr.toIntOrNull() ?: return false
                    if (p !in 1..32) return false
                    (0xFFFFFFFFL shl (32 - p)) and 0xFFFFFFFFL
                }
                else -> return false // Do not invent a /24
            }
            return (host4 and mask4) == (target4 and mask4)
        }

        val host6 = parseIpv6(host)
        val target6 = parseIpv6(target)
        if (host6 != null && target6 != null) {
            val p = prefixStr?.toIntOrNull() ?: return false
            if (p !in 1..128) return false
            val fullBytes = p / 8
            for (i in 0 until fullBytes) {
                if (host6[i] != target6[i]) return false
            }
            val rem = p % 8
            if (rem > 0) {
                val mask = (0xFF shl (8 - rem)) and 0xFF
                if ((host6[fullBytes].toInt() and mask) != (target6[fullBytes].toInt() and mask)) return false
            }
            return true
        }

        return false
    }

    companion object {
        fun fromNetworkIface(iface: NetworkIface): NodeReportedAddress =
            NodeReportedAddress(address = iface.address, netmask = iface.netmask, cidr = iface.cidr)

        fun fromMap(m: Map<String, Any>): List<NodeReportedAddress> {
            val list = mutableListOf<NodeReportedAddress>()
            val cidr4 = MapParse.str(m, "cidr")
            val addr4 = MapParse.str(m, "address")
            val mask4 = MapParse.str(m, "netmask")
            if (cidr4 != null || addr4 != null) {
                list.add(NodeReportedAddress(address = addr4, netmask = mask4, cidr = cidr4))
            }
            val cidr6 = MapParse.str(m, "cidr6")
            val addr6 = MapParse.str(m, "address6")
            val mask6 = MapParse.str(m, "netmask6")
            if (cidr6 != null || addr6 != null) {
                list.add(NodeReportedAddress(address = addr6, netmask = mask6, cidr = cidr6))
            }
            return list
        }
    }
}

/**
 * Strips scheme (http/https), path, and port from a host string.
 */
fun stripSchemeAndPort(rawHost: String): String {
    val clean = rawHost.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
    return if (clean.startsWith("[")) {
        clean.substringAfter("[").substringBefore("]")
    } else if (clean.count { it == ':' } == 1) {
        clean.substringBefore(':')
    } else {
        clean
    }
}

/**
 * Validates IPv4 syntax without DNS or network I/O.
 */
fun parseIpv4(ip: String): Long? {
    val trimmed = ip.trim().removeSurrounding("[", "]")
    val parts = trimmed.split('.')
    if (parts.size != 4) return null
    var res = 0L
    for (part in parts) {
        if (part.isEmpty() || (part.length > 1 && part.startsWith('0'))) return null
        val n = part.toLongOrNull() ?: return null
        if (n !in 0..255) return null
        res = (res shl 8) or n
    }
    return res
}

/**
 * Validates IPv6 syntax without DNS or network I/O.
 */
fun parseIpv6(ip: String): ByteArray? {
    val clean = ip.trim().removeSurrounding("[", "]")
    if (clean.count { it == ':' } < 2) return null
    return try {
        HttpUrl.Builder().scheme("https").host(clean).build()
        val addr = java.net.InetAddress.getByName(clean)
        if (addr is java.net.Inet6Address) addr.address else null
    } catch (_: Exception) {
        null
    }
}

/**
 * Checks whether a string is a valid IPv4 or IPv6 address literal without DNS lookups.
 */
fun isIpAddress(s: String): Boolean {
    val clean = s.trim().removeSurrounding("[", "]")
    if (parseIpv4(clean) != null) return true
    if (clean.count { it == ':' } >= 2) {
        return try {
            HttpUrl.Builder().scheme("https").host(clean).build()
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }
    return false
}

/**
 * Pure chooser for node SSH target address following the documented rules:
 *
 * 1. Local node (local == 1) and session host is non-blank and not demo:
 *    SSH to the session host.
 * 2. Any other node: keep the corosync IP, unless GET nodes/{node}/network reports
 *    an interface whose own CIDR (or address+netmask) contains the session host.
 *    Use that interface's host part. If several match, use one only when one of them equals
 *    the session host; otherwise keep corosync. If the network call fails or the session
 *    host is not an IP, keep corosync. Do not invent a /24.
 * 3. Never SSH a remote node at the login host just because login worked. Never accept
 *    an address that is not the corosync IP, the session host on the local node, or an
 *    address that node reported.
 * 4. Still fail when the node is missing or has no usable address (returns null).
 */
fun chooseNodeSshHost(
    corosyncIp: String?,
    isLocal: Boolean,
    sessionHost: String?,
    reportedAddresses: List<NodeReportedAddress> = emptyList(),
): String? {
    val cleanSession = sessionHost?.let(::stripSchemeAndPort)?.takeIf { it.isNotBlank() }
    val cleanCorosync = corosyncIp?.trim()?.removeSurrounding("[", "]")?.takeIf { it.isNotBlank() }

    // Rule 1: Local node (local == 1) and session host is non-blank and not demo:
    // SSH to the session host.
    if (isLocal && cleanSession != null && !cleanSession.equals("demo", ignoreCase = true)) {
        return cleanSession
    }

    // Rule 2 & 3: Any other node
    if (cleanSession != null && isIpAddress(cleanSession)) {
        val matching = reportedAddresses.filter { it.contains(cleanSession) }
        val matchingHosts = matching.mapNotNull { it.hostPart }
        if (matchingHosts.isNotEmpty()) {
            if (matchingHosts.size == 1) {
                return matchingHosts.first()
            }
            val exact = matchingHosts.firstOrNull { it.equals(cleanSession, ignoreCase = true) }
            if (exact != null) {
                return exact
            }
        }
    }

    // Keep corosync IP
    return cleanCorosync
}

/**
 * Resolves the SSH host for a cluster node.
 */
suspend fun resolveNodeSshHost(
    api: ProxmoxApi,
    node: String,
    sessionHost: String = "",
): String {
    val cleanSession = stripSchemeAndPort(sessionHost)
    val entries = api.clusterStatus().data ?: throw PveException("No cluster metadata for SSH target '$node'")
    val match = entries.map(ClusterStatusEntry::fromMap).filter { it.isNode && it.name == node }.singleOrNull()
        ?: throw PveException("Cannot uniquely resolve SSH target for node '$node'")

    val reportedAddresses = if (!match.isLocal && isIpAddress(cleanSession)) {
        try {
            val netResp = api.nodeNetwork(node)
            netResp.data.orEmpty().flatMap { NodeReportedAddress.fromMap(it) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    } else {
        emptyList()
    }

    val chosen = chooseNodeSshHost(
        corosyncIp = match.ip,
        isLocal = match.isLocal,
        sessionHost = cleanSession,
        reportedAddresses = reportedAddresses,
    ) ?: throw PveException("No SSH address reported for node '$node'")

    return try {
        // HttpUrl validates IPv4/IPv6 syntax without DNS or network I/O; host retains the whole IPv6 literal.
        HttpUrl.Builder().scheme("https").host(chosen.removeSurrounding("[", "]")).build().host
    } catch (e: IllegalArgumentException) {
        throw PveException("Invalid SSH address reported for node '$node'", e)
    }
}
