package com.pxmx.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.data.model.RrdPoint
import com.pxmx.app.ui.util.formatBytes
import com.pxmx.app.ui.util.formatPercent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HistoryMetric(val label: String) {
    CPU("CPU"),
    MEMORY("MEM"),
    DISK("DISK"),
    NETWORK("NET"),
}

@Composable
fun TechHistoryGraph(
    points: List<RrdPoint>,
    isGuest: Boolean,
    modifier: Modifier = Modifier,
) {
    val availableMetrics = remember(isGuest) {
        if (isGuest) {
            listOf(HistoryMetric.CPU, HistoryMetric.MEMORY, HistoryMetric.DISK, HistoryMetric.NETWORK)
        } else {
            listOf(HistoryMetric.CPU, HistoryMetric.MEMORY, HistoryMetric.NETWORK)
        }
    }

    var selectedMetric by remember { mutableStateOf(HistoryMetric.CPU) }

    val hasDiskData = remember(points) {
        points.any { it.disk != null }
    }

    val nowSec = remember { System.currentTimeMillis() / 1000 }
    val startTime = points.firstOrNull()?.time ?: (nowSec - 3600)
    val endTime = points.lastOrNull()?.time ?: nowSec
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val startLabel = remember(startTime) { timeFmt.format(Date(startTime * 1000)) }
    val endLabel = remember(endTime) { timeFmt.format(Date(endTime * 1000)) }

    TechPlate(
        modifier = modifier.fillMaxWidth(),
        railColor = when (selectedMetric) {
            HistoryMetric.CPU -> TechColors.LinkGreen
            HistoryMetric.MEMORY -> TechColors.CoolBlue
            HistoryMetric.DISK -> TechColors.Amber
            HistoryMetric.NETWORK -> Color(0xFFCE93D8)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header: Section label + Hour label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "HISTORY GRAPH",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "PAST HOUR · 1h",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Metric Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                availableMetrics.forEach { metric ->
                    val isSelected = selectedMetric == metric
                    val shape = CutCornerShape(bottomEnd = 6.dp)
                    val activeColor = when (metric) {
                        HistoryMetric.CPU -> TechColors.LinkGreen
                        HistoryMetric.MEMORY -> TechColors.CoolBlue
                        HistoryMetric.DISK -> TechColors.Amber
                        HistoryMetric.NETWORK -> Color(0xFFCE93D8)
                    }

                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .background(if (isSelected) activeColor.copy(alpha = 0.2f) else TechColors.Hull)
                            .border(
                                1.dp,
                                if (isSelected) activeColor else TechColors.Edge,
                                shape,
                            )
                            .clickable { selectedMetric = metric }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = metric.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            letterSpacing = 0.8.sp,
                            color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Latest value readout
            val latestValueText = remember(points, selectedMetric) {
                val last = points.lastOrNull()
                when (selectedMetric) {
                    HistoryMetric.CPU -> {
                        val cpu = last?.cpu
                        if (cpu != null) formatPercent(if (cpu <= 1.0) cpu * 100.0 else cpu) else "—"
                    }
                    HistoryMetric.MEMORY -> {
                        val mem = last?.mem
                        val maxmem = last?.maxmem
                        if (mem != null && maxmem != null && maxmem > 0) {
                            "${formatBytes(mem)} / ${formatBytes(maxmem)}"
                        } else if (mem != null) {
                            formatBytes(mem)
                        } else "—"
                    }
                    HistoryMetric.DISK -> {
                        val disk = last?.disk
                        val maxdisk = last?.maxdisk
                        if (disk != null && maxdisk != null && maxdisk > 0) {
                            "${formatBytes(disk)} / ${formatBytes(maxdisk)}"
                        } else if (disk != null) {
                            formatBytes(disk)
                        } else "—"
                    }
                    HistoryMetric.NETWORK -> {
                        val netin = last?.netin
                        val netout = last?.netout
                        if (netin != null && netout != null) {
                            "↓ ${formatBytes(netin)} · ↑ ${formatBytes(netout)}"
                        } else "—"
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${selectedMetric.label}: $latestValueText",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Chart area
            val isDiskMissing = selectedMetric == HistoryMetric.DISK && !hasDiskData
            val lineColor = when (selectedMetric) {
                HistoryMetric.CPU -> TechColors.LinkGreen
                HistoryMetric.MEMORY -> TechColors.CoolBlue
                HistoryMetric.DISK -> TechColors.Amber
                HistoryMetric.NETWORK -> Color(0xFFCE93D8)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(TechColors.Deck.copy(alpha = 0.4f))
                    .border(1.dp, TechColors.Edge.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (points.isEmpty()) {
                    Text(
                        text = "NO DATA",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (isDiskMissing) {
                    // Crucial brief rule: Missing disk is an empty series, NOT 0%
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "NO DISK METRICS",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TechColors.Amber,
                        )
                        Text(
                            text = "EMPTY SERIES",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    HistoryCanvas(
                        points = points,
                        metric = selectedMetric,
                        lineColor = lineColor,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Hour timestamp axis
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = startLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
                Text(
                    text = "1 HOUR TIMEFRAME",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                )
                Text(
                    text = endLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun HistoryCanvas(
    points: List<RrdPoint>,
    metric: HistoryMetric,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    val dividerColor = TechColors.Divider

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f || points.isEmpty()) return@Canvas

        // Draw 3 horizontal grid guide lines (25%, 50%, 75%)
        val gridSteps = 3
        for (i in 1..gridSteps) {
            val y = h * (i.toFloat() / (gridSteps + 1))
            drawLine(
                color = dividerColor.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
            )
        }

        // Extract series values
        val values: List<Double?> = points.map { pt ->
            when (metric) {
                HistoryMetric.CPU -> {
                    pt.cpu?.let { if (it <= 1.0) it * 100.0 else it }
                }
                HistoryMetric.MEMORY -> {
                    if (pt.mem != null && pt.maxmem != null && pt.maxmem > 0) {
                        (pt.mem.toDouble() / pt.maxmem.toDouble()) * 100.0
                    } else pt.mem?.toDouble()
                }
                HistoryMetric.DISK -> {
                    // Do NOT convert null to 0.0! A null disk is kept null to render gaps.
                    if (pt.disk != null && pt.maxdisk != null && pt.maxdisk > 0) {
                        (pt.disk.toDouble() / pt.maxdisk.toDouble()) * 100.0
                    } else pt.disk?.toDouble()
                }
                HistoryMetric.NETWORK -> {
                    val inBytes = pt.netin ?: 0L
                    val outBytes = pt.netout ?: 0L
                    (inBytes + outBytes).toDouble()
                }
            }
        }

        val nonNullVals = values.filterNotNull()
        if (nonNullVals.isEmpty()) return@Canvas

        val maxVal = when (metric) {
            HistoryMetric.CPU, HistoryMetric.MEMORY -> {
                val m = nonNullVals.maxOrNull() ?: 100.0
                if (m <= 100.0) 100.0 else m
            }
            HistoryMetric.DISK -> {
                val hasMaxDisk = points.any { it.maxdisk != null && it.maxdisk > 0 }
                if (hasMaxDisk) 100.0 else (nonNullVals.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
            }
            HistoryMetric.NETWORK -> {
                (nonNullVals.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
            }
        }

        val minVal = 0.0
        val valRange = (maxVal - minVal).coerceAtLeast(0.001)

        val n = points.size
        val xStep = if (n > 1) w / (n - 1) else w

        // Draw line segments, breaking paths over nulls (so gaps are not drawn as 0)
        var currentPath: Path? = null
        for (i in points.indices) {
            val v = values[i]
            val x = i * xStep
            if (v != null) {
                val norm = ((v - minVal) / valRange).coerceIn(0.0, 1.0).toFloat()
                val y = h - (norm * (h - 8f)) - 4f
                if (currentPath == null) {
                    currentPath = Path().apply { moveTo(x, y) }
                } else {
                    currentPath.lineTo(x, y)
                }
                // Draw point dot
                drawCircle(
                    color = lineColor,
                    radius = 2.5f,
                    center = Offset(x, y),
                )
            } else {
                // Gap: finish current path and reset
                currentPath?.let { path ->
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 2f),
                    )
                }
                currentPath = null
            }
        }

        currentPath?.let { path ->
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2f),
            )
        }
    }
}
