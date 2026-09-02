package com.pxmx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.data.model.ClusterLogEntry

/**
 * Syslog priority color mapping:
 * pri <= 3 (emerg, alert, crit, err) -> Red
 * pri == 4 (warning) -> Amber
 * else (notice, info, debug) -> Green
 */
@Composable
fun logSeverityColor(pri: Int?): Color = when {
    pri == null -> TechColors.Mute
    pri <= 3 -> Color(0xFFFF5252)
    pri == 4 -> TechColors.Amber
    else -> TechColors.LinkGreen
}

/**
 * Slim (~28dp) system log strip pinned above bottom bars.
 * Shows latest syslog line: dot + tag + msg, monospace, ellipsized.
 *
 * Applies [navigationBarsPadding] to the outermost container so the strip
 * sits fully above system navigation bars (both gesture and 3-button nav).
 * The entire strip (including padded area) acts as a single click target.
 */
@Composable
fun SystemLogStrip(
    entry: ClusterLogEntry?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dotColor = if (entry != null) logSeverityColor(entry.pri) else TechColors.StoppedRail

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(TechColors.Hull)
            .border(1.dp, TechColors.Edge.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(dotColor, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                if (entry != null) {
                    val tagStr = entry.tag?.takeIf { it.isNotBlank() } ?: "sys"
                    val msgStr = entry.msg?.takeIf { it.isNotBlank() } ?: ""
                    Text(
                        text = "$tagStr $msgStr",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
