package com.pxmx.app.data.ssh

import com.pxmx.app.data.repo.PveException
import kotlinx.coroutines.CancellationException


import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException



import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

open class SshUpgradeExecutor(
    private val getStoredFingerprint: (String) -> String?,
    private val storeFingerprint: (String, String) -> Unit,
    private val clientFactory: () -> SSHClient = ::SSHClient,
    private val timeoutMs: Long = 600_000,
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

    open suspend fun executeUpgrade(
        host: String,
        port: Int = 22,
        username: String = "root",
        password: String,
        command: String = "apt-get update && apt-get full-upgrade -y",
        onOutputLine: (String) -> Unit = {},
    ): Result<Int> = try {
        val client = clientFactory()
        boundedSshOperation(client, timeoutMs) { checkActive ->
            val tailLines = ArrayDeque<String>(25)
            client.addHostKeyVerifier(object : HostKeyVerifier {
                override fun verify(h: String, p: Int, key: PublicKey): Boolean =
                    verifyHostKey(host, key, getStoredFingerprint(host), storeFingerprint)
                override fun findExistingAlgorithms(h: String, p: Int): List<String> = emptyList()
            })
            checkActive()
            client.connect(host, port)
            checkActive()
            try {
                client.authPassword(username, password)
            } catch (e: UserAuthException) {
                throw PveException("SSH authentication failed for user $username. Ensure password authentication is enabled for root.", e)
            }
            checkActive()
            client.startSession().use { session ->
                checkActive()
                val cmd = session.exec(command)
                val readers = listOf(cmd.inputStream, cmd.errorStream).mapIndexed { index, stream ->
                    thread(isDaemon = true, name = "ssh-output-$index") {
                        try {
                            stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                                lines.forEach { line ->
                                    checkActive()
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
                        } catch (_: Exception) { /* socket closure wakes readers on cancellation */ }
                    }
                }
                try {
                    // Never wait for EOF before waiting for the command's bounded lifetime.
                    cmd.join(timeoutMs, TimeUnit.MILLISECONDS)
                    checkActive()
                    readers.forEach { it.join(1_000) }
                    checkActive()
                    if (readers.any { it.isAlive }) throw PveException("SSH output did not close before the deadline")
                    mapExitStatus(cmd.exitStatus ?: -1, synchronized(tailLines) { tailLines.toList() })
                } finally {
                    try { cmd.close() } catch (_: Exception) {}
                    readers.forEach { it.interrupt() }
                    readers.forEach { reader ->
                        try { reader.join(250) } catch (_: InterruptedException) {}
                    }
                }
            }
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(if (e is PveException) e else PveException(e.message ?: "SSH upgrade failed", e))
    }
}
