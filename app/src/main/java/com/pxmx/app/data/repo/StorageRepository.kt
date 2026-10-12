package com.pxmx.app.data.repo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.model.BackupVolume
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.NodeStorageEntry
import com.pxmx.app.data.model.StorageContentItem
import com.pxmx.app.data.model.StorageDetail
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

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
    private val sftp: SftpDownloader = SftpDownloader(sessionStore::getHostKey, sessionStore::saveHostKey),
    private val downloadSink: (suspend (String, suspend (OutputStream) -> Unit) -> Result<Unit>)? = null,
    private val onLongTask: ((String) -> Unit)? = null,
) {

    suspend fun loadStorage(
        api: ProxmoxApi,
        nodeName: String,
    ): List<ClusterResource> {
        val rows = api.nodeStorage(nodeName).data.orEmpty()
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
            val status = api.storageStatus(node, storage).data
                ?: throw PveException("No status data for storage '$node/$storage'")
            val content = api.storageContent(node, storage, content = contentFilter).data
                ?: throw PveException("No content data for storage '$node/$storage'")
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

    suspend fun listNodeStorageNames(node: String): Result<List<String>> = pveClient.apiCall { api ->
        api.nodeStorage(node).data.orEmpty().mapNotNull { it.storage }
    }

    suspend fun listNodeStorageEntries(node: String): Result<List<NodeStorageEntry>> = pveClient.apiCall { api ->
        api.nodeStorage(node).data.orEmpty()
    }

    suspend fun listNodeVzTemplates(node: String): Result<List<StorageContentItem>> = pveClient.apiCall { api ->
        val storages = api.nodeStorage(node).data.orEmpty()
        val vzStorages = storages.filter {
            (it.content?.split(",")?.map { c -> c.trim() }?.contains("vztmpl") == true) &&
                (it.active == null || it.active == 1) &&
                (it.enabled == null || it.enabled == 1)
        }
        val templates = mutableListOf<StorageContentItem>()
        for (st in vzStorages) {
            val storageName = st.storage ?: continue
            try {
                val items = api.storageContent(node, storageName, content = "vztmpl").data.orEmpty()
                templates += items.filter { (it.content == "vztmpl" || it.volid?.contains("vztmpl") == true) && !it.volid.isNullOrBlank() }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // storage may be unreachable, continue
            }
        }
        templates.distinctBy { it.volid }
    }

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
            if (st.active != 1 || st.enabled == 0) continue
            val items = api.storageContent(node, name, content = "backup", vmid = vmid).data.orEmpty()
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
        onLongTask: ((String) -> Unit)? = null,
    ): Result<String> = pveClient.apiCall { api ->
        val upid = api.createBackup(
            node = node,
            vmid = vmid,
            storage = storage,
            mode = mode,
            compress = compress,
            notesTemplate = "{{guestname}}",
        ).data ?: throw PveException("Backup returned no UPID")
        try {
            (onLongTask ?: this.onLongTask)?.invoke(upid)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Live update callback must never fail the backup itself
        }
        upid
    }

    suspend fun deleteBackup(
        node: String,
        volid: String,
    ): Result<String> {
        val snapshot = sessionStore.snapshot() ?: return Result.failure(PveException("No active session"))
        val response = pveClient.apiCall(snapshot) { api ->
            api.deleteStorageContent(node, volid.substringBefore(':'), volid).data ?: "OK"
        }
        val upid = response.getOrElse { return Result.failure(it) }
        if (!upid.startsWith("UPID:")) return response
        // Poll separately: renewing authentication must never replay the DELETE.
        return try {
            kotlinx.coroutines.withTimeoutOrNull(600_000) {
                while (true) {
                    val status = pveClient.apiCall(snapshot) { api ->
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
        val snapshot = sessionStore.snapshot() ?: return Result.failure(PveException("No active session"))
        val profile = com.pxmx.app.data.ssh.SshCredentialPolicy.rootProfile(sessionStore, snapshot)
            ?: return Result.failure(PveException("SFTP requires the saved password of the exact active root PAM profile. Use server-side backups for other accounts."))
        fun checkSession() {
            if (!sessionStore.isCurrent(snapshot)) {
                throw PveException("Session changed during backup download")
            }
        }

        return try {
            pveClient.inSession(snapshot) {
            val sessionHost = com.pxmx.app.data.ssh.stripSchemeAndPort(snapshot.state.config.host)
            val targetHost = pveClient.apiCall { api ->
                checkSession()
                com.pxmx.app.data.ssh.resolveNodeSshHost(api, node, sessionHost)
            }.getOrThrow()
            onProgress("Backing up on server...")
            checkSession()
            val upid = createBackup(node, vmid, storage).getOrThrow()

            // Poll for completion (max 10 mins)
            val startTime = System.currentTimeMillis()
            var finished = false
            while (System.currentTimeMillis() - startTime < 600_000) {
                checkSession()
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
            val resp = pveClient.apiCall { checkSession(); it.storageContent(node, storage, content = "backup", vmid = vmid) }.getOrThrow()
            val items = resp.data.orEmpty()
            val newest = items.maxByOrNull { it.ctime ?: 0L }
                ?: throw PveException("Could not find resulting backup volume")

            val volid = newest.volid ?: throw PveException("Volume missing ID")
            val volumeName = volid.substringAfter(':')

            // Fetch single volume metadata to get the 'path'
            val detailResp = pveClient.apiCall { checkSession(); it.storageVolume(node, storage, volumeName) }.getOrThrow()
            val remotePath = detailResp.data?.path
                ?: throw PveException("Server did not return file path. SFTP download requires the full path.")

            val rawFilename = volid.substringAfterLast('/')
            val filename = sanitizeBackupFilename(rawFilename)

            onProgress("Starting SFTP download...")


            val downloadResult = attemptRead {
                (downloadSink ?: ::saveToDownloadsToStream)(filename) { outputStream ->
                    checkSession()
                    sftp.download(
                        host = targetHost,
                        port = 22,
                        username = "root",
                        password = profile.password,
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
            }
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
            e.rethrowAuthOrCancellation()
            Result.failure(e)
        }
    }

    suspend fun uploadIso(
        node: String,
        storage: String,
        uri: Uri?,
        displayName: String,
        onProgress: (String) -> Unit = {},
    ): Result<String> {
        val sanitizedFilename = try {
            sanitizeIsoFilename(displayName)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return Result.failure(e)
        }

        val snapshot = sessionStore.snapshot() ?: return Result.failure(PveException("No active session"))

        return try {
            pveClient.inSession(snapshot) {
                val status = pveClient.apiCall(snapshot) { api ->
                    api.storageStatus(node, storage).data
                }.getOrNull()
                val contentFromStatus = status?.content
                val hasIsoInStatus = contentFromStatus?.split(",")?.any { it.trim().equals("iso", ignoreCase = true) } == true
                val hasIso = if (hasIsoInStatus) {
                    true
                } else {
                    val entries = pveClient.apiCall(snapshot) { api ->
                        api.nodeStorage(node).data.orEmpty()
                    }.getOrNull().orEmpty()
                    val entry = entries.firstOrNull { it.storage == storage }
                    entry?.content?.split(",")?.any { it.trim().equals("iso", ignoreCase = true) } == true
                }

                if (!hasIso) {
                    throw PveException("Storage '$storage' on node '$node' does not allow ISO images")
                }

                val isDemo = snapshot.state.config.host.equals("demo", ignoreCase = true)

                var fileSize = -1L
                if (uri != null) {
                    try {
                        context.contentResolver?.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (idx != -1 && cursor.moveToFirst()) {
                                fileSize = cursor.getLong(idx)
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
                    if (fileSize <= 0L) {
                        try {
                            context.contentResolver?.openFileDescriptor(uri, "r")?.use { pfd ->
                                fileSize = pfd.statSize
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                        }
                    }
                }

                var lastReportedPct = -1
                val countingBody = CountingStreamRequestBody(
                    contentType = "application/octet-stream".toMediaTypeOrNull(),
                    contentLength = fileSize,
                    openStream = {
                        if (isDemo) {
                            val stream = if (uri != null) {
                                try {
                                    context.contentResolver?.openInputStream(uri)
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    null
                                }
                            } else null
                            stream ?: java.io.ByteArrayInputStream(ByteArray(0))
                        } else {
                            if (uri == null) throw IOException("URI is null")
                            val stream = try {
                                context.contentResolver?.openInputStream(uri)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                throw (e as? IOException) ?: IOException("Unable to open stream for $uri", e)
                            }
                            stream ?: throw IOException("Unable to open stream for $uri")
                        }
                    },
                    onProgress = { bytesWritten, totalBytes ->
                        if (totalBytes > 0L) {
                            val pct = ((bytesWritten.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(0, 100)
                            if (pct != lastReportedPct) {
                                lastReportedPct = pct
                                onProgress("Uploading $sanitizedFilename ($pct%)…")
                            }
                        } else {
                            onProgress("Uploading $sanitizedFilename…")
                        }
                    },
                )

                val contentPart = "iso".toRequestBody("text/plain".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("filename", sanitizedFilename, countingBody)

                onProgress("Uploading $sanitizedFilename (0%)…")
                val response = pveClient.apiCallForUpload(snapshot) { api ->
                    api.uploadStorageContent(
                        node = node,
                        storage = storage,
                        content = contentPart,
                        filename = filePart,
                    )
                }.getOrThrow()

                onProgress("Uploading $sanitizedFilename (100%)…")
                response.data?.takeIf { it.isNotBlank() } ?: "$storage:iso/$sanitizedFilename"
            }.let { Result.success(it) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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

        fun sanitizeIsoFilename(name: String): String {
            val trimmed = name.trim()
            if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.contains('\\') || trimmed.contains("..")) {
                throw PveException("Invalid ISO filename '$name': contains path separators or traversal sequences")
            }
            if (!trimmed.endsWith(".iso", ignoreCase = true)) {
                throw PveException("Invalid ISO filename '$name': missing required .iso extension")
            }
            val base = trimmed.substring(0, trimmed.length - 4)
            if (base.isEmpty()) {
                throw PveException("Invalid ISO filename '$name': filename base is empty")
            }
            if (!trimmed.matches(Regex("^[A-Za-z0-9._-]+$"))) {
                throw PveException("Invalid ISO filename '$name': contains disallowed characters (allowed: [A-Za-z0-9._-])")
            }
            return trimmed
        }
    }
}

private class CountingStreamRequestBody(
    private val contentType: okhttp3.MediaType?,
    private val contentLength: Long,
    private val openStream: () -> java.io.InputStream,
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit,
) : okhttp3.RequestBody() {
    override fun contentType(): okhttp3.MediaType? = contentType
    override fun contentLength(): Long = contentLength
    override fun isOneShot(): Boolean = true

    override fun writeTo(sink: okio.BufferedSink) {
        openStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var bytesWritten = 0L
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                sink.write(buffer, 0, bytesRead)
                bytesWritten += bytesRead
                onProgress(bytesWritten, contentLength)
            }
        }
    }
}
