package com.pxmx.app.data.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

enum class PingBand {
    GOOD,
    FAIR,
    SLOW,
    BAD,
}

object HostPingMath {
    const val OUTRAGEOUS_MS = 200L
    const val GOOD_MS = 40L
    const val FAIR_MS = 80L
    const val FILL_FLOOR = 0.05f

    fun fillFraction(latencyMs: Long): Float =
        (latencyMs / OUTRAGEOUS_MS.toFloat()).coerceIn(FILL_FLOOR, 1f)

    fun band(latencyMs: Long): PingBand = when {
        latencyMs < GOOD_MS -> PingBand.GOOD
        latencyMs < FAIR_MS -> PingBand.FAIR
        latencyMs < OUTRAGEOUS_MS -> PingBand.SLOW
        else -> PingBand.BAD
    }
}

suspend fun tcpPing(host: String, port: Int, timeoutMs: Int = 2000): Result<Long> =
    HostPing.default.tcpPing(host, port, timeoutMs)

open class HostPing {
    companion object {
        val default = HostPing()
    }

    open suspend fun tcpPing(host: String, port: Int, timeoutMs: Int = 2000): Result<Long> =
        withContext(Dispatchers.IO) {
            val socket = Socket()
            val startNs = System.nanoTime()
            try {
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                Result.success(elapsedMs.coerceAtLeast(0L))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            } finally {
                try {
                    socket.close()
                } catch (_: Throwable) {
                }
            }
        }
}
