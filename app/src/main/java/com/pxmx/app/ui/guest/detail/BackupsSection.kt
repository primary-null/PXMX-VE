package com.pxmx.app.ui.guest.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pxmx.app.data.model.BackupVolume
import com.pxmx.app.ui.util.formatBytes
import com.pxmx.app.ui.util.formatEpoch

@Composable
fun BackupsBody(
    backups: List<BackupVolume>,
    busy: Boolean,
    onDelete: (BackupVolume) -> Unit,
) {
    if (backups.isEmpty()) {
        Text("No backups for this guest", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    backups.forEach { b ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        b.notes?.takeIf { it.isNotBlank() }
                            ?: b.volid?.substringAfterLast('/')
                            ?: "backup",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${formatEpoch(b.ctime)} · ${formatBytes(b.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onDelete(b) }, enabled = !busy) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
fun CreateBackupDialog(
    storages: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (storage: String, mode: String) -> Unit,
) {
    var storage by remember { mutableStateOf(storages.firstOrNull().orEmpty()) }
    var mode by remember { mutableStateOf("snapshot") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup now") },
        text = {
            Column {
                Text("Storage", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    storages.forEach { s ->
                        FilterChip(
                            selected = storage == s,
                            onClick = { storage = s },
                            label = { Text(s) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Mode", style = MaterialTheme.typography.labelLarge)
                Row {
                    listOf("snapshot", "suspend", "stop").forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(m) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(storage, mode) }, enabled = storage.isNotBlank()) {
                Text("Start")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
