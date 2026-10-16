package com.pxmx.app.ui.guest.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import com.pxmx.app.ui.adaptive.AdaptiveAlertDialog
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pxmx.app.data.model.BackupVolume
import com.pxmx.app.ui.components.TechColors
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
    val sorted = backups.sortedByDescending { it.ctime ?: 0L }
    sorted.forEachIndexed { index, b ->
        if (index > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TechColors.Divider),
            )
        }
        val storage = b.volid?.substringBefore(':') ?: "storage"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    b.notes?.takeIf { it.isNotBlank() }
                        ?: b.volid?.substringAfterLast('/')
                        ?: "backup",
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "$storage · ${formatEpoch(b.ctime)} · ${formatBytes(b.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onDelete(b) }, enabled = !busy) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

fun canConfirmBackup(storages: List<String>, selectedStorage: String): Boolean =
    storages.isNotEmpty() && selectedStorage.isNotBlank() && storages.contains(selectedStorage)

@Composable
fun CreateBackupDialog(
    storages: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (storage: String, mode: String) -> Unit,
) {
    var storage by remember(storages) { mutableStateOf(storages.firstOrNull().orEmpty()) }
    var mode by remember { mutableStateOf("snapshot") }

    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup now") },
        text = {
            Column {
                Text("Storage", style = MaterialTheme.typography.labelLarge)
                if (storages.isEmpty()) {
                    Text(
                        text = "No backup datastore available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
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
            TextButton(
                onClick = { onConfirm(storage, mode) },
                enabled = canConfirmBackup(storages, storage),
            ) {
                Text("Start")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
