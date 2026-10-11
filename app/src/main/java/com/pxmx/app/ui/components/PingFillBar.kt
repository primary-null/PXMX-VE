package com.pxmx.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pxmx.app.data.net.HostPingMath
import com.pxmx.app.data.net.PingBand
import kotlinx.coroutines.delay

fun fillFraction(latencyMs: Long): Float = HostPingMath.fillFraction(latencyMs)
fun pingBand(latencyMs: Long): PingBand = HostPingMath.band(latencyMs)

@Composable
fun pingBandColor(band: PingBand): Color = when (band) {
    PingBand.GOOD -> TechColors.LinkGreen
    PingBand.FAIR, PingBand.SLOW -> TechColors.Amber
    PingBand.BAD -> TechColors.Danger
}

@Composable
fun PingPlateFill(
    running: Boolean,
    latencyMs: Long?,
    failed: Boolean,
    modifier: Modifier = Modifier,
) {
    // LAN results land in tens of ms. Hold the CoolBlue load-across for the
    // same 800ms tween as storage/memory so the plate actually washes, then
    // settle to the latency fraction/color.
    var loadAcross by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        if (running) {
            loadAcross = true
            return@LaunchedEffect
        }
        if (loadAcross) {
            delay(800)
            loadAcross = false
        }
    }
    val sweeping = running || loadAcross
    val targetFraction = when {
        sweeping -> 1f
        failed -> 1f
        latencyMs != null -> HostPingMath.fillFraction(latencyMs)
        else -> 0f
    }
    val animatedUsage by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(800),
        label = "pingPlateFill",
    )
    val fillColor = when {
        failed && !sweeping -> TechColors.Danger
        sweeping -> TechColors.CoolBlue
        latencyMs != null -> pingBandColor(HostPingMath.band(latencyMs))
        else -> TechColors.CoolBlue
    }
    Box(
        modifier = modifier.drawWithContent {
            if (animatedUsage > 0f) {
                val fillWidth = size.width * animatedUsage
                drawRect(
                    color = fillColor.copy(alpha = 0.10f),
                    size = size.copy(width = fillWidth),
                )
                drawLine(
                    color = fillColor.copy(alpha = 0.4f),
                    start = Offset(fillWidth, 0f),
                    end = Offset(fillWidth, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            drawContent()
        },
    )
}
