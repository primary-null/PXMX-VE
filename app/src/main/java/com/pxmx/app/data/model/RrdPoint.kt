package com.pxmx.app.data.model

import kotlinx.serialization.Serializable

enum class HistoryTimeframe(val apiName: String) {
    HOUR("hour"),
    DAY("day"),
}

@Serializable
data class RrdPoint(
    val time: Long,
    val cpu: Double? = null,
    val iowait: Double? = null,
    val mem: Long? = null,
    val maxmem: Long? = null,
    val netin: Double? = null,
    val netout: Double? = null,
    val disk: Long? = null,
    val maxdisk: Long? = null,
    val diskread: Double? = null,
    val diskwrite: Double? = null,
) {
    fun hasMetrics(): Boolean =
        cpu != null ||
            iowait != null ||
            mem != null ||
            maxmem != null ||
            netin != null ||
            netout != null ||
            disk != null ||
            maxdisk != null ||
            diskread != null ||
            diskwrite != null

    companion object {
        fun fromMap(map: Map<String, Any?>): RrdPoint {
            return RrdPoint(
                time = number(map, "time")?.toLong() ?: 0L,
                cpu = number(map, "cpu"),
                iowait = number(map, "iowait"),
                mem = number(map, "mem", "memused")?.toLong(),
                maxmem = number(map, "maxmem", "memtotal")?.toLong(),
                netin = number(map, "netin"),
                netout = number(map, "netout"),
                disk = number(map, "disk", "rootused")?.toLong(),
                maxdisk = number(map, "maxdisk", "roottotal")?.toLong(),
                diskread = number(map, "diskread"),
                diskwrite = number(map, "diskwrite"),
            )
        }

        private fun number(map: Map<String, Any?>, vararg keys: String): Double? {
            for (key in keys) {
                val value = map[key] as? Number ?: continue
                return value.toDouble()
            }
            return null
        }
    }
}

fun List<RrdPoint>.withoutEmptySamples(): List<RrdPoint> = filter { it.hasMetrics() }
