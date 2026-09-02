package com.pxmx.app.data.repo

import com.pxmx.app.data.model.ClusterLogEntry
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory registry of recent user-initiated quick actions (~15s TTL).
 * Used by log strips to suppress local echo (the app already showed a toast).
 */
class RecentActionRegistry(
    private val ttlMs: Long = 15_000L,
) {
    data class RecordedAction(
        val taskType: String,
        val guestId: Long,
        val timestampMs: Long,
    )

    private val actions = ConcurrentHashMap<String, RecordedAction>()

    fun record(taskType: String, guestId: Long, nowMs: Long = System.currentTimeMillis()) {
        val normType = taskType.lowercase(Locale.US).trim()
        val key = "$normType:$guestId"
        actions[key] = RecordedAction(normType, guestId, nowMs)
    }

    /**
     * Checks if a log entry matches a recently recorded quick action from the session user.
     * Long jobs (aptupdate, vzdump, qmclone, deploy, backup) are NEVER suppressed.
     */
    fun isSuppressed(
        entry: ClusterLogEntry,
        sessionUser: String?,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (sessionUser.isNullOrBlank()) return false

        val tag = entry.tag?.lowercase(Locale.US).orEmpty()
        val msg = entry.msg?.lowercase(Locale.US).orEmpty()
        val id = entry.id?.lowercase(Locale.US).orEmpty()

        // 1. Long jobs are NEVER suppressed
        if (isLongJob(tag, msg, id)) return false

        // 2. Must match a quick action task type
        val matchedType = QUICK_TASK_TYPES.firstOrNull { type ->
            tag == type || tag.contains(type) || msg.contains(type) || id.contains(type)
        } ?: return false

        // 3. Must be initiated by the session user
        val entryUser = entry.user.orEmpty().lowercase(Locale.US)
        val sUser = sessionUser.lowercase(Locale.US)
        val userMatches = entryUser == sUser ||
            (entryUser.isNotEmpty() && entryUser.substringBefore('@') == sUser.substringBefore('@'))
        if (!userMatches) return false

        // 4. Extract guestId and check active TTL
        cleanExpired(nowMs)
        for ((_, act) in actions) {
            if (nowMs - act.timestampMs <= ttlMs) {
                if (taskTypesEquivalent(act.taskType, matchedType)) {
                    val vmidStr = act.guestId.toString()
                    if (msg.contains(vmidStr) || id.contains(vmidStr) || tag.contains(vmidStr)) {
                        return true
                    }
                }
            }
        }

        return false
    }

    fun cleanExpired(nowMs: Long = System.currentTimeMillis()) {
        actions.entries.removeIf { nowMs - it.value.timestampMs > ttlMs }
    }

    fun clear() {
        actions.clear()
    }

    companion object {
        val QUICK_TASK_TYPES = setOf(
            "qmstart", "qmstop", "qmshutdown", "qmreboot", "qmreset", "qmsuspend", "qmresume",
            "pctstart", "pctstop", "pctshutdown", "pctreboot", "pctsuspend", "pctresume",
            "qmset", "pctset",
        )

        private val LONG_JOB_TYPES = setOf(
            "aptupdate", "aptupgrade", "apt-update", "apt-upgrade",
            "vzdump", "qmclone", "pctclone", "qmrestore", "pctrestore",
            "deploy", "backup",
        )

        fun isLongJob(tag: String, msg: String, id: String): Boolean {
            return LONG_JOB_TYPES.any {
                tag.contains(it) || msg.contains(it) || id.contains(it)
            }
        }

        private fun taskTypesEquivalent(t1: String, t2: String): Boolean {
            if (t1 == t2) return true
            return t1.removePrefix("qm").removePrefix("pct") == t2.removePrefix("qm").removePrefix("pct")
        }
    }
}

/**
 * Picks the latest log entry for the log strip, applying echo suppression, guest scoping,
 * and updates screen context filtering.
 */
fun filterLatestLogForStrip(
    entries: List<ClusterLogEntry>,
    recentRegistry: RecentActionRegistry,
    sessionUser: String?,
    targetVmid: Long? = null,
    isUpdatesScreenActive: Boolean = false,
    nowMs: Long = System.currentTimeMillis(),
): ClusterLogEntry? {
    if (entries.isEmpty()) return null

    // 1. If targetVmid is specified (guest detail screen), scope to that VMID first
    val scopedEntries = if (targetVmid != null) {
        val vmidStr = targetVmid.toString()
        entries.filter { entry ->
            entry.msg?.contains(vmidStr) == true ||
                entry.id?.contains(vmidStr) == true ||
                entry.tag?.contains(vmidStr) == true
        }
    } else {
        entries
    }

    // 2. Filter out active apt/upgrade task streaming lines if Updates screen is active
    val candidateEntries = if (isUpdatesScreenActive) {
        scopedEntries.filter { entry ->
            val tag = entry.tag?.lowercase(Locale.US).orEmpty()
            tag != "apt-upgrade" && tag != "apt-update" && tag != "aptupgrade" && tag != "aptupdate"
        }
    } else {
        scopedEntries
    }

    if (candidateEntries.isEmpty()) return null

    // 3. Find newest non-suppressed line
    val nonSuppressed = candidateEntries.firstOrNull { entry ->
        !recentRegistry.isSuppressed(entry, sessionUser, nowMs)
    }

    // When newest line is suppressed, the strip shows the previous non-suppressed line (never blank)
    return nonSuppressed ?: candidateEntries.firstOrNull()
}
