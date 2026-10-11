package com.pxmx.app.data.repo

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pxmx.app.R
import com.pxmx.app.data.model.TaskStatus
import com.pxmx.app.data.model.UpidLiveTask
import com.pxmx.app.data.session.SessionStore
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface TaskNotificationPoster {
    fun postOngoing(id: Int, title: String, text: String)
    fun postCompleted(id: Int, title: String, text: String)
    fun cancel(id: Int)
}

class PlatformNotificationPoster(private val context: Context) : TaskNotificationPoster {
    private val notificationManager: NotificationManager? by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }

    override fun postOngoing(id: Int, title: String, text: String) {
        val nm = notificationManager ?: return
        ensureChannel(nm)

        if (Build.VERSION.SDK_INT >= 36) {
            val progressStyle = Notification.ProgressStyle()
                .setProgressIndeterminate(true)

            val builder = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_task)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setRequestPromotedOngoing(true)
                .setOnlyAlertOnce(true)
                .setColorized(false)
                .setStyle(progressStyle)

            nm.notify(id, builder.build())
        } else {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_task)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            nm.notify(id, builder.build())
        }
    }

    override fun postCompleted(id: Int, title: String, text: String) {
        val nm = notificationManager ?: return
        ensureChannel(nm)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        nm.notify(id, builder.build())
    }

    override fun cancel(id: Int) {
        notificationManager?.cancel(id)
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Tasks",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Proxmox task updates"
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "pve-tasks"
    }
}

class PveTaskLiveUpdate(
    private val context: Context,
    private val sessionStore: SessionStore,
    private val taskStatusProvider: suspend (node: String, upid: String) -> Result<TaskStatus>,
    private val scope: CoroutineScope,
    private val notificationPoster: TaskNotificationPoster = PlatformNotificationPoster(context),
    private val permissionChecker: (Context) -> Boolean = { ctx ->
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    },
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val completionDismissDelayMs: Long? = null,
) {
    private val activeWatches = ConcurrentHashMap<String, Job>()

    fun watchTask(upid: String): Job? {
        val liveTask = UpidLiveTask.parse(upid) ?: return null
        activeWatches[upid]?.let { return it }
        if (activeWatches.size >= MAX_IN_FLIGHT_WATCHES) {
            return null
        }

        val currentSession = sessionStore.session.value ?: return null
        val host = currentSession.config.host
        val hasPermission = permissionChecker(context)
        if (!shouldPost(sdkInt = sdkInt, hasPermission = hasPermission, host = host)) {
            return null
        }
        val snapshot = sessionStore.snapshot() ?: return null
        val notifId = liveTask.upid.hashCode()

        notificationPoster.postOngoing(
            id = notifId,
            title = liveTask.title,
            text = "In progress…",
        )

        val job = scope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val capMs = 600_000L
                val intervalMs = 2_000L

                while (System.currentTimeMillis() - startTime < capMs) {
                    delay(intervalMs)

                    if (!sessionStore.isCurrent(snapshot)) {
                        notificationPoster.cancel(notifId)
                        return@launch
                    }

                    val statusResult = taskStatusProvider(liveTask.node, liveTask.upid)
                    if (statusResult.isSuccess) {
                        val status = statusResult.getOrThrow()
                        if (!status.isRunning) {
                            if (status.isOk) {
                                notificationPoster.postCompleted(notifId, liveTask.title, "Completed")
                                if (completionDismissDelayMs != null && completionDismissDelayMs > 0) {
                                    delay(completionDismissDelayMs)
                                    notificationPoster.cancel(notifId)
                                }
                            } else {
                                val errText = status.exitstatus ?: "Failed"
                                notificationPoster.postCompleted(notifId, liveTask.title, errText)
                                if (completionDismissDelayMs != null && completionDismissDelayMs > 0) {
                                    delay(completionDismissDelayMs)
                                    notificationPoster.cancel(notifId)
                                }
                            }
                            return@launch
                        }
                    }
                }
                notificationPoster.cancel(notifId)
            } catch (e: CancellationException) {
                notificationPoster.cancel(notifId)
                throw e
            } catch (e: Exception) {
                notificationPoster.cancel(notifId)
            } finally {
                activeWatches.remove(upid)
            }
        }
        activeWatches[upid] = job
        return job
    }

    fun shouldPost(
        sdkInt: Int = this.sdkInt,
        hasPermission: Boolean = permissionChecker(context),
        host: String? = sessionStore.session.value?.config?.host,
    ): Boolean = Companion.shouldPost(sdkInt, hasPermission, host)

    companion object {
        const val CHANNEL_ID = "pve-tasks"
        const val MAX_IN_FLIGHT_WATCHES = 5

        fun isDemo(host: String?): Boolean {
            if (host.isNullOrBlank()) return true
            val trimmed = host.trim().lowercase()
            return trimmed == "demo" || trimmed.startsWith("demo.")
        }

        fun shouldPost(
            sdkInt: Int = Build.VERSION.SDK_INT,
            hasPermission: Boolean,
            host: String?,
        ): Boolean {
            if (sdkInt < 26) return false
            if (!hasPermission) return false
            if (isDemo(host)) return false
            return true
        }
    }
}
