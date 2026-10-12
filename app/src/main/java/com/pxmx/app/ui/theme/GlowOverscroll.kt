package com.pxmx.app.ui.theme

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure math helpers for classic Android EdgeEffect glow overscroll.
 * Port of Lollipop–R TYPE_GLOW crescent geometry and deceleration curves.
 * Decoupled from Android runtime so it can be verified in standard JVM unit tests.
 */
@Suppress("TooManyFunctions", "MagicNumber")
object GlowOverscrollMath {
    val ANGLE: Float = (PI / 6.0).toFloat()
    const val SIN: Float = 0.5f
    const val COS: Float = 0.8660254f
    const val RADIUS_FACTOR: Float = 0.6f
    const val MAX_ALPHA: Float = 0.15f
    const val GLOW_ALPHA_START: Float = 0.6f * MAX_ALPHA // 0.09f
    const val PULL_DISTANCE_ALPHA_GLOW_FACTOR: Float = 0.8f
    const val VELOCITY_GLOW_FACTOR: Float = 6f
    const val PULL_TIME: Long = 167L
    const val PULL_DECAY_TIME: Long = 2000L
    const val RECEDE_TIME: Long = 600L
    const val MIN_VELOCITY: Int = 100
    const val MAX_VELOCITY: Int = 10000
    const val EPSILON: Float = 0.001f
    const val ACTIVE_EPSILON: Float = 0.001f
    const val PULL_GLOW_BEGIN: Float = 0f

    fun r(width: Float): Float = width * RADIUS_FACTOR / SIN

    fun capH(width: Float): Float = r(width) * (1f - COS)

    fun boundsH(width: Float, height: Float): Float = min(height, capH(width))

    fun baseGlowScale(width: Float, height: Float): Float {
        val cap = capH(width)
        return if (cap > 0f) min(height / width, 1f) else 1f
    }

    fun centerY(width: Float, height: Float): Float = boundsH(width, height) - r(width)

    fun clamp(value: Float, minVal: Float = 0f, maxVal: Float = 1f): Float = value.coerceIn(minVal, maxVal)

    fun translateX(width: Float, displacement: Float): Float =
        width * (displacement.coerceIn(0f, 1f) - 0.5f) / 2f

    fun decelerate(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return 1f - (1f - clamped) * (1f - clamped)
    }

    fun pullScale(pullDistance: Float, boundsH: Float): Float {
        if (pullDistance == 0f || boundsH <= 0f) return 0f
        val denom = sqrt(abs(pullDistance) * boundsH)
        val scale = if (denom == 0f) 0f else (1f - 1f / denom - 0.3f) / 0.7f
        return scale.coerceIn(0f, 1f)
    }

    fun pullAlpha(currentAlpha: Float, delta: Float): Float =
        min(MAX_ALPHA, currentAlpha + abs(delta) * PULL_DISTANCE_ALPHA_GLOW_FACTOR)

    fun absorbDuration(velocity: Float): Float {
        val vInt = abs(velocity).toInt().coerceIn(MIN_VELOCITY, MAX_VELOCITY)
        return 0.15f + vInt * 0.02f
    }

    fun absorbScaleFinish(velocity: Float): Float {
        val vInt = abs(velocity).toInt().coerceIn(MIN_VELOCITY, MAX_VELOCITY)
        return min(0.025f + (vInt * (vInt / 100) * 0.00015f) / 2f, 1f)
    }

    fun absorbAlphaFinish(velocity: Float, alphaStart: Float = GLOW_ALPHA_START): Float {
        val vInt = abs(velocity).toInt().coerceIn(MIN_VELOCITY, MAX_VELOCITY)
        return max(alphaStart, min(vInt * VELOCITY_GLOW_FACTOR * 1e-5f, MAX_ALPHA))
    }
}

/**
 * State machine for EdgeEffect glow physics per edge.
 * Pure Kotlin without Android runtime dependencies.
 */
@Suppress("MagicNumber")
class GlowEdgePhysics(
    var width: Float = 0f,
    var height: Float = 0f,
) {
    enum class State {
        IDLE,
        PULL,
        ABSORB,
        RECEDE,
        PULL_DECAY,
    }

    var state: State = State.IDLE
        internal set

    var glowAlpha: Float = 0f
        internal set

    var glowScaleY: Float = 0f
        internal set

    var displacement: Float = 0.5f
        internal set

    var targetDisplacement: Float = 0.5f
        internal set

    var pullDistance: Float = 0f
        internal set

    var alphaStart: Float = 0f
        internal set

    var alphaFinish: Float = 0f
        internal set

    var scaleStart: Float = 0f
        internal set

    var scaleFinish: Float = 0f
        internal set

    var startTime: Long = 0L
        internal set

    var duration: Float = 0f
        internal set

    val boundsH: Float
        get() = GlowOverscrollMath.boundsH(width, height)

    val isFinished: Boolean
        get() = state == State.IDLE

    fun setSize(w: Float, h: Float) {
        this.width = w
        this.height = h
    }

    fun update(now: Long) {
        if (state == State.IDLE) return

        val t = if (duration > 0f) {
            ((now - startTime).toFloat() / duration).coerceIn(0f, 1f)
        } else {
            1f
        }
        val interp = GlowOverscrollMath.decelerate(t)
        glowAlpha = alphaStart + (alphaFinish - alphaStart) * interp
        glowScaleY = scaleStart + (scaleFinish - scaleStart) * interp
        displacement = (displacement + targetDisplacement) / 2f

        if (t >= 1f - GlowOverscrollMath.EPSILON) {
            when (state) {
                State.ABSORB -> {
                    state = State.RECEDE
                    startTime = now
                    duration = GlowOverscrollMath.RECEDE_TIME.toFloat()
                    alphaStart = glowAlpha
                    alphaFinish = 0f
                    scaleStart = glowScaleY
                    scaleFinish = 0f
                }
                State.PULL -> {
                    state = State.PULL_DECAY
                    startTime = now
                    duration = GlowOverscrollMath.PULL_DECAY_TIME.toFloat()
                    alphaStart = glowAlpha
                    alphaFinish = 0f
                    scaleStart = glowScaleY
                    scaleFinish = 0f
                }
                State.PULL_DECAY -> {
                    state = State.RECEDE
                    // no re-arm
                }
                State.RECEDE -> {
                    state = State.IDLE
                    glowAlpha = 0f
                    glowScaleY = 0f
                }
                State.IDLE -> {}
            }
        }
    }

    fun onPull(delta: Float, disp: Float, now: Long, clipHeight: Float = boundsH) {
        if (state == State.PULL_DECAY && (now - startTime) < duration) {
            return
        }
        targetDisplacement = disp.coerceIn(0f, 1f)
        if (state != State.PULL) {
            glowScaleY = max(0f, glowScaleY)
        }
        state = State.PULL
        startTime = now
        duration = GlowOverscrollMath.PULL_TIME.toFloat()

        pullDistance += delta
        glowAlpha = min(
            GlowOverscrollMath.MAX_ALPHA,
            glowAlpha + abs(delta) * GlowOverscrollMath.PULL_DISTANCE_ALPHA_GLOW_FACTOR,
        )
        alphaStart = glowAlpha
        alphaFinish = glowAlpha

        val scale = GlowOverscrollMath.pullScale(pullDistance, clipHeight)
        glowScaleY = scale
        scaleStart = glowScaleY
        scaleFinish = glowScaleY
    }

    fun onRelease(now: Long) {
        pullDistance = 0f
        if (state == State.PULL || state == State.PULL_DECAY) {
            state = State.RECEDE
            startTime = now
            duration = GlowOverscrollMath.RECEDE_TIME.toFloat()
            alphaStart = glowAlpha
            alphaFinish = 0f
            scaleStart = glowScaleY
            scaleFinish = 0f
        }
    }

    fun onAbsorb(velocity: Float, now: Long) {
        state = State.ABSORB
        val vInt = abs(velocity).toInt().coerceIn(GlowOverscrollMath.MIN_VELOCITY, GlowOverscrollMath.MAX_VELOCITY)
        startTime = now
        duration = 0.15f + vInt * 0.02f

        alphaStart = GlowOverscrollMath.GLOW_ALPHA_START
        glowAlpha = alphaStart
        alphaFinish = max(
            alphaStart,
            min(vInt * GlowOverscrollMath.VELOCITY_GLOW_FACTOR * 1e-5f, GlowOverscrollMath.MAX_ALPHA),
        )

        scaleStart = max(glowScaleY, 0f)
        glowScaleY = scaleStart
        val scaleCalc = 0.025f + (vInt * (vInt / 100) * 0.00015f) / 2f
        scaleFinish = min(scaleCalc, 1f)

        targetDisplacement = 0.5f
    }

    fun absorbOrRelease(velocity: Float, now: Long) {
        if (state != State.IDLE) {
            onRelease(now)
        } else {
            onAbsorb(velocity, now)
        }
    }
}

typealias GlowEdgeState = GlowEdgePhysics.State

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

    private companion object {
        const val DEADZONE_PX = 0.5f
    }

    val topPhysics: GlowEdgePhysics = GlowEdgePhysics()
    val bottomPhysics: GlowEdgePhysics = GlowEdgePhysics()

    private val drawNode = GlowDrawModifierNode(
        color = color,
        topPhysics = topPhysics,
        bottomPhysics = bottomPhysics,
    )

    override val node: DelegatableNode = drawNode

    override val isInProgress: Boolean
        get() = !topPhysics.isFinished || !bottomPhysics.isFinished

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val consumed = performScroll(delta)
        val leftover = delta - consumed

        if (source == NestedScrollSource.UserInput) {
            val h = if (drawNode.containerHeight > 0f) drawNode.containerHeight else 1f
            val w = if (drawNode.containerWidth > 0f) drawNode.containerWidth else 1f
            val pointerX = drawNode.currentPointerX
            val disp = pointerX / w
            val now = drawNode.now()

            if (leftover.y > DEADZONE_PX) {
                topPhysics.onPull(leftover.y / h, disp, now)
                bottomPhysics.onRelease(now)
            } else if (leftover.y < -DEADZONE_PX) {
                bottomPhysics.onPull(-leftover.y / h, disp, now)
                topPhysics.onRelease(now)
            } else {
                if (delta.y > 0f) bottomPhysics.onRelease(now)
                if (delta.y < 0f) topPhysics.onRelease(now)
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
        val consumed = performFling(velocity)
        val leftoverV = velocity - consumed
        val now = drawNode.now()

        if (leftoverV.y > 0f) {
            topPhysics.absorbOrRelease(leftoverV.y, now)
        } else if (leftoverV.y < 0f) {
            bottomPhysics.absorbOrRelease(-leftoverV.y, now)
        } else {
            topPhysics.onRelease(now)
            bottomPhysics.onRelease(now)
        }
        drawNode.requestRedraw()
    }
}

/**
 * Modifier node drawing the classic Android EdgeEffect crescent glow at top and bottom.
 */
@Suppress("MagicNumber", "TooGenericExceptionCaught")
private class GlowDrawModifierNode(
    val color: Color,
    val topPhysics: GlowEdgePhysics,
    val bottomPhysics: GlowEdgePhysics,
) : DelegatingNode(), DrawModifierNode, LayoutAwareModifierNode {

    var containerWidth: Float = 0f
        private set
    var containerHeight: Float = 0f
        private set

    var pointerX: Float? = null
        private set

    val currentPointerX: Float
        get() = pointerX ?: if (containerWidth > 0f) containerWidth / 2f else 0f

    var clock: () -> Long = {
        try {
            android.os.SystemClock.uptimeMillis()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: RuntimeException) {
            System.currentTimeMillis()
        }
    }

    fun now(): Long = clock()

    init {
        delegate(
            SuspendingPointerInputModifierNode {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    pointerX = down.position.x
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId }
                        if (change == null || !change.pressed) break
                        pointerX = change.position.x
                    }
                }
            }
        )
    }

    override fun onRemeasured(size: IntSize) {
        containerWidth = size.width.toFloat()
        containerHeight = size.height.toFloat()
        topPhysics.setSize(containerWidth, containerHeight)
        bottomPhysics.setSize(containerWidth, containerHeight)
    }

    fun requestRedraw() {
        if (isAttached) {
            invalidateDraw()
        }
    }

    private fun ContentDrawScope.drawTopEdge(
        w: Float,
        r: Float,
        boundsH: Float,
        baseGlowScale: Float,
    ) {
        val translateX = GlowOverscrollMath.translateX(w, topPhysics.displacement)
        clipRect(left = 0f, top = 0f, right = w, bottom = boundsH) {
            withTransform({
                scale(
                    scaleX = 1f,
                    scaleY = min(topPhysics.glowScaleY, 1f) * baseGlowScale,
                    pivot = Offset(w / 2f, 0f),
                )
                translate(left = translateX, top = 0f)
            }) {
                // Note: AOSP used BlendMode.SrcAtop; SrcOver is used so an offscreen
                // dst-alpha-0 layer does not erase the glow.
                drawCircle(
                    color = color.copy(alpha = topPhysics.glowAlpha),
                    radius = r,
                    center = Offset(w / 2f, boundsH - r),
                    blendMode = BlendMode.SrcOver,
                )
            }
        }
    }

    private fun ContentDrawScope.drawBottomEdge(
        w: Float,
        h: Float,
        r: Float,
        boundsH: Float,
        baseGlowScale: Float,
    ) {
        val translateX = GlowOverscrollMath.translateX(w, bottomPhysics.displacement)
        withTransform({
            translate(left = 0f, top = h)
            scale(scaleX = 1f, scaleY = -1f, pivot = Offset.Zero)
        }) {
            clipRect(left = 0f, top = 0f, right = w, bottom = boundsH) {
                withTransform({
                    scale(
                        scaleX = 1f,
                        scaleY = min(bottomPhysics.glowScaleY, 1f) * baseGlowScale,
                        pivot = Offset(w / 2f, 0f),
                    )
                    translate(left = translateX, top = 0f)
                }) {
                    // Note: AOSP used BlendMode.SrcAtop; SrcOver is used so an offscreen
                    // dst-alpha-0 layer does not erase the glow.
                    drawCircle(
                        color = color.copy(alpha = bottomPhysics.glowAlpha),
                        radius = r,
                        center = Offset(w / 2f, boundsH - r),
                        blendMode = BlendMode.SrcOver,
                    )
                }
            }
        }
    }

    private fun ContentDrawScope.updateAndDrawEdge(
        physics: GlowEdgePhysics,
        now: Long,
        drawEdge: () -> Unit,
    ): Boolean {
        if (physics.state == GlowEdgePhysics.State.IDLE) return false

        physics.update(now)
        if (physics.glowAlpha > 0f && physics.glowScaleY > 0f) {
            drawEdge()
        }
        if (physics.state == GlowEdgePhysics.State.RECEDE && physics.glowScaleY == 0f) {
            physics.state = GlowEdgePhysics.State.IDLE
        }
        return physics.state != GlowEdgePhysics.State.IDLE
    }

    override fun ContentDrawScope.draw() {
        drawContent()

        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return

        if (containerWidth != w || containerHeight != h) {
            containerWidth = w
            containerHeight = h
            topPhysics.setSize(w, h)
            bottomPhysics.setSize(w, h)
        }

        val now = now()
        val r = GlowOverscrollMath.r(w)
        val boundsH = GlowOverscrollMath.boundsH(w, h)
        val baseGlowScale = GlowOverscrollMath.baseGlowScale(w, h)

        val topInvalidate = updateAndDrawEdge(topPhysics, now) {
            drawTopEdge(w, r, boundsH, baseGlowScale)
        }
        val bottomInvalidate = updateAndDrawEdge(bottomPhysics, now) {
            drawBottomEdge(w, h, r, boundsH, baseGlowScale)
        }

        if (topInvalidate || bottomInvalidate) {
            invalidateDraw()
        }
    }
}
