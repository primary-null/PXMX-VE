package com.pxmx.app.data.ssh

import com.pxmx.app.data.repo.PveException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import net.schmizz.sshj.SSHClient
import kotlin.concurrent.thread
import kotlin.coroutines.resume

/** The deadline covers connect, auth, command/transfer, and output. Cancellation closes the socket,
 * not just the coroutine: SSHJ socket reads are not reliably interruptible. */
internal suspend fun <T> boundedSshOperation(
    client: SSHClient,
    timeoutMs: Long,
    block: (checkActive: () -> Unit) -> T,
): T {
    val result = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine<Result<T>> { continuation ->
            val worker = thread(start = false, isDaemon = true, name = "ssh-operation") {
                val outcome = try {
                    val checkActive = {
                        if (!continuation.isActive) throw CancellationException("SSH operation cancelled")
                    }
                    checkActive()
                    client.connectTimeout = minOf(15_000L, timeoutMs).toInt().coerceAtLeast(1)
                    client.timeout = minOf(15_000L, timeoutMs).toInt().coerceAtLeast(1)
                    Result.success(block(checkActive))
                } catch (e: Exception) {
                    Result.failure(e)
                } finally {
                    // Closing the socket first also bounds SSHJ's channel/client cleanup.
                    try { client.socket?.close() } catch (_: Exception) {}
                    try { client.disconnect() } catch (_: Exception) {}
                }
                continuation.resume(outcome)
            }
            continuation.invokeOnCancellation {
                try { client.socket?.close() } catch (_: Exception) {}
                worker.interrupt()
            }
            worker.start()
        }
    } ?: throw PveException("SSH operation timed out; check the node task before retrying")
    return result.getOrThrow()
}
