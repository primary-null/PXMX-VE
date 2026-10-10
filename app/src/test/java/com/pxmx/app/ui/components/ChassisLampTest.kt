package com.pxmx.app.ui.components

import com.pxmx.app.data.model.ClusterLogEntry
import com.pxmx.app.data.model.ClusterResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChassisLampTest {

    private fun node(
        status: String? = "online",
        cpu: Double? = 0.01,
        readErrors: Map<String, String> = emptyMap(),
        name: String = "pve1",
    ) = ClusterResource(
        id = "node/$name",
        type = "node",
        node = name,
        status = status,
        cpu = cpu,
        readErrors = readErrors,
    )

    private fun guest(
        status: String? = "running",
        cpu: Double? = 0.01,
        template: Int? = null,
        busyErrors: Map<String, String> = emptyMap(),
        type: String = "qemu",
    ) = ClusterResource(
        id = "$type/100",
        type = type,
        node = "pve1",
        vmid = 100L,
        name = "web01",
        status = status,
        cpu = cpu,
        template = template,
        readErrors = busyErrors,
    )

    @Test
    fun nodeOnlineIdle_steadyGreen() {
        assertEquals(
            ChassisLampState(ChassisLampMode.STEADY, ChassisLampHue.GREEN),
            lampForNode(node(cpu = 0.01)),
        )
    }

    @Test
    fun nodeOnlineMissingCpu_steadyGreen() {
        assertEquals(
            ChassisLampState(ChassisLampMode.STEADY, ChassisLampHue.GREEN),
            lampForNode(node(cpu = null)),
        )
    }

    @Test
    fun nodeOnlineActivity_activityGreen() {
        assertEquals(
            ChassisLampState(ChassisLampMode.ACTIVITY, ChassisLampHue.GREEN),
            lampForNode(node(cpu = 0.05)),
        )
    }

    @Test
    fun nodeHighLoad_activityAmber() {
        assertEquals(
            ChassisLampState(ChassisLampMode.ACTIVITY, ChassisLampHue.AMBER),
            lampForNode(node(cpu = 0.85)),
        )
    }

    @Test
    fun nodeOffline_offMute() {
        assertEquals(
            ChassisLampState(ChassisLampMode.OFF, ChassisLampHue.MUTE),
            lampForNode(node(status = "offline", cpu = 0.4)),
        )
    }

    @Test
    fun nodeUnknown_steadyMute() {
        assertEquals(
            ChassisLampState(ChassisLampMode.STEADY, ChassisLampHue.MUTE),
            lampForNode(node(status = "unknown")),
        )
    }

    @Test
    fun nodeReadErrors_faultRed() {
        assertEquals(
            ChassisLampState(ChassisLampMode.FAULT, ChassisLampHue.RED),
            lampForNode(node(readErrors = mapOf("node" to "timeout"))),
        )
    }

    @Test
    fun guestStopped_offMute() {
        assertEquals(
            ChassisLampState(ChassisLampMode.OFF, ChassisLampHue.MUTE),
            lampForGuest(guest(status = "stopped", cpu = 0.4)),
        )
    }

    @Test
    fun guestTemplate_offMute() {
        assertEquals(
            ChassisLampState(ChassisLampMode.OFF, ChassisLampHue.MUTE),
            lampForGuest(guest(status = "stopped", template = 1)),
        )
    }

    @Test
    fun guestPaused_steadyAmber() {
        assertEquals(
            ChassisLampState(ChassisLampMode.STEADY, ChassisLampHue.AMBER),
            lampForGuest(guest(status = "paused")),
        )
    }

    @Test
    fun guestRunningIdle_steadyGreen() {
        assertEquals(
            ChassisLampState(ChassisLampMode.STEADY, ChassisLampHue.GREEN),
            lampForGuest(guest(cpu = 0.02)),
        )
    }

    @Test
    fun guestRunningActivity_activityGreen() {
        assertEquals(
            ChassisLampState(ChassisLampMode.ACTIVITY, ChassisLampHue.GREEN),
            lampForGuest(guest(cpu = 0.4)),
        )
    }

    @Test
    fun guestBusy_activityAmber() {
        assertEquals(
            ChassisLampState(ChassisLampMode.ACTIVITY, ChassisLampHue.AMBER),
            lampForGuest(guest(status = "stopped"), busy = true),
        )
    }

    @Test
    fun guestError_faultRed() {
        assertEquals(
            ChassisLampState(ChassisLampMode.FAULT, ChassisLampHue.RED),
            lampForGuest(guest(status = "error")),
        )
    }

    @Test
    fun lampForResource_storageIsNull() {
        assertNull(
            lampForResource(
                ClusterResource(id = "storage/pve1/local", type = "storage", status = "available"),
            ),
        )
    }

    @Test
    fun lampForLog_nullOffMute() {
        assertEquals(
            ChassisLampState(ChassisLampMode.OFF, ChassisLampHue.MUTE),
            lampForLog(null),
        )
    }

    @Test
    fun lampForLog_infoSteadyGreen() {
        assertEquals(
            ChassisLampState(ChassisLampMode.STEADY, ChassisLampHue.GREEN),
            lampForLog(ClusterLogEntry(pri = 6, tag = "pvedaemon", msg = "ok")),
        )
    }

    @Test
    fun lampForLog_warningSteadyAmber() {
        assertEquals(
            ChassisLampState(ChassisLampMode.STEADY, ChassisLampHue.AMBER),
            lampForLog(ClusterLogEntry(pri = 4, tag = "pvedaemon", msg = "warn")),
        )
    }

    @Test
    fun lampForLog_errFaultRed() {
        assertEquals(
            ChassisLampState(ChassisLampMode.FAULT, ChassisLampHue.RED),
            lampForLog(ClusterLogEntry(pri = 3, tag = "pvedaemon", msg = "fail")),
        )
    }

    @Test
    fun shouldBlinkLog_firstPaintDoesNotBlink() {
        assertFalse(shouldBlinkLog(null, "a"))
        assertFalse(shouldBlinkLog(null, null))
    }

    @Test
    fun shouldBlinkLog_newLineBlinksOnce() {
        assertTrue(shouldBlinkLog("a", "b"))
        assertFalse(shouldBlinkLog("a", "a"))
        assertFalse(shouldBlinkLog("a", null))
    }

    @Test
    fun clusterBezelNodes_sortedByNodeName() {
        val nodes = clusterBezelNodes(
            listOf(
                guest(),
                node(name = "pve2"),
                node(name = "pve1"),
                ClusterResource(id = "storage/x", type = "storage"),
            ),
        )
        assertEquals(listOf("pve1", "pve2"), nodes.map { it.node })
    }

    @Test
    fun chassisLampDescription_activityAndFault() {
        assertEquals(
            "pve1 activity",
            chassisLampDescription("pve1", ChassisLampState(ChassisLampMode.ACTIVITY, ChassisLampHue.GREEN)),
        )
        assertEquals(
            "web01 fault",
            chassisLampDescription("web01", ChassisLampState(ChassisLampMode.FAULT, ChassisLampHue.RED)),
        )
        assertEquals(
            "web01 off",
            chassisLampDescription("web01", ChassisLampState(ChassisLampMode.OFF, ChassisLampHue.MUTE)),
        )
        assertEquals(
            "web01 paused",
            chassisLampDescription("web01", ChassisLampState(ChassisLampMode.STEADY, ChassisLampHue.AMBER)),
        )
    }

    @Test
    fun activityAlpha_reduceMotionIsFull() {
        assertEquals(1f, activityAlpha(0.3f, reduceMotion = true), 0.0f)
        assertEquals(CHASSIS_ACTIVITY_ALPHA_MIN, activityAlpha(0f, reduceMotion = false), 0.0f)
        assertEquals(1f, activityAlpha(1f, reduceMotion = false), 0.0f)
    }

    @Test
    fun isReduceMotionEnabled_zeroScale() {
        assertTrue(isReduceMotionEnabled(0f))
        assertFalse(isReduceMotionEnabled(1f))
    }

    @Test
    fun faultAlpha_blinksUnlessReduceMotion() {
        assertEquals(1f, faultAlpha(0.2f, reduceMotion = true), 0.0f)
        assertEquals(CHASSIS_FAULT_ALPHA_DIM, faultAlpha(0.2f, reduceMotion = false), 0.0f)
        assertEquals(1f, faultAlpha(0.8f, reduceMotion = false), 0.0f)
    }
}
