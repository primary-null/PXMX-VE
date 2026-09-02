package com.pxmx.app.ui.guest.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.pxmx.app.data.model.SnapshotInfo
import com.pxmx.app.ui.util.formatEpoch

@Composable
fun SnapshotsBody(
    snapshots: List<SnapshotInfo>,
    busy: Boolean,
    onDelete: (String) -> Unit,
    onRollback: (String) -> Unit,
) {
    if (snapshots.isEmpty()) {
        Text("No snapshots", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    snapshots.forEach { snap ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Row(
                Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        snap.name ?: "?",
                        fontWeight = FontWeight.SemiBold,
                        color = if (snap.isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        buildString {
                            if (snap.isCurrent) append("You are here")
                            else append(formatEpoch(snap.snaptime))
                            snap.description?.takeIf { it.isNotBlank() && it != "You are here!" }
                                ?.let { append(" · ").append(it) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!snap.isCurrent && snap.name != null) {
                    IconButton(onClick = { onRollback(snap.name!!) }, enabled = !busy) {
                        Icon(Icons.Default.Restore, contentDescription = "Rollback")
                    }
                    IconButton(onClick = { onDelete(snap.name!!) }, enabled = !busy) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

@Composable
fun CreateSnapshotDialog(
    isQemu: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, desc: String, includeRam: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var ram by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create snapshot") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isQemu) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = ram, onCheckedChange = { ram = it })
                        Text("Include RAM")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, desc, ram) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
