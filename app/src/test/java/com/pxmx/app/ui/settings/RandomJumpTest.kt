package com.pxmx.app.ui.settings

import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.ui.navigation.Routes
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomJumpTest {

    @Test
    fun cleanSlateWaveformTimingsSumTo10000AndAmplitudesAreValid() {
        val waveform = cleanSlateWaveform()
        assertEquals(waveform.timings.size, waveform.amplitudes.size)
        assertEquals(10000L, waveform.timings.sum())
        assertTrue(waveform.timings.all { it > 0L })
        assertTrue(waveform.amplitudes.all { it in 1..255 })
        assertEquals(255, waveform.amplitudes.last())
        for (i in 1 until waveform.amplitudes.size) {
            assertTrue(waveform.amplitudes[i] >= waveform.amplitudes[i - 1])
        }
    }

    @Test
    fun creatorBarEdgesAreEmptyAtZeroAndMeetAtMidpointAtOne() {
        val width = 320f
        val startEdges = creatorBarEdges(width, 0f)
        assertEquals(0f, startEdges.leftWidth, 0.001f)
        assertEquals(width, startEdges.rightStart, 0.001f)
        assertEquals(0f, width - startEdges.rightStart, 0.001f)

        val endEdges = creatorBarEdges(width, 1f)
        val midpoint = width * 0.5f
        assertEquals(midpoint, endEdges.leftWidth, 0.001f)
        assertEquals(midpoint, endEdges.rightStart, 0.001f)
        assertEquals(endEdges.leftWidth, endEdges.rightStart, 0.001f)
    }

    @Test
    fun buildRoutesFromNodeGuestAndStorageResources() {
        val resources = listOf(
            ClusterResource(type = "node", node = "alpha", name = "alpha"),
            ClusterResource(type = "qemu", node = "alpha", vmid = 100L, name = "nova"),
            ClusterResource(type = "lxc", node = "beta", vmid = 200L, name = null),
            ClusterResource(type = "storage", node = "alpha", storage = "local-lvm"),
        )
        val routes = buildRandomJumpRoutes(resources)

        assertTrue(routes.contains("node/alpha"))
        assertTrue(routes.contains("console/alpha/node/0/alpha?cmd=login"))
        assertTrue(routes.contains("guest/alpha/qemu/100/nova"))
        assertTrue(routes.contains("console/alpha/qemu/100/nova"))
        assertTrue(routes.contains("guest/beta/lxc/200/lxc%20200"))
        assertTrue(routes.contains("console/beta/lxc/200/lxc%20200"))
        assertTrue(routes.contains("storage/alpha/local-lvm"))
    }

    @Test
    fun guestWithNullVmidDoesNotProduceRoute() {
        val resources = listOf(
            ClusterResource(type = "qemu", node = "alpha", vmid = null, name = "orphan"),
            ClusterResource(type = "node", node = " "),
            ClusterResource(type = "storage", node = "alpha", storage = ""),
        )
        val routes = buildRandomJumpRoutes(resources)

        assertEquals(STATIC_RANDOM_JUMP_ROUTES, routes)
        assertFalse(routes.any { it.startsWith("guest/") })
        assertFalse(routes.any { it.startsWith("node/") })
        assertFalse(routes.any { it.startsWith("storage/") })
        assertFalse(routes.any { it.startsWith("console/") })
    }

    @Test
    fun emptyResourceListReturnsStaticRoutesAndNeverIncludesLoginOrSplash() {
        val routes = buildRandomJumpRoutes(emptyList())

        assertEquals(STATIC_RANDOM_JUMP_ROUTES, routes)
        assertTrue(routes.contains(Routes.HOME))
        assertTrue(routes.contains(Routes.TASKS))
        assertTrue(routes.contains(Routes.SETTINGS))
        assertTrue(routes.contains(Routes.SERVERS))
        assertTrue(routes.contains(Routes.NUCLEUS))
        assertTrue(routes.contains(Routes.NETWORK))
        assertTrue(routes.contains(Routes.SDN))
        assertTrue(routes.contains(Routes.FIREWALL))
        assertTrue(routes.contains(Routes.UPDATES))
        assertTrue(routes.contains(Routes.PERMISSIONS))
        assertTrue(routes.contains(Routes.SERVER_LAMPS))
        assertTrue(routes.contains(Routes.NOTIFICATIONS))
        assertTrue(routes.contains(Routes.LOG))
        assertFalse(routes.contains(Routes.LOGIN))
        assertFalse(routes.contains(Routes.SPLASH))
    }

    @Test
    fun pickReturnsCandidateMemberForFixedSeedAndHomeWhenEmpty() {
        val candidates = buildRandomJumpRoutes(
            listOf(
                ClusterResource(type = "node", node = "alpha"),
                ClusterResource(type = "qemu", node = "alpha", vmid = 100L, name = "nova"),
            )
        )
        val random = Random(42)
        repeat(25) {
            val picked = pickRandomJumpRoute(candidates, random)
            assertTrue(picked in candidates)
        }
        assertEquals(Routes.HOME, pickRandomJumpRoute(emptyList(), Random(42)))
    }
}
