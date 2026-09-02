package com.pxmx.app.ui.guest.detail

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pxmx.app.data.model.GuestAction
import com.pxmx.app.data.model.GuestType

@Composable
fun Line(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun availableActions(
    type: GuestType,
    running: Boolean,
    status: String?,
): List<GuestAction> {
    val list = mutableListOf<GuestAction>()
    if (!running) list += GuestAction.START
    if (running) {
        list += listOf(GuestAction.SHUTDOWN, GuestAction.REBOOT, GuestAction.STOP)
        if (type == GuestType.QEMU) {
            list += listOf(GuestAction.RESET, GuestAction.SUSPEND)
        }
    }
    if (status == "paused" || status == "suspended" || status == "frozen") list += GuestAction.RESUME
    return list
}
