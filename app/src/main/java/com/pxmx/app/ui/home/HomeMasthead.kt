package com.pxmx.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.rememberReduceMotion
import com.pxmx.app.ui.extras.LocalStupidAnimations
import com.pxmx.app.ui.extras.stupidCartwheelDegrees
import com.pxmx.app.ui.extras.stupidCartwheelHopDp
import com.pxmx.app.ui.extras.stupidSpinTurns
import com.pxmx.app.ui.icons.InstrumentGlyphs
import kotlinx.coroutines.launch

/** Pane width at which the host still fits beside the site name and version. */
const val HOME_MASTHEAD_HOST_MIN_DP = 640f
private val CommandBayIconSize = 14.dp

/**
 * Identity line under the wordmark.
 * Narrow panes drop the host so the site, the kind, and the version stay readable.
 */
fun homeMastheadLine(
    siteTitle: String,
    host: String,
    version: String,
    kind: String?,
    includeHost: Boolean,
): String {
    val title = siteTitle.ifBlank { "Proxmox" }
    val ver = version.ifBlank { "—" }
    val showHost = includeHost && host.isNotBlank() && host != "—"
    val parts = mutableListOf<String>()
    parts.add(title)
    if (showHost) parts.add(host)
    if (!showHost && !kind.isNullOrBlank()) parts.add(kind)
    parts.add("PVE $ver")
    if (showHost && !kind.isNullOrBlank()) parts.add(kind)
    return parts.joinToString(" · ")
}

fun homeMastheadPrefersHost(widthDp: Float): Boolean = widthDp >= HOME_MASTHEAD_HOST_MIN_DP

/**
 * Wordmark size for the current pane. Narrow panes start smaller so the mark
 * does not collide with the command strip. [collapse] is 0 at rest and 1 when scrolled.
 */
fun homeWordmarkSp(widthDp: Float, collapse: Float): Float {
    val base = when {
        widthDp < 340f -> 24f
        widthDp < 480f -> 28f
        else -> 34f
    }
    val floor = 20f
    val t = collapse.coerceIn(0f, 1f)
    return base - (base - floor) * t
}

/**
 * Sync, Tasks, and Menu as one instrument. Each bay keeps its own click.
 * The amber tick marks whichever bay is live.
 */
@Composable
fun HomeCommandStrip(
    refreshing: Boolean,
    menuOpen: Boolean,
    onSync: () -> Unit,
    onTasks: () -> Unit,
    onMenu: () -> Unit,
    menu: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CutCornerShape(bottomEnd = 12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(40.dp)
            .clip(shape)
            .background(TechColors.Hull)
            .border(1.dp, TechColors.Edge, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CommandBay(
            label = "Sync",
            emphasized = refreshing,
            onClick = onSync,
            modifier = Modifier.weight(1f),
        )
        BayDivider()
        CommandBay(
            label = "Tasks",
            emphasized = false,
            onClick = onTasks,
            modifier = Modifier.weight(1f),
        )
        BayDivider()
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            CommandBay(
                label = "Menu",
                emphasized = menuOpen,
                onClick = onMenu,
                modifier = Modifier.fillMaxWidth(),
            )
            menu()
        }
    }
}

@Composable
private fun BayDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(TechColors.Edge),
    )
}

@Composable
private fun CommandBay(
    label: String,
    emphasized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val isSync = label.equals("Sync", ignoreCase = true)

    val stupidEnabled = LocalStupidAnimations.current
    val reduceMotion = rememberReduceMotion()
    val rotation = remember { Animatable(0f) }
    val cartwheel = remember { Animatable(0f) }
    val hopY = remember { Animatable(0f) }
    var spinCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(emphasized, spinCount, stupidEnabled) {
        if ((emphasized || spinCount > 0) && isSync && stupidEnabled && !reduceMotion) {
            val turns = stupidSpinTurns(true)
            rotation.snapTo(0f)
            cartwheel.snapTo(0f)
            hopY.snapTo(0f)
            rotation.animateTo(
                targetValue = turns * 360f,
                animationSpec = tween(
                    durationMillis = (turns * 260).toInt(),
                    easing = FastOutSlowInEasing,
                ),
            )
            val cartwheelTarget = stupidCartwheelDegrees(true)
            val hopTarget = stupidCartwheelHopDp(true)
            kotlinx.coroutines.coroutineScope {
                launch {
                    cartwheel.animateTo(
                        targetValue = cartwheelTarget,
                        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                    )
                }
                launch {
                    hopY.animateTo(
                        targetValue = hopTarget,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    )
                    hopY.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    )
                }
            }
        } else {
            rotation.snapTo(0f)
            cartwheel.snapTo(0f)
            hopY.snapTo(0f)
        }
    }

    val spinDegrees = rotation.value + cartwheel.value
    val density = androidx.compose.ui.platform.LocalDensity.current
    val hopPx = with(density) { hopY.value.dp.toPx() }
    Column(
        modifier = modifier
            .testTag("tech_action_" + label.lowercase())
            .clickable {
                if (isSync) {
                    spinCount++
                }
                onClick()
            }
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            if (isSync) {
                Icon(
                    imageVector = InstrumentGlyphs.Refresh,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .graphicsLayer {
                            rotationZ = spinDegrees
                            translationY = hopPx
                        },
                )
            } else if (label.equals("Tasks", ignoreCase = true)) {
                Icon(
                    imageVector = InstrumentGlyphs.Tasks,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(CommandBayIconSize),
                )
            } else if (label.equals("Menu", ignoreCase = true)) {
                Icon(
                    imageVector = InstrumentGlyphs.Menu,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(CommandBayIconSize),
                )
            }
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .width(28.dp)
                .height(2.dp)
                .background(if (emphasized) color else Color.Transparent),
        )
    }
}
