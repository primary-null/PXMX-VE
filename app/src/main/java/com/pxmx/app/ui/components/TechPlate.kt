package com.pxmx.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TechPalette(
    val hull: Color,
    val deck: Color,
    val edge: Color,
    val divider: Color,
    val amber: Color,
    val linkGreen: Color,
    val coolBlue: Color,
    val mute: Color,
    val stoppedRail: Color,
    val danger: Color = Color(0xFFE53935),
)

val DarkTechPalette = TechPalette(
    hull = Color(0xFF050505),
    deck = Color(0xFF2C2C2C),
    edge = Color(0xFF3D3D3D),
    divider = Color(0xFF1A1A1A),
    amber = Color(0xFFFF9800),
    linkGreen = Color(0xFF69F0AE),
    coolBlue = Color(0xFF42A5F5),
    mute = Color(0xFF757575),
    stoppedRail = Color(0xFF4A4A4A),
    danger = Color(0xFFE53935),
)

val LightTechPalette = TechPalette(
    hull = Color(0xFFF6F6F6),
    deck = Color(0xFFEAEAEA),
    edge = Color(0xFFCCCCCC),
    divider = Color(0xFFDCDCDC),
    amber = Color(0xFFE65100),
    linkGreen = Color(0xFF2E7D32),
    coolBlue = Color(0xFF1565C0),
    mute = Color(0xFF6E6E6E),
    stoppedRail = Color(0xFF9E9E9E),
    danger = Color(0xFFE53935),
)

val LocalTechColors = staticCompositionLocalOf { DarkTechPalette }

/** Shared instrument / communicator chrome used on home + secondary screens. */
object TechColors {
    val Hull: Color @Composable get() = LocalTechColors.current.hull
    val Deck: Color @Composable get() = LocalTechColors.current.deck
    val Edge: Color @Composable get() = LocalTechColors.current.edge
    val Divider: Color @Composable get() = LocalTechColors.current.divider
    val Amber: Color @Composable get() = LocalTechColors.current.amber
    val LinkGreen: Color @Composable get() = LocalTechColors.current.linkGreen
    val CoolBlue: Color @Composable get() = LocalTechColors.current.coolBlue
    val Mute: Color @Composable get() = LocalTechColors.current.mute
    val StoppedRail: Color @Composable get() = LocalTechColors.current.stoppedRail
    val Danger: Color @Composable get() = LocalTechColors.current.danger

    val DarkHull = DarkTechPalette.hull
    val DarkDeck = DarkTechPalette.deck
    val DarkEdge = DarkTechPalette.edge
    val DarkDivider = DarkTechPalette.divider
    val DarkAmber = DarkTechPalette.amber
    val DarkLinkGreen = DarkTechPalette.linkGreen
    val DarkCoolBlue = DarkTechPalette.coolBlue
    val DarkMute = DarkTechPalette.mute
    val DarkDanger = DarkTechPalette.danger

    val LightHull = LightTechPalette.hull
    val LightDeck = LightTechPalette.deck
    val LightEdge = LightTechPalette.edge
    val LightDivider = LightTechPalette.divider
    val LightAmber = LightTechPalette.amber
    val LightLinkGreen = LightTechPalette.linkGreen
    val LightCoolBlue = LightTechPalette.coolBlue
    val LightMute = LightTechPalette.mute
    val LightDanger = LightTechPalette.danger

    @Composable
    fun current(): TechPalette = LocalTechColors.current
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun techTopAppBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = TechColors.Hull,
    scrolledContainerColor = TechColors.Hull,
    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    actionIconContentColor = MaterialTheme.colorScheme.primary,
)

/** Paper-cut on bottom-right — same language as home guest cards. */
val TechPlateShape = CutCornerShape(
    topStart = 0.dp,
    topEnd = 0.dp,
    bottomEnd = 28.dp,
    bottomStart = 0.dp,
)

@Composable
fun techRailColor(status: String?, busy: Boolean = false): Color = when {
    busy -> TechColors.LinkGreen.copy(alpha = 0.55f)
    status.equals("running", true) || status.equals("online", true) ||
        status.equals("available", true) -> TechColors.LinkGreen
    status.equals("paused", true) || status.equals("suspended", true) ||
        status.equals("frozen", true) || status.equals("prelaunch", true) -> TechColors.Amber
    status.equals("stopped", true) || status.equals("offline", true) ||
        status.equals("disabled", true) -> TechColors.StoppedRail
    else -> TechColors.Mute
}

/**
 * Persistent soft blurred halo in accent color for selection chrome only.
 * No infinite animation, steady alpha.
 */
fun Modifier.neonSelectionHalo(
    enabled: Boolean = true,
    color: Color = Color(0xFFFF9500),
    haloRadius: Dp = 6.dp,
    shape: Shape? = null,
    pathProvider: (Density.(Size) -> Path)? = null,
): Modifier = if (!enabled) this else this.drawBehind {
    val haloPx = haloRadius.toPx()
    val haloAlpha = 0.40f
    val haloColor = color.copy(alpha = haloAlpha)
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2.dp.toPx()
            this.color = haloColor.toArgb()
            try {
                maskFilter = android.graphics.BlurMaskFilter(haloPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
            } catch (_: Throwable) {
                // Ignore in headless test environments where BlurMaskFilter is unmocked
            }
        }
        if (pathProvider != null) {
            val p = pathProvider(size)
            canvas.nativeCanvas.drawPath(p.asAndroidPath(), paint)
        } else if (shape != null) {
            val outline = shape.createOutline(size, layoutDirection, this)
            when (outline) {
                is Outline.Rectangle -> canvas.nativeCanvas.drawRect(
                    android.graphics.RectF(0f, 0f, size.width, size.height),
                    paint,
                )
                is Outline.Rounded -> {
                    val rr = outline.roundRect
                    canvas.nativeCanvas.drawRoundRect(
                        android.graphics.RectF(0f, 0f, size.width, size.height),
                        rr.bottomRightCornerRadius.x,
                        rr.bottomRightCornerRadius.y,
                        paint,
                    )
                }
                is Outline.Generic -> canvas.nativeCanvas.drawPath(outline.path.asAndroidPath(), paint)
            }
        } else {
            canvas.nativeCanvas.drawRect(
                android.graphics.RectF(0f, 0f, size.width, size.height),
                paint,
            )
        }
    }
}

/** Small top-bar control (SYNC / MENU) — cut corner, not stock Android icons. */
@Composable
fun TechActionPlate(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = CutCornerShape(bottomEnd = 10.dp)
    val contentColor = when {
        !enabled -> TechColors.Mute
        emphasized -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when {
        !enabled -> TechColors.Edge
        emphasized -> MaterialTheme.colorScheme.primary
        else -> TechColors.Edge
    }
    val backgroundColor = if (emphasized && enabled) TechColors.Deck else TechColors.Hull
    val hasNeon = emphasized && enabled
    Box(
        modifier = modifier
            .testTag("tech_action_" + label.lowercase())
            .then(if (hasNeon) Modifier.neonSelectionHalo(enabled = true, color = borderColor, shape = shape) else Modifier)
            .clip(shape)
            .background(backgroundColor)
            .border(
                1.dp,
                borderColor,
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = contentColor,
            )
        }
    }
}

@Composable
fun TechPlate(
    modifier: Modifier = Modifier,
    railColor: Color = TechColors.Amber,
    showRail: Boolean = true,
    fillMaxWidth: Boolean = true,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val plateModifier = if (testTag != null) modifier.testTag(testTag) else modifier
    val baseModifier = if (fillMaxWidth) plateModifier.fillMaxWidth() else plateModifier
    Box(
        modifier = baseModifier
            .clip(TechPlateShape)
            .background(TechColors.Hull)
            .border(1.dp, TechColors.Edge, TechPlateShape),
    ) {
        Row(
            modifier = (if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
                .height(IntrinsicSize.Min),
        ) {
            if (showRail) {
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(railColor),
                )
            }
            Column(
                modifier = if (fillMaxWidth) {
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                } else {
                    Modifier
                },
                content = content,
            )
        }
    }
}

/** Raised mid-gray control / metrics deck inside a plate. */
@Composable
fun TechDeck(
    modifier: Modifier = Modifier,
    showAccentBar: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TechColors.Deck)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (showAccentBar) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            )
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

@Composable
fun TechIconBay(
    icon: ImageVector,
    accent: Color,
    contentDescription: String? = null,
    size: Dp = 44.dp,
    iconSize: Dp = 26.dp,
    modifier: Modifier = Modifier,
) {
    val cutShape = CutCornerShape(topStart = 8.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(cutShape)
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.22f),
                        accent.copy(alpha = 0.08f),
                    ),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.55f), cutShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = accent,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun TechStatusPlate(status: String?) {
    val label = (status ?: "?").uppercase()
    val color = when (status?.lowercase()) {
        "running", "online", "available", "ok", "enabled" -> TechColors.LinkGreen
        "paused", "suspended", "frozen", "prelaunch", "connecting" -> TechColors.Amber
        "stopped", "offline", "disabled" -> TechColors.Mute
        "unknown", "failed", "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 120.dp)
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
fun TechSectionLabel(
    title: String,
    count: Int? = null,
    accent: Color = TechColors.Amber,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(3.dp)
                .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            color = accent,
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(accent.copy(alpha = 0.35f)),
        )
    }
}

@Composable
fun TechMetaLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun TechDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.border(1.dp, TechColors.Edge, TechPlateShape),
        shape = TechPlateShape,
        containerColor = TechColors.Hull,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        content = content,
    )
}

@Composable
fun TechMenuHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun TechMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    selected: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val color = when {
        !enabled -> TechColors.Mute
        danger -> TechColors.Danger
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    DropdownMenuItem(
        modifier = modifier,
        enabled = enabled,
        text = {
            Column {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = color,
                )
                if (hint != null) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = TechColors.Mute,
                    )
                }
            }
        },
        leadingIcon = leadingIcon?.let {
            {
                Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
        },
        trailingIcon = trailing,
        onClick = onClick,
        colors = MenuDefaults.itemColors(
            textColor = color,
            leadingIconColor = color,
            disabledTextColor = TechColors.Mute,
        ),
    )
}

/**
 * Cyberdeck-styled switch control.
 * Replaces Material Switch across settings rows.
 */
@Composable
fun TechSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackShape = CutCornerShape(bottomEnd = 8.dp)
    val thumbShape = CutCornerShape(bottomEnd = 6.dp)

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 26.dp else 0.dp,
        animationSpec = tween(durationMillis = 140),
        label = "techSwitchThumb",
    )

    val borderColor = if (enabled && checked) TechColors.Amber else TechColors.Edge
    val thumbColor = when {
        !enabled -> TechColors.Deck
        checked -> TechColors.Amber
        else -> TechColors.Mute
    }

    val toggleModifier = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(if (!enabled) Modifier.alpha(0.45f) else Modifier)
            .size(width = 64.dp, height = 28.dp)
            .clip(trackShape)
            .background(TechColors.Hull, trackShape)
            .border(1.dp, borderColor, trackShape)
            .then(toggleModifier)
            .padding(3.dp),
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(32.dp)
                .fillMaxHeight()
                .clip(thumbShape)
                .background(thumbColor, thumbShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (checked) "ON" else "OFF",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = if (checked) TechColors.Hull else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

