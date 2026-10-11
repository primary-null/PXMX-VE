package com.pxmx.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.data.net.HostPingMath
import com.pxmx.app.data.net.PingBand

fun fillFraction(latencyMs: Long): Float = HostPingMath.fillFraction(latencyMs)
fun pingBand(latencyMs: Long): PingBand = HostPingMath.band(latencyMs)

@Composable
fun pingBandColor(band: PingBand): Color = when (band) {
    PingBand.GOOD -> TechColors.LinkGreen
    PingBand.FAIR -> TechColors.CoolBlue
    PingBand.SLOW -> TechColors.Amber
    PingBand.BAD -> TechColors.Danger
}

@Composable
fun PingFillBar(
    running: Boolean,
    latencyMs: Long?,
    failed: Boolean,
    modifier: Modifier = Modifier,
    barHeight: Dp = 4.dp,
) {
    if (!running && latencyMs == null && !failed) {
        return
    }

    val (valueText, valueColor) = when {
        running -> "…" to TechColors.CoolBlue
        failed -> "TIMEOUT" to TechColors.Danger
        latencyMs != null -> {
            val band = HostPingMath.band(latencyMs)
            "${latencyMs}ms" to pingBandColor(band)
        }
        else -> "" to MaterialTheme.colorScheme.onSurface
    }

    Column(modifier = modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "PING",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            )
        }
        Spacer(Modifier.height(3.dp))
        if (running) {
            PingSweepTrack(barHeight = barHeight)
        } else {
            PingResultTrack(
                failed = failed,
                latencyMs = latencyMs,
                barHeight = barHeight,
            )
        }
    }
}

@Composable
private fun PingSweepTrack(barHeight: Dp) {
    val trackColor = TechColors.Deck
    val coolBlue = TechColors.CoolBlue
    val amber = TechColors.Amber
    val infiniteTransition = rememberInfiniteTransition(label = "ping-sweep-transition")
    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ping-sweep-progress",
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight),
    ) {
        drawRect(color = trackColor, size = size)
        val pulseWidth = size.width * 0.35f
        val startX = -pulseWidth + (size.width + pulseWidth) * sweepProgress
        val brush = Brush.horizontalGradient(
            colors = listOf(
                coolBlue.copy(alpha = 0.1f),
                coolBlue,
                amber,
                amber.copy(alpha = 0.1f),
            ),
            startX = startX,
            endX = startX + pulseWidth,
        )
        val clampLeft = startX.coerceAtLeast(0f)
        val clampRight = (startX + pulseWidth).coerceAtMost(size.width)
        if (clampRight > clampLeft) {
            drawRect(
                brush = brush,
                topLeft = Offset(clampLeft, 0f),
                size = Size(clampRight - clampLeft, size.height),
            )
        }
    }
}

@Composable
private fun PingResultTrack(
    failed: Boolean,
    latencyMs: Long?,
    barHeight: Dp,
) {
    val trackColor = TechColors.Deck
    val danger = TechColors.Danger
    val targetFraction = when {
        failed -> 1f
        latencyMs != null -> HostPingMath.fillFraction(latencyMs)
        else -> 0f
    }
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 300),
        label = "ping-fill-anim",
    )
    val fillColor = when {
        failed -> danger
        latencyMs != null -> pingBandColor(HostPingMath.band(latencyMs))
        else -> TechColors.CoolBlue
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight),
    ) {
        drawRect(color = trackColor, size = size)
        if (animatedFraction > 0f) {
            drawRect(
                color = fillColor,
                topLeft = Offset.Zero,
                size = Size(size.width * animatedFraction, size.height),
            )
        }
    }
}
