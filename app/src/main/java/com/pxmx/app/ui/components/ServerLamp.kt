package com.pxmx.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.data.model.ClusterLogEntry
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.ServerLampThresholds
import kotlinx.coroutines.delay

typealias ServerLampThresholds = com.pxmx.app.data.model.ServerLampThresholds

/** Front-panel lamp language. Hue is palette, not Compose Color, so mapping stays unit-testable. */
enum class ServerLampHue {
    MUTE,
    GREEN,
    AMBER,
    RED,
}

enum class ServerLampMode {
    OFF,
    STEADY,
    ACTIVITY,
    FAULT,
}

data class ServerLampState(
    val mode: ServerLampMode,
    val hue: ServerLampHue,
)

/** CPU fraction (0–1) at which a live node/guest starts a few-percent flicker. */
const val SERVER_LAMP_ACTIVITY_CPU_DEFAULT = 0.05

/** CPU fraction (0–1) that turns activity amber — load caution, not an alarm. */
const val SERVER_LAMP_WARN_CPU_DEFAULT = 0.85

const val SERVER_LAMP_ACTIVITY_ALPHA_MIN = 0.88f
const val SERVER_LAMP_FAULT_ALPHA_DIM = 0.38f

// Legacy aliases for backward compatibility
const val CHASSIS_ACTIVITY_CPU = SERVER_LAMP_ACTIVITY_CPU_DEFAULT
const val CHASSIS_WARN_CPU = SERVER_LAMP_WARN_CPU_DEFAULT
const val CHASSIS_ACTIVITY_ALPHA_MIN = SERVER_LAMP_ACTIVITY_ALPHA_MIN
const val CHASSIS_FAULT_ALPHA_DIM = SERVER_LAMP_FAULT_ALPHA_DIM
typealias ChassisLampHue = ServerLampHue
typealias ChassisLampMode = ServerLampMode
typealias ChassisLampState = ServerLampState

fun isReduceMotionEnabled(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

fun activityAlpha(pulse: Float, reduceMotion: Boolean): Float {
    if (reduceMotion) return 1f
    val p = pulse.coerceIn(0f, 1f)
    return SERVER_LAMP_ACTIVITY_ALPHA_MIN + (1f - SERVER_LAMP_ACTIVITY_ALPHA_MIN) * p
}

fun faultAlpha(pulse: Float, reduceMotion: Boolean): Float {
    if (reduceMotion) return 1f
    return if (pulse.coerceIn(0f, 1f) >= 0.5f) 1f else SERVER_LAMP_FAULT_ALPHA_DIM
}

/** Null means no shared clock; each lamp may run its own pulse. */
val LocalServerLampPulse = compositionLocalOf<Float?> { null }
val LocalChassisPulse get() = LocalServerLampPulse

@Composable
fun ProvideServerLampPulse(reduceMotion: Boolean, content: @Composable () -> Unit) {
    val pulse = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "server-lamp-pulse")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse",
        )
        value
    }
    CompositionLocalProvider(LocalServerLampPulse provides pulse, content = content)
}

@Composable
fun ProvideChassisPulse(reduceMotion: Boolean, content: @Composable () -> Unit) =
    ProvideServerLampPulse(reduceMotion, content)

fun shouldBlinkLog(previousKey: String?, currentKey: String?): Boolean =
    currentKey != null && previousKey != null && previousKey != currentKey

fun clusterBezelNodes(resources: List<ClusterResource>): List<ClusterResource> =
    resources.filter { it.type == "node" }
        .sortedBy { it.node?.lowercase() ?: it.displayName.lowercase() }

fun lampForResource(
    resource: ClusterResource,
    busy: Boolean = false,
    thresholds: ServerLampThresholds = ServerLampThresholds(),
): ServerLampState? = when {
    resource.type == "node" -> lampForNode(resource, thresholds)
    resource.isGuest -> lampForGuest(resource, busy, thresholds)
    else -> null
}

fun lampForNode(
    resource: ClusterResource,
    thresholds: ServerLampThresholds = ServerLampThresholds(),
): ServerLampState {
    if (resource.readErrors.isNotEmpty()) {
        return ServerLampState(ServerLampMode.FAULT, ServerLampHue.RED)
    }
    val status = resource.status?.lowercase()
    if (status == "error") {
        return ServerLampState(ServerLampMode.FAULT, ServerLampHue.RED)
    }
    if (status == "offline" || status == "stopped") {
        return ServerLampState(ServerLampMode.OFF, ServerLampHue.MUTE)
    }
    if (status != "online" && status != "running") {
        return ServerLampState(ServerLampMode.STEADY, ServerLampHue.MUTE)
    }
    val cpu = resource.cpu ?: 0.0
    val warnCpu = thresholds.warnPercent / 100.0
    val activityCpu = thresholds.activityPercent / 100.0
    return when {
        cpu >= warnCpu -> ServerLampState(ServerLampMode.ACTIVITY, ServerLampHue.AMBER)
        cpu >= activityCpu -> ServerLampState(ServerLampMode.ACTIVITY, ServerLampHue.GREEN)
        else -> ServerLampState(ServerLampMode.STEADY, ServerLampHue.GREEN)
    }
}

fun lampForGuest(
    resource: ClusterResource,
    busy: Boolean = false,
    thresholds: ServerLampThresholds = ServerLampThresholds(),
): ServerLampState {
    if (resource.readErrors.isNotEmpty()) {
        return ServerLampState(ServerLampMode.FAULT, ServerLampHue.RED)
    }
    val status = resource.status?.lowercase()
    if (status == "error") {
        return ServerLampState(ServerLampMode.FAULT, ServerLampHue.RED)
    }
    if (busy) {
        return ServerLampState(ServerLampMode.ACTIVITY, ServerLampHue.AMBER)
    }
    if (resource.template == 1) {
        return ServerLampState(ServerLampMode.OFF, ServerLampHue.MUTE)
    }
    if (resource.isPausedOrSuspended) {
        return ServerLampState(ServerLampMode.STEADY, ServerLampHue.AMBER)
    }
    if (!resource.isRunning) {
        return ServerLampState(ServerLampMode.OFF, ServerLampHue.MUTE)
    }
    val cpu = resource.cpu ?: 0.0
    val warnCpu = thresholds.warnPercent / 100.0
    val activityCpu = thresholds.activityPercent / 100.0
    return when {
        cpu >= warnCpu -> ServerLampState(ServerLampMode.ACTIVITY, ServerLampHue.AMBER)
        cpu >= activityCpu -> ServerLampState(ServerLampMode.ACTIVITY, ServerLampHue.GREEN)
        else -> ServerLampState(ServerLampMode.STEADY, ServerLampHue.GREEN)
    }
}

fun lampForLog(entry: ClusterLogEntry?): ServerLampState {
    if (entry == null) {
        return ServerLampState(ServerLampMode.OFF, ServerLampHue.MUTE)
    }
    val pri = entry.pri
    return when {
        pri == null -> ServerLampState(ServerLampMode.STEADY, ServerLampHue.MUTE)
        pri <= 3 -> ServerLampState(ServerLampMode.FAULT, ServerLampHue.RED)
        pri == 4 -> ServerLampState(ServerLampMode.STEADY, ServerLampHue.AMBER)
        else -> ServerLampState(ServerLampMode.STEADY, ServerLampHue.GREEN)
    }
}

fun serverLampDescription(name: String?, state: ServerLampState): String {
    val who = name?.takeIf { it.isNotBlank() } ?: "server"
    val status = when (state.mode) {
        ServerLampMode.OFF -> "off"
        ServerLampMode.FAULT -> "fault"
        ServerLampMode.ACTIVITY -> if (state.hue == ServerLampHue.AMBER) "load" else "activity"
        ServerLampMode.STEADY -> when (state.hue) {
            ServerLampHue.AMBER -> "paused"
            ServerLampHue.GREEN -> "on"
            ServerLampHue.RED -> "fault"
            ServerLampHue.MUTE -> "idle"
        }
    }
    return "$who $status"
}

fun chassisLampDescription(name: String?, state: ServerLampState): String =
    serverLampDescription(name, state)

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
fun ServerLamp(
    state: ServerLampState,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    blinkToken: String? = null,
    reduceMotion: Boolean = false,
) {
    val needsPulse = !reduceMotion &&
        (state.mode == ServerLampMode.ACTIVITY || state.mode == ServerLampMode.FAULT)
    val sharedPulse = LocalServerLampPulse.current
    val pulse = when {
        !needsPulse -> 0f
        sharedPulse != null -> sharedPulse
        else -> ServerLampPulse()
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
        ServerLampMode.OFF -> 0.35f
        ServerLampMode.ACTIVITY -> activityAlpha(pulse, reduceMotion)
        ServerLampMode.FAULT -> faultAlpha(pulse, reduceMotion)
        else -> 1f
    }
    val alpha = (baseAlpha + blinkBoost * (1f - baseAlpha)).coerceIn(0f, 1f)
    val color = lampColor(state.hue)
    val description = contentDescription ?: serverLampDescription(null, state)
    Box(
        modifier = modifier
            .size(7.dp)
            .semantics { this.contentDescription = description }
            .drawBehind {
                val c = color.copy(alpha = alpha)
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.minDimension / 2f - 0.5.dp.toPx()
                val innerRadius = (outerRadius * 0.42f).coerceAtLeast(1f)

                // Outer lens ring
                drawCircle(
                    color = c,
                    radius = outerRadius,
                    center = centerOffset,
                    style = Stroke(width = 1.dp.toPx()),
                )

                // Steady center core
                drawCircle(
                    color = c,
                    radius = innerRadius,
                    center = centerOffset,
                )
            },
    )
}

@Composable
fun ChassisLamp(
    state: ServerLampState,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    blinkToken: String? = null,
    reduceMotion: Boolean = false,
) = ServerLamp(
    state = state,
    modifier = modifier,
    contentDescription = contentDescription,
    blinkToken = blinkToken,
    reduceMotion = reduceMotion,
)

@Composable
private fun ServerLampPulse(): Float {
    val transition = rememberInfiniteTransition(label = "server-lamp")
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
private fun lampColor(hue: ServerLampHue): Color = when (hue) {
    ServerLampHue.MUTE -> TechColors.Mute
    ServerLampHue.GREEN -> TechColors.LinkGreen
    ServerLampHue.AMBER -> TechColors.Amber
    ServerLampHue.RED -> TechColors.Danger
}

@Composable
fun ClusterBezel(
    nodes: List<ClusterResource>,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    thresholds: ServerLampThresholds = ServerLampThresholds(),
    onOpenLampSettings: (() -> Unit)? = null,
) {
    if (nodes.isEmpty()) return
    val interactionSource = remember { MutableInteractionSource() }
    val clickableModifier = if (onOpenLampSettings != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onOpenLampSettings,
        )
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TechColors.Hull)
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "SERVER",
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
                val lamp = lampForNode(node, thresholds)
                ServerLamp(
                    state = lamp,
                    contentDescription = serverLampDescription(node.node ?: node.displayName, lamp),
                    reduceMotion = reduceMotion,
                )
            }
        }
    }
}
