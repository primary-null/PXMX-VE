package com.pxmx.app.ui.components

import com.pxmx.app.data.model.ClusterLogEntry
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.ServerLampThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerLampTest {

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
            ServerLampState(ServerLampMode.STEADY, ServerLampHue.GREEN),
            lampForNode(node(cpu = 0.01)),
        )
    }

    @Test
    fun nodeOnlineMissingCpu_steadyGreen() {
        assertEquals(
            ServerLampState(ServerLampMode.STEADY, ServerLampHue.GREEN),
            lampForNode(node(cpu = null)),
        )
    }

    @Test
    fun nodeOnlineActivity_activityGreen() {
        assertEquals(
            ServerLampState(ServerLampMode.ACTIVITY, ServerLampHue.GREEN),
            lampForNode(node(cpu = 0.05)),
        )
    }

    @Test
    fun nodeHighLoad_activityAmber() {
        assertEquals(
            ServerLampState(ServerLampMode.ACTIVITY, ServerLampHue.AMBER),
            lampForNode(node(cpu = 0.85)),
        )
    }

    @Test
    fun nodeCustomThreshold_warn55WithCpu056_isActivityAmber() {
        val thresholds = ServerLampThresholds(activityPercent = 5, warnPercent = 55)
        assertEquals(
            ServerLampState(ServerLampMode.ACTIVITY, ServerLampHue.AMBER),
            lampForNode(node(cpu = 0.56), thresholds),
        )
    }

    @Test
    fun nodeCustomThreshold_activity55WithCpu050_isSteadyGreen() {
        val thresholds = ServerLampThresholds(activityPercent = 55, warnPercent = 85)
        assertEquals(
            ServerLampState(ServerLampMode.STEADY, ServerLampHue.GREEN),
            lampForNode(node(cpu = 0.50), thresholds),
        )
    }

    @Test
    fun guestCustomThreshold_warn55WithCpu056_isActivityAmber() {
        val thresholds = ServerLampThresholds(activityPercent = 5, warnPercent = 55)
        assertEquals(
            ServerLampState(ServerLampMode.ACTIVITY, ServerLampHue.AMBER),
            lampForGuest(guest(cpu = 0.56), thresholds = thresholds),
        )
    }

    @Test
    fun guestCustomThreshold_activity55WithCpu050_isSteadyGreen() {
        val thresholds = ServerLampThresholds(activityPercent = 55, warnPercent = 85)
        assertEquals(
            ServerLampState(ServerLampMode.STEADY, ServerLampHue.GREEN),
            lampForGuest(guest(cpu = 0.50), thresholds = thresholds),
        )
    }

    @Test
    fun lampForResource_withThresholds() {
        val thresholds = ServerLampThresholds(activityPercent = 55, warnPercent = 85)
        val n = node(cpu = 0.50)
        assertEquals(
            ServerLampState(ServerLampMode.STEADY, ServerLampHue.GREEN),
            lampForResource(n, thresholds = thresholds),
        )
    }

    @Test
    fun nodeOffline_offMute() {
        assertEquals(
            ServerLampState(ServerLampMode.OFF, ServerLampHue.MUTE),
            lampForNode(node(status = "offline", cpu = 0.4)),
        )
    }

    @Test
    fun nodeUnknown_steadyMute() {
        assertEquals(
            ServerLampState(ServerLampMode.STEADY, ServerLampHue.MUTE),
            lampForNode(node(status = "unknown")),
        )
    }

    @Test
    fun nodeReadErrors_faultRed() {
        assertEquals(
            ServerLampState(ServerLampMode.FAULT, ServerLampHue.RED),
            lampForNode(node(readErrors = mapOf("node" to "timeout"))),
        )
    }

    @Test
    fun guestStopped_offMute() {
        assertEquals(
            ServerLampState(ServerLampMode.OFF, ServerLampHue.MUTE),
            lampForGuest(guest(status = "stopped", cpu = 0.4)),
        )
    }

    @Test
    fun guestTemplate_offMute() {
        assertEquals(
            ServerLampState(ServerLampMode.OFF, ServerLampHue.MUTE),
            lampForGuest(guest(status = "stopped", template = 1)),
        )
    }

    @Test
    fun guestPaused_steadyAmber() {
        assertEquals(
            ServerLampState(ServerLampMode.STEADY, ServerLampHue.AMBER),
            lampForGuest(guest(status = "paused")),
        )
    }

    @Test
    fun guestRunningIdle_steadyGreen() {
        assertEquals(
            ServerLampState(ServerLampMode.STEADY, ServerLampHue.GREEN),
            lampForGuest(guest(cpu = 0.02)),
        )
    }

    @Test
    fun guestRunningActivity_activityGreen() {
        assertEquals(
            ServerLampState(ServerLampMode.ACTIVITY, ServerLampHue.GREEN),
            lampForGuest(guest(cpu = 0.4)),
        )
    }

    @Test
    fun guestBusy_activityAmber() {
        assertEquals(
            ServerLampState(ServerLampMode.ACTIVITY, ServerLampHue.AMBER),
            lampForGuest(guest(status = "stopped"), busy = true),
        )
    }

    @Test
    fun guestError_faultRed() {
        assertEquals(
            ServerLampState(ServerLampMode.FAULT, ServerLampHue.RED),
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
            ServerLampState(ServerLampMode.OFF, ServerLampHue.MUTE),
            lampForLog(null),
        )
    }

    @Test
    fun lampForLog_infoSteadyGreen() {
        assertEquals(
            ServerLampState(ServerLampMode.STEADY, ServerLampHue.GREEN),
            lampForLog(ClusterLogEntry(pri = 6, tag = "pvedaemon", msg = "ok")),
        )
    }

    @Test
    fun lampForLog_warningSteadyAmber() {
        assertEquals(
            ServerLampState(ServerLampMode.STEADY, ServerLampHue.AMBER),
            lampForLog(ClusterLogEntry(pri = 4, tag = "pvedaemon", msg = "warn")),
        )
    }

    @Test
    fun lampForLog_errFaultRed() {
        assertEquals(
            ServerLampState(ServerLampMode.FAULT, ServerLampHue.RED),
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
    fun serverLampDescription_activityAndFault() {
        assertEquals(
            "pve1 activity",
            serverLampDescription("pve1", ServerLampState(ServerLampMode.ACTIVITY, ServerLampHue.GREEN)),
        )
        assertEquals(
            "web01 fault",
            serverLampDescription("web01", ServerLampState(ServerLampMode.FAULT, ServerLampHue.RED)),
        )
        assertEquals(
            "web01 off",
            serverLampDescription("web01", ServerLampState(ServerLampMode.OFF, ServerLampHue.MUTE)),
        )
        assertEquals(
            "web01 paused",
            serverLampDescription("web01", ServerLampState(ServerLampMode.STEADY, ServerLampHue.AMBER)),
        )
    }

    @Test
    fun serverLampDescription_blankOrNullName_fallsBackToServer() {
        assertEquals(
            "server on",
            serverLampDescription(null, ServerLampState(ServerLampMode.STEADY, ServerLampHue.GREEN)),
        )
        assertEquals(
            "server on",
            serverLampDescription("", ServerLampState(ServerLampMode.STEADY, ServerLampHue.GREEN)),
        )
        assertEquals(
            "server on",
            serverLampDescription("   ", ServerLampState(ServerLampMode.STEADY, ServerLampHue.GREEN)),
        )
    }

    @Test
    fun serverLampThresholds_clampAndSlideRules() {
        // Defaults
        val defaultT = ServerLampThresholds()
        assertEquals(5, defaultT.activityPercent)
        assertEquals(85, defaultT.warnPercent)

        // Clamping bounds: activity 0..99, warn 1..100
        val clampedMin = ServerLampThresholds.clamp(-10, -5)
        assertEquals(0, clampedMin.activityPercent)
        assertEquals(1, clampedMin.warnPercent)

        val clampedMax = ServerLampThresholds.clamp(120, 150)
        assertEquals(99, clampedMax.activityPercent)
        assertEquals(100, clampedMax.warnPercent)

        // Sliding activity up against warn bumps warn to activity+1 (cap 100)
        val bumpWarn = defaultT.slideActivity(85)
        assertEquals(85, bumpWarn.activityPercent)
        assertEquals(86, bumpWarn.warnPercent)

        val bumpWarnCap = defaultT.slideActivity(99)
        assertEquals(99, bumpWarnCap.activityPercent)
        assertEquals(100, bumpWarnCap.warnPercent)

        // Sliding warn down against activity drops activity to warn-1 (floor 0)
        val dropActivity = defaultT.slideWarn(5)
        assertEquals(4, dropActivity.activityPercent)
        assertEquals(5, dropActivity.warnPercent)

        val dropActivityFloor = defaultT.slideWarn(1)
        assertEquals(0, dropActivityFloor.activityPercent)
        assertEquals(1, dropActivityFloor.warnPercent)

        // Activity strictly less than warn
        val equalSlide = ServerLampThresholds.clamp(50, 50, slidingActivity = true)
        assertEquals(50, equalSlide.activityPercent)
        assertEquals(51, equalSlide.warnPercent)

        val equalWarnSlide = ServerLampThresholds.clamp(50, 50, slidingActivity = false)
        assertEquals(49, equalWarnSlide.activityPercent)
        assertEquals(50, equalWarnSlide.warnPercent)
    }

    @Test
    fun activityAlpha_reduceMotionIsFull() {
        assertEquals(1f, activityAlpha(0.3f, reduceMotion = true), 0.0f)
        assertEquals(SERVER_LAMP_ACTIVITY_ALPHA_MIN, activityAlpha(0f, reduceMotion = false), 0.0f)
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
        assertEquals(SERVER_LAMP_FAULT_ALPHA_DIM, faultAlpha(0.2f, reduceMotion = false), 0.0f)
        assertEquals(1f, faultAlpha(0.8f, reduceMotion = false), 0.0f)
    }
}
