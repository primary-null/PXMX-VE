package com.pxmx.app.data.ops

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.pxmx.app.data.api.AppJson
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.ui.guest.GuestSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

fun sectionToClipId(section: GuestSection): String = when (section) {
    GuestSection.HARDWARE -> "HW"
    GuestSection.NETWORK -> "NET"
    GuestSection.FIREWALL -> "FW"
    GuestSection.SNAPSHOTS -> "SNAP"
    GuestSection.BACKUPS -> "BKP"
    GuestSection.OPTIONS -> "OPT"
    GuestSection.RAW -> "RAW"
}

fun clipIdToSection(clipId: String): GuestSection? = when (clipId.uppercase()) {
    "HW", "HARDWARE" -> GuestSection.HARDWARE
    "NET", "NETWORK" -> GuestSection.NETWORK
    "FW", "FIREWALL" -> GuestSection.FIREWALL
    "SNAP", "SNAPSHOTS" -> GuestSection.SNAPSHOTS
    "BKP", "BACKUPS" -> GuestSection.BACKUPS
    "OPT", "OPTIONS" -> GuestSection.OPTIONS
    "RAW" -> GuestSection.RAW
    else -> null
}

fun GuestSection.itemKey(): String = when (this) {
    GuestSection.HARDWARE -> "hardware"
    GuestSection.NETWORK -> "network"
    GuestSection.FIREWALL -> "firewall"
    GuestSection.SNAPSHOTS -> "snapshots"
    GuestSection.BACKUPS -> "backups"
    GuestSection.OPTIONS -> "options"
    GuestSection.RAW -> "raw"
}

@Serializable
data class OpsBoard(
    val clipOrder: List<String> = DEFAULT_CLIPS,
    val pinnedSections: List<String> = emptyList(),
    val includeConsole: Boolean = true,
    val includeHistory: Boolean = true,
) {
    companion object {
        val DEFAULT_CLIPS = listOf("VITALS", "FACTS", "CONSOLE", "HISTORY", "RAW")
        val DEFAULT = OpsBoard()
    }

    fun isPinned(section: GuestSection): Boolean {
        val id = sectionToClipId(section)
        return id in pinnedSections || section.name in pinnedSections
    }

    fun pinSection(section: GuestSection): OpsBoard {
        if (section == GuestSection.RAW) return this
        val id = sectionToClipId(section)
        if (isPinned(section)) return this
        if (pinnedSections.size >= 4) return this

        val newPinned = pinnedSections + id
        val newOrder = clipOrder.toMutableList()
        newOrder.remove(id)
        val lastPinnedIdx = newOrder.indexOfLast { it in pinnedSections || it == "VITALS" }
        val insertIdx = if (lastPinnedIdx >= 0) lastPinnedIdx + 1 else 1
        newOrder.add(insertIdx, id)
        return copy(clipOrder = newOrder, pinnedSections = newPinned)
    }

    fun unpinSection(section: GuestSection): OpsBoard {
        val id = sectionToClipId(section)
        val newPinned = pinnedSections.filter { it != id && it != section.name }
        val newOrder = clipOrder.filter { it != id && it != section.name }
        return copy(clipOrder = newOrder, pinnedSections = newPinned)
    }

    fun togglePin(section: GuestSection): OpsBoard {
        return if (isPinned(section)) unpinSection(section) else pinSection(section)
    }

    fun toggleConsole(include: Boolean): OpsBoard {
        val newOrder = clipOrder.toMutableList()
        if (!include) {
            newOrder.remove("CONSOLE")
        } else if ("CONSOLE" !in newOrder) {
            val histIdx = newOrder.indexOf("HISTORY")
            val rawIdx = newOrder.indexOf("RAW")
            val insertIdx = when {
                histIdx >= 0 -> histIdx
                rawIdx >= 0 -> rawIdx
                else -> newOrder.size
            }
            newOrder.add(insertIdx, "CONSOLE")
        }
        return copy(clipOrder = newOrder, includeConsole = include)
    }

    fun toggleHistory(include: Boolean): OpsBoard {
        val newOrder = clipOrder.toMutableList()
        if (!include) {
            newOrder.remove("HISTORY")
        } else if ("HISTORY" !in newOrder) {
            val rawIdx = newOrder.indexOf("RAW")
            val insertIdx = if (rawIdx >= 0) rawIdx else newOrder.size
            newOrder.add(insertIdx, "HISTORY")
        }
        return copy(clipOrder = newOrder, includeHistory = include)
    }

    fun reorderVisibleClip(clipId: String, direction: Int): OpsBoard {
        val norm = normalizeClipId(clipId)
        if (norm == "VITALS" || norm == "RAW" || direction == 0) return this

        val visible = computeAppliedClips(this, isTourActive = false).toMutableList()
        val idx = visible.indexOf(norm)
        if (idx < 0) return this

        val targetIdx = if (direction < 0) idx - 1 else idx + 1
        if (targetIdx <= 0 || targetIdx >= visible.size - 1) return this
        if (visible[targetIdx] == "VITALS" || visible[targetIdx] == "RAW") return this

        val neighbor = visible[targetIdx]
        visible[idx] = neighbor
        visible[targetIdx] = norm

        return copy(clipOrder = visible)
    }

    fun moveClipUp(clipId: String): OpsBoard = reorderVisibleClip(clipId, -1)

    fun moveClipDown(clipId: String): OpsBoard = reorderVisibleClip(clipId, 1)
}

fun normalizeClipId(clipId: String): String = when (clipId.uppercase()) {
    "HARDWARE", "HW" -> "HW"
    "NETWORK", "NET" -> "NET"
    "FIREWALL", "FW" -> "FW"
    "SNAPSHOTS", "SNAP" -> "SNAP"
    "BACKUPS", "BKP" -> "BKP"
    "OPTIONS", "OPT" -> "OPT"
    "CONSOLE" -> "CONSOLE"
    "HISTORY", "HISTORY-GRAPH" -> "HISTORY"
    "RAW" -> "RAW"
    "VITALS", "HERO" -> "VITALS"
    else -> clipId.uppercase()
}

fun isTourActiveNow(): Boolean = runCatching {
    com.pxmx.app.ui.tour.TourController.currentStep.value != null
}.getOrDefault(false)

fun computeAppliedClips(board: OpsBoard, isTourActive: Boolean = isTourActiveNow()): List<String> {
    if (isTourActive) {
        return listOf("VITALS", "HW", "NET", "FW", "SNAP", "BKP", "OPT", "CONSOLE", "HISTORY", "RAW")
    }
    val allFactClips = listOf("HW", "NET", "FW", "SNAP", "BKP", "OPT")

    val result = mutableListOf<String>()
    for (clip in board.clipOrder) {
        when (clip.uppercase()) {
            "VITALS" -> if ("VITALS" !in result) result.add("VITALS")
            "FACTS" -> {
                val unplaced = allFactClips.filter { it !in board.pinnedSections && it !in result }
                result.addAll(unplaced)
            }
            "CONSOLE" -> {
                if (board.includeConsole && "CONSOLE" !in result) result.add("CONSOLE")
            }
            "HISTORY" -> {
                if (board.includeHistory && "HISTORY" !in result) result.add("HISTORY")
            }
            "RAW" -> if ("RAW" !in result) result.add("RAW")
            else -> {
                val normalized = normalizeClipId(clip)
                if (normalized in allFactClips && normalized !in result) {
                    result.add(normalized)
                }
            }
        }
    }
    if ("VITALS" !in result) {
        result.add(0, "VITALS")
    } else if (result.indexOf("VITALS") != 0) {
        result.remove("VITALS")
        result.add(0, "VITALS")
    }
    for (clip in allFactClips) {
        if (clip !in result) {
            val rawIdx = result.indexOf("RAW")
            if (rawIdx >= 0) result.add(rawIdx, clip)
            else result.add(clip)
        }
    }
    result.remove("RAW")
    result.add("RAW")
    return result
}

fun computeAppliedKeys(board: OpsBoard, isTourActive: Boolean = isTourActiveNow()): List<String> {
    val clips = computeAppliedClips(board, isTourActive)
    return clips.mapNotNull { clip ->
        when (clip) {
            "VITALS" -> "hero"
            "HW" -> "hardware"
            "NET" -> "network"
            "FW" -> "firewall"
            "SNAP" -> "snapshots"
            "BKP" -> "backups"
            "OPT" -> "options"
            "CONSOLE" -> "console"
            "HISTORY" -> "history-graph"
            "RAW" -> "raw"
            else -> null
        }
    }
}

fun guestDetailLazyKeys(isEditingBoard: Boolean, appliedKeys: List<String>): List<String> =
    buildList {
        if (isEditingBoard) add("edit_board")
        addAll(appliedKeys)
    }

@Serializable
data class OpsBoardPayload(
    val profileDefaults: Map<String, OpsBoard> = emptyMap(),
    val guestOverrides: Map<String, OpsBoard> = emptyMap(),
    val overrideOrder: List<String> = emptyList(),
    val pinHintShown: Boolean = false,
) {
    fun resolveBoard(profileId: String, guestType: String, vmid: Long): OpsBoard {
        val key = "$profileId|$guestType|$vmid"
        return guestOverrides[key]
            ?: profileDefaults[profileId]
            ?: OpsBoard.DEFAULT
    }
}

object OpsBoardSerializer : Serializer<OpsBoardPayload> {
    override val defaultValue: OpsBoardPayload = OpsBoardPayload()

    override suspend fun readFrom(input: InputStream): OpsBoardPayload {
        return try {
            val text = input.readBytes().decodeToString()
            if (text.isBlank()) defaultValue
            else AppJson.decodeFromString(OpsBoardPayload.serializer(), text)
        } catch (e: CancellationException) {
            throw e
        } catch (_: SerializationException) {
            defaultValue
        } catch (_: IOException) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: OpsBoardPayload, output: OutputStream) {
        val text = AppJson.encodeToString(OpsBoardPayload.serializer(), t)
        output.write(text.toByteArray(Charsets.UTF_8))
        output.flush()
    }
}

interface OpsBoardStore {
    val data: Flow<OpsBoardPayload>

    suspend fun getBoard(profileId: String, guestType: GuestType, vmid: Long): OpsBoard
    suspend fun saveGuestBoard(profileId: String, guestType: GuestType, vmid: Long, board: OpsBoard)
    suspend fun clearGuestBoard(profileId: String, guestType: GuestType, vmid: Long)
    suspend fun setProfileDefault(profileId: String, board: OpsBoard)
    suspend fun setPinHintShown(shown: Boolean = true)
    suspend fun clear()
    suspend fun purge()

    companion object {
        const val FILE_NAME = "ops_board.json"

        fun guestKey(profileId: String, guestType: GuestType, vmid: Long): String =
            "$profileId|${guestType.name.lowercase()}|$vmid"

        fun create(context: Context?): OpsBoardStore {
            val filesDir = runCatching { context?.filesDir }.getOrNull()
            if (context == null || filesDir == null) {
                return InMemoryOpsBoardStore()
            }
            return RealOpsBoardStore(context)
        }

        fun purge(context: Context?) {
            RealOpsBoardStore.purgeFile(context)
        }
    }
}

class RealOpsBoardStore(private val context: Context) : OpsBoardStore {
    private val dataStore: DataStore<OpsBoardPayload> = DataStoreFactory.create(
        serializer = OpsBoardSerializer,
        produceFile = { File(context.filesDir, OpsBoardStore.FILE_NAME) },
    )

    override val data: Flow<OpsBoardPayload> = dataStore.data.catch { e ->
        if (e is CancellationException) throw e
        emit(OpsBoardPayload())
    }

    override suspend fun getBoard(profileId: String, guestType: GuestType, vmid: Long): OpsBoard {
        val payload = data.first()
        val key = OpsBoardStore.guestKey(profileId, guestType, vmid)
        return payload.guestOverrides[key]
            ?: payload.profileDefaults[profileId]
            ?: OpsBoard.DEFAULT
    }

    override suspend fun saveGuestBoard(profileId: String, guestType: GuestType, vmid: Long, board: OpsBoard) {
        val key = OpsBoardStore.guestKey(profileId, guestType, vmid)
        dataStore.updateData { current ->
            val newOverrides = current.guestOverrides.toMutableMap()
            newOverrides[key] = board

            val newOrder = current.overrideOrder.toMutableList()
            newOrder.remove(key)
            newOrder.add(key)

            while (newOverrides.size > 200 && newOrder.isNotEmpty()) {
                val oldestKey = newOrder.removeAt(0)
                newOverrides.remove(oldestKey)
            }

            current.copy(
                guestOverrides = newOverrides,
                overrideOrder = newOrder,
            )
        }
    }

    override suspend fun clearGuestBoard(profileId: String, guestType: GuestType, vmid: Long) {
        val key = OpsBoardStore.guestKey(profileId, guestType, vmid)
        dataStore.updateData { current ->
            val newOverrides = current.guestOverrides.toMutableMap()
            newOverrides.remove(key)
            val newOrder = current.overrideOrder.filter { it != key }
            current.copy(
                guestOverrides = newOverrides,
                overrideOrder = newOrder,
            )
        }
    }

    override suspend fun setProfileDefault(profileId: String, board: OpsBoard) {
        dataStore.updateData { current ->
            val newDefaults = current.profileDefaults.toMutableMap()
            newDefaults[profileId] = board
            current.copy(profileDefaults = newDefaults)
        }
    }

    override suspend fun setPinHintShown(shown: Boolean) {
        dataStore.updateData { current ->
            current.copy(pinHintShown = shown)
        }
    }

    override suspend fun clear() {
        dataStore.updateData { OpsBoardPayload() }
    }

    override suspend fun purge() {
        clear()
        purgeFile(context)
    }

    companion object {
        fun purgeFile(context: Context?) {
            runCatching {
                val dir = context?.filesDir ?: return@runCatching
                val file = File(dir, OpsBoardStore.FILE_NAME)
                if (file.exists()) file.delete()
            }
        }
    }
}

class InMemoryOpsBoardStore : OpsBoardStore {
    private val _data = MutableStateFlow(OpsBoardPayload())
    override val data: Flow<OpsBoardPayload> = _data.asStateFlow()

    override suspend fun getBoard(profileId: String, guestType: GuestType, vmid: Long): OpsBoard {
        val payload = _data.value
        val key = OpsBoardStore.guestKey(profileId, guestType, vmid)
        return payload.guestOverrides[key]
            ?: payload.profileDefaults[profileId]
            ?: OpsBoard.DEFAULT
    }

    override suspend fun saveGuestBoard(profileId: String, guestType: GuestType, vmid: Long, board: OpsBoard) {
        val key = OpsBoardStore.guestKey(profileId, guestType, vmid)
        _data.update { current ->
            val newOverrides = current.guestOverrides.toMutableMap()
            newOverrides[key] = board

            val newOrder = current.overrideOrder.toMutableList()
            newOrder.remove(key)
            newOrder.add(key)

            while (newOverrides.size > 200 && newOrder.isNotEmpty()) {
                val oldestKey = newOrder.removeAt(0)
                newOverrides.remove(oldestKey)
            }

            current.copy(
                guestOverrides = newOverrides,
                overrideOrder = newOrder,
            )
        }
    }

    override suspend fun clearGuestBoard(profileId: String, guestType: GuestType, vmid: Long) {
        val key = OpsBoardStore.guestKey(profileId, guestType, vmid)
        _data.update { current ->
            val newOverrides = current.guestOverrides.toMutableMap()
            newOverrides.remove(key)
            val newOrder = current.overrideOrder.filter { it != key }
            current.copy(
                guestOverrides = newOverrides,
                overrideOrder = newOrder,
            )
        }
    }

    override suspend fun setProfileDefault(profileId: String, board: OpsBoard) {
        _data.update { current ->
            val newDefaults = current.profileDefaults.toMutableMap()
            newDefaults[profileId] = board
            current.copy(profileDefaults = newDefaults)
        }
    }

    override suspend fun setPinHintShown(shown: Boolean) {
        _data.update { it.copy(pinHintShown = shown) }
    }

    override suspend fun clear() {
        _data.value = OpsBoardPayload()
    }

    override suspend fun purge() {
        clear()
    }
}
