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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pxmx.app.data.model.ConfigNet
import com.pxmx.app.data.model.ConfigUsb
import com.pxmx.app.data.model.FirewallSnapshot
import com.pxmx.app.data.model.GuestLiveInterface
import com.pxmx.app.data.model.HostUsbDevice
import com.pxmx.app.data.model.ParsedGuestConfig

@Composable
fun NetworkUsbBody(
    cfg: ParsedGuestConfig?,
    hostUsbs: List<HostUsbDevice>,
    liveInterfaces: List<GuestLiveInterface> = emptyList(),
    firewall: FirewallSnapshot? = null,
    isQemu: Boolean,
    busy: Boolean,
    onDetach: (usbKey: String) -> Unit,
    onAttach: (hostId: String, usb3: Boolean) -> Unit,
    onRefreshUsb: () -> Unit,
    onToggleFirewall: ((net: ConfigNet) -> Unit)? = null,
    onToggleLinkDown: ((net: ConfigNet) -> Unit)? = null,
) {
    if (cfg == null) {
        Text("No config", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    if (firewall != null) {
        val fwState = if (firewall.enabled) "on" else "off"
        val inPol = firewall.policyIn?.uppercase() ?: "—"
        val outPol = firewall.policyOut?.uppercase() ?: "—"
        Text(
            text = "Guest fw · $fwState · in $inPol · out $outPol",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = if (firewall.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }

    Text("Network", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))

    val matchedLiveMap = mutableMapOf<ConfigNet, GuestLiveInterface>()
    val matchedIfaces = mutableSetOf<GuestLiveInterface>()

    for (net in cfg.nets) {
        val netMac = net.mac?.trim()?.lowercase()
        val lxcName = net.extra["name"]?.trim()?.lowercase()
        val match = liveInterfaces.firstOrNull { iface ->
            iface !in matchedIfaces && (
                (netMac != null && iface.mac?.trim()?.lowercase() == netMac) ||
                (lxcName != null && iface.name.trim().lowercase() == lxcName) ||
                (iface.name.trim().lowercase() == net.key.trim().lowercase())
            )
        }
        if (match != null) {
            matchedLiveMap[net] = match
            matchedIfaces.add(match)
        }
    }
    val leftoverLiveIfaces = liveInterfaces.filter { it !in matchedIfaces }

    if (cfg.nets.isEmpty()) {
        Text("No NICs", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        cfg.nets.forEach { net ->
            MiniNet(
                net = net,
                cloudInitIp = cfg.ipconfigForNet(net.key),
                liveInterface = matchedLiveMap[net],
                isQemu = isQemu,
                busy = busy,
                onToggleFirewall = onToggleFirewall?.let { { it(net) } },
                onToggleLinkDown = onToggleLinkDown?.let { { it(net) } },
            )
        }
    }

    if (leftoverLiveIfaces.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Live addresses",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        leftoverLiveIfaces.forEach { iface ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        listOfNotNull(iface.name, iface.mac).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    )
                    iface.addresses.forEach { addr ->
                        val p = addr.prefix?.let { "/$it" } ?: ""
                        Text(
                            "${addr.ip}$p (${addr.type})",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
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
private fun MiniNet(
    net: ConfigNet,
    cloudInitIp: String?,
    liveInterface: GuestLiveInterface?,
    isQemu: Boolean,
    busy: Boolean,
    onToggleFirewall: (() -> Unit)?,
    onToggleLinkDown: (() -> Unit)?,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Cable,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        listOfNotNull(net.key, net.model ?: if (isQemu) "virtio" else "veth").joinToString(" · "),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (net.linkDown) {
                        Text(
                            "DOWN",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            "UP",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (net.firewall) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "FW",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            net.mac?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            val bridgeVlan = buildString {
                net.bridge?.let {
                    append("bridge $it")
                    net.tag?.let { t -> append(" · vlan $t") }
                }
            }
            if (bridgeVlan.isNotEmpty()) {
                Text(
                    bridgeVlan,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val extras = listOfNotNull(
                net.rate?.let { "rate ${it}MB/s" },
                net.mtu?.let { "mtu $it" },
                net.queues?.let { "queues $it" },
            ).joinToString(" · ")
            if (extras.isNotEmpty()) {
                Text(
                    extras,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val lxcIps = listOfNotNull(
                net.lxcIp?.let { "ip $it" },
                net.lxcGw?.let { "gw $it" },
                net.lxcIp6?.let { "ip6 $it" },
                net.lxcGw6?.let { "gw6 $it" },
            ).joinToString(" · ")
            if (lxcIps.isNotEmpty()) {
                Text(
                    lxcIps,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            cloudInitIp?.let {
                Text(
                    "ipconfig: $it",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (liveInterface != null && liveInterface.addresses.isNotEmpty()) {
                liveInterface.addresses.forEach { addr ->
                    val p = addr.prefix?.let { "/$it" } ?: ""
                    Text(
                        "live: ${addr.ip}$p (${addr.type})",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (onToggleFirewall != null || onToggleLinkDown != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onToggleFirewall != null) {
                        TextButton(
                            onClick = onToggleFirewall,
                            enabled = !busy,
                        ) {
                            Text(
                                if (net.firewall) "Firewall: ON" else "Firewall: OFF",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    if (onToggleLinkDown != null) {
                        TextButton(
                            onClick = onToggleLinkDown,
                            enabled = !busy,
                        ) {
                            Text(
                                if (net.linkDown) "Link: DOWN" else "Link: UP",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
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
