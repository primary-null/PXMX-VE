package com.pxmx.app.ui.home

import com.pxmx.app.data.model.ClusterResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceMetricsTest {

    @Test
    fun testNodeMetricsTokensStayIntact() {
        val nodeResource = ClusterResource(
            id = "node/node1",
            type = "node",
            node = "node1",
            cpu = 0.007,
            mem = 744L,
            maxmem = 1000L,
            disk = 87831154688L, // ~81.8 GiB
            maxdisk = 100824940544L, // ~93.9 GiB
            uptime = 1164180L, // 13d 11h 23m
        )

        val tokens = metricsLine(nodeResource)
        assertEquals(4, tokens.size)
        assertEquals("CPU 0.7%", tokens[0])
        assertEquals("MEM 74.4%", tokens[1])
        assertEquals("DISK 81.8 GiB / 93.9 GiB", tokens[2])
        assertEquals("up 13d 11h 23m", tokens[3])
    }

    @Test
    fun testGuestMetricsTokensStayIntact() {
        val guestResource = ClusterResource(
            id = "qemu/100",
            type = "qemu",
            node = "node1",
            vmid = 100L,
            cpu = 0.012,
            mem = 345L,
            maxmem = 1000L,
            cpus = 2,
            uptime = 93600L,
        )

        val tokens = metricsLine(guestResource)
        assertEquals(4, tokens.size)
        assertEquals("CPU 1.2%", tokens[0])
        assertEquals("MEM 34.5%", tokens[1])
        assertEquals("2 vCPU", tokens[2])
        assertEquals("up 1d 2h 0m", tokens[3])
    }

    @Test
    fun testStorageMetricsTokensStayIntact() {
        val storageResource = ClusterResource(
            id = "storage/node1/vm-disks",
            type = "storage",
            node = "node1",
            storage = "vm-disks",
            disk = 87831154688L,
            maxdisk = 100824940544L,
        )

        val tokens = metricsLine(storageResource)
        assertEquals(2, tokens.size)
        assertEquals("81.8 GiB / 93.9 GiB", tokens[0])
        assertEquals("87.1%", tokens[1])
    }

    @Test
    fun testEmptyMetricsReturnsEmptyList() {
        val emptyResource = ClusterResource(
            id = "node/node1",
            type = "node",
            node = "node1",
        )

        val tokens = metricsLine(emptyResource)
        assertTrue(tokens.isEmpty())
    }
}
