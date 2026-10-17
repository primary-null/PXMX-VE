package com.pxmx.app.ui.extras

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.pxmx.app.ui.components.TechSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.ui.components.rememberReduceMotion
import kotlinx.coroutines.launch

val LocalStupidAnimations = compositionLocalOf { false }

data class StupidAnimationActions(
    val set: (Boolean) -> Unit,
)

val LocalStupidAnimationActions = compositionLocalOf<StupidAnimationActions?> { null }

fun stupidPressScale(pressed: Boolean, enabled: Boolean): Float =
    if (pressed && enabled) 1.06f else 1f

fun stupidPressYaw(pressed: Boolean, enabled: Boolean): Float =
    if (pressed && enabled) 22f else 0f

fun stupidSpinTurns(enabled: Boolean): Float =
    if (enabled) 3f else 1f

private const val CHIP_FLIP_DURATION_MS = 280
private const val TAB_YAW_DURATION_MS = 280
private const val HEADER_SQUASH_DURATION_MS = 220
private const val LOG_SHAKE_DURATION_MS = 220
private const val SEARCH_WOBBLE_DURATION_MS = 280
private const val CHEVRON_EXTRA_SPIN_DEGREES = 360f

fun stupidChipFlipDegrees(enabled: Boolean): Float =
    if (enabled) 90f else 0f

fun stupidTabYawDegrees(enabled: Boolean): Float =
    if (enabled) 80f else 0f

fun stupidHeaderSquash(enabled: Boolean): Float =
    if (enabled) 0.85f else 1f

fun stupidLogShakeOffsets(enabled: Boolean): List<Float> =
    if (enabled) listOf(-8f, 8f, -4f, 0f) else listOf(0f)

fun stupidSearchWobbleDegrees(enabled: Boolean): List<Float> =
    if (enabled) listOf(-3f, 3f, 0f) else listOf(0f)

@Composable
fun StupidAnimationsRow(modifier: Modifier = Modifier) {
    val actions = LocalStupidAnimationActions.current ?: return
    val enabled = LocalStupidAnimations.current

    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        thickness = 0.5.dp,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "STUPID ANIMATIONS",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Wacky motion on taps. The deck stays still while this is off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        TechSwitch(
            checked = enabled,
            onCheckedChange = { actions.set(it) },
        )
    }
}

@Composable
fun StupidCardWrapper(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val stupidEnabled = LocalStupidAnimations.current
    val reduceMotion = rememberReduceMotion()
    val enabled = stupidEnabled && !reduceMotion

    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!enabled) isPressed = false
    }

    val targetScale = stupidPressScale(isPressed, enabled)
    val targetYaw = stupidPressYaw(isPressed, enabled)

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "stupidCardScale",
    )
    val yaw by animateFloatAsState(
        targetValue = targetYaw,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "stupidCardYaw",
    )

    Box(
        modifier = modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        isPressed = false
                    }
                }
            }
            .graphicsLayer {
                if (enabled) {
                    scaleX = scale
                    scaleY = scale
                    rotationY = yaw
                    cameraDistance = 12.dp.toPx()
                }
            },
    ) {
        content()
    }
}

@Composable
fun Modifier.stupidMenuSquash(expanded: Boolean): Modifier {
    val stupidEnabled = LocalStupidAnimations.current
    val reduceMotion = rememberReduceMotion()
    val enabled = stupidEnabled && !reduceMotion

    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }

    LaunchedEffect(expanded, enabled) {
        if (expanded && enabled) {
            scaleX.snapTo(1.2f)
            scaleY.snapTo(0.65f)
            launch {
                scaleX.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                )
            }
            launch {
                scaleY.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                )
            }
        } else {
            scaleX.snapTo(1f)
            scaleY.snapTo(1f)
        }
    }

    // Read in composition so each animation frame redraws. A graphicsLayer
    // block alone does not subscribe to Animatable.
    val squashX = scaleX.value
    val squashY = scaleY.value
    return this.graphicsLayer {
        if (enabled && expanded) {
            this.scaleX = squashX
            this.scaleY = squashY
        }
    }
}

@Composable
fun StupidDetailPane(
    identityKey: Any?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val stupidEnabled = LocalStupidAnimations.current
    val reduceMotion = rememberReduceMotion()
    val enabled = stupidEnabled && !reduceMotion

    val yaw = remember { Animatable(if (enabled && identityKey != null) 70f else 0f) }

    LaunchedEffect(identityKey, enabled) {
        if (enabled && identityKey != null) {
            yaw.snapTo(70f)
            yaw.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            )
        } else {
            yaw.snapTo(0f)
        }
    }

    val yawDegrees = yaw.value
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                if (enabled && yawDegrees != 0f) {
                    rotationY = yawDegrees
                    cameraDistance = 12.dp.toPx()
                }
            },
    ) {
        content()
    }
}

@Composable
fun Modifier.stupidChipFlip(selected: Boolean): Modifier {
    val stupidEnabled = LocalStupidAnimations.current
    val reduceMotion = rememberReduceMotion()
    val enabled = stupidEnabled && !reduceMotion

    val rotationX = remember { Animatable(0f) }
    var isFirstFrame by remember { mutableStateOf(true) }

    LaunchedEffect(selected, enabled) {
        if (isFirstFrame) {
            isFirstFrame = false
            return@LaunchedEffect
        }
        if (enabled && selected) {
            rotationX.snapTo(stupidChipFlipDegrees(true))
            rotationX.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = CHIP_FLIP_DURATION_MS, easing = FastOutSlowInEasing),
            )
        } else {
            rotationX.snapTo(0f)
        }
    }

    val flipDegrees = rotationX.value
    return this.graphicsLayer {
        if (enabled && flipDegrees != 0f) {
            this.rotationX = flipDegrees
            cameraDistance = 12.dp.toPx()
        }
    }
}

@Composable
fun Modifier.stupidTabDeckYaw(expandedKey: String): Modifier {
    val stupidEnabled = LocalStupidAnimations.current
    val reduceMotion = rememberReduceMotion()
    val enabled = stupidEnabled && !reduceMotion

    val yaw = remember { Animatable(0f) }
    var isFirstFrame by remember { mutableStateOf(true) }

    LaunchedEffect(expandedKey, enabled) {
        if (isFirstFrame) {
            isFirstFrame = false
            return@LaunchedEffect
        }
        if (enabled) {
            yaw.snapTo(stupidTabYawDegrees(true))
            yaw.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = TAB_YAW_DURATION_MS, easing = FastOutSlowInEasing),
            )
        } else {
            yaw.snapTo(0f)
        }
    }

    val yawDegrees = yaw.value
    return this.graphicsLayer {
        if (enabled && yawDegrees != 0f) {
            rotationY = yawDegrees
            cameraDistance = 12.dp.toPx()
        }
    }
}

class StupidHeaderMotionState(
    val scaleY: Float,
    val extraSpin: Float,
)

@Composable
fun rememberStupidHeaderMotion(collapsed: Boolean): StupidHeaderMotionState {
    val stupidEnabled = LocalStupidAnimations.current
    val reduceMotion = rememberReduceMotion()
    val enabled = stupidEnabled && !reduceMotion

    val extraSpin = remember { Animatable(0f) }
    val scaleY = remember { Animatable(1f) }
    var isFirstFrame by remember { mutableStateOf(true) }

    LaunchedEffect(collapsed, enabled) {
        if (isFirstFrame) {
            isFirstFrame = false
            return@LaunchedEffect
        }
        if (enabled) {
            extraSpin.snapTo(CHEVRON_EXTRA_SPIN_DEGREES)
            scaleY.snapTo(stupidHeaderSquash(true))
            launch {
                extraSpin.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = HEADER_SQUASH_DURATION_MS, easing = FastOutSlowInEasing),
                )
            }
            launch {
                scaleY.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = HEADER_SQUASH_DURATION_MS, easing = FastOutSlowInEasing),
                )
            }
        } else {
            extraSpin.snapTo(0f)
            scaleY.snapTo(1f)
        }
    }

    return remember(enabled, scaleY.value, extraSpin.value) {
        StupidHeaderMotionState(
            scaleY = if (enabled) scaleY.value else 1f,
            extraSpin = if (enabled) extraSpin.value else 0f,
        )
    }
}

@Composable
fun Modifier.stupidLogShake(shakeToken: Int): Modifier {
    val stupidEnabled = LocalStupidAnimations.current
    val reduceMotion = rememberReduceMotion()
    val enabled = stupidEnabled && !reduceMotion

    val translationX = remember { Animatable(0f) }
    val density = LocalDensity.current

    LaunchedEffect(shakeToken, enabled) {
        if (shakeToken == 0 || !enabled) {
            translationX.snapTo(0f)
            return@LaunchedEffect
        }
        // Offsets are dp. graphicsLayer translationX is pixels.
        val offsets = stupidLogShakeOffsets(true)
        val stepDuration = LOG_SHAKE_DURATION_MS / offsets.size
        for (targetOffset in offsets) {
            val targetPx = with(density) { targetOffset.dp.toPx() }
            translationX.animateTo(
                targetValue = targetPx,
                animationSpec = tween(durationMillis = stepDuration, easing = LinearEasing),
            )
        }
        translationX.snapTo(0f)
    }

    val shakePx = translationX.value
    return this.graphicsLayer {
        if (enabled && shakePx != 0f) {
            this.translationX = shakePx
        }
    }
}

@Composable
fun Modifier.stupidSearchWobble(): Modifier {
    val stupidEnabled = LocalStupidAnimations.current
    val reduceMotion = rememberReduceMotion()
    val enabled = stupidEnabled && !reduceMotion

    val rotationZ = remember { Animatable(0f) }
    var wasFocused by remember { mutableStateOf(false) }
    var isFirstFocusEvent by remember { mutableStateOf(true) }
    var focusToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(focusToken, enabled) {
        if (focusToken == 0 || !enabled) {
            rotationZ.snapTo(0f)
            return@LaunchedEffect
        }
        val steps = stupidSearchWobbleDegrees(true)
        val stepDuration = SEARCH_WOBBLE_DURATION_MS / steps.size
        for (targetDegrees in steps) {
            rotationZ.animateTo(
                targetValue = targetDegrees,
                animationSpec = tween(durationMillis = stepDuration, easing = LinearEasing),
            )
        }
        rotationZ.snapTo(0f)
    }

    val wobbleDegrees = rotationZ.value
    return this
        .onFocusChanged { focusState ->
            val nowFocused = focusState.isFocused
            if (isFirstFocusEvent) {
                isFirstFocusEvent = false
                wasFocused = nowFocused
                return@onFocusChanged
            }
            if (nowFocused && !wasFocused) {
                if (enabled) {
                    focusToken++
                }
            }
            wasFocused = nowFocused
        }
        .graphicsLayer {
            if (enabled && wobbleDegrees != 0f) {
                this.rotationZ = wobbleDegrees
            }
        }
}
