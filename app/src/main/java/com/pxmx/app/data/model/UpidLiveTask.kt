package com.pxmx.app.data.model

/**
 * Pure Kotlin helper for Proxmox VE tasks eligible for Android Live Updates.
 *
 * UPID format: UPID:node:pid:pstart:starttime:type:id:user:
 * Example: UPID:alpha:00001234:00000000:66D1B000:vzdump:100:root@pam:
 */
data class UpidLiveTask(
    val upid: String,
    val node: String,
    val type: String,
    val id: String,
    val title: String,
) {
    val displayTitle: String get() = title

    companion object {
        val ELIGIBLE_TYPES: Set<String> = setOf("vzdump", "qmigrate", "vzmigrate")

        fun parse(upid: String?): UpidLiveTask? {
            if (upid.isNullOrBlank() || !upid.startsWith("UPID:")) return null
            val parts = upid.split(":")
            if (parts.size < 7) return null
            val node = parts.getOrNull(1)?.trim().orEmpty()
            val type = parts.getOrNull(5)?.trim()?.lowercase().orEmpty()
            val id = parts.getOrNull(6)?.trim().orEmpty()
            if (type.isBlank() || id.isBlank()) return null
            if (type !in ELIGIBLE_TYPES) return null

            val title = when (type) {
                "vzdump" -> "BACKUP $id"
                "qmigrate", "vzmigrate" -> "MIGRATE $id"
                else -> "${type.uppercase()} $id"
            }

            return UpidLiveTask(
                upid = upid,
                node = node,
                type = type,
                id = id,
                title = title,
            )
        }
    }
}
