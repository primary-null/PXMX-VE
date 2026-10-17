package com.pxmx.app.ui.watch

import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.WatchIds
import com.pxmx.app.data.notify.WatchNotice
import com.pxmx.app.ui.util.formatBytes
import com.pxmx.app.ui.util.formatPercent
import com.pxmx.app.ui.util.formatUptime

/**
 * Drawer copy from the fields already on a cluster resource.
 * Unknown fields are omitted. Storage returns null.
 */
fun watchNotice(resource: ClusterResource): WatchNotice? {
    if (WatchIds.of(resource) == null) return null
    val kind = when (resource.type) {
        "qemu" -> "VM"
        "lxc" -> "CT"
        "node" -> "NODE"
        else -> return null
    }
    val name = if (resource.type == "node") {
        resource.node?.takeIf { it.isNotBlank() } ?: resource.displayName
    } else {
        resource.displayName
    }
    val title = if (resource.type == "node") "WATCH · ${name.uppercase()}" else "WATCH · $name"
    val lines = ArrayList<String>(12)
    lines += buildString {
        append(kind)
        if (resource.type != "node") {
            resource.node?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        } else {
            append(" · ").append(name)
        }
        resource.vmid?.let { append(" · VMID ").append(it) }
    }
    resource.status?.takeIf { it.isNotBlank() }?.let { lines += "status $it" }
    resource.cpuPercent?.let { lines += "CPU ${formatPercent(it)}" }
    if (resource.mem != null || resource.maxmem != null) {
        lines += "MEM ${formatBytes(resource.mem)} / ${formatBytes(resource.maxmem)} (${formatPercent(resource.memPercent)})"
    }
    if (resource.disk != null || resource.maxdisk != null) {
        lines += "DISK ${formatBytes(resource.disk)} / ${formatBytes(resource.maxdisk)} (${formatPercent(resource.diskPercent)})"
    }
    resource.uptime?.takeIf { it > 0L }?.let { lines += "up ${formatUptime(it)}" }
    resource.tags?.takeIf { it.isNotBlank() }?.let { lines += "tags $it" }
    resource.hastate?.takeIf { it.isNotBlank() }?.let { lines += "HA $it" }
    resource.pool?.takeIf { it.isNotBlank() }?.let { lines += "pool $it" }
    resource.ostype?.takeIf { it.isNotBlank() }?.let { lines += "os $it" }
    resource.vcpuCount?.let { lines += "vcpu $it" }
    if (resource.onboot != null) lines += "onboot ${if (resource.onboot == 1) "yes" else "no"}"
    if (resource.template == 1) lines += "template"
    if (resource.netin != null || resource.netout != null) {
        lines += "net in ${formatBytes(resource.netin)} · out ${formatBytes(resource.netout)}"
    }
    val big = lines.joinToString("\n")
    val collapsed = lines.drop(1).take(3).joinToString(" · ").ifBlank { lines.first() }
    return WatchNotice(title = title, text = collapsed, bigText = big)
}
