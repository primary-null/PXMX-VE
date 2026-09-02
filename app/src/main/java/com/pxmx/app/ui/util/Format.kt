package com.pxmx.app.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "—"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var v = bytes.toDouble()
    var i = -1
    do {
        v /= 1024.0
        i++
    } while (v >= 1024 && i < units.lastIndex)
    return String.format(Locale.US, "%.1f %s", v, units[i])
}

fun formatUptime(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return "—"
    val d = TimeUnit.SECONDS.toDays(seconds)
    val h = TimeUnit.SECONDS.toHours(seconds) % 24
    val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
    return buildString {
        if (d > 0) append("${d}d ")
        if (d > 0 || h > 0) append("${h}h ")
        append("${m}m")
    }.trim()
}

fun formatPercent(value: Double?): String {
    if (value == null) return "—"
    return String.format(Locale.US, "%.1f%%", value)
}

fun formatEpoch(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return "—"
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    return fmt.format(Date(seconds * 1000))
}

/** Relative + absolute for “last login” / jump-back banners. */
fun formatLastLoginMs(epochMs: Long?): String {
    if (epochMs == null || epochMs <= 0) return "never"
    val ago = System.currentTimeMillis() - epochMs
    val relative = when {
        ago < 60_000 -> "just now"
        ago < 3_600_000 -> "${ago / 60_000}m ago"
        ago < 86_400_000 -> "${ago / 3_600_000}h ago"
        ago < 7 * 86_400_000L -> "${ago / 86_400_000}d ago"
        else -> null
    }
    val abs = SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(epochMs))
    return if (relative != null) "$relative · $abs" else abs
}

/** Proxmox memory config is MiB as string/number. */
fun formatMemoryMiB(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    val mib = value.toLongOrNull() ?: return value
    return formatBytes(mib * 1024L * 1024L)
}
