package com.pxmx.app.data.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pxmx.app.R
import com.pxmx.app.data.model.WatchIds
import kotlinx.coroutines.CancellationException

data class WatchNotice(
    val title: String,
    val text: String,
    val bigText: String,
)

/**
 * Ongoing drawer rows for watched guests and nodes.
 * Own channel, low importance, so task alerts stay on `pve-tasks`.
 * A missing resource is left alone; only unwatch or logout removes the row.
 */
interface WatchPoster {
    fun upsert(id: String, notice: WatchNotice)
    fun retainOnly(ids: Set<String>)
}

class PlatformWatchPoster(private val context: Context) : WatchPoster {
    override fun upsert(id: String, notice: WatchNotice) {
        if (!WatchIds.isValid(id)) return
        try {
            val nm = notificationManager() ?: return
            ensureChannel(nm)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_watch)
                .setContentTitle(notice.title)
                .setContentText(notice.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notice.bigText))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            nm.notify(TAG, WatchIds.notificationId(id), notification)
            synchronized(postedLock) { postedIds.add(id) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Revoked permission, or a headless test context with no notification service.
        }
    }

    fun cancel(ids: Set<String>) {
        if (ids.isEmpty()) return
        synchronized(postedLock) { postedIds.removeAll(ids) }
        try {
            val nm = notificationManager() ?: return
            for (id in ids) {
                if (!WatchIds.isValid(id)) continue
                nm.cancel(TAG, WatchIds.notificationId(id))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No notification service in this process.
        }
    }

    override fun retainOnly(ids: Set<String>) {
        val drop = synchronized(postedLock) {
            val gone = postedIds.filter { it !in ids }
            if (gone.isNotEmpty()) postedIds.removeAll(gone.toSet())
            gone
        }
        if (drop.isEmpty()) return
        try {
            val nm = notificationManager() ?: return
            for (id in drop) {
                nm.cancel(TAG, WatchIds.notificationId(id))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Already gone, or no notification service in this process.
        }
    }

    private fun notificationManager(): NotificationManager? {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Watched",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Ongoing status for guests and nodes you are watching"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "pxmx-watch"
        const val TAG = "pxmx-watch"

        private val postedIds = mutableSetOf<String>()
        private val postedLock = Any()
    }
}
