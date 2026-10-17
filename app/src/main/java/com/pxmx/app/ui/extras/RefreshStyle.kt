package com.pxmx.app.ui.extras

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.ui.components.rememberReduceMotion
import com.pxmx.app.ui.icons.InstrumentGlyphs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.isActive

val LocalRefreshCog = compositionLocalOf { false }

data class RefreshCogActions(
    val set: (Boolean) -> Unit,
)

val LocalRefreshCogActions = compositionLocalOf<RefreshCogActions?> { null }

private const val ROCKET_CHARGE_MS = 220
private const val ROCKET_LAUNCH_MS = 560
private const val COG_MESH_MS = 140

private val INDICATOR_SIZE = 28.dp
private val RESTING_TOP_OFFSET = 16.dp
private const val COG_DECELERATION_DEG_PER_SEC = 1800f
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

@Suppress("MagicNumber", "LongMethod", "FunctionNaming", "CyclomaticComplexMethod")
@Composable
private fun RocketIndicator(
    pullFraction: Float,
    isRefreshing: Boolean,
    reduceMotion: Boolean,
    unlocked: Boolean = false,
) {
    val chargeAnim = remember { Animatable(0f) }
    val climbAnim = remember { Animatable(0f) }

    LaunchedEffect(isRefreshing, reduceMotion) {
        if (isRefreshing) {
            if (reduceMotion) {
                chargeAnim.snapTo(1f)
                climbAnim.snapTo(0f)
            } else {
                climbAnim.snapTo(0f)
                chargeAnim.snapTo(0f)
                chargeAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = ROCKET_CHARGE_MS, easing = FastOutSlowInEasing),
                )
                climbAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = ROCKET_LAUNCH_MS, easing = FastOutLinearInEasing),
                )
            }
        } else {
            chargeAnim.snapTo(0f)
            climbAnim.snapTo(0f)
        }
    }

    val chargeProgress = if (reduceMotion) 1f else chargeAnim.value
    val climbProgress = if (reduceMotion) 0f else climbAnim.value
    val rocketAlpha = when {
        !isRefreshing && pullFraction <= 0f -> 0f
        reduceMotion -> 1f
        !isRefreshing -> 1f
        climbProgress < 0.67f -> 1f
        else -> (1f - (climbProgress - 0.67f) / 0.33f).coerceIn(0f, 1f)
    }

    val density = LocalDensity.current
    val baseOffsetDp = RESTING_TOP_OFFSET
    val translationYPx = with(density) { baseOffsetDp.toPx() }

    val rocketPainter = rememberVectorPainter(InstrumentGlyphs.Rocket)

    val isLaunching = isRefreshing
    val canvasWidth = if (isLaunching) 112.dp else INDICATOR_SIZE
    val canvasHeight = if (isLaunching) 184.dp else INDICATOR_SIZE

    Box(
        modifier = Modifier
            .size(
                width = if (isLaunching) 112.dp else INDICATOR_SIZE,
                height = INDICATOR_SIZE,
            )
            .wrapContentSize(align = Alignment.BottomCenter, unbounded = true)
            .graphicsLayer { translationY = translationYPx },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(
            modifier = Modifier.size(width = canvasWidth, height = canvasHeight),
        ) {
            val glyph = INDICATOR_SIZE.toPx()
            val cx = size.width / 2f
            val rocketLeft = cx - glyph / 2f
            val padY = if (isLaunching) size.height - 8.dp.toPx() else glyph
            val climbPx = 140.dp.toPx() * climbProgress
            val nozzleY = if (isLaunching) padY - climbPx else glyph

            if (isLaunching && chargeProgress > 0.05f) {
                val lobeSpread = (if (unlocked) 40.dp else 28.dp).toPx() * (0.4f + 0.6f * chargeProgress)
                val lobeRise = (if (unlocked) 16.dp else 11.dp).toPx() * chargeProgress
                val padPath = Path().apply {
                    moveTo(cx - lobeSpread, padY)
                    lineTo(cx - lobeSpread * 0.62f, padY - lobeRise)
                    lineTo(cx - 8.dp.toPx(), padY - lobeRise * 0.35f)
                    lineTo(cx + 8.dp.toPx(), padY - lobeRise * 0.35f)
                    lineTo(cx + lobeSpread * 0.62f, padY - lobeRise)
                    lineTo(cx + lobeSpread, padY)
                    close()
                }
                drawPath(padPath, color = AMBER_FLAME_COLOR.copy(alpha = 0.5f))

                val plumeHalf = (if (unlocked) 32.dp else 24.dp).toPx() * (0.35f + 0.65f * chargeProgress)
                val nozzleHalf = 2.5.dp.toPx()
                val trailTop = nozzleY.coerceAtMost(padY - 1f)
                val trailFade = (1f - climbProgress * 0.35f).coerceIn(0.4f, 1f)
                val plumePath = Path().apply {
                    moveTo(cx - nozzleHalf, trailTop)
                    lineTo(cx + nozzleHalf, trailTop)
                    lineTo(cx + plumeHalf, padY)
                    lineTo(cx - plumeHalf, padY)
                    close()
                }
                drawPath(plumePath, color = AMBER_FLAME_COLOR.copy(alpha = 0.82f * trailFade))

                val coreLen = (8.dp.toPx() + 6.dp.toPx() * chargeProgress)
                    .coerceAtMost((padY - trailTop).coerceAtLeast(4.dp.toPx()))
                val corePath = Path().apply {
                    moveTo(cx - nozzleHalf, trailTop)
                    lineTo(cx + nozzleHalf, trailTop)
                    lineTo(cx, trailTop + coreLen)
                    close()
                }
                drawPath(corePath, color = CORE_FLAME_COLOR.copy(alpha = 0.95f * trailFade))
            }

            if (rocketAlpha > 0f) {
                val rocketTop = if (isLaunching) nozzleY - glyph else 0f
                translate(left = rocketLeft, top = rocketTop) {
                    with(rocketPainter) {
                        draw(size = Size(glyph, glyph), alpha = rocketAlpha)
                    }
                }
            }
        }
    }
}

@Suppress("MagicNumber", "LongMethod", "FunctionNaming", "CyclomaticComplexMethod")
@Composable
private fun CogIndicator(
    pullFraction: Float,
    isRefreshing: Boolean,
    reduceMotion: Boolean,
    unlocked: Boolean = false,
) {
    val angleState = remember { mutableFloatStateOf(0f) }
    val speedState = remember { mutableFloatStateOf(0f) }
    val fractionState = rememberUpdatedState(pullFraction)
    val refreshingState = rememberUpdatedState(isRefreshing)

    val meshAnim = remember { Animatable(if (unlocked) 1f else 0f) }
    LaunchedEffect(unlocked, reduceMotion) {
        if (unlocked) {
            if (reduceMotion) {
                meshAnim.snapTo(1f)
            } else {
                meshAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = COG_MESH_MS, easing = FastOutSlowInEasing),
                )
            }
        } else {
            meshAnim.snapTo(0f)
        }
    }

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
                    minOf(targetSpeed, currentSpeed + (900f / 0.22f) * dt)
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

    val currentAngle = angleState.floatValue
    val currentSpeed = speedState.floatValue
    val currentAlpha = if (!isRefreshing && pullFraction <= 0f) 0f else 1f

    val density = LocalDensity.current
    val baseOffsetDp = RESTING_TOP_OFFSET
    val translationYPx = with(density) { baseOffsetDp.toPx() }

    val cogPainter = rememberVectorPainter(InstrumentGlyphs.Cog)

    val showSparks = unlocked && !reduceMotion
    val meshProgress = if (reduceMotion) (if (unlocked) 1f else 0f) else meshAnim.value

    Box(
        modifier = Modifier
            .size(width = 112.dp, height = 80.dp)
            .graphicsLayer {
                translationY = translationYPx
                alpha = currentAlpha
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = 20.dp.toPx()
            val mainSize = 28.dp.toPx()
            val secSize = 16.dp.toPx()

            val unmeshedDist = 34.dp.toPx()
            val meshedDist = 19.5.dp.toPx()
            val currentDist = unmeshedDist + (meshedDist - unmeshedDist) * meshProgress
            val diagUnit = 0.7071f

            val leftCogX = cx - diagUnit * currentDist
            val leftCogY = cy + diagUnit * currentDist
            val rightCogX = cx + diagUnit * currentDist
            val rightCogY = cy + diagUnit * currentDist

            val rim = mainSize / 2f
            val leftMeshX = cx - diagUnit * rim
            val leftMeshY = cy + diagUnit * rim
            val rightMeshX = cx + diagUnit * rim
            val rightMeshY = cy + diagUnit * rim

            val drivingAngle = -currentAngle * 1.75f

            if (showSparks && meshProgress > 0.2f) {
                val speedFactor = (currentSpeed / 900f).coerceIn(0.35f, 1.2f)
                val baseLen = (12.dp.toPx() + 14.dp.toPx() * speedFactor)
                val strokeW = 2.dp.toPx()

                repeat(4) { idx ->
                    val sparkPhase = ((currentAngle / 40f + idx * 0.28f) % 1f)
                    val streakLen = baseLen * (0.6f + 0.4f * (1f - sparkPhase))
                    val angleRad = (PI * 0.75f + (idx - 1.5f) * 0.32f).toFloat()
                    val sparkAlpha = (0.5f + 0.5f * (1f - sparkPhase)) * meshProgress
                    val color = if (idx % 2 == 0) AMBER_FLAME_COLOR else CORE_FLAME_COLOR
                    val startX = leftMeshX + cos(angleRad) * 2.dp.toPx()
                    val startY = leftMeshY + sin(angleRad) * 2.dp.toPx()
                    val endX = leftMeshX + cos(angleRad) * streakLen
                    val endY = leftMeshY + sin(angleRad) * streakLen
                    drawLine(
                        color = color.copy(alpha = sparkAlpha),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round,
                    )
                }

                repeat(4) { idx ->
                    val sparkPhase = ((currentAngle / 40f + idx * 0.28f) % 1f)
                    val streakLen = baseLen * (0.6f + 0.4f * (1f - sparkPhase))
                    val angleRad = (PI * 0.25f - (idx - 1.5f) * 0.32f).toFloat()
                    val sparkAlpha = (0.5f + 0.5f * (1f - sparkPhase)) * meshProgress
                    val color = if (idx % 2 == 0) AMBER_FLAME_COLOR else CORE_FLAME_COLOR
                    val startX = rightMeshX + cos(angleRad) * 2.dp.toPx()
                    val startY = rightMeshY + sin(angleRad) * 2.dp.toPx()
                    val endX = rightMeshX + cos(angleRad) * streakLen
                    val endY = rightMeshY + sin(angleRad) * streakLen
                    drawLine(
                        color = color.copy(alpha = sparkAlpha),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round,
                    )
                }
            }

            if (meshProgress > 0f) {
                rotate(degrees = drivingAngle, pivot = Offset(leftCogX, leftCogY)) {
                    translate(left = leftCogX - secSize / 2f, top = leftCogY - secSize / 2f) {
                        with(cogPainter) {
                            draw(size = Size(secSize, secSize), alpha = meshProgress)
                        }
                    }
                }

                rotate(degrees = drivingAngle, pivot = Offset(rightCogX, rightCogY)) {
                    translate(left = rightCogX - secSize / 2f, top = rightCogY - secSize / 2f) {
                        with(cogPainter) {
                            draw(size = Size(secSize, secSize), alpha = meshProgress)
                        }
                    }
                }
            }

            rotate(degrees = currentAngle, pivot = Offset(cx, cy)) {
                translate(left = cx - mainSize / 2f, top = cy - mainSize / 2f) {
                    with(cogPainter) {
                        draw(size = Size(mainSize, mainSize))
                    }
                }
            }
        }
    }
}
