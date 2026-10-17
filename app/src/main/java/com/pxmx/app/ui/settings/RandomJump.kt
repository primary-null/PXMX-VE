package com.pxmx.app.ui.settings

import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.ui.navigation.Routes
import kotlin.random.Random

/**
 * Timings and amplitudes for the 10-second Clean Slate hold vibration.
 */
data class CleanSlateWaveform(
    val timings: LongArray,
    val amplitudes: IntArray,
)

/**
 * Builds a single continuous 10000ms vibration waveform with no silent gaps,
 * ramping amplitude from a noticeable low value up to 255.
 */
fun cleanSlateWaveform(): CleanSlateWaveform {
    val steps = 10
    val timings = LongArray(steps) { 1000L }
    val amplitudes = IntArray(steps) { index ->
        (60 + (195 * index) / (steps - 1)).coerceIn(1, 255)
    }
    return CleanSlateWaveform(
        timings = timings,
        amplitudes = amplitudes,
    )
}

/**
 * Horizontal bar edge coordinates for the dual-converging hold bars on the creator plate.
 */
data class DualBarEdges(
    val leftWidth: Float,
    val rightStart: Float,
)

/**
 * Computes the left fill width and right fill start X for a plate of [plateWidth] at [progress] in 0f..1f.
 * At progress 0 both fills are empty; at progress 1 both edges meet at the midpoint.
 */
fun creatorBarEdges(plateWidth: Float, progress: Float): DualBarEdges {
    val width = plateWidth.coerceAtLeast(0f)
    val clampedProgress = progress.coerceIn(0f, 1f)
    val leftWidth = (width * 0.5f) * clampedProgress
    val rightStart = width - leftWidth
    return DualBarEdges(
        leftWidth = leftWidth,
        rightStart = rightStart,
    )
}

val STATIC_RANDOM_JUMP_ROUTES: List<String> = listOf(
    Routes.HOME,
    Routes.TASKS,
    Routes.SETTINGS,
    Routes.SERVERS,
    Routes.NUCLEUS,
    Routes.NETWORK,
    Routes.SDN,
    Routes.FIREWALL,
    Routes.UPDATES,
    Routes.PERMISSIONS,
    Routes.SERVER_LAMPS,
    Routes.NOTIFICATIONS,
    Routes.LOG,
)

/**
 * Builds the candidate navigation routes from static destinations plus valid [resources].
 * Never includes login, splash, or destructive actions.
 */
fun buildRandomJumpRoutes(resources: List<ClusterResource>): List<String> {
    val routes = mutableListOf<String>()
    routes.addAll(STATIC_RANDOM_JUMP_ROUTES)

    for (res in resources) {
        when (res.type) {
            "node" -> {
                val nodeName = res.node?.takeIf { it.isNotBlank() }
                if (nodeName != null) {
                    routes.add(Routes.node(nodeName))
                    routes.add(Routes.console(nodeName, GuestType.NODE.path, 0L, nodeName, "login"))
                }
            }
            "qemu", "lxc" -> {
                val nodeName = res.node?.takeIf { it.isNotBlank() }
                val vmid = res.vmid
                val type = res.type
                if (nodeName != null && vmid != null) {
                    val guestName = res.name?.takeIf { it.isNotBlank() } ?: res.displayName
                    routes.add(Routes.guest(nodeName, type, vmid, guestName))
                    routes.add(Routes.console(nodeName, type, vmid, guestName))
                }
            }
            "storage" -> {
                val nodeName = res.node?.takeIf { it.isNotBlank() }
                val storageName = res.storage?.takeIf { it.isNotBlank() }
                if (nodeName != null && storageName != null) {
                    routes.add(Routes.storage(nodeName, storageName))
                }
            }
        }
    }

    return routes.distinct()
}

/**
 * Picks a single route from [candidates] using [random], falling back to [Routes.HOME] if empty.
 */
fun pickRandomJumpRoute(
    candidates: List<String>,
    random: Random = Random.Default,
): String {
    if (candidates.isEmpty()) return Routes.HOME
    return candidates[random.nextInt(candidates.size)]
}
