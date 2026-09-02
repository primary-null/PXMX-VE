package com.pxmx.app.ui.guest.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pxmx.app.data.model.ConfigDisk
import com.pxmx.app.data.model.ParsedGuestConfig
import com.pxmx.app.ui.util.formatMemoryMiB

@Composable
fun HardwareBody(cfg: ParsedGuestConfig?) {
    if (cfg == null) {
        Text("No config", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Line("Sockets × cores", cfg.vcpus ?: "—")
    Line("CPU type", cfg.cpu ?: "—")
    Line("Memory", formatMemoryMiB(cfg.memory))
    Line("Balloon", cfg.balloon?.let { formatMemoryMiB(it) } ?: "—")
    Line("SCSI", cfg.scsihw ?: "—")
    Spacer(Modifier.height(8.dp))
    Text("Disks", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    if (cfg.disks.isEmpty()) {
        Text("None", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        cfg.disks.forEach { MiniDisk(it) }
    }
}

@Composable
private fun MiniDisk(disk: ConfigDisk) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Storage, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Column {
            Text("${disk.key} · ${disk.label}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                listOfNotNull(disk.volume, disk.size?.let { "size $it" }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
