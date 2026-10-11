package com.pxmx.app.data.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException

@OptIn(ExperimentalCoroutinesApi::class)
class HostPingTest {

    @Test
    fun hostPingMath_fillFraction_and_band_allRanges() {
        // 12ms: GOOD, full-ish
        assertEquals(PingBand.GOOD, HostPingMath.band(12L))
        val fill12 = HostPingMath.fillFraction(12L)
        assertEquals(1f - 12f / 2000f, fill12, 0.001f)
        assertTrue("12ms should be nearly full", fill12 > 0.99f)

        // 90ms: FAIR
        assertEquals(PingBand.FAIR, HostPingMath.band(90L))
        val fill90 = HostPingMath.fillFraction(90L)
        assertEquals(1f - 90f / 2000f, fill90, 0.001f)
        assertTrue("90ms should be > 0.95", fill90 > 0.95f)

        // 400ms: SLOW
        assertEquals(PingBand.SLOW, HostPingMath.band(400L))
        val fill400 = HostPingMath.fillFraction(400L)
        assertEquals(0.8f, fill400, 0.001f)

        // 1500ms: BAD
        assertEquals(PingBand.BAD, HostPingMath.band(1500L))
        val fill1500 = HostPingMath.fillFraction(1500L)
        assertEquals(0.25f, fill1500, 0.001f)

        // 3000ms: BAD with min fill 0.08
        assertEquals(PingBand.BAD, HostPingMath.band(3000L))
        val fill3000 = HostPingMath.fillFraction(3000L)
        assertEquals(0.08f, fill3000, 0.0001f)

        // Boundary cases
        assertEquals(PingBand.GOOD, HostPingMath.band(0L))
        assertEquals(1.0f, HostPingMath.fillFraction(0L), 0.0001f)
        assertEquals(PingBand.GOOD, HostPingMath.band(79L))
        assertEquals(PingBand.FAIR, HostPingMath.band(80L))
        assertEquals(PingBand.FAIR, HostPingMath.band(249L))
        assertEquals(PingBand.SLOW, HostPingMath.band(250L))
        assertEquals(PingBand.SLOW, HostPingMath.band(799L))
        assertEquals(PingBand.BAD, HostPingMath.band(800L))
    }

    @Test
    fun hostPing_openForSubclassing_and_stubbing() = runTest {
        val stub = object : HostPing() {
            override suspend fun tcpPing(host: String, port: Int, timeoutMs: Int): Result<Long> {
                return if (port == 8006) {
                    Result.success(42L)
                } else {
                    Result.failure(ConnectException("Connection refused"))
                }
            }
        }

        val success = stub.tcpPing("192.0.2.1", 8006)
        assertTrue(success.isSuccess)
        assertEquals(42L, success.getOrNull())

        val failure = stub.tcpPing("192.0.2.1", 9999)
        assertFalse(failure.isSuccess)
        assertTrue(failure.exceptionOrNull() is ConnectException)
    }
}
