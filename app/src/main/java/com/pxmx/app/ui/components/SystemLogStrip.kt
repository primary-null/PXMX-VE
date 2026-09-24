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
                    val text = formatSystemLogStripText(entry)
                    Text(
                        text = text,
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

/**
 * Strips realm-user information (e.g., "for user 'root@pam'", "user 'root@pam'", "root@pam")
 * from the task ticker log message while preserving daemon name, tag, and status words.
 */
fun stripLogUser(msg: String): String {
    var result = msg
    // Remove "for user '...'" or "user '...'" (handles single, double, or curly quotes)
    result = Regex("""(?i)(?:\bfor\s+)?\buser\s+['"‘“][^'"’”]*['"’”]\s*""").replace(result, "")
    // Remove "for user=..." or "user=..." with realm or quotes
    result = Regex("""(?i)(?:\bfor\s+)?\buser\s*=\s*['"]?[A-Za-z0-9._-]+@[A-Za-z0-9._-]+['"]?\s*""").replace(result, "")
    // Remove "for user [user@realm]" or "user [user@realm]"
    result = Regex("""(?i)(?:\bfor\s+)?\buser\s+[A-Za-z0-9._-]+@[A-Za-z0-9._-]+\s*""").replace(result, "")
    // Remove "<user@realm>", "[user@realm]", "(user@realm)", or standalone "user@realm" (optionally preceded by "for ")
    result = Regex("""(?i)(?:\bfor\s+)?(?:<|\[|\()?[A-Za-z0-9._-]+@[A-Za-z0-9._-]+(?:>|\]|\))?\s*""").replace(result, "")
    // Clean up residual punctuation and normalize spaces
    result = Regex("""\s*;\s*;\s*""").replace(result, "; ")
    result = Regex("""\s+""").replace(result, " ")
    return result.trim()
}

/**
 * Formats the text shown in the system log strip: "$tagStr $msgStr".
 * If the stripped message is blank, the tag string is shown alone.
 */
fun formatSystemLogStripText(tag: String?, msg: String?): String {
    val tagStr = tag?.takeIf { it.isNotBlank() } ?: "sys"
    val rawMsg = msg?.takeIf { it.isNotBlank() } ?: ""
    val cleanedMsg = stripLogUser(rawMsg)
    return if (cleanedMsg.isNotBlank()) "$tagStr $cleanedMsg" else tagStr
}

fun formatSystemLogStripText(entry: ClusterLogEntry?): String {
    if (entry == null) return "—"
    return formatSystemLogStripText(entry.tag, entry.msg)
}

