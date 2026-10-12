package com.pxmx.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.ui.login.NetworkScanState

/**
 * Scan visualization modes.
 * SWEEP_STRIP is the default (Claude hybrid: ~12dp cell strip, CoolBlue sweep,
 * 1.dp leading line, LinkGreen/Amber hit lights).
 * ADDRESS_BUS is the Gemini 2 subnet strip (.1–.254 bus, probe pip, beacons).
 */
enum class ScanVizMode {
    SWEEP_STRIP,
    ADDRESS_BUS,
}

object ScanViz {
    /**
     * One-constant swap. Default is SWEEP_STRIP (Claude hybrid).
     * Assign ADDRESS_BUS here to ship Gemini 2 + Claude subnet bus instead.
     */
    val mode: ScanVizMode = ScanVizMode.SWEEP_STRIP
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
