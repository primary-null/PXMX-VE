package com.pxmx.app.ui.guest.detail

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pxmx.app.data.model.ParsedGuestConfig

@Composable
fun OptionsBody(cfg: ParsedGuestConfig?) {
    if (cfg == null) {
        Text("No config", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Line("Name", cfg.name ?: "—")
    Line("OS type", cfg.ostype ?: "—")
    Line("Machine", cfg.machine ?: "—")
    Line("BIOS", cfg.bios ?: "—")
    Line("Boot", cfg.boot ?: "—")
    Line(
        "Start at boot",
        when (cfg.onboot) {
            true -> "Yes"
            false -> "No"
            null -> "—"
        },
    )
    Line("Agent", cfg.agent ?: "—")
    Line("Tags", cfg.tags?.replace(";", ", ") ?: "—")
    if (cfg.other.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("Other", fontWeight = FontWeight.SemiBold)
        cfg.other.take(12).forEach { (k, v) -> Line(k, v) }
        if (cfg.other.size > 12) {
            Text("+${cfg.other.size - 12} more in Raw config", style = MaterialTheme.typography.labelSmall)
        }
    }
}
