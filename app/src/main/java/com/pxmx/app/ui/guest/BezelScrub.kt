package com.pxmx.app.ui.guest

/**
 * Hold-then-slide on the guest bezel (OPS, HW, NET, and the rest).
 * A fast sideways move before the hold stays a scroll. A short tap stays a click.
 */
const val BEZEL_SCRUB_HOLD_MS = 240L

fun bezelScrubShouldArm(heldMs: Long, travelPx: Float, slopPx: Float): Boolean =
    heldMs >= BEZEL_SCRUB_HOLD_MS && travelPx < slopPx

fun bezelScrubShouldYieldToScroll(heldMs: Long, travelPx: Float, slopPx: Float): Boolean =
    heldMs < BEZEL_SCRUB_HOLD_MS && travelPx >= slopPx

/** A vertical move that outruns the sideways slide leaves the bezel without selecting. */
fun bezelScrubShouldCancel(dxPx: Float, dyPx: Float, escapePx: Float): Boolean {
    val dy = kotlin.math.abs(dyPx)
    return dy > escapePx && dy > kotlin.math.abs(dxPx)
}

fun bezelScrubIndex(xPx: Float, spans: List<ClosedFloatingPointRange<Float>>): Int {
    if (spans.isEmpty()) return 0
    spans.forEachIndexed { index, span ->
        if (xPx >= span.start && xPx <= span.endInclusive) return index
    }
    return spans.indices.minBy { index ->
        val mid = (spans[index].start + spans[index].endInclusive) / 2f
        kotlin.math.abs(xPx - mid)
    }
}

/** Upward bow in px. The armed stamp gets the peak. One step away gets half. */
fun bezelScrubBowPx(index: Int, armedIndex: Int, peakPx: Float): Float {
    if (armedIndex < 0 || peakPx == 0f) return 0f
    val falloff = when (kotlin.math.abs(index - armedIndex)) {
        0 -> 1f
        1 -> 0.5f
        else -> 0f
    }
    val shift = -peakPx * falloff
    // A zero falloff times a negative peak is -0, which is not the rest pose.
    return if (shift == 0f) 0f else shift
}

fun bezelScrubScale(index: Int, armedIndex: Int, reduceMotion: Boolean): Float {
    if (reduceMotion || armedIndex < 0) return 1f
    return when (kotlin.math.abs(index - armedIndex)) {
        0 -> 1.08f
        1 -> 1.03f
        else -> 1f
    }
}
