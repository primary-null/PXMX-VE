package com.pxmx.app.ui.components

import com.pxmx.app.data.model.RrdPoint

enum class HistoryRowId {
    CPU,
    MEM,
    NET,
    DISK_IO,
    ROOT,
    PSI,
}

fun cpuPercent(cpu: Double?): Double? {
    if (cpu == null) return null
    return if (cpu <= 1.0) cpu * 100.0 else cpu
}

fun psiPercent(psi: Double?): Double? {
    if (psi == null) return null
    return if (psi <= 1.0) psi * 100.0 else psi
}

fun memPercent(point: RrdPoint): Double? {
    val mem = point.mem ?: return null
    val max = point.maxmem ?: return null
    if (max <= 0L) return null
    return (mem.toDouble() / max.toDouble()) * 100.0
}

fun arcPercent(point: RrdPoint): Double? {
    val arc = point.arcsize ?: return null
    val max = point.maxmem ?: return null
    if (max <= 0L) return null
    return (arc.toDouble() / max.toDouble()) * 100.0
}

fun memhostPercent(point: RrdPoint): Double? {
    val host = point.memhost ?: return null
    val max = point.maxmem ?: return null
    if (max <= 0L) return null
    return (host.toDouble() / max.toDouble()) * 100.0
}

fun rootPercent(point: RrdPoint): Double? {
    val used = point.disk ?: return null
    val max = point.maxdisk ?: return null
    if (max <= 0L) return null
    return (used.toDouble() / max.toDouble()) * 100.0
}

fun historyRows(isGuest: Boolean, points: List<RrdPoint>): List<HistoryRowId> {
    if (points.isEmpty()) return emptyList()
    val rows = mutableListOf<HistoryRowId>()
    if (points.any { it.cpu != null || it.iowait != null }) rows.add(HistoryRowId.CPU)
    if (points.any { memPercent(it) != null }) rows.add(HistoryRowId.MEM)
    if (points.any { it.netin != null || it.netout != null }) rows.add(HistoryRowId.NET)
    if (isGuest && points.any { it.diskread != null || it.diskwrite != null }) {
        rows.add(HistoryRowId.DISK_IO)
    }
    if (!isGuest && points.any { rootPercent(it) != null }) {
        rows.add(HistoryRowId.ROOT)
    }
    if (points.any { it.pressurecpusome != null || it.pressurememorysome != null }) {
        rows.add(HistoryRowId.PSI)
    }
    return rows
}

fun lastPointWith(points: List<RrdPoint>, predicate: (RrdPoint) -> Boolean): RrdPoint? =
    points.lastOrNull(predicate)

fun rowHasValue(row: HistoryRowId, point: RrdPoint): Boolean = when (row) {
    HistoryRowId.CPU -> point.cpu != null || point.iowait != null
    HistoryRowId.MEM -> memPercent(point) != null
    HistoryRowId.NET -> point.netin != null || point.netout != null
    HistoryRowId.DISK_IO -> point.diskread != null || point.diskwrite != null
    HistoryRowId.ROOT -> rootPercent(point) != null
    HistoryRowId.PSI -> point.pressurecpusome != null || point.pressurememorysome != null
}
