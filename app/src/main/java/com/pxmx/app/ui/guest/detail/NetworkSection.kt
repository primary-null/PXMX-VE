package com.pxmx.app.ui.guest.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pxmx.app.data.model.ConfigNet
import com.pxmx.app.data.model.ConfigUsb
import com.pxmx.app.data.model.HostUsbDevice
import com.pxmx.app.data.model.ParsedGuestConfig

@Composable
fun NetworkUsbBody(
    cfg: ParsedGuestConfig?,
    hostUsbs: List<HostUsbDevice>,
    isQemu: Boolean,
    busy: Boolean,
    onDetach: (usbKey: String) -> Unit,
    onAttach: (hostId: String, usb3: Boolean) -> Unit,
    onRefreshUsb: () -> Unit,
) {
    if (cfg == null) {
        Text("No config", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    Text("Network", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    if (cfg.nets.isEmpty()) {
        Text("No NICs", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        cfg.nets.forEach { MiniNet(it) }
    }

    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Usb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("USB attached to guest", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconButton(onClick = onRefreshUsb, enabled = !busy, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh USB", modifier = Modifier.size(18.dp))
        }
    }
    Spacer(Modifier.height(4.dp))
    if (!isQemu) {
        Text("USB passthrough is only available for QEMU VMs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (cfg.usbs.isEmpty()) {
        Text("None attached", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        cfg.usbs.forEach { usb ->
            AttachedUsbRow(usb = usb, busy = busy, onDetach = { onDetach(usb.key) })
        }
    }

    if (isQemu) {
        Spacer(Modifier.height(14.dp))
        Text("Host USB (live)", fontWeight = FontWeight.SemiBold)
        Text(
            "Tap Attach to map a device. Hubs are listed dimmed.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        val attachedIds = cfg.usbs.mapNotNull { it.host?.lowercase() }.toSet()
        val devices = hostUsbs
            .filter { it.hostId != null }
            .sortedWith(
                compareBy<HostUsbDevice> { it.isHub }
                    .thenBy { it.busnum ?: 0 }
                    .thenBy { it.devnum ?: 0 },
            )

        if (devices.isEmpty()) {
            Text("No host USB reported", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            devices.forEach { dev ->
                val id = dev.hostId ?: return@forEach
                val attached = id in attachedIds
                HostUsbRow(
                    device = dev,
                    attached = attached,
                    busy = busy,
                    onAttach = { onAttach(id, true) },
                )
            }
        }
    }

    if (cfg.pcis.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("PCI", fontWeight = FontWeight.SemiBold)
        cfg.pcis.forEach {
            Text("${it.key}: ${it.raw}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun MiniNet(net: ConfigNet) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            listOfNotNull(net.key, net.model).joinToString(" · "),
            fontWeight = FontWeight.Medium,
        )
        Text(
            buildString {
                net.mac?.let { append(it) }
                net.bridge?.let {
                    if (isNotEmpty()) append(" · ")
                    append(it)
                    net.tag?.let { t -> append(" vlan$t") }
                }
                if (net.firewall) append(" · fw")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MiniUsb(usb: ConfigUsb) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(usb.key, fontWeight = FontWeight.Medium)
            Text(
                usb.resolvedName ?: usb.host ?: usb.raw,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AttachedUsbRow(
    usb: ConfigUsb,
    busy: Boolean,
    onDetach: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Usb, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(usb.key, fontWeight = FontWeight.SemiBold)
                Text(
                    usb.resolvedName ?: usb.host ?: usb.raw,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    usb.raw + if (usb.usb3) " · usb3" else "",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onDetach, enabled = !busy) {
                Text("Detach")
            }
        }
    }
}

@Composable
private fun HostUsbRow(
    device: HostUsbDevice,
    attached: Boolean,
    busy: Boolean,
    onAttach: () -> Unit,
) {
    val dim = device.isHub
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                alpha = if (dim) 0.55f else 1f,
            ),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    device.displayName,
                    fontWeight = FontWeight.Medium,
                    color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
                device.hostId?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                }
                Text(
                    device.detailLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                attached -> Text(
                    "In use",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                device.isHub -> Text(
                    "Hub",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Button(onClick = onAttach, enabled = !busy) { Text("Attach") }
            }
        }
    }
}
