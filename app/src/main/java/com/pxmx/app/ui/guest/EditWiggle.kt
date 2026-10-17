package com.pxmx.app.ui.guest

import kotlin.math.PI
import kotlin.math.sin

private const val WIGGLE_PERIOD_MS = 280L
private const val WIGGLE_PHASE_MS = 70L
private const val WIGGLE_HALF_MS = 140L
private const val WIGGLE_THREE_QUARTER_MS = 210L
private const val WIGGLE_PEAK_DEGREES = 2f
private const val SINE_TURNS = 2.0
private const val DRAG_ARM_MS = 240L

/**
 * Pure functions for deck card wiggling and hold-to-drag reordering in edit mode.
 */

fun editWiggleDegrees(index: Int, elapsedMs: Long, reduceMotion: Boolean): Float {
    val shiftedMs = (elapsedMs + index * WIGGLE_PHASE_MS) % WIGGLE_PERIOD_MS
    val normalizedMs = if (shiftedMs < 0L) shiftedMs + WIGGLE_PERIOD_MS else shiftedMs
    val turns = normalizedMs.toDouble() / WIGGLE_PERIOD_MS.toDouble()
    return when {
        reduceMotion || index < 0 -> 0f
        normalizedMs == 0L || normalizedMs == WIGGLE_HALF_MS -> 0f
        normalizedMs == WIGGLE_PHASE_MS -> WIGGLE_PEAK_DEGREES
        normalizedMs == WIGGLE_THREE_QUARTER_MS -> -WIGGLE_PEAK_DEGREES
        else -> (WIGGLE_PEAK_DEGREES * sin(SINE_TURNS * PI * turns)).toFloat()
    }
}

fun editDragShouldArm(heldMs: Long, travelPx: Float, slopPx: Float): Boolean =
    heldMs >= DRAG_ARM_MS && travelPx < slopPx

fun editDragShouldYieldToScroll(heldMs: Long, travelPx: Float, slopPx: Float): Boolean =
    heldMs < DRAG_ARM_MS && travelPx >= slopPx

fun editDragStep(translationYPx: Float, thresholdPx: Float): Int = when {
    translationYPx <= -thresholdPx -> -1
    translationYPx >= thresholdPx -> 1
    else -> 0
}
