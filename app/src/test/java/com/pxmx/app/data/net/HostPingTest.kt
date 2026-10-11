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
        // 8ms: GOOD, floor fill (8/800 coerced to 0.08)
        assertEquals(PingBand.GOOD, HostPingMath.band(8L))
        assertEquals(0.08f, HostPingMath.fillFraction(8L), 0.0001f)

        // 12ms: GOOD, still at floor
        assertEquals(PingBand.GOOD, HostPingMath.band(12L))
        assertEquals(0.08f, HostPingMath.fillFraction(12L), 0.0001f)

        // 90ms: FAIR, 90/800
        assertEquals(PingBand.FAIR, HostPingMath.band(90L))
        assertEquals(90f / 800f, HostPingMath.fillFraction(90L), 0.001f)

        // 400ms: SLOW, half plate
        assertEquals(PingBand.SLOW, HostPingMath.band(400L))
        assertEquals(0.5f, HostPingMath.fillFraction(400L), 0.001f)

        // 800ms: BAD, full
        assertEquals(PingBand.BAD, HostPingMath.band(800L))
        assertEquals(1f, HostPingMath.fillFraction(800L), 0.0001f)

        // 1500ms: BAD, clamped full
        assertEquals(PingBand.BAD, HostPingMath.band(1500L))
        assertEquals(1f, HostPingMath.fillFraction(1500L), 0.0001f)

        // 3000ms: BAD, clamped full
        assertEquals(PingBand.BAD, HostPingMath.band(3000L))
        assertEquals(1f, HostPingMath.fillFraction(3000L), 0.0001f)

        // Boundary cases
        assertEquals(PingBand.GOOD, HostPingMath.band(0L))
        assertEquals(0.08f, HostPingMath.fillFraction(0L), 0.0001f)
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
