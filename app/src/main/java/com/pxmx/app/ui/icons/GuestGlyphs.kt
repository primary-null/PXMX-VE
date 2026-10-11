package com.pxmx.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Cyberdeck instrument geometry glyphs for PXMX guests, nodes, and storage.
 * Designed on a 24x24 viewport with thin plates and cut corners matching TechPlate.
 */
object GuestGlyphs {

    private fun glyph(name: String, fillType: PathFillType = PathFillType.NonZero, block: PathBuilder.() -> Unit): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4.0f,
                pathFillType = fillType,
                pathBuilder = block,
            )
        }.build()
    }

    /** Node: server rack chassis with mounting ears and blade plates with drive slits. */
    val Node: ImageVector = glyph("NodeRack") {
        // Left rack mounting rail with ear notches
        moveTo(2f, 3f); lineTo(4f, 3f); lineTo(4f, 21f); lineTo(2f, 21f); close()
        // Right rack mounting rail
        moveTo(20f, 3f); lineTo(22f, 3f); lineTo(22f, 21f); lineTo(20f, 21f); close()

        // Blade 1 (top unit) with chamfered top-left corner & drive slit
        moveTo(7f, 4f); lineTo(19f, 4f); lineTo(19f, 5.5f); lineTo(5.5f, 5.5f); lineTo(7f, 4f); close()
        moveTo(5f, 6.5f); lineTo(19f, 6.5f); lineTo(19f, 8f); lineTo(5f, 8f); close()

        // Blade 2 (middle unit)
        moveTo(5f, 10f); lineTo(19f, 10f); lineTo(19f, 11.5f); lineTo(5f, 11.5f); close()
        moveTo(5f, 12.5f); lineTo(19f, 12.5f); lineTo(19f, 14f); lineTo(5f, 14f); close()

        // Blade 3 (bottom unit) with bottom-right chamfer
        moveTo(5f, 16f); lineTo(19f, 16f); lineTo(19f, 17.5f); lineTo(5f, 17.5f); close()
        moveTo(5f, 18.5f); lineTo(17.5f, 18.5f); lineTo(16f, 20f); lineTo(5f, 20f); close()
    }

    /** Storage: cylindrical canister with faceted platter tiers and vertical spine. */
    val Storage: ImageVector = glyph("StorageCylinder") {
        // Top faceted cap platter
        moveTo(7f, 3f); lineTo(17f, 3f); lineTo(20f, 5f); lineTo(17f, 7f); lineTo(7f, 7f); lineTo(4f, 5f); close()

        // Middle platter tier with bevel
        moveTo(4f, 8f); lineTo(7f, 7.5f); lineTo(17f, 7.5f); lineTo(20f, 8f)
        lineTo(20f, 12f); lineTo(17f, 14f); lineTo(7f, 14f); lineTo(4f, 12f); close()

        // Bottom platter tier
        moveTo(4f, 15f); lineTo(7f, 14.5f); lineTo(17f, 14.5f); lineTo(20f, 15f)
        lineTo(20f, 19f); lineTo(17f, 21f); lineTo(7f, 21f); lineTo(4f, 19f); close()

        // Center spine slit
        moveTo(11.25f, 7.5f); lineTo(12.75f, 7.5f); lineTo(12.75f, 20.5f); lineTo(11.25f, 20.5f); close()
    }

    /** Container: outer bracket with CutCornerShape and nested inner CT plate. */
    val Container: ImageVector = glyph("NestedContainer", PathFillType.EvenOdd) {
        // Outer bracket with top-left cut corner
        moveTo(8f, 3f); lineTo(21f, 3f); lineTo(21f, 21f); lineTo(3f, 21f); lineTo(3f, 8f); close()
        // Outer cutout / frame opening
        moveTo(6f, 9f); lineTo(9f, 6f); lineTo(18f, 6f); lineTo(18f, 18f); lineTo(6f, 18f); close()

        // Inner nested CT core plate with matching cut corner
        moveTo(10.5f, 8.5f); lineTo(15.5f, 8.5f); lineTo(15.5f, 15.5f); lineTo(8.5f, 15.5f); lineTo(8.5f, 10.5f); close()
    }

    /** Windows: 4-pane telemetry instrument tiles with cut corners, not a brand logo. */
    val Windows: ImageVector = glyph("WindowsInstrument") {
        // Top-left pane with top-left chamfer
        moveTo(6f, 3f); lineTo(11f, 3f); lineTo(11f, 11f); lineTo(3f, 11f); lineTo(3f, 6f); close()
        // Top-right pane with top-right chamfer
        moveTo(13f, 3f); lineTo(18f, 3f); lineTo(21f, 6f); lineTo(21f, 11f); lineTo(13f, 11f); close()
        // Bottom-left pane with bottom-left chamfer
        moveTo(3f, 13f); lineTo(11f, 13f); lineTo(11f, 21f); lineTo(6f, 21f); lineTo(3f, 18f); close()
        // Bottom-right pane with bottom-right chamfer
        moveTo(13f, 13f); lineTo(21f, 13f); lineTo(21f, 18f); lineTo(18f, 21f); lineTo(13f, 21f); close()

        // Internal HUD reticle marks across panes
        moveTo(6f, 6f); lineTo(8.5f, 6f); lineTo(8.5f, 7.5f); lineTo(6f, 7.5f); close()
        moveTo(15.5f, 6f); lineTo(18f, 6f); lineTo(18f, 7.5f); lineTo(15.5f, 7.5f); close()
        moveTo(6f, 16.5f); lineTo(8.5f, 16.5f); lineTo(8.5f, 18f); lineTo(6f, 18f); close()
        moveTo(15.5f, 16.5f); lineTo(18f, 16.5f); lineTo(18f, 18f); lineTo(15.5f, 18f); close()
    }

    /** Linux: hexagonal microprocessor IC chip with peripheral lead pins. */
    val Linux: ImageVector = glyph("LinuxHexChip", PathFillType.EvenOdd) {
        // Outer hexagon body
        moveTo(12f, 4f); lineTo(18.5f, 7.75f); lineTo(18.5f, 15.25f); lineTo(12f, 19f); lineTo(5.5f, 15.25f); lineTo(5.5f, 7.75f); close()
        // Inner core junction cutout
        moveTo(12f, 7.5f); lineTo(15.5f, 9.5f); lineTo(15.5f, 13.5f); lineTo(12f, 15.5f); lineTo(8.5f, 13.5f); lineTo(8.5f, 9.5f); close()
        // Inner silicon die plate
        moveTo(11f, 10.5f); lineTo(13f, 10.5f); lineTo(13f, 12.5f); lineTo(11f, 12.5f); close()

        // Left lead pins
        moveTo(2f, 9.5f); lineTo(5f, 9.5f); lineTo(5f, 11f); lineTo(2f, 11f); close()
        moveTo(2f, 12.5f); lineTo(5f, 12.5f); lineTo(5f, 14f); lineTo(2f, 14f); close()
        // Right lead pins
        moveTo(19f, 9.5f); lineTo(22f, 9.5f); lineTo(22f, 11f); lineTo(19f, 11f); close()
        moveTo(19f, 12.5f); lineTo(22f, 12.5f); lineTo(22f, 14f); lineTo(19f, 14f); close()
        // Top and bottom trace pins
        moveTo(11.25f, 1.5f); lineTo(12.75f, 1.5f); lineTo(12.75f, 3.5f); lineTo(11.25f, 3.5f); close()
        moveTo(11.25f, 19.5f); lineTo(12.75f, 19.5f); lineTo(12.75f, 21.5f); lineTo(11.25f, 21.5f); close()
    }

    /** Firewall: layered chevron plates forming a defensive energy gate. */
    val Firewall: ImageVector = glyph("FirewallChevronGate") {
        // Top chevron gate plate
        moveTo(12f, 3f); lineTo(21f, 8.5f); lineTo(19f, 11f); lineTo(12f, 6.8f); lineTo(5f, 11f); lineTo(3f, 8.5f); close()
        // Middle chevron gate plate
        moveTo(12f, 9.5f); lineTo(21f, 15f); lineTo(19f, 17.5f); lineTo(12f, 13.3f); lineTo(5f, 17.5f); lineTo(3f, 15f); close()
        // Base barrier anchor
        moveTo(6f, 19.5f); lineTo(18f, 19.5f); lineTo(17f, 21.5f); lineTo(7f, 21.5f); close()
        // Central vertical gate runner
        moveTo(11.25f, 14.5f); lineTo(12.75f, 14.5f); lineTo(12.75f, 19f); lineTo(11.25f, 19f); close()
    }

    /** Database: 3 stacked platter disks with cylindrical depth and tech cutouts. */
    val Database: ImageVector = glyph("DatabaseStackedDisks", PathFillType.EvenOdd) {
        // Disk 1 (top platter)
        moveTo(7f, 3f); lineTo(17f, 3f); lineTo(21f, 5.5f); lineTo(17f, 8f); lineTo(7f, 8f); lineTo(3f, 5.5f); close()
        moveTo(11f, 5f); lineTo(13f, 5f); lineTo(13f, 6f); lineTo(11f, 6f); close()

        // Disk 2 (middle platter)
        moveTo(3f, 9.5f); lineTo(21f, 9.5f); lineTo(21f, 11.5f); lineTo(17f, 14f); lineTo(7f, 14f); lineTo(3f, 11.5f); close()
        moveTo(11f, 11f); lineTo(13f, 11f); lineTo(13f, 12.5f); lineTo(11f, 12.5f); close()

        // Disk 3 (bottom platter)
        moveTo(3f, 15.5f); lineTo(21f, 15.5f); lineTo(21f, 17.5f); lineTo(17f, 20f); lineTo(7f, 20f); lineTo(3f, 17.5f); close()
        moveTo(11f, 17f); lineTo(13f, 17f); lineTo(13f, 18.5f); lineTo(11f, 18.5f); close()
    }

    /** Media: CRT screen plate with horizontal scanline slits and media play chevron. */
    val Media: ImageVector = glyph("MediaScanline") {
        // Top display bezel with chamfer
        moveTo(6f, 4f); lineTo(21f, 4f); lineTo(21f, 6.5f); lineTo(3f, 6.5f); lineTo(3f, 7f); lineTo(6f, 4f); close()
        // Bottom display bezel with chamfer
        moveTo(3f, 17.5f); lineTo(21f, 17.5f); lineTo(18f, 20f); lineTo(3f, 20f); close()
        // Side bezels
        moveTo(3f, 6.5f); lineTo(5.5f, 6.5f); lineTo(5.5f, 17.5f); lineTo(3f, 17.5f); close()
        moveTo(18.5f, 6.5f); lineTo(21f, 6.5f); lineTo(21f, 17.5f); lineTo(18.5f, 17.5f); close()

        // Scanline slats across display
        moveTo(7f, 8f); lineTo(17f, 8f); lineTo(17f, 9.5f); lineTo(7f, 9.5f); close()
        moveTo(7f, 14.5f); lineTo(17f, 14.5f); lineTo(17f, 16f); lineTo(7f, 16f); close()

        // Center scanline bar intersected with play chevron
        moveTo(7f, 11f); lineTo(9.5f, 11f); lineTo(15f, 12f); lineTo(9.5f, 13f); lineTo(7f, 13f); close()
        moveTo(10f, 9.5f); lineTo(13.5f, 11f); lineTo(10f, 11f); close()
        moveTo(10f, 13f); lineTo(13.5f, 13f); lineTo(10f, 14.5f); close()
    }

    /** Home Assistant: peaked roof shelter plate with telemetry spire and entryway. */
    val HomeAssistant: ImageVector = glyph("HomeAssistantPlate") {
        // Peaked roof gable
        moveTo(12f, 3f); lineTo(21f, 10.5f); lineTo(18.5f, 12.5f); lineTo(12f, 7f); lineTo(5.5f, 12.5f); lineTo(3f, 10.5f); close()
        // Telemetry spire atop roof
        moveTo(11.25f, 1f); lineTo(12.75f, 1f); lineTo(12.75f, 3.5f); lineTo(11.25f, 3.5f); close()

        // Outer domicile walls
        moveTo(5.5f, 13.5f); lineTo(8f, 13.5f); lineTo(8f, 21f); lineTo(5.5f, 21f); close()
        moveTo(16f, 13.5f); lineTo(18.5f, 13.5f); lineTo(18.5f, 21f); lineTo(16f, 21f); close()
        moveTo(8f, 19.5f); lineTo(16f, 19.5f); lineTo(16f, 21f); lineTo(8f, 21f); close()

        // Central smart node diamond
        moveTo(12f, 10f); lineTo(14.5f, 12.5f); lineTo(12f, 15f); lineTo(9.5f, 12.5f); close()
    }

    /** Template: dashed rectangular blueprint frame with cut corner brackets. */
    val Template: ImageVector = glyph("TemplateDashedRect") {
        // Corner 1: Top-left with cut corner
        moveTo(7f, 3f); lineTo(10f, 3f); lineTo(10f, 5f); lineTo(7.5f, 5f); lineTo(5f, 7.5f); lineTo(5f, 10f); lineTo(3f, 10f); lineTo(3f, 7f); close()
        // Corner 2: Top-right
        moveTo(14f, 3f); lineTo(21f, 3f); lineTo(21f, 10f); lineTo(19f, 10f); lineTo(19f, 5f); lineTo(14f, 5f); close()
        // Corner 3: Bottom-left
        moveTo(3f, 14f); lineTo(5f, 14f); lineTo(5f, 19f); lineTo(10f, 19f); lineTo(10f, 21f); lineTo(3f, 21f); close()
        // Corner 4: Bottom-right
        moveTo(19f, 14f); lineTo(21f, 14f); lineTo(21f, 21f); lineTo(14f, 21f); lineTo(14f, 19f); lineTo(19f, 19f); close()

        // Dashes along edges
        moveTo(11f, 3f); lineTo(13f, 3f); lineTo(13f, 5f); lineTo(11f, 5f); close()
        moveTo(11f, 19f); lineTo(13f, 19f); lineTo(13f, 21f); lineTo(11f, 21f); close()
        moveTo(3f, 11f); lineTo(5f, 11f); lineTo(5f, 13f); lineTo(3f, 13f); close()
        moveTo(19f, 11f); lineTo(21f, 11f); lineTo(21f, 13f); lineTo(19f, 13f); close()

        // Center reticle cross
        moveTo(11.25f, 9.5f); lineTo(12.75f, 9.5f); lineTo(12.75f, 14.5f); lineTo(11.25f, 14.5f); close()
        moveTo(9.5f, 11.25f); lineTo(14.5f, 11.25f); lineTo(14.5f, 12.75f); lineTo(9.5f, 12.75f); close()
    }

    /** Generic VM: chassis enclosure with top-left cut corner and terminal screen slit. */
    val GenericVm: ImageVector = glyph("GenericVmChassis") {
        // Top deck with cut corner
        moveTo(7f, 4f); lineTo(21f, 4f); lineTo(21f, 6.5f); lineTo(3f, 6.5f); lineTo(3f, 8f); close()
        // Side panels
        moveTo(3f, 8f); lineTo(5.5f, 8f); lineTo(5.5f, 20f); lineTo(3f, 20f); close()
        moveTo(18.5f, 6.5f); lineTo(21f, 6.5f); lineTo(21f, 20f); lineTo(18.5f, 20f); close()
        // Bottom chassis plate
        moveTo(3f, 18.5f); lineTo(21f, 18.5f); lineTo(21f, 20f); lineTo(3f, 20f); close()
        // Mid chassis divider separating screen slit from drive bay
        moveTo(5.5f, 12.5f); lineTo(18.5f, 12.5f); lineTo(18.5f, 14f); lineTo(5.5f, 14f); close()

        // Screen slit prompt marks
        moveTo(7.5f, 8.5f); lineTo(11f, 8.5f); lineTo(11f, 10.5f); lineTo(7.5f, 10.5f); close()
        moveTo(12.5f, 9.5f); lineTo(16.5f, 9.5f); lineTo(16.5f, 10.5f); lineTo(12.5f, 10.5f); close()

        // Lower drive bay & indicator
        moveTo(7.5f, 15.5f); lineTo(13f, 15.5f); lineTo(13f, 17f); lineTo(7.5f, 17f); close()
        moveTo(15f, 15.5f); lineTo(16.5f, 15.5f); lineTo(16.5f, 17f); lineTo(15f, 17f); close()
    }
}
