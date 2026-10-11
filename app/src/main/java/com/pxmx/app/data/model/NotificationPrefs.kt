package com.pxmx.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationPrefs(
    val masterEnabled: Boolean = true,
    val newGuest: Boolean = true,
    val backupFail: Boolean = true,
    val fencing: Boolean = true,
    val migrate: Boolean = true,
    val packageUpdates: Boolean = false,
    val externalStartStop: Boolean = false,
) {
    val activeTriggersCount: Int
        get() {
            var count = 0
            if (newGuest) count++
            if (backupFail) count++
            if (fencing) count++
            if (migrate) count++
            if (packageUpdates) count++
            if (externalStartStop) count++
            return count
        }
}
