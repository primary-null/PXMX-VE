package com.pxmx.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.data.model.ClusterLogEntry
import com.pxmx.app.data.model.ClusterResource
import kotlinx.coroutines.delay

/** Front-panel lamp language. Hue is palette, not Compose Color, so mapping stays unit-testable. */
enum class ChassisLampHue {
    MUTE,
    GREEN,
    AMBER,
    RED,
}

enum class ChassisLampMode {
    OFF,
    STEADY,
    ACTIVITY,
    FAULT,
}

data class ChassisLampState(
    val mode: ChassisLampMode,
    val hue: ChassisLampHue,
)

/** CPU fraction (0–1) at which a live node/guest starts a few-percent flicker. */
const val CHASSIS_ACTIVITY_CPU = 0.05

/** CPU fraction (0–1) that turns activity amber — load caution, not an alarm. */
const val CHASSIS_WARN_CPU = 0.85

const val CHASSIS_ACTIVITY_ALPHA_MIN = 0.88f

fun isReduceMotionEnabled(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

fun activityAlpha(pulse: Float, reduceMotion: Boolean): Float {
    if (reduceMotion) return 1f
    val p = pulse.coerceIn(0f, 1f)
    return CHASSIS_ACTIVITY_ALPHA_MIN + (1f - CHASSIS_ACTIVITY_ALPHA_MIN) * p
}

fun shouldBlinkLog(previousKey: String?, currentKey: String?): Boolean =
    currentKey != null && previousKey != null && previousKey != currentKey

fun clusterBezelNodes(resources: List<ClusterResource>): List<ClusterResource> =
    resources.filter { it.type == "node" }
        .sortedBy { it.node?.lowercase() ?: it.displayName.lowercase() }

fun lampForResource(resource: ClusterResource, busy: Boolean = false): ChassisLampState? = when {
    resource.type == "node" -> lampForNode(resource)
    resource.isGuest -> lampForGuest(resource, busy)
    else -> null
}

fun lampForNode(resource: ClusterResource): ChassisLampState {
    if (resource.readErrors.isNotEmpty()) {
        return ChassisLampState(ChassisLampMode.FAULT, ChassisLampHue.RED)
    }
    val status = resource.status?.lowercase()
    if (status == "error") {
        return ChassisLampState(ChassisLampMode.FAULT, ChassisLampHue.RED)
    }
    if (status == "offline" || status == "stopped") {
        return ChassisLampState(ChassisLampMode.OFF, ChassisLampHue.MUTE)
    }
    if (status != "online" && status != "running") {
        return ChassisLampState(ChassisLampMode.STEADY, ChassisLampHue.MUTE)
    }
    val cpu = resource.cpu ?: 0.0
    return when {
        cpu >= CHASSIS_WARN_CPU -> ChassisLampState(ChassisLampMode.ACTIVITY, ChassisLampHue.AMBER)
        cpu >= CHASSIS_ACTIVITY_CPU -> ChassisLampState(ChassisLampMode.ACTIVITY, ChassisLampHue.GREEN)
        else -> ChassisLampState(ChassisLampMode.STEADY, ChassisLampHue.GREEN)
    }
}

fun lampForGuest(resource: ClusterResource, busy: Boolean = false): ChassisLampState {
    if (resource.readErrors.isNotEmpty()) {
        return ChassisLampState(ChassisLampMode.FAULT, ChassisLampHue.RED)
    }
    val status = resource.status?.lowercase()
    if (status == "error") {
        return ChassisLampState(ChassisLampMode.FAULT, ChassisLampHue.RED)
    }
    if (busy) {
        return ChassisLampState(ChassisLampMode.ACTIVITY, ChassisLampHue.AMBER)
    }
    if (resource.template == 1) {
        return ChassisLampState(ChassisLampMode.OFF, ChassisLampHue.MUTE)
    }
    if (resource.isPausedOrSuspended) {
        return ChassisLampState(ChassisLampMode.STEADY, ChassisLampHue.AMBER)
    }
    if (!resource.isRunning) {
        return ChassisLampState(ChassisLampMode.OFF, ChassisLampHue.MUTE)
    }
    val cpu = resource.cpu ?: 0.0
    return when {
        cpu >= CHASSIS_WARN_CPU -> ChassisLampState(ChassisLampMode.ACTIVITY, ChassisLampHue.AMBER)
        cpu >= CHASSIS_ACTIVITY_CPU -> ChassisLampState(ChassisLampMode.ACTIVITY, ChassisLampHue.GREEN)
        else -> ChassisLampState(ChassisLampMode.STEADY, ChassisLampHue.GREEN)
    }
}

fun lampForLog(entry: ClusterLogEntry?): ChassisLampState {
    if (entry == null) {
        return ChassisLampState(ChassisLampMode.OFF, ChassisLampHue.MUTE)
    }
    val pri = entry.pri
    return when {
        pri == null -> ChassisLampState(ChassisLampMode.STEADY, ChassisLampHue.MUTE)
        pri <= 3 -> ChassisLampState(ChassisLampMode.FAULT, ChassisLampHue.RED)
        pri == 4 -> ChassisLampState(ChassisLampMode.STEADY, ChassisLampHue.AMBER)
        else -> ChassisLampState(ChassisLampMode.STEADY, ChassisLampHue.GREEN)
    }
}

fun chassisLampDescription(name: String?, state: ChassisLampState): String {
    val who = name?.takeIf { it.isNotBlank() } ?: "chassis"
    val status = when (state.mode) {
        ChassisLampMode.OFF -> "off"
        ChassisLampMode.FAULT -> "fault"
        ChassisLampMode.ACTIVITY -> if (state.hue == ChassisLampHue.AMBER) "load" else "activity"
        ChassisLampMode.STEADY -> when (state.hue) {
            ChassisLampHue.AMBER -> "paused"
            ChassisLampHue.GREEN -> "on"
            ChassisLampHue.RED -> "fault"
            ChassisLampHue.MUTE -> "idle"
        }
    }
    return "$who $status"
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            isReduceMotionEnabled(
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ),
            )
        } catch (_: Throwable) {
            false
        }
    }
}

@Composable
fun ChassisLamp(
    state: ChassisLampState,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    blinkToken: String? = null,
    reduceMotion: Boolean = false,
) {
    val pulse = if (state.mode == ChassisLampMode.ACTIVITY && !reduceMotion) {
        ChassisLampPulse()
    } else {
        0f
    }
    var blinkBoost by remember { mutableFloatStateOf(0f) }
    val lastToken = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(blinkToken, reduceMotion) {
        val previous = lastToken.value
        lastToken.value = blinkToken
        if (reduceMotion || !shouldBlinkLog(previous, blinkToken)) {
            blinkBoost = 0f
            return@LaunchedEffect
        }
        blinkBoost = 1f
        delay(160)
        blinkBoost = 0f
    }
    val baseAlpha = when (state.mode) {
        ChassisLampMode.OFF -> 0.35f
        ChassisLampMode.ACTIVITY -> activityAlpha(pulse, reduceMotion)
        else -> 1f
    }
    val alpha = (baseAlpha + blinkBoost * (1f - baseAlpha)).coerceIn(0f, 1f)
    val color = lampColor(state.hue)
    val description = contentDescription ?: chassisLampDescription(null, state)
    Box(
        modifier = modifier
            .size(6.dp)
            .semantics { this.contentDescription = description }
            .background(color.copy(alpha = alpha), CircleShape),
    )
}

@Composable
private fun ChassisLampPulse(): Float {
    val transition = rememberInfiniteTransition(label = "chassis-lamp")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    return pulse
}

@Composable
private fun lampColor(hue: ChassisLampHue): Color = when (hue) {
    ChassisLampHue.MUTE -> TechColors.Mute
    ChassisLampHue.GREEN -> TechColors.LinkGreen
    ChassisLampHue.AMBER -> TechColors.Amber
    ChassisLampHue.RED -> TechColors.Danger
}

@Composable
fun ClusterBezel(
    nodes: List<ClusterResource>,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    if (nodes.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TechColors.Hull)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "CHASSIS",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            fontSize = 10.sp,
            color = TechColors.Mute,
        )
        Spacer(Modifier.width(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            nodes.forEach { node ->
                val lamp = lampForNode(node)
                ChassisLamp(
                    state = lamp,
                    contentDescription = chassisLampDescription(node.node ?: node.displayName, lamp),
                    reduceMotion = reduceMotion,
                )
            }
        }
    }
}
