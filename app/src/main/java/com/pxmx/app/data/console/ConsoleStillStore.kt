package com.pxmx.app.data.console

import android.content.ComponentCallbacks2
import android.content.Context
import android.os.SystemClock
import com.pxmx.app.data.model.GuestType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ConsoleStill(
    val jpegBytes: ByteArray,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val elapsedRealtimeMs: Long = runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L),
    val width: Int = 0,
    val height: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ConsoleStill
        return jpegBytes.contentEquals(other.jpegBytes) &&
            timestampEpochMs == other.timestampEpochMs &&
            width == other.width &&
            height == other.height
    }

    override fun hashCode(): Int {
        var result = jpegBytes.contentHashCode()
        result = 31 * result + timestampEpochMs.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }

    fun ageFormatted(nowEpochMs: Long = System.currentTimeMillis()): String {
        val diffSeconds = Math.max(0L, (nowEpochMs - timestampEpochMs) / 1000L)
        return when {
            diffSeconds < 60 -> "${diffSeconds}S AGO"
            diffSeconds < 3600 -> "${diffSeconds / 60}M AGO"
            diffSeconds < 86400 -> "${diffSeconds / 3600}H AGO"
            else -> "${diffSeconds / 86400}D AGO"
        }
    }
}

interface ConsoleStillStore {
    val stills: StateFlow<Map<String, ConsoleStill>>

    fun getStill(profileId: String, guestType: GuestType, vmid: Long): ConsoleStill?
    fun putStill(profileId: String, guestType: GuestType, vmid: Long, still: ConsoleStill)
    fun clear()
    fun onTrimMemory(level: Int)

    companion object {
        const val MAX_ENTRIES = 8

        fun stillKey(profileId: String, guestType: GuestType, vmid: Long): String =
            "$profileId|${guestType.name.lowercase()}|$vmid"

        fun create(context: Context? = null): ConsoleStillStore = InMemoryConsoleStillStore()
    }
}

class InMemoryConsoleStillStore : ConsoleStillStore {
    private val _stills = MutableStateFlow<Map<String, ConsoleStill>>(emptyMap())
    override val stills: StateFlow<Map<String, ConsoleStill>> = _stills.asStateFlow()

    private val accessOrder = mutableListOf<String>()

    @Synchronized
    override fun getStill(profileId: String, guestType: GuestType, vmid: Long): ConsoleStill? {
        if (guestType != GuestType.QEMU) return null
        val key = ConsoleStillStore.stillKey(profileId, guestType, vmid)
        val still = _stills.value[key]
        if (still != null) {
            accessOrder.remove(key)
            accessOrder.add(key)
        }
        return still
    }

    @Synchronized
    override fun putStill(profileId: String, guestType: GuestType, vmid: Long, still: ConsoleStill) {
        if (guestType != GuestType.QEMU) return
        val key = ConsoleStillStore.stillKey(profileId, guestType, vmid)
        _stills.update { current ->
            val mutable = current.toMutableMap()
            mutable[key] = still
            accessOrder.remove(key)
            accessOrder.add(key)
            while (mutable.size > ConsoleStillStore.MAX_ENTRIES && accessOrder.isNotEmpty()) {
                val oldest = accessOrder.removeAt(0)
                mutable.remove(oldest)
            }
            mutable
        }
    }

    @Synchronized
    override fun clear() {
        accessOrder.clear()
        _stills.value = emptyMap()
    }

    @Synchronized
    override fun onTrimMemory(level: Int) {
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE
        ) {
            clear()
        }
    }
}
