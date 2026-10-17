package com.pxmx.app.ui.guest

import kotlin.math.PI
import kotlin.math.sin

/**
 * Pure functions for deck card wiggling and hold-to-drag reordering in edit mode.
 */

fun editWiggleDegrees(index: Int, elapsedMs: Long, reduceMotion: Boolean): Float {
    if (reduceMotion || index < 0) return 0f
    val shiftedMs = (elapsedMs + index * 70L) % 280L
    val normalizedMs = if (shiftedMs < 0L) shiftedMs + 280L else shiftedMs
    if (normalizedMs == 0L || normalizedMs == 140L) return 0f
    if (normalizedMs == 70L) return 2f
    if (normalizedMs == 210L) return -2f
    val angle = 2.0 * PI * (normalizedMs.toDouble() / 280.0)
    return (2.0 * sin(angle)).toFloat()
}

fun editDragShouldArm(heldMs: Long, travelPx: Float, slopPx: Float): Boolean =
    heldMs >= 240L && travelPx < slopPx

fun editDragShouldYieldToScroll(heldMs: Long, travelPx: Float, slopPx: Float): Boolean =
    heldMs < 240L && travelPx >= slopPx

fun editDragStep(translationYPx: Float, thresholdPx: Float): Int = when {
    translationYPx <= -thresholdPx -> -1
    translationYPx >= thresholdPx -> 1
    else -> 0
}
