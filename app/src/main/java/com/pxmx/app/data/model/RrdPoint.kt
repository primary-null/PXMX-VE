package com.pxmx.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RrdPoint(
    val time: Long,
    val cpu: Double? = null,
    val mem: Long? = null,
    val maxmem: Long? = null,
    val netin: Long? = null,
    val netout: Long? = null,
    val disk: Long? = null,
    val maxdisk: Long? = null,
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): RrdPoint {
            val time = (map["time"] as? Number)?.toLong() ?: 0L
            val cpu = (map["cpu"] as? Number)?.toDouble()
            val mem = (map["mem"] as? Number)?.toLong()
            val maxmem = (map["maxmem"] as? Number)?.toLong()
            val netin = (map["netin"] as? Number)?.toLong()
            val netout = (map["netout"] as? Number)?.toLong()
            val disk = (map["disk"] as? Number)?.toLong()
            val maxdisk = (map["maxdisk"] as? Number)?.toLong()

            return RrdPoint(
                time = time,
                cpu = cpu,
                mem = mem,
                maxmem = maxmem,
                netin = netin,
                netout = netout,
                disk = disk,
                maxdisk = maxdisk,
            )
        }
    }
}
