package com.pxmx.app.data.ssh

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.OutputStream
import java.security.PublicKey
import java.security.MessageDigest
import android.util.Base64

class SftpDownloader(
    private val getStoredFingerprint: (String) -> String?,
    private val storeFingerprint: (String, String) -> Unit
) {

    suspend fun download(
        host: String,
        port: Int = 22,
        username: String,
        password: String,
        remotePath: String,
        localSink: OutputStream,
        onProgress: (Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val client = SSHClient()
        try {
            client.addHostKeyVerifier(object : HostKeyVerifier {
                override fun verify(h: String, p: Int, key: PublicKey): Boolean {
                    val fingerprint = "SHA256:" + Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(key.encoded), Base64.NO_WRAP)
                    val stored = getStoredFingerprint(host)
                    if (stored == null) {
                        storeFingerprint(host, fingerprint)
                        return true
                    }
                    if (stored == fingerprint) return true
                    throw RuntimeException("Host key changed — possible MITM attack detected!\nStored: $stored\nServer: $fingerprint")
                }

                override fun findExistingAlgorithms(h: String, p: Int): List<String> = emptyList()
            })

            client.connect(host, port)
            client.authPassword(username, password)
            
            client.newSFTPClient().use { sftp ->
                val attributes = sftp.stat(remotePath)
                val totalBytes = attributes.size
                
                sftp.open(remotePath).use { file ->
                    val buffer = ByteArray(256 * 1024)
                    var bytesDownloaded = 0L
                    var read: Int
                    var offset = 0L
                    
                    while (true) {
                        ensureActive()
                        read = file.read(offset, buffer, 0, buffer.size)
                        if (read == -1) break
                        
                        localSink.write(buffer, 0, read)
                        bytesDownloaded += read
                        offset += read
                        onProgress(bytesDownloaded, totalBytes)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        } finally {
            try {
                client.disconnect()
            } catch (_: Exception) {}
        }
    }
}
