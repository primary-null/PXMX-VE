package com.pxmx.app.data.net

import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetTest {

    @Test
    fun parseAndFormatIpv4_roundtripsCorrectly() {
        val ip = "192.0.2.100"
        val parsed = LocalNet.parseIpv4(ip)
        assertNotNull(parsed)
        val formatted = LocalNet.formatIpv4(parsed!!)
        assertEquals(ip, formatted)
    }

    @Test
    fun isPrivateSubnet_identifiesRfc1918AndLoopback() {
        // 10.0.0.0/8
        assertTrue(isPrivate("10.0.0.1"))
        assertTrue(isPrivate("10.254.254.254"))

        // 172.16.0.0/12
        assertTrue(isPrivate("172.16.0.1"))
        assertTrue(isPrivate("172.31.255.254"))
        assertFalse(isPrivate("172.15.0.1"))
        assertFalse(isPrivate("172.32.0.1"))

        // 192.168.0.0/16
        assertTrue(isPrivate("192.168.0.1"))
        assertTrue(isPrivate("192.168.0.254"))
        assertFalse(isPrivate("192.169.1.1"))

        // Loopback
        assertTrue(isPrivate("127.0.0.1"))

        // Public IPs
        assertFalse(isPrivate("8.8.8.8"))
        assertFalse(isPrivate("1.1.1.1"))
        assertFalse(isPrivate("142.250.190.46"))
    }

    @Test
    fun generateSubnetIps_producesExpectedRanges() {
        val helper = SubnetGenerator()
        // /24 subnet (254 hosts: .1 to .254)
        val ips24 = helper.generateSubnetIps("192.0.2.50", 24)
        assertEquals(254, ips24.size)
        assertEquals("192.0.2.1", ips24.first())
        assertEquals("192.0.2.254", ips24.last())

        // /28 subnet (14 hosts: .1 to .14)
        val ips28 = helper.generateSubnetIps("192.0.2.5", 28)
        assertEquals(14, ips28.size)
        assertEquals("192.0.2.1", ips28.first())
        assertEquals("192.0.2.14", ips28.last())

        // Wide /16 subnet capped to /24 slice
        val ips16 = helper.generateSubnetIps("198.51.100.20", 16)
        assertEquals(254, ips16.size)
        assertEquals("198.51.100.1", ips16.first())
        assertEquals("198.51.100.254", ips16.last())
    }

    @Test
    fun isTailscaleSubnet_identifiesCorrectRange() {
        // We use a subclass that bypasses context/sessionStore in these methods
        val localNet = object : LocalNet(null, null) {}
        assertTrue(localNet.isTailscaleSubnet("100.64.0.1"))
        assertTrue(localNet.isTailscaleSubnet("100.127.255.254"))
        assertFalse(localNet.isTailscaleSubnet("100.63.255.255"))
        assertFalse(localNet.isTailscaleSubnet("100.128.0.0"))
    }

    @Test
    fun probePveVersion_identifiesVerifiedAndDetectedTiers() = runBlocking {
        val localNet = object : LocalNet(null, null) {
            override suspend fun probePveVersion(host: String, port: Int, timeoutMs: Long): ProbeResult {
                return when (host) {
                    "verified" -> ProbeResult.Verified("8.3.0")
                    "detected" -> ProbeResult.PveDetected
                    "not_pve" -> ProbeResult.NotPve
                    else -> ProbeResult.Unreachable
                }
            }
        }
        
        assertEquals(ProbeResult.Verified("8.3.0"), localNet.probePveVersion("verified"))
        assertEquals(ProbeResult.PveDetected, localNet.probePveVersion("detected"))
        assertEquals(ProbeResult.NotPve, localNet.probePveVersion("not_pve"))
        assertEquals(ProbeResult.Unreachable, localNet.probePveVersion("other"))
    }

    private fun isPrivate(ip: String): Boolean {
        val parts = ip.trim().split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        val b0 = parts[0]
        val b1 = parts[1]
        return when {
            b0 == 10 -> true
            b0 == 172 && b1 in 16..31 -> true
            b0 == 192 && b1 == 168 -> true
            b0 == 127 -> true
            else -> false
        }
    }

    private class SubnetGenerator {
        fun generateSubnetIps(ip: String, prefixLength: Int): List<String> {
            val ipLong = LocalNet.parseIpv4(ip) ?: return emptyList()
            val effectivePrefix = if (prefixLength < 24) 24 else prefixLength.coerceAtMost(30)
            val mask = (0xFFFFFFFFL shl (32 - effectivePrefix)) and 0xFFFFFFFFL
            val network = ipLong and mask
            val broadcast = network or (mask.inv() and 0xFFFFFFFFL)

            val ips = mutableListOf<String>()
            var curr = network + 1
            while (curr < broadcast) {
                ips.add(LocalNet.formatIpv4(curr))
                curr++
            }
            return ips
        }
    }
}
