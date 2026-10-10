package com.pxmx.app.ui.guest.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.data.model.FirewallRule
import com.pxmx.app.data.model.FirewallSnapshot
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechStatusPlate

@Composable
fun FirewallBody(
    snapshot: FirewallSnapshot?,
    error: String? = null,
) {
    if (error != null) {
        TechPlate(railColor = MaterialTheme.colorScheme.error) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "FIREWALL ERROR",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    if (snapshot == null) {
        TechPlate(railColor = TechColors.Mute) {
            Box(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(
                    "No firewall data",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val isEnabled = snapshot.enabled
    val railColor = if (isEnabled) TechColors.LinkGreen else TechColors.Mute

    TechPlate(railColor = railColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        snapshot.scope.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${snapshot.rules.size} rule(s) configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TechStatusPlate(status = if (isEnabled) "enabled" else "disabled")
            }

            Spacer(Modifier.height(12.dp))

            if (snapshot.rules.isEmpty()) {
                Text(
                    "No rules configured",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "RULES",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                snapshot.rules.forEachIndexed { index, rule ->
                    if (index > 0) {
                        Spacer(Modifier.height(6.dp))
                    }
                    RuleSummaryRow(rule)
                }
            }
        }
    }
}

@Composable
private fun RuleSummaryRow(rule: FirewallRule) {
    val summaryText = rule.summary.ifBlank { "Rule #${rule.pos ?: "?"}" }
    val isEnabled = rule.enable
    val rowAlpha = if (isEnabled) 1f else 0.5f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .background(
                color = TechColors.Deck,
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rule.pos?.let { pos ->
            Text(
                text = "#$pos",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summaryText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodySmall,
                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            rule.comment?.takeIf { it.isNotBlank() }?.let { comment ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = comment,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
