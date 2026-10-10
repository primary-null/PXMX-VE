package com.pxmx.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.data.model.HistoryTimeframe
import com.pxmx.app.data.model.RrdPoint
import com.pxmx.app.ui.util.formatBytes
import com.pxmx.app.ui.util.formatPercent
import com.pxmx.app.ui.util.formatRate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun HistoryTimeframe.chipLabel(): String = when (this) {
    HistoryTimeframe.HOUR -> "1h"
    HistoryTimeframe.DAY -> "1d"
}

private fun HistoryTimeframe.axisCaption(): String = when (this) {
    HistoryTimeframe.HOUR -> "PAST HOUR"
    HistoryTimeframe.DAY -> "PAST DAY"
}

private fun HistoryRowId.label(): String = when (this) {
    HistoryRowId.CPU -> "CPU"
    HistoryRowId.MEM -> "MEM"
    HistoryRowId.NET -> "NET"
    HistoryRowId.DISK_IO -> "DISK I/O"
    HistoryRowId.ROOT -> "ROOT"
    HistoryRowId.PSI -> "PSI"
}

@Composable
private fun HistoryRowId.color(): Color = when (this) {
    HistoryRowId.CPU -> TechColors.LinkGreen
    HistoryRowId.MEM -> TechColors.CoolBlue
    HistoryRowId.NET -> Color(0xFFCE93D8)
    HistoryRowId.DISK_IO -> TechColors.Amber
    HistoryRowId.ROOT -> TechColors.Amber
    HistoryRowId.PSI -> TechColors.Mute
}

@Composable
fun TechHistoryGraph(
    points: List<RrdPoint>,
    isGuest: Boolean,
    modifier: Modifier = Modifier,
    timeframe: HistoryTimeframe = HistoryTimeframe.HOUR,
    onTimeframe: ((HistoryTimeframe) -> Unit)? = null,
) {
    val rows = remember(points, isGuest) { historyRows(isGuest, points) }
    var expanded by remember { mutableStateOf<HistoryRowId?>(HistoryRowId.CPU) }
    var scrubIndex by remember { mutableStateOf<Int?>(null) }
    val active = expanded?.takeIf { it in rows } ?: rows.firstOrNull()
    LaunchedEffect(timeframe, points.size, points.firstOrNull()?.time, points.lastOrNull()?.time) {
        scrubIndex = null
    }

    val nowSec = remember { System.currentTimeMillis() / 1000 }
    val startTime = points.firstOrNull()?.time ?: (nowSec - 3600)
    val endTime = points.lastOrNull()?.time ?: nowSec
    val timeFmt = remember(timeframe) {
        SimpleDateFormat(if (timeframe == HistoryTimeframe.DAY) "MM-dd HH:mm" else "HH:mm", Locale.US)
    }
    val startLabel = remember(startTime, timeframe) { timeFmt.format(Date(startTime * 1000)) }
    val endLabel = remember(endTime, timeframe) { timeFmt.format(Date(endTime * 1000)) }

    TechPlate(
        modifier = modifier.fillMaxWidth(),
        railColor = active?.color() ?: TechColors.LinkGreen,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "HISTORY",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HistoryTimeframe.entries.forEach { tf ->
                        val selected = tf == timeframe
                        val shape = CutCornerShape(bottomEnd = 6.dp)
                        Box(
                            modifier = Modifier
                                .clip(shape)
                                .background(if (selected) TechColors.LinkGreen.copy(alpha = 0.2f) else TechColors.Hull)
                                .border(
                                    1.dp,
                                    if (selected) TechColors.LinkGreen else TechColors.Edge,
                                    shape,
                                )
                                .clickable(enabled = onTimeframe != null) { onTimeframe?.invoke(tf) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = tf.chipLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                letterSpacing = 0.8.sp,
                                color = if (selected) TechColors.LinkGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (points.isEmpty() || rows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(TechColors.Deck.copy(alpha = 0.4f))
                        .border(1.dp, TechColors.Edge.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "NO DATA",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                rows.forEach { row ->
                    val selected = row == active
                    HistorySparkRow(
                        row = row,
                        points = points,
                        isGuest = isGuest,
                        expanded = selected,
                        scrubIndex = if (selected) scrubIndex else null,
                        onSelect = {
                            if (expanded != row) {
                                expanded = row
                                scrubIndex = null
                            }
                        },
                        onScrub = { scrubIndex = it },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(2.dp))
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
                    text = timeframe.axisCaption(),
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
private fun HistorySparkRow(
    row: HistoryRowId,
    points: List<RrdPoint>,
    isGuest: Boolean,
    expanded: Boolean,
    scrubIndex: Int?,
    onSelect: () -> Unit,
    onScrub: (Int?) -> Unit,
) {
    val color = row.color()
    val readoutIndex = scrubIndex?.coerceIn(0, points.lastIndex)
    val readoutPoint = readoutIndex?.let { points[it] }
        ?: lastPointWith(points) { rowHasValue(row, it) }
    val valueText = rowValueText(row, readoutPoint, isGuest)
    val height = if (expanded) 88.dp else 40.dp
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.label(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = if (expanded) color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .background(TechColors.Deck.copy(alpha = 0.4f))
                .border(1.dp, if (expanded) color.copy(alpha = 0.5f) else TechColors.Edge.copy(alpha = 0.5f))
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .then(
                    if (expanded) {
                        Modifier.pointerInput(points.size) {
                            detectTapGestures(
                                onTap = { offset ->
                                    onScrub(indexAt(offset.x, size.width.toFloat(), points.size))
                                },
                            )
                        }.pointerInput(points.size) {
                            detectHorizontalDragGestures(
                                onDragEnd = { },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    onScrub(indexAt(change.position.x, size.width.toFloat(), points.size))
                                },
                            )
                        }
                    } else {
                        Modifier.clickable(onClick = onSelect)
                    },
                ),
        ) {
            HistoryCanvas(
                points = points,
                row = row,
                isGuest = isGuest,
                lineColor = color,
                scrubIndex = readoutIndex,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun indexAt(x: Float, width: Float, count: Int): Int {
    if (count <= 1 || width <= 0f) return 0
    val t = (x / width).coerceIn(0f, 1f)
    return (t * (count - 1)).roundToInt()
}

private fun rowValueText(row: HistoryRowId, point: RrdPoint?, isGuest: Boolean): String {
    if (point == null) return "—"
    return when (row) {
        HistoryRowId.CPU -> {
            val cpu = formatPercent(cpuPercent(point.cpu))
            val io = cpuPercent(point.iowait)
            if (io != null) "$cpu  IO ${formatPercent(io)}" else cpu
        }
        HistoryRowId.MEM -> {
            val pct = formatPercent(memPercent(point))
            val base = if (point.mem != null && point.maxmem != null) {
                "$pct  ${formatBytes(point.mem)} / ${formatBytes(point.maxmem)}"
            } else pct
            val extra = if (isGuest) {
                point.memhost?.let { "  HOST ${formatBytes(it)}" }
            } else {
                point.arcsize?.let { "  ARC ${formatBytes(it)}" }
            }
            if (extra != null) "$base$extra" else base
        }
        HistoryRowId.NET -> "↑ ${formatRate(point.netin)}  ↓ ${formatRate(point.netout)}"
        HistoryRowId.DISK_IO -> "R ${formatRate(point.diskread)}  W ${formatRate(point.diskwrite)}"
        HistoryRowId.ROOT -> {
            val pct = formatPercent(rootPercent(point))
            if (point.disk != null && point.maxdisk != null) {
                "$pct  ${formatBytes(point.disk)} / ${formatBytes(point.maxdisk)}"
            } else pct
        }
        HistoryRowId.PSI -> {
            val cpuPsi = formatPercent(psiPercent(point.pressurecpusome))
            val memPsi = formatPercent(psiPercent(point.pressurememorysome))
            "CPU $cpuPsi  MEM $memPsi"
        }
    }
}

@Composable
private fun HistoryCanvas(
    points: List<RrdPoint>,
    row: HistoryRowId,
    isGuest: Boolean,
    lineColor: Color,
    scrubIndex: Int?,
    modifier: Modifier = Modifier,
) {
    val dividerColor = TechColors.Divider
    val overlayColor = TechColors.Amber
    val netOutColor = TechColors.CoolBlue
    val diskWriteColor = TechColors.Danger.copy(alpha = 0.85f)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f || points.isEmpty()) return@Canvas

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

        when (row) {
            HistoryRowId.CPU -> {
                drawPercentSeries(
                    points = points,
                    color = lineColor,
                    overlayColor = overlayColor,
                    overlay = { cpuPercent(it.iowait) },
                    value = { cpuPercent(it.cpu) },
                )
            }
            HistoryRowId.MEM -> {
                drawPercentSeries(
                    points = points,
                    color = lineColor,
                    overlayColor = overlayColor,
                    overlay = if (isGuest) { { memhostPercent(it) } } else { { arcPercent(it) } },
                    value = { memPercent(it) },
                )
            }
            HistoryRowId.ROOT -> drawPercentSeries(points, lineColor) { rootPercent(it) }
            HistoryRowId.NET -> drawBidirectional(
                points = points,
                upColor = lineColor,
                downColor = netOutColor,
                axisColor = dividerColor,
                up = { it.netin },
                down = { it.netout },
            )
            HistoryRowId.DISK_IO -> drawBidirectional(
                points = points,
                upColor = lineColor,
                downColor = diskWriteColor,
                axisColor = dividerColor,
                up = { it.diskread },
                down = { it.diskwrite },
            )
            HistoryRowId.PSI -> {
                drawPercentSeries(
                    points = points,
                    color = lineColor,
                    overlayColor = overlayColor,
                    overlay = { psiPercent(it.pressurememorysome) },
                    value = { psiPercent(it.pressurecpusome) },
                )
            }
        }

        if (scrubIndex != null && points.size > 1) {
            val x = scrubIndex.toFloat() / (points.size - 1).toFloat() * w
            drawLine(
                color = lineColor.copy(alpha = 0.7f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1.2f,
            )
        }
    }
}

private fun DrawScope.drawPercentSeries(
    points: List<RrdPoint>,
    color: Color,
    overlayColor: Color = color,
    overlay: ((RrdPoint) -> Double?)? = null,
    value: (RrdPoint) -> Double?,
) {
    drawLineSeries(
        points = points,
        color = color,
        fill = true,
        minVal = 0.0,
        maxVal = 100.0,
        value = value,
    )
    if (overlay != null) {
        drawLineSeries(
            points = points,
            color = overlayColor,
            fill = false,
            minVal = 0.0,
            maxVal = 100.0,
            value = overlay,
            strokeWidth = 1.4f,
        )
    }
}

private fun DrawScope.drawBidirectional(
    points: List<RrdPoint>,
    upColor: Color,
    downColor: Color,
    axisColor: Color,
    up: (RrdPoint) -> Double?,
    down: (RrdPoint) -> Double?,
) {
    val w = size.width
    val h = size.height
    val mid = h / 2f
    drawLine(
        color = axisColor.copy(alpha = 0.8f),
        start = Offset(0f, mid),
        end = Offset(w, mid),
        strokeWidth = 1f,
    )
    val peak = points.maxOf { p ->
        maxOf(abs(up(p) ?: 0.0), abs(down(p) ?: 0.0))
    }.coerceAtLeast(1.0)
    drawLineSeries(
        points = points,
        color = upColor,
        fill = true,
        minVal = 0.0,
        maxVal = peak,
        value = up,
        yZero = mid,
        yPositiveIsUp = true,
        halfHeight = mid,
    )
    drawLineSeries(
        points = points,
        color = downColor,
        fill = true,
        minVal = 0.0,
        maxVal = peak,
        value = down,
        yZero = mid,
        yPositiveIsUp = false,
        halfHeight = mid,
    )
}

private fun DrawScope.drawLineSeries(
    points: List<RrdPoint>,
    color: Color,
    fill: Boolean,
    minVal: Double,
    maxVal: Double,
    value: (RrdPoint) -> Double?,
    strokeWidth: Float = 1.8f,
    yZero: Float? = null,
    yPositiveIsUp: Boolean = true,
    halfHeight: Float? = null,
) {
    val w = size.width
    val h = size.height
    val n = points.size
    if (n == 0) return
    val xStep = if (n > 1) w / (n - 1) else w
    val range = (maxVal - minVal).coerceAtLeast(0.001)
    val baseline = yZero ?: h

    fun yFor(v: Double): Float {
        val norm = ((v - minVal) / range).coerceIn(0.0, 1.0).toFloat()
        return if (halfHeight != null && yZero != null) {
            if (yPositiveIsUp) yZero - norm * (halfHeight - 3f)
            else yZero + norm * (halfHeight - 3f)
        } else {
            h - (norm * (h - 6f)) - 3f
        }
    }

    var segment = mutableListOf<Offset>()
    fun flush() {
        if (segment.isEmpty()) return
        if (fill && segment.size >= 2) {
            val path = Path()
            path.moveTo(segment.first().x, baseline)
            path.lineTo(segment.first().x, segment.first().y)
            for (i in 1 until segment.size) {
                path.lineTo(segment[i].x, segment[i].y)
            }
            path.lineTo(segment.last().x, baseline)
            path.close()
            drawPath(path, color.copy(alpha = 0.16f))
        }
        if (segment.size == 1) {
            drawCircle(color, radius = 2.4f, center = segment.first())
        } else {
            val path = Path()
            path.moveTo(segment.first().x, segment.first().y)
            for (i in 1 until segment.size) {
                path.lineTo(segment[i].x, segment[i].y)
            }
            drawPath(path, color, style = Stroke(width = strokeWidth))
        }
        val last = segment.last()
        drawCircle(color, radius = 2.4f, center = last)
        segment = mutableListOf()
    }

    for (i in points.indices) {
        val v = value(points[i])
        if (v == null) {
            flush()
        } else {
            segment.add(Offset(i * xStep, yFor(v)))
        }
    }
    flush()
}
