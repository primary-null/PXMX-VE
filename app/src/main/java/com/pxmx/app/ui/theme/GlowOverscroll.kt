package com.pxmx.app.ui.theme

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Pure math helpers for classic Android EdgeEffect glow overscroll.
 * Decoupled from Android runtime so it can be verified in standard JVM unit tests.
 */
object GlowOverscrollMath {
    const val DEFAULT_MAX_DISTANCE_PX: Float = 300f
    const val DEFAULT_DECAY_RATE: Float = 0.05f
    const val ACTIVE_EPSILON: Float = 0.001f
    const val DRAG_DECAY_RATE: Float = 0.1f
    const val FLING_FRAME_MS: Long = 16L
    const val GLOW_HEIGHT_FRACTION: Float = 0.18f
    const val GLOW_MIN_SCALE: Float = 0.2f
    const val GLOW_CORE_ALPHA: Float = 0.50f
    const val GLOW_MID_ALPHA: Float = 0.15f

    /** Clamps intensity to valid [0f, 1f] range. */
    fun clamp(intensity: Float): Float = intensity.coerceIn(0f, 1f)

    /**
     * Calculates intensity from leftover vertical delta.
     * Absolute delta divided by max distance, clamped to [0f, 1f].
     */
    fun intensityFromDelta(deltaY: Float, maxDistance: Float = DEFAULT_MAX_DISTANCE_PX): Float {
        if (maxDistance <= 0f) return 0f
        return (abs(deltaY) / maxDistance).coerceIn(0f, 1f)
    }

    /**
     * Accumulates delta into existing intensity, clamped to [0f, 1f].
     */
    fun accumulate(current: Float, deltaY: Float, maxDistance: Float = DEFAULT_MAX_DISTANCE_PX): Float {
        if (maxDistance <= 0f) return clamp(current)
        return (current + abs(deltaY) / maxDistance).coerceIn(0f, 1f)
    }

    /**
     * Applies a single decay step to the intensity.
     * Decreases intensity by decayRate down to 0f.
     */
    fun decayStep(current: Float, decayRate: Float = DEFAULT_DECAY_RATE): Float {
        return (current - decayRate).coerceAtLeast(0f)
    }

    /**
     * Determines whether the glow is considered active (intensity > 0.001f).
     */
    fun isActive(intensity: Float): Boolean = intensity > ACTIVE_EPSILON
}

/**
 * Custom PXMX OverscrollFactory that always draws classic EdgeEffect orange glow
 * across all Android versions (including API 31+ where platform overscroll is stretch-only).
 */
class GlowOverscrollFactory(
    val color: Color,
) : OverscrollFactory {

    override fun createOverscrollEffect(): OverscrollEffect = GlowOverscrollEffect(color)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlowOverscrollFactory) return false
        return color == other.color
    }

    override fun hashCode(): Int = color.hashCode()
}

/**
 * OverscrollEffect implementation driving top/bottom glow intensities from nested scroll deltas.
 */
class GlowOverscrollEffect(
    val color: Color,
) : OverscrollEffect {

    var topIntensity by mutableFloatStateOf(0f)
        private set

    var bottomIntensity by mutableFloatStateOf(0f)
        private set

    private val drawNode = GlowDrawModifierNode(
        color = color,
        getTopIntensity = { topIntensity },
        getBottomIntensity = { bottomIntensity },
    )

    override val node: DelegatableNode = drawNode

    override val isInProgress: Boolean
        get() = GlowOverscrollMath.isActive(topIntensity) || GlowOverscrollMath.isActive(bottomIntensity)

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val consumed = performScroll(delta)
        val leftover = delta - consumed

        if (leftover.y > 0f) {
            // Pulling past start (top of scrollable)
            topIntensity = GlowOverscrollMath.accumulate(topIntensity, leftover.y)
            bottomIntensity = 0f
        } else if (leftover.y < 0f) {
            // Pulling past end (bottom of scrollable)
            bottomIntensity = GlowOverscrollMath.accumulate(bottomIntensity, leftover.y)
            topIntensity = 0f
        } else {
            // Moving within scroll bounds, decay glow
            if (topIntensity > 0f) {
                topIntensity = GlowOverscrollMath.decayStep(
                    topIntensity,
                    GlowOverscrollMath.DRAG_DECAY_RATE,
                )
            }
            if (bottomIntensity > 0f) {
                bottomIntensity = GlowOverscrollMath.decayStep(
                    bottomIntensity,
                    GlowOverscrollMath.DRAG_DECAY_RATE,
                )
            }
        }
        drawNode.requestRedraw()
        // Consume leftover Y so the nested-scroll parent does not stretch.
        return consumed + Offset(0f, leftover.y)
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        performFling(velocity)

        // Smoothly decay any leftover glow intensity after fling/release
        while (GlowOverscrollMath.isActive(topIntensity) || GlowOverscrollMath.isActive(bottomIntensity)) {
            topIntensity = GlowOverscrollMath.decayStep(topIntensity)
            bottomIntensity = GlowOverscrollMath.decayStep(bottomIntensity)
            drawNode.requestRedraw()
            delay(GlowOverscrollMath.FLING_FRAME_MS)
        }
        topIntensity = 0f
        bottomIntensity = 0f
        drawNode.requestRedraw()
    }
}

/**
 * Modifier node drawing the classic Android EdgeEffect radial wash at top and bottom.
 */
private class GlowDrawModifierNode(
    val color: Color,
    val getTopIntensity: () -> Float,
    val getBottomIntensity: () -> Float,
) : Modifier.Node(), DrawModifierNode {

    fun requestRedraw() {
        if (isAttached) invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        // Draw normal content first
        drawContent()

        val top = getTopIntensity()
        if (GlowOverscrollMath.isActive(top)) {
            val maxH = 96.dp.toPx()
            val glowH = (size.height * GlowOverscrollMath.GLOW_HEIGHT_FRACTION)
                .coerceAtMost(maxH) * top.coerceIn(GlowOverscrollMath.GLOW_MIN_SCALE, 1f)
            val brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = GlowOverscrollMath.GLOW_CORE_ALPHA * top),
                    color.copy(alpha = GlowOverscrollMath.GLOW_MID_ALPHA * top),
                    Color.Transparent,
                ),
                center = Offset(size.width / 2f, 0f),
                radius = size.width * 0.75f,
            )
            drawRect(
                brush = brush,
                topLeft = Offset(0f, 0f),
                size = Size(size.width, glowH),
            )
        }

        val bottom = getBottomIntensity()
        if (GlowOverscrollMath.isActive(bottom)) {
            val maxH = 96.dp.toPx()
            val glowH = (size.height * GlowOverscrollMath.GLOW_HEIGHT_FRACTION)
                .coerceAtMost(maxH) * bottom.coerceIn(GlowOverscrollMath.GLOW_MIN_SCALE, 1f)
            val brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = GlowOverscrollMath.GLOW_CORE_ALPHA * bottom),
                    color.copy(alpha = GlowOverscrollMath.GLOW_MID_ALPHA * bottom),
                    Color.Transparent,
                ),
                center = Offset(size.width / 2f, size.height),
                radius = size.width * 0.75f,
            )
            drawRect(
                brush = brush,
                topLeft = Offset(0f, size.height - glowH),
                size = Size(size.width, glowH),
            )
        }
    }
}
