package com.pxmx.app.data.repo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.model.BackupVolume
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.NodeStorageEntry
import com.pxmx.app.data.model.StorageContentItem
import com.pxmx.app.data.model.StorageDetail
import com.pxmx.app.data.model.StorageStatus
import com.pxmx.app.data.model.TaskStatus
import com.pxmx.app.data.session.SessionStore
import com.pxmx.app.data.ssh.SftpDownloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

/**
 * Handles Proxmox node storage, content queries, vzdump backup lifecycle,
 * and direct SFTP-to-MediaStore Android downloads.
 */
class StorageRepository(
    private val context: Context,
    private val sessionStore: SessionStore,
    private val pveClient: PveClient,
    private val taskStatusProvider: suspend (node: String, upid: String) -> Result<TaskStatus>,
) {

    suspend fun loadStorage(
        api: ProxmoxApi,
        nodeName: String,
    ): List<ClusterResource> {
        val rows = try {
            api.nodeStorage(nodeName).data.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        return rows.map { s ->
            val name = s.storage ?: "storage"
            ClusterResource(
                id = "storage/$nodeName/$name",
                type = "storage",
                node = nodeName,
                name = name,
                storage = name,
                plugintype = s.type,
                content = s.content,
                shared = s.shared,
                active = s.active,
                enabled = s.enabled,
                status = when {
                    s.active == 1 -> "available"
                    s.enabled == 0 -> "disabled"
                    else -> "unknown"
                },
                disk = s.used,
                maxdisk = s.total,
            )
        }
    }

    suspend fun storageDetail(
        node: String,
        storage: String,
        contentFilter: String? = null,
    ): Result<StorageDetail> {
        return pveClient.apiCall { api ->
            val status = runCatching { api.storageStatus(node, storage).data }.getOrNull()
                ?: StorageStatus(storage = storage)
            val content = runCatching {
                api.storageContent(node, storage, content = contentFilter).data.orEmpty()
            }.getOrDefault(emptyList())
            StorageDetail(
                node = node,
                storage = storage,
                status = status.copy(storage = status.storage ?: storage),
                content = content.sortedWith(
                    compareBy<StorageContentItem> { it.content ?: "" }
                        .thenByDescending { it.ctime ?: 0L }
                        .thenBy { it.volid ?: "" },
                ),
            )
        }
    }

    suspend fun deleteStorageVolume(node: String, volid: String): Result<String> =
        deleteBackup(node, volid)

    suspend fun loadBackupsForVmid(
        api: ProxmoxApi,
        node: String,
        vmid: Long,
        storages: List<NodeStorageEntry>,
    ): List<BackupVolume> {
        val out = mutableListOf<BackupVolume>()
        for (st in storages) {
            val name = st.storage ?: continue
            if (!(st.content ?: "").contains("backup")) continue
            val items = runCatching {
                api.storageContent(node, name, content = "backup", vmid = vmid).data.orEmpty()
            }.getOrElse {
                runCatching {
                    api.storageContent(node, name, content = "backup").data.orEmpty()
                        .filter { it.vmid == vmid || it.volid?.contains("-$vmid-") == true }
                }.getOrDefault(emptyList())
            }
            out += items
                .filter { it.vmid == null || it.vmid == vmid || it.volid?.contains("-$vmid-") == true }
                .map { it.toBackupVolume() }
        }
        return out.distinctBy { it.volid }
    }

    private fun StorageContentItem.toBackupVolume() = BackupVolume(
        volid = volid,
        content = content,
        format = format,
        size = size,
        ctime = ctime,
        vmid = vmid,
        notes = notes,
        subtype = subtype,
    )

    suspend fun createBackup(
        node: String,
        vmid: Long,
        storage: String,
        mode: String = "snapshot",
        compress: String = "zstd",
    ): Result<String> = pveClient.apiCall { api ->
        api.createBackup(
            node = node,
            vmid = vmid,
            storage = storage,
            mode = mode,
            compress = compress,
            notesTemplate = "{{guestname}}",
        ).data ?: throw PveException("Backup returned no UPID")
    }

    suspend fun deleteBackup(
        node: String,
        volid: String,
    ): Result<String> {
        val config = sessionStore.session.value?.config
        val response = pveClient.apiCall { api ->
            api.deleteStorageContent(node, volid.substringBefore(':'), volid).data ?: "OK"
        }
        val upid = response.getOrElse { return Result.failure(it) }
        if (!upid.startsWith("UPID:")) return response
        // Poll separately: renewing authentication must never replay the DELETE.
        return try {
            kotlinx.coroutines.withTimeoutOrNull(600_000) {
                while (true) {
                    val status = pveClient.apiCall { api ->
                        if (sessionStore.session.value?.config != config) throw PveException("Session changed during deletion")
                        api.taskStatus(node, upid).data ?: throw PveException("No deletion task status")
                    }.getOrThrow()
                    if (!status.isRunning) {
                        if (!status.isOk) throw PveException("Deletion task failed: ${status.exitstatus ?: "unknown exit status"}")
                        break
                    }
                    delay(1_000)
                }
                Unit
            } ?: throw PveException("Deletion task timed out: $upid; check task status before retrying")
            Result.success(upid)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun backupToDevice(
        node: String,
        type: String,
        vmid: Long,
        storage: String,
        onProgress: (String) -> Unit
    ): Result<String> {
        val profileId = sessionStore.lastProfileId()
        val profile = profileId?.let { sessionStore.getProfile(it) } ?: return Result.failure(PveException("No profile found"))
        val config = profile.toServerConfig(includeSecrets = true)

        return try {
            onProgress("Backing up on server...")
            val upid = createBackup(node, vmid, storage).getOrThrow()

            // Poll for completion (max 10 mins)
            val startTime = System.currentTimeMillis()
            var finished = false
            while (System.currentTimeMillis() - startTime < 600_000) {
                val status = taskStatusProvider(node, upid).getOrThrow()
                if (!status.isRunning) {
                    if (!status.isOk) throw PveException("Backup task failed: ${status.exitstatus}")
                    finished = true
                    break
                }
                delay(2000)
            }
            if (!finished) throw PveException("Backup timed out")

            onProgress("Locating backup volume...")
            // Find newest volume for this VM
            val resp = pveClient.apiCall { it.storageContent(node, storage, content = "backup", vmid = vmid) }.getOrThrow()
            val items = resp.data.orEmpty()
            val newest = items.maxByOrNull { it.ctime ?: 0L }
                ?: throw PveException("Could not find resulting backup volume")

            val volid = newest.volid ?: throw PveException("Volume missing ID")
            val volumeName = volid.substringAfter(':')

            // Fetch single volume metadata to get the 'path'
            val detailResp = pveClient.apiCall { it.storageVolume(node, storage, volumeName) }.getOrThrow()
            val remotePath = detailResp.data?.path
                ?: throw PveException("Server did not return file path. SFTP download requires the full path.")

            val rawFilename = volid.substringAfterLast('/')
            val filename = sanitizeBackupFilename(rawFilename)

            onProgress("Starting SFTP download...")
            val sftp = SftpDownloader(
                getStoredFingerprint = { host -> sessionStore.getHostKey(host) },
                storeFingerprint = { host, key -> sessionStore.saveHostKey(host, key) }
            )

            val downloadResult = runCatching {
                saveToDownloadsToStream(filename) { outputStream ->
                    sftp.download(
                        host = config.host,
                        port = 22,
                        username = config.username,
                        password = config.password,
                        remotePath = remotePath,
                        localSink = outputStream,
                        onProgress = { downloaded, total ->
                            val pct = if (total > 0) " (${(downloaded * 100 / total)}%)" else ""
                            onProgress("Downloading $filename$pct...")
                        }
                    )
                }
            }

            downloadResult.getOrElse { e ->
                throw PveException("Backup created on server but download failed: ${e.message}. The backup remains on the server.", e)
            }.map { filename }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private suspend fun saveToDownloadsToStream(filename: String, block: suspend (OutputStream) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val outputStream: OutputStream?
            val uri: Uri?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Proxmox")
                }
                uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IOException("Failed to create MediaStore entry")
                outputStream = context.contentResolver.openOutputStream(uri)
            } else {
                throw PveException("Saving backups to Downloads requires Android 10 or newer")
            }

            try {
                outputStream.use { out ->
                    if (out == null) throw IOException("Failed to open output stream")
                    block(out)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                uri?.let { context.contentResolver.delete(it, null, null) }
                throw e
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun sanitizeBackupFilename(name: String): String {
            val trimmed = name.trim()
            if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.contains('\\') || trimmed.contains("..")) {
                throw PveException("Invalid backup filename '$name': contains path separators or traversal sequences")
            }
            if (!trimmed.matches(Regex("^[A-Za-z0-9._-]+$"))) {
                throw PveException("Invalid backup filename '$name': contains disallowed characters (allowed: [A-Za-z0-9._-])")
            }
            return trimmed
        }
    }
}
