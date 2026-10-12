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
        // 8ms: GOOD, floor fill (8/200 coerced to 0.05)
        assertEquals(PingBand.GOOD, HostPingMath.band(8L))
        assertEquals(0.05f, HostPingMath.fillFraction(8L), 0.0001f)

        // 12ms: GOOD, 12/200 = 0.06f, no longer floor or sharing 0.08 with 8ms
        assertEquals(PingBand.GOOD, HostPingMath.band(12L))
        assertEquals(0.06f, HostPingMath.fillFraction(12L), 0.0001f)
        assertTrue(HostPingMath.fillFraction(8L) != HostPingMath.fillFraction(12L))

        // Measured LAN latency checks: 18ms vs 41ms fractions differ; 18ms > 0.05
        assertEquals(PingBand.GOOD, HostPingMath.band(18L))
        assertEquals(0.09f, HostPingMath.fillFraction(18L), 0.0001f)
        assertTrue(HostPingMath.fillFraction(18L) > 0.05f)

        assertEquals(PingBand.GOOD, HostPingMath.band(24L))
        assertEquals(0.12f, HostPingMath.fillFraction(24L), 0.0001f)

        assertEquals(PingBand.GOOD, HostPingMath.band(28L))
        assertEquals(0.14f, HostPingMath.fillFraction(28L), 0.0001f)

        assertEquals(PingBand.FAIR, HostPingMath.band(41L))
        assertEquals(0.205f, HostPingMath.fillFraction(41L), 0.0001f)
        assertTrue(HostPingMath.fillFraction(18L) != HostPingMath.fillFraction(41L))

        // 80ms: SLOW, 80/200 = 0.40f
        assertEquals(PingBand.SLOW, HostPingMath.band(80L))
        assertEquals(0.40f, HostPingMath.fillFraction(80L), 0.0001f)

        // 90ms: SLOW, 90/200 = 0.45f
        assertEquals(PingBand.SLOW, HostPingMath.band(90L))
        assertEquals(0.45f, HostPingMath.fillFraction(90L), 0.001f)

        // 200ms: BAD, full plate
        assertEquals(PingBand.BAD, HostPingMath.band(200L))
        assertEquals(1f, HostPingMath.fillFraction(200L), 0.0001f)

        // 800ms: BAD, clamped full
        assertEquals(PingBand.BAD, HostPingMath.band(800L))
        assertEquals(1f, HostPingMath.fillFraction(800L), 0.0001f)

        // 1500ms / 3000ms: BAD, clamped full
        assertEquals(PingBand.BAD, HostPingMath.band(1500L))
        assertEquals(1f, HostPingMath.fillFraction(1500L), 0.0001f)
        assertEquals(PingBand.BAD, HostPingMath.band(3000L))
        assertEquals(1f, HostPingMath.fillFraction(3000L), 0.0001f)

        // Boundary cases
        assertEquals(PingBand.GOOD, HostPingMath.band(0L))
        assertEquals(0.05f, HostPingMath.fillFraction(0L), 0.0001f)
        assertEquals(PingBand.GOOD, HostPingMath.band(39L))
        assertEquals(PingBand.FAIR, HostPingMath.band(40L))
        assertEquals(PingBand.FAIR, HostPingMath.band(79L))
        assertEquals(PingBand.SLOW, HostPingMath.band(80L))
        assertEquals(PingBand.SLOW, HostPingMath.band(199L))
        assertEquals(PingBand.BAD, HostPingMath.band(200L))
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
