package com.pxmx.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.ui.login.NetworkScanState

/**
 * Scan visualization modes.
 * SCAN_PICTURE is the default (~72dp cyberdeck plate: server stack, cable, router).
 * SWEEP_STRIP and ADDRESS_BUS are legacy alternate modes.
 */
enum class ScanVizMode {
    SCAN_PICTURE,
    SWEEP_STRIP,
    ADDRESS_BUS,
}

object ScanViz {
    /**
     * One-constant swap. Default is SCAN_PICTURE (server stack, cable, router).
     * Assign ADDRESS_BUS or SWEEP_STRIP here to test legacy strips.
     */
    val mode: ScanVizMode = ScanVizMode.SCAN_PICTURE
}

private const val SCAN_CYCLE_DURATION_MS = 1600
private const val PACKET_TRAVEL_DURATION_MS = 650f
private const val ANTENNA_EXPAND_DURATION_MS = 650f
private const val SCAN_FADE_OUT_DURATION_MS = 150

/**
 * 72dp cyberdeck scan plate: three-unit server stack on the left, horizontal cable,
 * and two-antenna router on the right.
 * While scanning, an amber packet travels across the cable (first 650ms), two antenna arcs
 * expand and fade (next 650ms), followed by 300ms of quiet.
 * On complete or STOP, the cycle is cancelled and fades out within 150ms leaving quiet devices and cable.
 * Indicator lamps under the picture show link green (verified), amber (detected-only), and mute (unverified).
 */
@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod", "MagicNumber")
@Composable
fun ScanPicturePlate(
    scanState: NetworkScanState,
    modifier: Modifier = Modifier,
) {
    val deckColor = TechColors.Deck
    val edgeColor = TechColors.Edge
    val amberColor = TechColors.Amber
    val linkGreenColor = TechColors.LinkGreen
    val muteColor = TechColors.Mute
    val metalColor = Color(0xFFE2E8F0)

    val isScanning = scanState.isScanning

    val infiniteTransition = rememberInfiniteTransition(label = "scanDiagramTransition")
    val cycleTimeMs by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SCAN_CYCLE_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scanCycleTime",
    )

    val scanFxAlpha by animateFloatAsState(
        targetValue = if (isScanning) 1f else 0f,
        animationSpec = tween(durationMillis = SCAN_FADE_OUT_DURATION_MS),
        label = "scanFxAlpha",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(TechPlateShape)
            .background(deckColor)
            .border(1.dp, edgeColor, TechPlateShape),
    ) {
        val w = size.width
        val diagramY = 28.dp.toPx()
        val lampsY = 58.dp.toPx()

        // 1. Left: Three-unit server stack (stationary)
        val serverLeft = 14.dp.toPx()
        val serverW = 36.dp.toPx()
        val unitH = 7.dp.toPx()
        val unitGap = 2.dp.toPx()
        val totalStackH = 3 * unitH + 2 * unitGap
        val stackTop = diagramY - totalStackH / 2f

        for (u in 0 until 3) {
            val uTop = stackTop + u * (unitH + unitGap)
            drawRect(
                color = TechColors.DarkHull,
                topLeft = Offset(serverLeft, uTop),
                size = Size(serverW, unitH),
            )
            drawRect(
                color = edgeColor,
                topLeft = Offset(serverLeft, uTop),
                size = Size(serverW, unitH),
                style = Stroke(width = 1.dp.toPx()),
            )
            // Mounting screw dots on rack ears
            drawCircle(
                color = metalColor,
                radius = 0.8.dp.toPx(),
                center = Offset(serverLeft + 2.dp.toPx(), uTop + unitH / 2f),
            )
            drawCircle(
                color = metalColor,
                radius = 0.8.dp.toPx(),
                center = Offset(serverLeft + serverW - 2.dp.toPx(), uTop + unitH / 2f),
            )
            // Drive bay slits
            val driveStart = serverLeft + 5.dp.toPx()
            for (d in 0 until 3) {
                val dx = driveStart + d * 6.dp.toPx()
                drawRect(
                    color = edgeColor.copy(alpha = 0.6f),
                    topLeft = Offset(dx, uTop + 1.5.dp.toPx()),
                    size = Size(4.5.dp.toPx(), unitH - 3.dp.toPx()),
                )
            }
            // Status LED
            val ledColor = when (u) {
                0 -> amberColor
                1 -> linkGreenColor
                else -> muteColor
            }
            drawCircle(
                color = ledColor,
                radius = 1.dp.toPx(),
                center = Offset(serverLeft + serverW - 6.dp.toPx(), uTop + unitH / 2f),
            )
        }

        // 2. Right: Two-antenna router (stationary)
        val routerW = 34.dp.toPx()
        val routerH = 12.dp.toPx()
        val routerLeft = w - 14.dp.toPx() - routerW
        val routerTop = diagramY - routerH / 2f

        drawRect(
            color = TechColors.DarkHull,
            topLeft = Offset(routerLeft, routerTop),
            size = Size(routerW, routerH),
        )
        drawRect(
            color = edgeColor,
            topLeft = Offset(routerLeft, routerTop),
            size = Size(routerW, routerH),
            style = Stroke(width = 1.dp.toPx()),
        )

        // Faceplate LED indicators
        for (l in 0 until 3) {
            val lx = routerLeft + 6.dp.toPx() + l * 5.dp.toPx()
            drawCircle(
                color = if (l == 0) amberColor else muteColor,
                radius = 1.dp.toPx(),
                center = Offset(lx, routerTop + routerH / 2f),
            )
        }

        // Two Antennas
        val antH = 14.dp.toPx()
        val ant1Base = Offset(routerLeft + 5.dp.toPx(), routerTop)
        val ant1Tip = Offset(routerLeft + 2.dp.toPx(), routerTop - antH)
        val ant2Base = Offset(routerLeft + routerW - 5.dp.toPx(), routerTop)
        val ant2Tip = Offset(routerLeft + routerW - 2.dp.toPx(), routerTop - antH)

        drawLine(
            color = metalColor,
            start = ant1Base,
            end = ant1Tip,
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = metalColor,
            start = ant2Base,
            end = ant2Tip,
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // 3. Center horizontal cable
        val cableStartX = serverLeft + serverW
        val cableEndX = routerLeft
        val cableY = diagramY

        drawLine(
            color = edgeColor,
            start = Offset(cableStartX, cableY),
            end = Offset(cableEndX, cableY),
            strokeWidth = 1.5.dp.toPx(),
        )
        drawRect(
            color = metalColor,
            topLeft = Offset(cableStartX, cableY - 2.dp.toPx()),
            size = Size(2.dp.toPx(), 4.dp.toPx()),
        )
        drawRect(
            color = metalColor,
            topLeft = Offset(cableEndX - 2.dp.toPx(), cableY - 2.dp.toPx()),
            size = Size(2.dp.toPx(), 4.dp.toPx()),
        )

        // 4. Transmission effects (amber packet & antenna arcs)
        if (scanFxAlpha > 0f) {
            val t = cycleTimeMs

            // First 650 ms: Amber packet travels the cable
            if (t < PACKET_TRAVEL_DURATION_MS) {
                val packetFrac = (t / PACKET_TRAVEL_DURATION_MS).coerceIn(0f, 1f)
                val packetX = cableStartX + packetFrac * (cableEndX - cableStartX)
                val pAlpha = scanFxAlpha

                drawCircle(
                    color = amberColor.copy(alpha = 0.25f * pAlpha),
                    radius = 5.dp.toPx(),
                    center = Offset(packetX, cableY),
                )
                drawRoundRect(
                    color = amberColor.copy(alpha = pAlpha),
                    topLeft = Offset(packetX - 3.5.dp.toPx(), cableY - 2.dp.toPx()),
                    size = Size(7.dp.toPx(), 4.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx()),
                )
            }

            // Next 650 ms: Two antenna arcs expand and fade (650..1300 ms)
            if (t in PACKET_TRAVEL_DURATION_MS..(PACKET_TRAVEL_DURATION_MS + ANTENNA_EXPAND_DURATION_MS)) {
                val arcFrac = ((t - PACKET_TRAVEL_DURATION_MS) / ANTENNA_EXPAND_DURATION_MS).coerceIn(0f, 1f)
                val arcAlpha = (1f - arcFrac) * scanFxAlpha

                if (arcAlpha > 0f) {
                    listOf(ant1Tip, ant2Tip).forEach { tip ->
                        val arcRadius = 3.dp.toPx() + arcFrac * 12.dp.toPx()
                        drawArc(
                            color = amberColor.copy(alpha = arcAlpha),
                            startAngle = 210f,
                            sweepAngle = 120f,
                            useCenter = false,
                            topLeft = Offset(tip.x - arcRadius, tip.y - arcRadius),
                            size = Size(arcRadius * 2, arcRadius * 2),
                            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
            }
        }

        // 5. Small lamps under the picture
        val verified = scanState.discoveredHosts
        val detected = scanState.pveDetectedHosts
        val unverified = scanState.unverifiedHosts
        val totalLamps = verified.size + detected.size + unverified.size

        if (totalLamps > 0) {
            val lampW = 5.dp.toPx()
            val lampH = 2.5.dp.toPx()
            val lampGap = 3.dp.toPx()
            val step = lampW + lampGap

            val maxLampsPossible = ((w - 28.dp.toPx()) / step).toInt().coerceAtLeast(1)
            val lampsToDraw = totalLamps.coerceAtMost(maxLampsPossible)
            val totalWidth = lampsToDraw * step - lampGap
            val startX = (w - totalWidth) / 2f

            var idx = 0
            val drawLamp: (Color, Boolean) -> Unit = { col, glow ->
                if (idx < lampsToDraw) {
                    val lx = startX + idx * step
                    if (glow) {
                        drawRect(
                            color = col.copy(alpha = 0.3f),
                            topLeft = Offset(lx - 1.dp.toPx(), lampsY - 1.dp.toPx()),
                            size = Size(lampW + 2.dp.toPx(), lampH + 2.dp.toPx()),
                        )
                    }
                    drawRect(
                        color = col,
                        topLeft = Offset(lx, lampsY),
                        size = Size(lampW, lampH),
                    )
                    idx++
                }
            }
            repeat(verified.size) { drawLamp(linkGreenColor, true) }
            repeat(detected.size) { drawLamp(amberColor, true) }
            repeat(unverified.size) { drawLamp(muteColor, false) }
        }
    }
}

/**
 * Pure math helpers for the sweep strip cell calculations.
 */
object ScanSweepMath {
    /** Sweep progress fraction [0f, 1f]. If totalCount <= 0, returns 0f. */
    fun sweepFraction(scannedCount: Int, totalCount: Int): Float {
        if (totalCount <= 0) return 0f
        return (scannedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    }

    /** Number of completed/filled cells for a given cell strip length. */
    fun filledCellCount(scannedCount: Int, totalCount: Int, totalCells: Int): Int {
        if (totalCount <= 0 || totalCells <= 0) return 0
        val frac = sweepFraction(scannedCount, totalCount)
        return (frac * totalCells).toInt().coerceIn(0, totalCells)
    }

    /** Active sweeping cell index, or -1 if complete or not scanning. */
    fun activeCellIndex(scannedCount: Int, totalCount: Int, totalCells: Int, isScanning: Boolean): Int {
        if (!isScanning || totalCount <= 0 || totalCells <= 0) return -1
        val frac = sweepFraction(scannedCount, totalCount)
        return if (frac >= 1f) -1 else (frac * totalCells).toInt().coerceIn(0, totalCells - 1)
    }

    /**
     * Maps a discovered host to a cell index in the strip.
     * When totalCount == 254 and IP is dotted quad, maps by last octet (1..254).
     * Otherwise, falls back to uniform proportional mapping using fallbackIndex.
     */
    fun cellIndexForHost(
        ip: String,
        totalCount: Int,
        totalCells: Int,
        fallbackIndex: Int = 0,
    ): Int {
        if (totalCells <= 0) return 0
        val octet = ScanAddressBusMath.extractLastOctet(ip, totalCount)
        return if (octet != null && totalCount == ScanAddressBusMath.SLASH24_HOSTS) {
            val span = ScanAddressBusMath.SLASH24_HOSTS.toFloat()
            ((octet - 1).toFloat() / span * totalCells).toInt().coerceIn(0, totalCells - 1)
        } else {
            if (totalCount > 0) {
                ((fallbackIndex.toFloat() / totalCount.toFloat()) * totalCells).toInt().coerceIn(0, totalCells - 1)
            } else {
                (fallbackIndex % totalCells).coerceIn(0, totalCells - 1)
            }
        }
    }
}

/**
 * Pure math helpers for the address bus subnet mapping.
 */
object ScanAddressBusMath {
    const val SLASH24_HOSTS: Int = 254
    const val FIRST_HOST_OCTET: Int = 1
    const val LAST_HOST_OCTET: Int = 254
    const val SLASH24_SPAN: Int = 253
    const val LAST_OCTET_GROUP: Int = 4
    const val SWEEP_TWEEN_MS: Int = 180
    const val SWEEP_FADE_MS: Int = 250

    private val DOTTED_QUAD_REGEX = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    /** Checks whether the subnet represents a standard single /24 (254 host addresses). */
    fun isSingleSlash24(totalCount: Int): Boolean = totalCount == SLASH24_HOSTS

    /** Parses the 4th octet (1..254) if the IP is a valid dotted quad. Returns null otherwise. */
    fun parseLastOctet(ip: String): Int? {
        val match = DOTTED_QUAD_REGEX.matchEntire(ip.trim())
        val octet = match?.groupValues?.getOrNull(LAST_OCTET_GROUP)?.toIntOrNull()
        return octet?.takeIf { it in FIRST_HOST_OCTET..LAST_HOST_OCTET }
    }

    /**
     * Extracts the last octet only if totalCount represents a single /24 (254 hosts).
     * Falls back to null when not a single /24 or not dotted quad.
     */
    fun extractLastOctet(ip: String, totalCount: Int): Int? {
        if (!isSingleSlash24(totalCount)) return null
        return parseLastOctet(ip)
    }

    /**
     * Probe pip position fraction [0f, 1f].
     * If totalCount is not a single /24 (254), falls back to sweep-fraction (scannedCount / totalCount).
     */
    fun pipFraction(scannedCount: Int, totalCount: Int, currentIp: String? = null): Float {
        if (totalCount <= 0) return 0f
        val slash24Octet = currentIp?.takeIf { isSingleSlash24(totalCount) }?.let { parseLastOctet(it) }
        return when {
            slash24Octet != null ->
                ((slash24Octet - 1).toFloat() / SLASH24_SPAN.toFloat()).coerceIn(0f, 1f)
            isSingleSlash24(totalCount) ->
                (scannedCount.toFloat() / SLASH24_HOSTS.toFloat()).coerceIn(0f, 1f)
            else ->
                (scannedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
        }
    }

    /**
     * Beacon position fraction [0f, 1f].
     * Beacons may use last octet only when the IP looks like a dotted quad and totalCount == 254.
     * Otherwise, falls back to proportional mapping from fallbackIndex.
     */
    fun beaconFraction(ip: String, totalCount: Int, fallbackIndex: Int = 0): Float {
        if (isSingleSlash24(totalCount)) {
            val octet = parseLastOctet(ip)
            if (octet != null) {
                return ((octet - 1).toFloat() / SLASH24_SPAN.toFloat()).coerceIn(0f, 1f)
            }
        }
        // Fallback for non-/24 or non-dotted-quad
        return if (totalCount > 0) {
            (fallbackIndex.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    /**
     * Bus header label. LoginViewModel stores the live probe IP in currentSubnet.
     * CIDR stays as-is; a dotted-quad host on a /24 becomes the .0/24 prefix.
     */
    fun busLabel(current: String?, totalCount: Int): String {
        if (current.isNullOrBlank()) return "BUS"
        val hostOctet = parseLastOctet(current)
        return when {
            current.contains('/') -> current
            hostOctet != null && isSingleSlash24(totalCount) ->
                "${current.substringBeforeLast('.')}.0/24"
            else -> current
        }
    }
}

/**
 * Default scan visualization: ~12dp cell strip with CoolBlue sweep, 1.dp leading line,
 * and LinkGreen/Amber hit lights.
 * Live sweep fades/hides on STOP so a half-full bar does not freeze.
 */
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun ScanSweepStrip(
    scanState: NetworkScanState,
    modifier: Modifier = Modifier,
    cellCount: Int = 24,
) {
    val deckColor = TechColors.Deck
    val edgeColor = TechColors.Edge
    val coolBlueColor = TechColors.CoolBlue
    val linkGreenColor = TechColors.LinkGreen
    val amberColor = TechColors.Amber
    val muteColor = TechColors.Mute

    val targetFrac = ScanSweepMath.sweepFraction(scanState.scannedCount, scanState.totalCount)
    val animatedProgress by animateFloatAsState(
        targetValue = targetFrac,
        animationSpec = tween(ScanAddressBusMath.SWEEP_TWEEN_MS),
        label = "sweepProgress",
    )
    val sweepAlpha by animateFloatAsState(
        targetValue = if (scanState.isScanning) 1f else 0f,
        animationSpec = tween(ScanAddressBusMath.SWEEP_FADE_MS),
        label = "sweepAlpha",
    )

    // Precalculate hit cell indices
    val verifiedCells = scanState.discoveredHosts.mapIndexed { idx, h ->
        ScanSweepMath.cellIndexForHost(h.ip, scanState.totalCount, cellCount, idx)
    }.toSet()
    val detectedCells = scanState.pveDetectedHosts.mapIndexed { idx, h ->
        ScanSweepMath.cellIndexForHost(h.ip, scanState.totalCount, cellCount, idx)
    }.toSet()
    val unverifiedCells = scanState.unverifiedHosts.mapIndexed { idx, ip ->
        ScanSweepMath.cellIndexForHost(ip, scanState.totalCount, cellCount, idx)
    }.toSet()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .background(deckColor)
            .border(1.dp, edgeColor)
            .drawWithContent {
                val w = size.width
                val h = size.height

                // Draw cell dividers
                if (cellCount > 1) {
                    val cellW = w / cellCount.toFloat()
                    for (i in 1 until cellCount) {
                        val cx = i * cellW
                        drawLine(
                            color = edgeColor.copy(alpha = 0.4f),
                            start = Offset(cx, 0f),
                            end = Offset(cx, h),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }

                // Draw live CoolBlue sweep wash + 1.dp leading line (fades/hides on STOP)
                if (sweepAlpha > 0f && animatedProgress > 0f) {
                    val fillWidth = (w * animatedProgress).coerceIn(0f, w)
                    // CoolBlue sweep wash
                    drawRect(
                        color = coolBlueColor.copy(alpha = 0.15f * sweepAlpha),
                        size = Size(fillWidth, h),
                    )
                    // 1.dp leading line like PingPlateFill
                    drawLine(
                        color = coolBlueColor.copy(alpha = 0.85f * sweepAlpha),
                        start = Offset(fillWidth, 0f),
                        end = Offset(fillWidth, h),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                // Draw hit lights across cells
                val cellW = w / cellCount.toFloat()
                for (c in 0 until cellCount) {
                    val hitColor = when {
                        verifiedCells.contains(c) -> linkGreenColor
                        detectedCells.contains(c) -> amberColor
                        unverifiedCells.contains(c) -> muteColor
                        else -> null
                    }
                    if (hitColor != null) {
                        val left = c * cellW
                        // Hit fill wash
                        drawRect(
                            color = hitColor.copy(alpha = 0.25f),
                            topLeft = Offset(left, 0f),
                            size = Size(cellW, h),
                        )
                        // Hit light pip marker in center of cell
                        drawRect(
                            color = hitColor,
                            topLeft = Offset(left + (cellW - 3.dp.toPx()) / 2f, (h - 3.dp.toPx()) / 2f),
                            size = Size(3.dp.toPx(), 3.dp.toPx()),
                        )
                    }
                }

                drawContent()
            },
    )
}

/**
 * Alternate address bus visualization: .1–.254 subnet bus, probe pip, and beacons.
 * Probe pip fades/hides on STOP so it does not freeze midway.
 */
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun ScanAddressBus(
    scanState: NetworkScanState,
    modifier: Modifier = Modifier,
) {
    val deckColor = TechColors.Deck
    val edgeColor = TechColors.Edge
    val coolBlueColor = TechColors.CoolBlue
    val linkGreenColor = TechColors.LinkGreen
    val amberColor = TechColors.Amber
    val muteColor = TechColors.Mute

    val pipFrac = ScanAddressBusMath.pipFraction(
        scanState.scannedCount,
        scanState.totalCount,
        currentIp = scanState.currentSubnet,
    )
    val animatedPipFrac by animateFloatAsState(
        targetValue = pipFrac,
        animationSpec = tween(ScanAddressBusMath.SWEEP_TWEEN_MS),
        label = "pipFrac",
    )
    val pipAlpha by animateFloatAsState(
        targetValue = if (scanState.isScanning) 1f else 0f,
        animationSpec = tween(ScanAddressBusMath.SWEEP_FADE_MS),
        label = "pipAlpha",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (ScanAddressBusMath.isSingleSlash24(scanState.totalCount)) ".1" else "0",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = ScanAddressBusMath.busLabel(scanState.currentSubnet, scanState.totalCount),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = coolBlueColor.copy(alpha = 0.7f),
            )
            Text(
                text = if (ScanAddressBusMath.isSingleSlash24(scanState.totalCount)) {
                    ".254"
                } else {
                    "${scanState.totalCount}"
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(deckColor)
                .border(1.dp, edgeColor)
                .drawWithContent {
                    val w = size.width
                    val h = size.height

                    // Center bus line
                    drawLine(
                        color = edgeColor,
                        start = Offset(0f, h / 2f),
                        end = Offset(w, h / 2f),
                        strokeWidth = 1.dp.toPx(),
                    )

                    // Draw beacons for discovered hosts
                    scanState.discoveredHosts.forEachIndexed { idx, host ->
                        val bFrac = ScanAddressBusMath.beaconFraction(host.ip, scanState.totalCount, idx)
                        val bx = (w * bFrac).coerceIn(2.dp.toPx(), w - 2.dp.toPx())
                        drawCircle(
                            color = linkGreenColor.copy(alpha = 0.35f),
                            radius = 4.dp.toPx(),
                            center = Offset(bx, h / 2f),
                        )
                        drawCircle(
                            color = linkGreenColor,
                            radius = 2.dp.toPx(),
                            center = Offset(bx, h / 2f),
                        )
                    }

                    scanState.pveDetectedHosts.forEachIndexed { idx, host ->
                        val bFrac = ScanAddressBusMath.beaconFraction(host.ip, scanState.totalCount, idx)
                        val bx = (w * bFrac).coerceIn(2.dp.toPx(), w - 2.dp.toPx())
                        drawCircle(
                            color = amberColor.copy(alpha = 0.35f),
                            radius = 4.dp.toPx(),
                            center = Offset(bx, h / 2f),
                        )
                        drawCircle(
                            color = amberColor,
                            radius = 2.dp.toPx(),
                            center = Offset(bx, h / 2f),
                        )
                    }

                    scanState.unverifiedHosts.forEachIndexed { idx, ip ->
                        val bFrac = ScanAddressBusMath.beaconFraction(ip, scanState.totalCount, idx)
                        val bx = (w * bFrac).coerceIn(2.dp.toPx(), w - 2.dp.toPx())
                        drawCircle(
                            color = muteColor,
                            radius = 1.5.dp.toPx(),
                            center = Offset(bx, h / 2f),
                        )
                    }

                    // Draw probe pip (fades/hides on STOP)
                    if (pipAlpha > 0f) {
                        val px = (w * animatedPipFrac).coerceIn(0f, w)
                        // Glowing probe pip
                        drawCircle(
                            color = coolBlueColor.copy(alpha = 0.4f * pipAlpha),
                            radius = 5.dp.toPx(),
                            center = Offset(px, h / 2f),
                        )
                        drawCircle(
                            color = coolBlueColor.copy(alpha = pipAlpha),
                            radius = 2.5.dp.toPx(),
                            center = Offset(px, h / 2f),
                        )
                        drawLine(
                            color = coolBlueColor.copy(alpha = 0.7f * pipAlpha),
                            start = Offset(px, 0f),
                            end = Offset(px, h),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    drawContent()
                },
        )
    }
}
