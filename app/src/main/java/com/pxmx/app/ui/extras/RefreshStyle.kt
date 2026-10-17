package com.pxmx.app.ui.extras

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.pxmx.app.ui.components.TechSwitch
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.ui.components.rememberReduceMotion
import com.pxmx.app.ui.icons.InstrumentGlyphs
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

val LocalRefreshCog = compositionLocalOf { false }

data class RefreshCogActions(
    val set: (Boolean) -> Unit,
)

val LocalRefreshCogActions = compositionLocalOf<RefreshCogActions?> { null }

private const val rocketChargeMs = 180
private const val rocketLaunchMs = 460

private val INDICATOR_SIZE = 28.dp
private val RESTING_TOP_OFFSET = 16.dp
private const val PULL_DISTANCE_MULTIPLIER = 44f
private const val COG_DECELERATION_DEG_PER_SEC = 1800f
private const val ROCKET_LAUNCH_OFFSET_Y = -160f
private val AMBER_FLAME_COLOR = Color(0xFFFF9800)
private val CORE_FLAME_COLOR = Color(0xFFE2E8F0)

@Suppress("MagicNumber")
fun cogSpinDegreesPerSecond(pullFraction: Float, refreshing: Boolean): Float {
    if (refreshing) return 900f
    val fraction = pullFraction.coerceIn(0f, 1f)
    return fraction * fraction * 720f
}

data class RefreshEggStreakResult(
    val streak: Int,
    val accepted: Boolean,
)

data class RefreshEggTick(
    val nowMillis: Long,
    val isRefreshing: Boolean,
    val isArmed: Boolean,
    val useCog: Boolean = false,
)

data class RefreshEggSample(
    val streak: Int = 0,
    val lastIdleMillis: Long? = null,
    val inFlight: Boolean = false,
    val lastRefreshing: Boolean = false,
    val lastAccepted: Boolean = false,
    val useCog: Boolean = false,
    val rocketUnlocked: Boolean = false,
    val cogUnlocked: Boolean = false,
)

@Suppress("MagicNumber")
fun nextRefreshEggStreak(previous: Int, idleGapMillis: Long?): RefreshEggStreakResult {
    return when {
        idleGapMillis == null -> RefreshEggStreakResult(streak = 1, accepted = true)
        idleGapMillis < 420L -> RefreshEggStreakResult(streak = previous, accepted = false)
        idleGapMillis > 750L -> RefreshEggStreakResult(streak = 1, accepted = true)
        else -> RefreshEggStreakResult(streak = minOf(3, previous + 1), accepted = true)
    }
}

@Suppress("MagicNumber")
fun stepRefreshEgg(
    sample: RefreshEggSample,
    tick: RefreshEggTick,
): RefreshEggSample {
    if (tick.useCog != sample.useCog) {
        return sample.copy(
            streak = 0,
            lastIdleMillis = null,
            inFlight = false,
            lastRefreshing = tick.isRefreshing,
            lastAccepted = false,
            useCog = tick.useCog,
        )
    }

    val isRisingEdge = !sample.lastRefreshing && tick.isRefreshing
    val isFallingEdge = sample.lastRefreshing && !tick.isRefreshing

    return when {
        isRisingEdge -> {
            if (!tick.isArmed) {
                sample.copy(
                    lastRefreshing = true,
                    lastAccepted = false,
                    inFlight = false,
                )
            } else {
                val gap = if (sample.lastIdleMillis != null) tick.nowMillis - sample.lastIdleMillis else null
                val result = nextRefreshEggStreak(sample.streak, gap)
                if (result.accepted) {
                    val newStreak = result.streak
                    val rocketUnlocked = sample.rocketUnlocked || (!tick.useCog && newStreak >= 3)
                    val cogUnlocked = sample.cogUnlocked || (tick.useCog && newStreak >= 3)
                    sample.copy(
                        streak = newStreak,
                        inFlight = true,
                        lastRefreshing = true,
                        lastAccepted = true,
                        rocketUnlocked = rocketUnlocked,
                        cogUnlocked = cogUnlocked,
                    )
                } else {
                    sample.copy(
                        lastRefreshing = true,
                        lastAccepted = false,
                        inFlight = false,
                    )
                }
            }
        }
        isFallingEdge -> {
            val newIdleMillis = if (sample.lastAccepted) tick.nowMillis else sample.lastIdleMillis
            sample.copy(
                lastIdleMillis = newIdleMillis,
                inFlight = false,
                lastRefreshing = false,
                lastAccepted = false,
            )
        }
        else -> sample
    }
}

fun stepRefreshEgg(
    sample: RefreshEggSample,
    nowMillis: Long,
    isRefreshing: Boolean,
    isArmed: Boolean,
    useCog: Boolean = sample.useCog,
): RefreshEggSample = stepRefreshEgg(
    sample = sample,
    tick = RefreshEggTick(
        nowMillis = nowMillis,
        isRefreshing = isRefreshing,
        isArmed = isArmed,
        useCog = useCog,
    ),
)

@Composable
fun RefreshCogRow(modifier: Modifier = Modifier) {
    val actions = LocalRefreshCogActions.current ?: return
    val enabled = LocalRefreshCog.current

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
                text = "COG REFRESH",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Spinning cog while you pull the list. The rocket stays the default.",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenPullRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    eggSample: RefreshEggSample = RefreshEggSample(),
) {
    val pullFraction = state.distanceFraction
    if (!isRefreshing && pullFraction <= 0f) {
        return
    }

    val useCog = LocalRefreshCog.current
    val reduceMotion = rememberReduceMotion()

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (useCog) {
            CogIndicator(
                pullFraction = pullFraction,
                isRefreshing = isRefreshing,
                reduceMotion = reduceMotion,
                unlocked = eggSample.cogUnlocked,
            )
        } else {
            RocketIndicator(
                pullFraction = pullFraction,
                isRefreshing = isRefreshing,
                reduceMotion = reduceMotion,
                unlocked = eggSample.rocketUnlocked,
            )
        }
    }
}

@Suppress("MagicNumber")
@Composable
private fun RocketIndicator(
    pullFraction: Float,
    isRefreshing: Boolean,
    reduceMotion: Boolean,
    unlocked: Boolean = false,
) {
    val flameAnim = remember { Animatable(0f) }
    val liftAnim = remember { Animatable(0f) }
    val alphaAnim = remember { Animatable(1f) }

    LaunchedEffect(isRefreshing, reduceMotion) {
        if (isRefreshing) {
            if (reduceMotion) {
                liftAnim.snapTo(0f)
                flameAnim.snapTo(0f)
                alphaAnim.snapTo(1f)
            } else {
                liftAnim.snapTo(0f)
                alphaAnim.snapTo(1f)
                flameAnim.snapTo(0f)
                flameAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = rocketChargeMs, easing = FastOutSlowInEasing),
                )
                launch {
                    liftAnim.animateTo(
                        targetValue = ROCKET_LAUNCH_OFFSET_Y,
                        animationSpec = tween(durationMillis = rocketLaunchMs, easing = FastOutLinearInEasing),
                    )
                }
                launch {
                    alphaAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = rocketLaunchMs, easing = LinearEasing),
                    )
                }
            }
        } else {
            liftAnim.snapTo(0f)
            flameAnim.snapTo(0f)
            alphaAnim.snapTo(1f)
        }
    }

    // Read animated values in composition before graphicsLayer
    val currentFlameScale = flameAnim.value
    val currentLiftOffset = liftAnim.value
    val currentAlpha = if (!isRefreshing && pullFraction <= 0f) 0f else alphaAnim.value

    val density = LocalDensity.current
    val baseOffsetDp = if (isRefreshing) {
        RESTING_TOP_OFFSET
    } else {
        RESTING_TOP_OFFSET + (pullFraction.coerceIn(0f, 1.5f) * PULL_DISTANCE_MULTIPLIER).dp
    }
    val translationYPx = with(density) { baseOffsetDp.toPx() } + currentLiftOffset

    val rocketPainter = rememberVectorPainter(InstrumentGlyphs.Rocket)

    Box(
        modifier = Modifier.graphicsLayer {
            translationY = translationYPx
            alpha = currentAlpha
        },
    ) {
        val showCloud = unlocked && isRefreshing
        Canvas(
            modifier = Modifier.size(
                width = INDICATOR_SIZE,
                height = if (showCloud) 72.dp else INDICATOR_SIZE,
            ),
        ) {
            val scale = size.width / 24f
            val glyph = Size(size.width, size.width)

            if (showCloud) {
                if (reduceMotion) {
                    drawOval(
                        color = AMBER_FLAME_COLOR.copy(alpha = 0.5f),
                        topLeft = Offset(6f * scale, 18.5f * scale),
                        size = Size(12f * scale, 9f * scale),
                    )
                } else {
                    val riseFraction = (-currentLiftOffset / -ROCKET_LAUNCH_OFFSET_Y).coerceIn(0f, 1f)
                    val growth = if (currentLiftOffset < 0f) {
                        0.75f + 0.45f * riseFraction
                    } else {
                        0.3f + 0.7f * currentFlameScale
                    }
                    if (growth > 0.05f) {
                        drawOval(
                            color = AMBER_FLAME_COLOR.copy(alpha = 0.55f),
                            topLeft = Offset((4f - growth) * scale, 18.5f * scale),
                            size = Size((8f + growth) * scale, (8f * growth) * scale),
                        )
                        drawOval(
                            color = AMBER_FLAME_COLOR.copy(alpha = 0.55f),
                            topLeft = Offset((12f - growth) * scale, 18.5f * scale),
                            size = Size((8f + growth) * scale, (8f * growth) * scale),
                        )
                        drawOval(
                            color = CORE_FLAME_COLOR.copy(alpha = 0.4f),
                            topLeft = Offset((7f - growth) * scale, (22f + 2f * growth) * scale),
                            size = Size((10f + 2f * growth) * scale, (9f * growth) * scale),
                        )
                    }
                }
            }

            with(rocketPainter) {
                draw(glyph)
            }
            if (currentFlameScale > 0f) {
                val flamePath = Path().apply {
                    moveTo(10.5f * scale, 17.5f * scale)
                    lineTo(13.5f * scale, 17.5f * scale)
                    lineTo(12f * scale, (17.5f + 6.5f * currentFlameScale) * scale)
                    close()
                }
                drawPath(flamePath, color = AMBER_FLAME_COLOR)
                val corePath = Path().apply {
                    moveTo(11.2f * scale, 17.5f * scale)
                    lineTo(12.8f * scale, 17.5f * scale)
                    lineTo(12f * scale, (17.5f + 3.5f * currentFlameScale) * scale)
                    close()
                }
                drawPath(corePath, color = CORE_FLAME_COLOR)
            }
        }
    }
}

@Suppress("MagicNumber")
@Composable
private fun CogIndicator(
    pullFraction: Float,
    isRefreshing: Boolean,
    reduceMotion: Boolean,
    unlocked: Boolean = false,
) {
    val angleState = remember { mutableFloatStateOf(0f) }
    val speedState = remember { mutableFloatStateOf(0f) }
    // pullFraction changes every frame. Keying the loop on it restarts the
    // frame clock and the cog never gathers speed.
    val fractionState = rememberUpdatedState(pullFraction)
    val refreshingState = rememberUpdatedState(isRefreshing)

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            angleState.floatValue = 0f
            speedState.floatValue = 0f
            return@LaunchedEffect
        }
        var lastFrameTimeNanos = 0L
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTimeNanos == 0L) {
                    lastFrameTimeNanos = frameTimeNanos
                }
                val dt = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
                lastFrameTimeNanos = frameTimeNanos

                val targetSpeed = cogSpinDegreesPerSecond(
                    fractionState.value,
                    refreshingState.value,
                )
                val currentSpeed = speedState.floatValue
                val newSpeed = if (targetSpeed >= currentSpeed) {
                    targetSpeed
                } else {
                    maxOf(0f, currentSpeed - COG_DECELERATION_DEG_PER_SEC * dt)
                }
                speedState.floatValue = newSpeed
                if (newSpeed > 0f) {
                    angleState.floatValue = (angleState.floatValue + newSpeed * dt) % 360f
                }
            }
        }
    }

    // Read cog angle and speed in composition before graphicsLayer
    val currentAngle = angleState.floatValue
    val currentSpeed = speedState.floatValue
    val currentAlpha = if (!isRefreshing && pullFraction <= 0f) 0f else 1f

    val density = LocalDensity.current
    val baseOffsetDp = if (isRefreshing) {
        RESTING_TOP_OFFSET
    } else {
        RESTING_TOP_OFFSET + (pullFraction.coerceIn(0f, 1.5f) * PULL_DISTANCE_MULTIPLIER).dp
    }
    val translationYPx = with(density) { baseOffsetDp.toPx() }

    val cogPainter = rememberVectorPainter(InstrumentGlyphs.Cog)

    val showSparks = unlocked && currentSpeed >= 480f && !reduceMotion

    Box(
        modifier = Modifier
            .size(width = 96.dp, height = INDICATOR_SIZE)
            .graphicsLayer {
                translationY = translationYPx
                alpha = currentAlpha
            },
        contentAlignment = Alignment.Center,
    ) {
        if (showSparks) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scale = size.height / 24f
                val cx = size.width / 2f
                val cy = size.height / 2f
                val reach = (7f + ((currentSpeed - 480f) / 420f).coerceIn(0f, 1f) * 10f) * scale
                val phase = currentAngle / 360f
                repeat(3) { index ->
                    val along = ((phase * 3f + index * 0.37f) % 1f)
                    val yOff = (index - 1) * 4.2f * scale
                    val sparkAlpha = 0.45f + 0.55f * (1f - along)
                    drawLine(
                        color = AMBER_FLAME_COLOR.copy(alpha = sparkAlpha),
                        start = Offset(cx - 11f * scale, cy + yOff * 0.25f),
                        end = Offset(cx - 11f * scale - reach * (0.45f + along), cy + yOff),
                        strokeWidth = 1.6f * scale,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = CORE_FLAME_COLOR.copy(alpha = sparkAlpha),
                        start = Offset(cx + 11f * scale, cy + yOff * 0.25f),
                        end = Offset(cx + 11f * scale + reach * (0.45f + along), cy + yOff),
                        strokeWidth = 1.6f * scale,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        Box(
            modifier = Modifier.graphicsLayer {
                rotationZ = currentAngle
            },
        ) {
            Canvas(modifier = Modifier.size(INDICATOR_SIZE)) {
                with(cogPainter) {
                    draw(size)
                }
            }
        }
    }
}
