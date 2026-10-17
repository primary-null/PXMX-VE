package com.pxmx.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Blade Runner instrument telemetry glyphs for PXMX.
 * Etched metal chassis geometry, cut corners, thin plates, and one amber instrument edge.
 * Designed on a 24x24 viewport matching the cyberdeck aesthetic.
 */
@Suppress("MagicNumber")
object InstrumentGlyphs {

    private val MetalColor = Color(0xFFE2E8F0)
    private val AmberEdgeColor = Color(0xFFFF9800)

    /**
     * Console instrument glyph.
     * Etched metal terminal monitor with chamfered bezel, CRT screen slit, prompt chevron,
     * instrument riser base, and a crisp amber edge along the top bezel facet.
     */
    val Console: ImageVector by lazy {
        ImageVector.Builder(
            name = "InstrumentConsole",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Etched metal chassis body & display bezel with cut top-left corner
            path(
                fill = SolidColor(MetalColor),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4.0f,
                pathFillType = PathFillType.EvenOdd,
            ) {
                // Outer chassis boundary (top-left chamfer)
                moveTo(6f, 3f)
                lineTo(21f, 3f)
                lineTo(21f, 17f)
                lineTo(3f, 17f)
                lineTo(3f, 6f)
                close()

                // Inner CRT screen cutout
                moveTo(5.5f, 5.5f)
                lineTo(18.5f, 5.5f)
                lineTo(18.5f, 14.5f)
                lineTo(5.5f, 14.5f)
                close()

                // Terminal prompt chevron '> '
                moveTo(7.5f, 7.5f)
                lineTo(10.5f, 9.5f)
                lineTo(7.5f, 11.5f)
                lineTo(8.5f, 11.5f)
                lineTo(11.5f, 9.5f)
                lineTo(8.5f, 7.5f)
                close()

                // Cursor / command slit
                moveTo(12.5f, 10.5f)
                lineTo(16.5f, 10.5f)
                lineTo(16.5f, 12f)
                lineTo(12.5f, 12f)
                close()

                // Instrument riser and pedestal base
                moveTo(10.5f, 17f)
                lineTo(13.5f, 17f)
                lineTo(13.5f, 18.5f)
                lineTo(10.5f, 18.5f)
                close()

                moveTo(7f, 19f)
                lineTo(17f, 19f)
                lineTo(16f, 21f)
                lineTo(8f, 21f)
                close()
            }

            // One amber edge: crisp glowing facet along the top bezel lip
            path(
                fill = SolidColor(AmberEdgeColor),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4.0f,
            ) {
                moveTo(6f, 3f)
                lineTo(21f, 3f)
                lineTo(21f, 4.5f)
                lineTo(7.5f, 4.5f)
                close()
            }
        }.build()
    }

    /**
     * Refresh instrument glyph.
     * Dual-arc circular sync dial with faceted instrument segments, sharp angled teeth,
     * and one amber edge on the upper return flange.
     */
    val Refresh: ImageVector by lazy {
        ImageVector.Builder(
            name = "InstrumentRefresh",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Etched metal dual-arc reload tracks and return arrowheads
            path(
                fill = SolidColor(MetalColor),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4.0f,
            ) {
                // Clockwise upper circular arc: from top-left to right, sweep ~140°
                moveTo(4.48f, 9.26f)
                arcTo(
                    horizontalEllipseRadius = 8f,
                    verticalEllipseRadius = 8f,
                    theta = 0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    x1 = 19.52f,
                    y1 = 9.26f,
                )
                lineTo(17.17f, 10.12f)
                arcTo(
                    horizontalEllipseRadius = 5.5f,
                    verticalEllipseRadius = 5.5f,
                    theta = 0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    x1 = 6.83f,
                    y1 = 10.12f,
                )
                close()

                // Lower counter-arc segment: from bottom-right to left, sweep ~140°
                moveTo(19.52f, 14.74f)
                arcTo(
                    horizontalEllipseRadius = 8f,
                    verticalEllipseRadius = 8f,
                    theta = 0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    x1 = 4.48f,
                    y1 = 14.74f,
                )
                lineTo(6.83f, 13.88f)
                arcTo(
                    horizontalEllipseRadius = 5.5f,
                    verticalEllipseRadius = 5.5f,
                    theta = 0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    x1 = 17.17f,
                    y1 = 13.88f,
                )
                close()

                // Lower arrowhead (left return tooth) sitting on the tangent of lower arc
                moveTo(3.68f, 15.03f)
                lineTo(4.22f, 10.36f)
                lineTo(7.63f, 13.59f)
                close()

                // Center pivot telemetry pip
                moveTo(11.25f, 11.25f)
                lineTo(12.75f, 11.25f)
                lineTo(12.75f, 12.75f)
                lineTo(11.25f, 12.75f)
                close()
            }

            // Upper arrowhead with one amber edge flange sitting on the tangent of upper arc
            path(
                fill = SolidColor(AmberEdgeColor),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4.0f,
            ) {
                moveTo(20.32f, 8.97f)
                lineTo(19.78f, 13.64f)
                lineTo(16.37f, 10.41f)
                close()
            }
        }.build()
    }

    /**
     * Optical lens instrument glyph.
     * Outer metal barrel ring, aperture tick marks, inner lens rim, and one amber edge on the outer rim.
     */
    val Lens: ImageVector by lazy {
        ImageVector.Builder(
            name = "InstrumentLens",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Etched outer metal barrel, aperture index notches, and central glass element
            path(
                fill = SolidColor(MetalColor),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4.0f,
                pathFillType = PathFillType.EvenOdd,
            ) {
                // Outer lens ring
                moveTo(8f, 3f); lineTo(16f, 3f); lineTo(21f, 8f); lineTo(21f, 16f)
                lineTo(16f, 21f); lineTo(8f, 21f); lineTo(3f, 16f); lineTo(3f, 8f); close()

                // Inner optic barrel cutout
                moveTo(9f, 5f); lineTo(15f, 5f); lineTo(19f, 9f); lineTo(19f, 15f)
                lineTo(15f, 19f); lineTo(9f, 19f); lineTo(5f, 15f); lineTo(5f, 9f); close()

                // Steady center core lens optic
                moveTo(10.5f, 10.5f)
                lineTo(13.5f, 10.5f)
                lineTo(13.5f, 13.5f)
                lineTo(10.5f, 13.5f)
                close()

                // Etched horizontal & vertical aperture tick marks
                moveTo(2f, 11.25f); lineTo(4f, 11.25f); lineTo(4f, 12.75f); lineTo(2f, 12.75f); close()
                moveTo(20f, 11.25f); lineTo(22f, 11.25f); lineTo(22f, 12.75f); lineTo(20f, 12.75f); close()
                moveTo(11.25f, 2f); lineTo(12.75f, 2f); lineTo(12.75f, 4f); lineTo(11.25f, 4f); close()
                moveTo(11.25f, 20f); lineTo(12.75f, 20f); lineTo(12.75f, 22f); lineTo(11.25f, 22f); close()
            }

            // One amber edge along top-right chamfer of the lens housing
            path(
                fill = SolidColor(AmberEdgeColor),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4.0f,
            ) {
                moveTo(16f, 3f)
                lineTo(21f, 8f)
                lineTo(19.5f, 9f)
                lineTo(15f, 4.5f)
                close()
            }
        }.build()
    }

    /** Hollow eye. The caller tints it. */
    val Eye: ImageVector by lazy { eyeVector(filled = false) }

    /** Filled eye with a cut pupil. Caller tints the watched state. */
    val EyeWatched: ImageVector by lazy { eyeVector(filled = true) }

    private fun eyeVector(filled: Boolean): ImageVector {
        return ImageVector.Builder(
            name = if (filled) "InstrumentEyeWatched" else "InstrumentEye",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = if (filled) null else SolidColor(Color.Black),
                strokeLineWidth = if (filled) 0f else 1.6f,
                pathFillType = if (filled) PathFillType.EvenOdd else PathFillType.NonZero,
            ) {
                moveTo(2f, 12f)
                curveTo(5.4f, 7.1f, 8.6f, 4.8f, 12f, 4.8f)
                curveTo(15.4f, 4.8f, 18.6f, 7.1f, 22f, 12f)
                curveTo(18.6f, 16.9f, 15.4f, 19.2f, 12f, 19.2f)
                curveTo(8.6f, 19.2f, 5.4f, 16.9f, 2f, 12f)
                close()
                if (filled) {
                    moveTo(12f, 9.1f)
                    curveTo(13.6f, 9.1f, 14.9f, 10.4f, 14.9f, 12f)
                    curveTo(14.9f, 13.6f, 13.6f, 14.9f, 12f, 14.9f)
                    curveTo(10.4f, 14.9f, 9.1f, 13.6f, 9.1f, 12f)
                    curveTo(9.1f, 10.4f, 10.4f, 9.1f, 12f, 9.1f)
                    close()
                }
            }
            if (!filled) {
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.6f,
                ) {
                    moveTo(12f, 9.6f)
                    curveTo(13.3f, 9.6f, 14.4f, 10.7f, 14.4f, 12f)
                    curveTo(14.4f, 13.3f, 13.3f, 14.4f, 12f, 14.4f)
                    curveTo(10.7f, 14.4f, 9.6f, 13.3f, 9.6f, 12f)
                    curveTo(9.6f, 10.7f, 10.7f, 9.6f, 12f, 9.6f)
                    close()
                }
            }
        }.build()
    }

    /**
     * Rocket pull-refresh instrument glyph.
     * Cyberdeck telemetry rocket pointing up (small y): fuselage body, observation window,
     * dual delta fins, nozzle base, and one amber edge along the right shoulder.
     */
    val Rocket: ImageVector by lazy {
        ImageVector.Builder(
            name = "InstrumentRocket",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Main chassis fuselage, nozzle, dual swept fins, and diamond porthole cutout
            path(
                fill = SolidColor(MetalColor),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4.0f,
                pathFillType = PathFillType.EvenOdd,
            ) {
                // Nose apex pointing up
                moveTo(12f, 2.5f)
                // Left shoulder and fuselage
                lineTo(8.5f, 7.5f)
                lineTo(8.5f, 16.5f)
                // Left nozzle recess
                lineTo(10.5f, 17.5f)
                // Right nozzle recess
                lineTo(13.5f, 17.5f)
                // Right fuselage and shoulder
                lineTo(15.5f, 16.5f)
                lineTo(15.5f, 7.5f)
                close()

                // Left swept fin
                moveTo(8.5f, 12.5f)
                lineTo(5f, 17.5f)
                lineTo(7.5f, 17.5f)
                lineTo(8.5f, 15.5f)
                close()

                // Right swept fin
                moveTo(15.5f, 12.5f)
                lineTo(19f, 17.5f)
                lineTo(16.5f, 17.5f)
                lineTo(15.5f, 15.5f)
                close()

                // Observation window diamond cutout centered at (12, 10)
                moveTo(12f, 8.5f)
                lineTo(13.5f, 10f)
                lineTo(12f, 11.5f)
                lineTo(10.5f, 10f)
                close()
            }

            // One amber edge: crisp glowing facet along the right shoulder
            path(
                fill = SolidColor(AmberEdgeColor),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4.0f,
            ) {
                moveTo(12f, 2.5f)
                lineTo(15.5f, 7.5f)
                lineTo(14.2f, 8.2f)
                lineTo(12f, 4.2f)
                close()
            }
        }.build()
    }

    /**
     * Cog pull-refresh instrument glyph.
     * Face-on gear centered on 12,12 so rotationZ spins in place:
     * circular hub, center hole cutout, five metal teeth, and one amber tooth.
     */
    val Cog: ImageVector by lazy {
        ImageVector.Builder(
            name = "InstrumentCog",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Gear hub ring, center hole cutout, and five metal teeth
            path(
                fill = SolidColor(MetalColor),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4.0f,
                pathFillType = PathFillType.EvenOdd,
            ) {
                // Outer hub ring (radius 6 centered on 12,12)
                moveTo(12f, 6f)
                arcTo(6f, 6f, 0f, false, true, 12f, 18f)
                arcTo(6f, 6f, 0f, false, true, 12f, 6f)
                close()

                // Center hole cutout (radius 2.5 centered on 12,12)
                moveTo(12f, 9.5f)
                arcTo(2.5f, 2.5f, 0f, false, false, 12f, 14.5f)
                arcTo(2.5f, 2.5f, 0f, false, false, 12f, 9.5f)
                close()

                // Tooth 1 (60 deg / 2 o'clock)
                moveTo(16.01f, 7.95f)
                lineTo(19.24f, 6.55f)
                lineTo(20.34f, 8.45f)
                lineTo(17.51f, 10.55f)
                close()

                // Tooth 2 (120 deg / 4 o'clock)
                moveTo(17.51f, 13.45f)
                lineTo(20.34f, 15.55f)
                lineTo(19.24f, 17.45f)
                lineTo(16.01f, 16.05f)
                close()

                // Tooth 3 (180 deg / 6 o'clock - bottom)
                moveTo(13.5f, 17.5f)
                lineTo(13.1f, 21.0f)
                lineTo(10.9f, 21.0f)
                lineTo(10.5f, 17.5f)
                close()

                // Tooth 4 (240 deg / 8 o'clock)
                moveTo(7.99f, 16.05f)
                lineTo(4.76f, 17.45f)
                lineTo(3.66f, 15.55f)
                lineTo(6.49f, 13.45f)
                close()

                // Tooth 5 (300 deg / 10 o'clock)
                moveTo(6.49f, 10.55f)
                lineTo(3.66f, 8.45f)
                lineTo(4.76f, 6.55f)
                lineTo(7.99f, 7.95f)
                close()
            }

            // One amber tooth (0 deg / 12 o'clock - top)
            path(
                fill = SolidColor(AmberEdgeColor),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4.0f,
            ) {
                moveTo(10.5f, 6.5f)
                lineTo(10.9f, 3.0f)
                lineTo(13.1f, 3.0f)
                lineTo(13.5f, 6.5f)
                close()
            }
        }.build()
    }
}
