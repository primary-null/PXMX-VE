package com.pxmx.app.data.repo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pxmx.app.R
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

open class ClusterTaskNotificationWatcher(
    private val sessionStore: SessionStore,
    private val recentActionRegistry: RecentActionRegistry,
    private val liveUpdate: PveTaskLiveUpdate,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val configFetcher: suspend (node: String, type: String, vmid: Long) -> Map<String, Any>? = { _, _, _ -> null },
    private val notificationPoster: (id: Int, title: String, text: String) -> Unit = { _, _, _ -> },
) {
    /** UPID -> last observed status. Status is kept so vzdump can alert on running→failed. */
    private val seenStatuses = ConcurrentHashMap<String, String>()
    private var isFirstPoll = true

    fun reset() {
        seenStatuses.clear()
        isFirstPoll = true
    }

    fun onClusterTasks(tasks: List<Map<String, Any>>) {
        if (tasks.isEmpty()) return

        val host = sessionStore.session.value?.config?.host
        if (PveTaskLiveUpdate.isDemo(host)) return

        val prefs = sessionStore.notificationPrefs.value
        if (!prefs.masterEnabled) {
            // Still track seen UPIDs so we don't alert on backlog if master is enabled later
            recordStatuses(tasks)
            return
        }

        if (isFirstPoll) {
            isFirstPoll = false
            recordStatuses(tasks)
            return
        }

        for (task in tasks) {
            val upid = task["upid"] as? String ?: continue
            val parts = upid.split(":")
            val node = (task["node"] as? String)?.takeIf { it.isNotBlank() } ?: parts.getOrNull(1).orEmpty()
            val rawType = (task["type"] as? String)?.takeIf { it.isNotBlank() } ?: parts.getOrNull(5).orEmpty()
            val type = rawType.lowercase(Locale.US).trim()
            val id = (task["id"]?.toString())?.takeIf { it.isNotBlank() } ?: parts.getOrNull(6).orEmpty()
            val status = (task["status"] as? String).orEmpty()
            val vmid = id.toLongOrNull()
            val previous = seenStatuses.put(upid, status)
            val isNew = previous == null

            // 1. Migrations -> Live Update
            if (type == "qmigrate" || type == "vzmigrate") {
                if (isNew && prefs.migrate) {
                    liveUpdate.watchTask(upid)
                }
                continue
            }

            // 2. New guest (qmcreate, vzcreate, qmclone, vzclone)
            if (type in NEW_GUEST_TYPES) {
                if (isNew && prefs.newGuest && vmid != null) {
                    val echoSuppressed = recentActionRegistry.hasRecentAction(type, vmid)
                    if (!echoSuppressed) {
                        val guestTypePath = if (type.startsWith("qm")) "qemu" else "lxc"
                        scope.launch {
                            try {
                                val config = configFetcher(node, guestTypePath, vmid)
                                val (name, source) = extractNameAndSource(config, vmid)
                                val body = if (!source.isNullOrBlank()) "$name · using $source" else name
                                val title = if (guestTypePath == "qemu") "New VM $vmid" else "New container $vmid"
                                notificationPoster(upid.hashCode(), title, body)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                notificationPoster(upid.hashCode(), "New guest $vmid", "Created on $node")
                            }
                        }
                    }
                }
                continue
            }

            // 3. Backup failure (vzdump with non-OK status, including running → failed)
            if (type == "vzdump") {
                if (prefs.backupFail && isVzdumpFailed(status) && (previous == null || !isVzdumpFailed(previous))) {
                    val title = if (id.isNotBlank()) "Backup failed ($id)" else "Backup failed"
                    val body = if (node.isNotBlank()) "Node $node · $status" else status
                    notificationPoster(upid.hashCode(), title, body)
                }
                continue
            }

            if (!isNew) continue

            // 4. Fencing events
            if (type.contains("fence")) {
                if (prefs.fencing) {
                    val title = "Node fence event"
                    val body = if (status.isNotBlank()) "Node $node · $type ($status)" else "Node $node · $type"
                    notificationPoster(upid.hashCode(), title, body)
                }
                continue
            }

            // 5. Package updates (if enabled)
            if (prefs.packageUpdates && type in PACKAGE_UPDATE_TYPES) {
                val title = "System updates ready"
                val body = "Node $node · $type ($status)"
                notificationPoster(upid.hashCode(), title, body)
                continue
            }

            // 6. External start/stop (if enabled and not initiated locally)
            if (prefs.externalStartStop && vmid != null && type in RecentActionRegistry.QUICK_TASK_TYPES) {
                if (!recentActionRegistry.hasRecentAction(type, vmid)) {
                    val title = "External guest event ($vmid)"
                    val body = "Node $node · $type ($status)"
                    notificationPoster(upid.hashCode(), title, body)
                }
                continue
            }
        }
    }

    private fun recordStatuses(tasks: List<Map<String, Any>>) {
        for (task in tasks) {
            val upid = task["upid"] as? String ?: continue
            seenStatuses[upid] = (task["status"] as? String).orEmpty()
        }
    }

    companion object {
        val NEW_GUEST_TYPES = setOf("qmcreate", "vzcreate", "qmclone", "vzclone")
        val PACKAGE_UPDATE_TYPES = setOf("aptupdate", "apt-update", "aptupgrade", "apt-upgrade")

        fun isVzdumpFailed(status: String): Boolean {
            if (status.isBlank()) return false
            return !status.equals("running", ignoreCase = true) &&
                !status.equals("OK", ignoreCase = true)
        }

        fun extractNameAndSource(config: Map<String, Any>?, vmid: Long): Pair<String, String?> {
            if (config == null) return "Guest $vmid" to null

            val name = (config["name"] as? String)?.takeIf { it.isNotBlank() }
                ?: (config["hostname"] as? String)?.takeIf { it.isNotBlank() }
                ?: "Guest $vmid"

            var source: String? = null

            // Check CDROM media or ISO
            for ((_, v) in config) {
                val s = v.toString()
                if (s.contains("media=cdrom", ignoreCase = true)) {
                    val vol = s.substringBefore(',').trim()
                    if (vol != "none" && vol.isNotBlank()) {
                        source = vol
                        break
                    }
                }
            }

            if (source == null) {
                val ide2 = (config["ide2"] as? String)?.substringBefore(',')?.trim()
                val sata0 = (config["sata0"] as? String)?.substringBefore(',')?.trim()
                if (!ide2.isNullOrBlank() && ide2 != "none" && (ide2.contains(".iso") || ide2.contains(":iso/"))) {
                    source = ide2
                } else if (!sata0.isNullOrBlank() && sata0 != "none" && (sata0.contains(".iso") || sata0.contains(":iso/"))) {
                    source = sata0
                }
            }

            if (source == null) {
                val ostemplate = (config["ostemplate"] as? String)?.takeIf { it.isNotBlank() }
                val ostype = (config["ostype"] as? String)?.takeIf { it.isNotBlank() }
                source = ostemplate ?: ostype
            }

            return name to source
        }

        fun createDefaultPoster(context: Context): (Int, String, String) -> Unit = { id, title, text ->
            try {
                val hasPermission = if (Build.VERSION.SDK_INT >= 33) {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

                if (hasPermission) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    if (nm != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val existing = nm.getNotificationChannel(PlatformNotificationPoster.CHANNEL_ID)
                            if (existing == null) {
                                val channel = NotificationChannel(
                                    PlatformNotificationPoster.CHANNEL_ID,
                                    "Tasks",
                                    NotificationManager.IMPORTANCE_DEFAULT,
                                ).apply {
                                    description = "Proxmox task updates"
                                }
                                nm.createNotificationChannel(channel)
                            }
                        }

                        val notification = NotificationCompat.Builder(context, PlatformNotificationPoster.CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_stat_task)
                            .setContentTitle(title)
                            .setContentText(text)
                            .setAutoCancel(true)
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .build()

                        nm.notify(id, notification)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: SecurityException) {
                // Fail soft if permission was revoked concurrently
            }
        }
    }
}


