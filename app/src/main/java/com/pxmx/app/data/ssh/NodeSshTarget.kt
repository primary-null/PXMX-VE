package com.pxmx.app.data.ssh

import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.model.ClusterStatusEntry
import com.pxmx.app.data.repo.PveException
import okhttp3.HttpUrl

/** Only authenticated cluster metadata may map a selected node to an SSH address. No entry-host fallback. */
suspend fun resolveNodeSshHost(api: ProxmoxApi, node: String): String {
    val entries = api.clusterStatus().data ?: throw PveException("No cluster metadata for SSH target '$node'")
    val match = entries.map(ClusterStatusEntry::fromMap).filter { it.isNode && it.name == node }.singleOrNull()
        ?: throw PveException("Cannot uniquely resolve SSH target for node '$node'")
    val ip = match.ip?.takeIf { it.isNotBlank() }
        ?: throw PveException("No SSH address reported for node '$node'")
    return try {
        // HttpUrl validates IPv4/IPv6 syntax without DNS or network I/O; host retains the whole IPv6 literal.
        HttpUrl.Builder().scheme("https").host(ip.removeSurrounding("[", "]")).build().host
    } catch (e: IllegalArgumentException) {
        throw PveException("Invalid SSH address reported for node '$node'", e)
    }
}
