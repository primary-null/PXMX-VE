package com.pxmx.app.data.repo

import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.config.GuestConfigParser
import com.pxmx.app.data.model.BackupVolume
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.GuestAction
import com.pxmx.app.data.model.GuestBundle
import com.pxmx.app.data.model.GuestStatus
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.data.model.HostUsbDevice
import com.pxmx.app.data.model.NodeStorageEntry
import com.pxmx.app.data.model.SnapshotInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles VM (QEMU) and Container (LXC) lifecycle, configurations, snapshots,
 * power actions, USB passthrough, and clone/deployment.
 */
class GuestRepository(
    private val pveClient: PveClient,
    private val recentActionRegistry: RecentActionRegistry,
    private val loadBackupsForVmid: suspend (ProxmoxApi, String, Long, List<NodeStorageEntry>) -> List<BackupVolume>,
) {
    private val guestConfigCache = ConcurrentHashMap<String, GuestConfigCacheEntry>()

    private data class GuestConfigCacheEntry(
        val ostype: String?,
        val onboot: Int?,
        val fetchedAtEpochMs: Long,
    )

    fun clearCache() {
        guestConfigCache.clear()
    }

    suspend fun loadGuests(
        api: ProxmoxApi,
        nodeName: String,
        guestType: String,
    ): List<ClusterResource> {
        val rows = when (guestType) {
            "qemu" -> api.nodeQemu(nodeName).data.orEmpty()
            "lxc" -> api.nodeLxc(nodeName).data.orEmpty()
            else -> emptyList()
        }
        return coroutineScope {
            rows.map { g ->
                async {
                    val vmid = g.vmid
                    val cacheKey = "$nodeName/$guestType/$vmid"
                    val now = System.currentTimeMillis()
                    val cached = if (vmid != null) guestConfigCache[cacheKey] else null

                    val extras = if (vmid != null) {
                        if (cached != null && now - cached.fetchedAtEpochMs < 60_000) {
                            cached.ostype to cached.onboot
                        } else {
                            try {
                                val cfg = api.guestConfig(nodeName, guestType, vmid).data.orEmpty()
                                val ostype = cfg["ostype"]?.let { pveScalar(it) }
                                val onboot = cfg["onboot"]?.let { pveScalar(it).toIntOrNull() }
                                guestConfigCache[cacheKey] = GuestConfigCacheEntry(ostype, onboot, now)
                                ostype to onboot
                            } catch (e: Exception) {
                                e.rethrowAuthOrCancellation()
                                cached?.ostype to cached?.onboot
                            }
                        }
                    } else {
                        null
                    }
                    g.copy(
                        id = g.id ?: "$guestType/${g.vmid}",
                        type = guestType,
                        node = nodeName,
                        maxcpu = g.maxcpu ?: g.cpus,
                        ostype = extras?.first ?: g.ostype,
                        onboot = extras?.second ?: g.onboot,
                    )
                }
            }.map { it.await() }
        }
    }

    private fun pveScalar(value: Any): String = when (value) {
        is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        is Float -> if (value % 1f == 0f) value.toLong().toString() else value.toString()
        else -> value.toString()
    }

    suspend fun guestStatus(node: String, guestType: GuestType, vmid: Long): Result<GuestStatus> =
        pveClient.apiCall { api ->
            api.guestStatus(node, guestType.path, vmid).data
                ?: throw PveException("No guest status")
        }

    suspend fun guestAction(
        node: String,
        guestType: GuestType,
        vmid: Long,
        action: GuestAction,
    ): Result<String> = pveClient.apiCall { api ->
        val taskType = when (guestType) {
            GuestType.QEMU -> when (action) {
                GuestAction.START -> "qmstart"
                GuestAction.STOP -> "qmstop"
                GuestAction.SHUTDOWN -> "qmshutdown"
                GuestAction.REBOOT -> "qmreboot"
                GuestAction.RESET -> "qmreset"
                GuestAction.SUSPEND -> "qmsuspend"
                GuestAction.RESUME -> "qmresume"
            }
            GuestType.LXC -> when (action) {
                GuestAction.START -> "pctstart"
                GuestAction.STOP -> "pctstop"
                GuestAction.SHUTDOWN -> "pctshutdown"
                GuestAction.REBOOT -> "pctreboot"
                GuestAction.RESET -> "pctstop"
                GuestAction.SUSPEND -> "pctsuspend"
                GuestAction.RESUME -> "pctresume"
            }
            GuestType.NODE -> "node"
        }
        recentActionRegistry.record(taskType, vmid)

        api.guestAction(node, guestType.path, vmid, action.apiName).data
            ?: throw PveException("Action returned no UPID")
    }

    suspend fun deployFromTemplate(
        source: ClusterResource,
        newId: Long,
        name: String,
    ): Result<String> = pveClient.apiCall { api ->
        val node = source.node ?: throw PveException("Template has no node")
        val type = source.type ?: throw PveException("Template has no type")
        val vmid = source.vmid ?: throw PveException("Template has no VMID")
        val isQemu = type == "qemu"
        api.cloneGuest(
            node = node,
            type = type,
            vmid = vmid,
            newid = newId,
            name = if (isQemu) name else null,
            hostname = if (!isQemu) name else null
        ).data ?: throw PveException("Clone returned no UPID")
    }

    /**
     * Full guest bundle: status + parsed config + snapshots + backups + host USB map.
     */
    suspend fun loadGuestBundle(
        node: String,
        guestType: GuestType,
        vmid: Long,
    ): Result<GuestBundle> {
        return pveClient.apiCall { api ->
            coroutineScope {
                val statusDef = async {
                    api.guestStatus(node, guestType.path, vmid).data
                }
                val configDef = async {
                    api.guestConfig(node, guestType.path, vmid).data.orEmpty()
                }
                // Catch inside each child so an auxiliary failure cannot cancel core reads.
                val snapsDef = async { attemptRead {
                    api.guestSnapshots(node, guestType.path, vmid).data.orEmpty()
                } }
                val usbDef = async { attemptRead {
                    if (guestType == GuestType.QEMU) api.nodeUsb(node).data.orEmpty() else emptyList()
                } }
                val storageDef = async { attemptRead {
                    api.nodeStorage(node).data.orEmpty()
                } }

                val status = statusDef.await()
                val rawConfig = configDef.await()
                val errors = linkedMapOf<String, String>()
                fun <T> section(name: String, result: Result<T>, fallback: T): T = result.getOrElse {
                    errors[name] = it.message ?: "Read failed"
                    fallback
                }
                val hostUsbs = section("USB", usbDef.await(), emptyList())
                val parsed = GuestConfigParser.parse(rawConfig, hostUsbs)
                val storages = section("backup storages", storageDef.await(), emptyList())
                val availableBackupStorages = storages.filter {
                    !it.storage.isNullOrBlank() &&
                        (it.content ?: "").contains("backup") &&
                        it.active == 1 &&
                        it.enabled != 0
                }
                val backups = if ("backup storages" in errors) {
                    errors["backups"] = "Storage discovery unavailable"
                    emptyList()
                } else availableBackupStorages.flatMap { storage ->
                    section("backups/${storage.storage}", attemptRead {
                        loadBackupsForVmid(api, node, vmid, listOf(storage))
                    }, emptyList())
                }.distinctBy { it.volid }
                val snapshots = section("snapshots", snapsDef.await(), emptyList())

                GuestBundle(
                    status = status,
                    config = parsed,
                    snapshots = snapshots
                        .sortedWith(compareBy<SnapshotInfo> { if (it.isCurrent) 0 else 1 }
                            .thenByDescending { it.snaptime ?: 0L }),
                    backups = backups.sortedByDescending { it.ctime ?: 0L },
                    hostUsbs = hostUsbs,
                    backupStorages = availableBackupStorages
                        .mapNotNull { it.storage },
                    sectionErrors = errors,
                )
            }
        }
    }

    /** Attach host USB (vid:pid) to next free usbN slot. */
    suspend fun attachUsb(
        node: String,
        guestType: GuestType,
        vmid: Long,
        hostId: String,
        usb3: Boolean = true,
    ): Result<String> = pveClient.apiCall { api ->
        if (guestType != GuestType.QEMU) throw PveException("USB passthrough is QEMU-only")
        repeat(3) { attempt ->
            val cfg = api.guestConfig(node, guestType.path, vmid, current = 0).data.orEmpty()
            val digest = cfg["digest"]?.toString()?.takeIf { it.isNotBlank() }
                ?: throw PveException("Cannot attach USB without a configuration digest; refresh and retry")
            val used = cfg.keys.filter { it.matches(Regex("^usb\\d+$")) }.toSet()
            val slot = (0..15).firstOrNull { "usb$it" !in used }
                ?: throw PveException("No free USB slots (usb0–usb15)")
            val value = buildString {
                append("host=").append(hostId.lowercase())
                if (usb3) append(",usb3=1")
            }
            try {
                return@apiCall api.updateGuestConfig(
                    node, guestType.path, vmid,
                    mapOf("usb$slot" to value, "digest" to digest),
                ).data ?: "OK"
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val code = when (e) {
                    is retrofit2.HttpException -> e.code()
                    is PveHttpException -> e.code
                    else -> null
                }
                val detail = if (e is retrofit2.HttpException) e.response()?.errorBody()?.string() else e.message
                val conflict = code == 409 || (code == 500 && detail?.contains("checksum mismatch", true) == true)
                if (!conflict || attempt == 2) throw e
            }
        }
        throw PveException("USB configuration changed repeatedly; refresh and retry")
    }

    /** Detach guest usbN (e.g. usb0). */
    suspend fun detachUsb(
        node: String,
        guestType: GuestType,
        vmid: Long,
        usbKey: String,
    ): Result<String> = pveClient.apiCall { api ->
        if (!usbKey.matches(Regex("^usb\\d+$"))) {
            throw PveException("Invalid USB key: $usbKey")
        }
        val resp = api.updateGuestConfig(
            node, guestType.path, vmid,
            mapOf("delete" to usbKey),
        )
        resp.data ?: "OK"
    }

    suspend fun listHostUsb(node: String): Result<List<HostUsbDevice>> = pveClient.apiCall { api ->
        api.nodeUsb(node).data.orEmpty()
    }

    /** Toggle start-at-boot on a guest (config write). */
    suspend fun setGuestOnboot(
        node: String,
        guestType: GuestType,
        vmid: Long,
        enabled: Boolean,
    ): Result<String> = pveClient.apiCall { api ->
        val taskType = if (guestType == GuestType.QEMU) "qmset" else "pctset"
        recentActionRegistry.record(taskType, vmid)

        val res = api.updateGuestConfig(
            node,
            guestType.path,
            vmid,
            mapOf("onboot" to if (enabled) "1" else "0"),
        ).data ?: "OK"

        // Update cache so UI doesn't flip back during next poll
        val cacheKey = "$node/${guestType.path}/$vmid"
        guestConfigCache[cacheKey]?.let {
            guestConfigCache[cacheKey] = it.copy(
                onboot = if (enabled) 1 else 0,
                fetchedAtEpochMs = System.currentTimeMillis()
            )
        }
        res
    }

    suspend fun createSnapshot(
        node: String,
        guestType: GuestType,
        vmid: Long,
        name: String,
        description: String? = null,
        includeRam: Boolean = false,
    ): Result<String> = pveClient.apiCall { api ->
        api.createSnapshot(
            node = node,
            type = guestType.path,
            vmid = vmid,
            snapname = name,
            description = description,
            vmstate = if (includeRam && guestType == GuestType.QEMU) 1 else null,
        ).data ?: throw PveException("Snapshot returned no UPID")
    }

    suspend fun deleteSnapshot(
        node: String,
        guestType: GuestType,
        vmid: Long,
        snap: String,
    ): Result<String> = pveClient.apiCall { api ->
        api.deleteSnapshot(node, guestType.path, vmid, snap).data
            ?: "OK"
    }

    suspend fun rollbackSnapshot(
        node: String,
        guestType: GuestType,
        vmid: Long,
        snap: String,
    ): Result<String> = pveClient.apiCall { api ->
        api.rollbackSnapshot(node, guestType.path, vmid, snap).data
            ?: throw PveException("Rollback returned no UPID")
    }
}
