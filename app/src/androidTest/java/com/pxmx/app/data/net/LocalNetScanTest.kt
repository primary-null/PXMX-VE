package com.pxmx.app.data.net

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class LocalNetScanTest {

    @Test
    fun scanSubnet_sortsVerifiedAndUnverifiedCorrectly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionStore = SessionStore(context)

        val localNet = object : LocalNet(context, sessionStore) {
            override suspend fun checkPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
                return when (ip) {
                    "192.0.2.10" -> true // Verified
                    "192.0.2.20" -> true // PVE Detected
                    "192.0.2.30" -> true // Not PVE
                    "192.0.2.40" -> false // Unreachable
                    else -> false
                }
            }

            override suspend fun probePveVersion(
                host: String,
                port: Int,
                timeoutMs: Long
            ): ProbeResult {
                return when (host) {
                    "192.0.2.10" -> ProbeResult.Verified("8.3.0")
                    "192.0.2.20" -> ProbeResult.PveDetected
                    "192.0.2.30" -> ProbeResult.NotPve
                    else -> ProbeResult.Unreachable
                }
            }

            override fun generateSubnetIps(ip: String, prefixLength: Int): List<String> {
                return listOf("192.0.2.10", "192.0.2.20", "192.0.2.30", "192.0.2.40")
            }

            override fun isPrivateSubnet(ip: String): Boolean = true
        }

        val subnet = SubnetInfo("192.0.2.1", 24, "192.0.2.0/24")
        val progressList = localNet.scanSubnet(subnet).toList()
        val finalProgress = progressList.last()

        assertTrue(finalProgress.isFinished)
        assertEquals(1, finalProgress.verified.size)
        assertEquals("192.0.2.10", finalProgress.verified.first().ip)

        assertEquals(1, finalProgress.pveDetected.size)
        assertEquals("192.0.2.20", finalProgress.pveDetected.first().ip)

        // Only .30 (Not PVE) is in unverified because .40 (Closed) is skipped.
        assertEquals(1, finalProgress.unverified.size)
        assertTrue(finalProgress.unverified.contains("192.0.2.30"))
    }

    @Test
    fun scanSubnet_retriesProbeOnceBeforeFailing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionStore = SessionStore(context)

        val localNet = object : LocalNet(context, sessionStore) {
            val probeAttempts = ConcurrentHashMap<String, Int>()

            override suspend fun checkPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean = true

            override suspend fun probePveVersion(host: String, port: Int, timeoutMs: Long): ProbeResult {
                val attempts = (probeAttempts[host] ?: 0) + 1
                probeAttempts[host] = attempts
                
                return when (host) {
                    "192.0.2.10" -> if (attempts == 1) ProbeResult.Unreachable else ProbeResult.Verified("8.3.0")
                    "192.0.2.20" -> ProbeResult.Unreachable
                    else -> ProbeResult.NotPve
                }
            }

            override fun generateSubnetIps(ip: String, prefixLength: Int): List<String> {
                return listOf("192.0.2.10", "192.0.2.20")
            }

            override fun isPrivateSubnet(ip: String): Boolean = true
        }

        val subnet = SubnetInfo("192.0.2.1", 24, "192.0.2.0/24")
        val progressList = localNet.scanSubnet(subnet).toList()
        val finalProgress = progressList.last { it.isFinished }

        // .10 should succeed on second attempt (retry)
        assertEquals(1, finalProgress.verified.size)
        assertEquals("192.0.2.10", finalProgress.verified.first().ip)
        assertEquals(Integer.valueOf(2), localNet.probeAttempts["192.0.2.10"])

        // .20 should fail after 2 attempts (initial + 1 retry) and land in unverified
        assertEquals(1, finalProgress.unverified.size)
        assertEquals("192.0.2.20", finalProgress.unverified.first())
        assertEquals(Integer.valueOf(2), localNet.probeAttempts["192.0.2.20"])
    }
}
