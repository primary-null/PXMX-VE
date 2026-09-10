package com.pxmx.app.ui.adaptive

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.pxmx.app.ui.components.TechColors

/**
 * Interactive, draggable splitter handle for two-pane layouts on foldables and tablets.
 * Features:
 * - 16 dp touch grab strip for easy thumb/finger operation
 * - Subtle 1.5 dp hairline divider with high-tech tactile grip pill
 * - Smooth highlight and glow on drag
 * - Double-tap to snap to default 360 dp
 * - Magnetic snapping to default and center crease with haptic feedback
 */
@Composable
fun AdaptivePaneSplitter(
    currentWidthDp: Float,
    onWidthChange: (Float) -> Unit,
    onResetDefault: () -> Unit,
    modifier: Modifier = Modifier,
    minWidthDp: Float = 260f,
    maxWidthDp: Float = 540f,
    defaultWidthDp: Float = OPERATOR_PRIMARY_PANE_WIDTH_DP.toFloat(),
    creaseX: Float? = null,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var accumulatedWidth by remember(currentWidthDp) { mutableFloatStateOf(currentWidthDp) }

    val snapPoints = remember(defaultWidthDp, creaseX) {
        listOfNotNull(defaultWidthDp, creaseX).distinct()
    }

    val handleColor by animateColorAsState(
        targetValue = if (isDragging) MaterialTheme.colorScheme.primary else TechColors.Edge,
        animationSpec = tween(150),
        label = "SplitterGripColor",
    )

    val railColor by animateColorAsState(
        targetValue = if (isDragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else TechColors.Edge,
        animationSpec = tween(150),
        label = "SplitterRailColor",
    )

    Box(
        modifier = modifier
            .width(16.dp)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onResetDefault()
                    }
                )
            }
            .pointerInput(minWidthDp, maxWidthDp, snapPoints) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        accumulatedWidth = currentWidthDp
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { change, dragAmountPx ->
                        change.consume()
                        val deltaDp = with(density) { dragAmountPx.toDp().value }
                        accumulatedWidth += deltaDp

                        val targetWidth = computePaneWidth(
                            rawWidth = accumulatedWidth,
                            minWidth = minWidthDp,
                            maxWidth = maxWidthDp,
                            snapPoints = snapPoints,
                            snapThreshold = 8f,
                        )

                        // If snapped to a snap point, provide haptic feedback
                        if (snapPoints.any { kotlin.math.abs(targetWidth - it) < 0.1f } &&
                            kotlin.math.abs(currentWidthDp - targetWidth) > 0.5f) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }

                        onWidthChange(targetWidth)
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Vertical divider hairline
        Box(
            modifier = Modifier
                .width(1.5.dp)
                .fillMaxHeight()
                .background(railColor)
        )

        // Tactile grip pill
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(handleColor),
            contentAlignment = Alignment.Center,
        ) {
            // Three micro tech notches inside the grip pill
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(width = 2.dp, height = 2.dp)
                        .background(TechColors.Hull)
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(width = 2.dp, height = 2.dp)
                        .background(TechColors.Hull)
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(width = 2.dp, height = 2.dp)
                        .background(TechColors.Hull)
                )
            }
        }
    }
}
