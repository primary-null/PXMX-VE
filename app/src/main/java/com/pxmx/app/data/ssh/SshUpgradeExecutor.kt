package com.pxmx.app.data.ssh

import com.pxmx.app.data.repo.PveException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class SshUpgradeExecutor(
    private val getStoredFingerprint: (String) -> String?,
    private val storeFingerprint: (String, String) -> Unit,
) {

    companion object {
        fun computeFingerprint(key: PublicKey): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(key.encoded)
            return "SHA256:" + Base64.getEncoder().encodeToString(digest)
        }

        fun verifyHostKey(
            host: String,
            key: PublicKey,
            stored: String?,
            onStore: (String, String) -> Unit,
        ): Boolean {
            val fingerprint = computeFingerprint(key)
            if (stored == null) {
                onStore(host, fingerprint)
                return true
            }
            if (stored.trim() == fingerprint.trim() || stored.trimEnd('=') == fingerprint.trimEnd('=')) {
                return true
            }
            throw RuntimeException(
                "Host key changed — possible MITM attack detected!\nStored: $stored\nServer: $fingerprint"
            )
        }

        fun mapExitStatus(exitStatus: Int, tailLines: List<String>): Result<Int> {
            return if (exitStatus == 0) {
                Result.success(0)
            } else {
                val tail = tailLines.takeLast(3).joinToString("; ").trim()
                val detail = if (tail.isNotBlank()) {
                    "Upgrade command exited with code $exitStatus: $tail"
                } else {
                    "Upgrade command exited with code $exitStatus"
                }
                Result.failure(PveException(detail))
            }
        }
    }

    suspend fun executeUpgrade(
        host: String,
        port: Int = 22,
        username: String = "root",
        password: String,
        command: String = "apt-get update && apt-get full-upgrade -y",
        onOutputLine: (String) -> Unit = {},
    ): Result<Int> = withContext(Dispatchers.IO) {
        val client = SSHClient()
        val tailLines = ArrayDeque<String>(25)
        try {
            client.addHostKeyVerifier(object : HostKeyVerifier {
                override fun verify(h: String, p: Int, key: PublicKey): Boolean {
                    val stored = getStoredFingerprint(host)
                    return verifyHostKey(host, key, stored, storeFingerprint)
                }

                override fun findExistingAlgorithms(h: String, p: Int): List<String> = emptyList()
            })

            try {
                client.connect(host, port)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val msg = e.message ?: "Connection failed"
                if (msg.contains("Host key changed")) {
                    throw PveException(msg, e)
                }
                throw PveException("SSH connection failed to $host:$port: $msg", e)
            }

            try {
                client.authPassword(username, password)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e is UserAuthException || e.message?.contains("auth", ignoreCase = true) == true) {
                    throw PveException("SSH authentication failed for user $username. Ensure password authentication is enabled for root.", e)
                }
                throw PveException("SSH authentication error: ${e.message}", e)
            }

            val session = client.startSession()
            try {
                val cmd = session.exec(command)

                val readStream = { stream: InputStream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                        reader.forEachLine { line ->
                            val trimmed = line.trimEnd()
                            if (trimmed.isNotBlank()) {
                                synchronized(tailLines) {
                                    if (tailLines.size >= 25) tailLines.removeFirst()
                                    tailLines.addLast(trimmed)
                                }
                                onOutputLine(trimmed)
                            }
                        }
                    }
                }

                val stdoutThread = thread(name = "ssh-upgrade-stdout") {
                    try {
                        readStream(cmd.inputStream)
                    } catch (_: Exception) {}
                }

                val stderrThread = thread(name = "ssh-upgrade-stderr") {
                    try {
                        readStream(cmd.errorStream)
                    } catch (_: Exception) {}
                }

                stdoutThread.join()
                stderrThread.join()

                cmd.join(600, TimeUnit.SECONDS)
                val exitStatus = cmd.exitStatus ?: 0

                val capturedTail = synchronized(tailLines) { tailLines.toList() }
                mapExitStatus(exitStatus, capturedTail)
            } finally {
                try {
                    session.close()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(if (e is PveException) e else PveException(e.message ?: "SSH upgrade failed", e))
        } finally {
            try {
                client.disconnect()
            } catch (_: Exception) {}
        }
    }
}
