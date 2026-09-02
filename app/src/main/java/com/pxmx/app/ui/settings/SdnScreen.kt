package com.pxmx.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.data.model.SdnStatusInfo
import com.pxmx.app.data.model.SdnVnetInfo
import com.pxmx.app.data.model.SdnZoneInfo
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechMetaLine
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechSectionLabel
import com.pxmx.app.ui.components.TechStatusPlate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SdnScreen(
    viewModel: SdnViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SDN")
                        Text(
                            "ZONES · VNETS · STATUS · READ-ONLY",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading && state.zones.isEmpty() && state.vnets.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.error?.let { err ->
                    item(key = "error-item") {
                        TechPlate(railColor = MaterialTheme.colorScheme.error) {
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.error,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }

                // 1. Zones Section
                item(key = "hdr-zones") {
                    TechSectionLabel("Zones", count = state.zones.size, accent = TechColors.CoolBlue)
                }

                if (state.zones.isEmpty()) {
                    item(key = "empty-zones") {
                        ZeroStateCard(title = "NO SDN ZONES", subtitle = "No SDN zones are defined in this cluster.")
                    }
                } else {
                    items(state.zones, key = { "z-${it.zone}" }) { zone ->
                        SdnZoneCard(zone)
                    }
                }

                // 2. Vnets Section
                item(key = "hdr-vnets") {
                    TechSectionLabel("Vnets", count = state.vnets.size, accent = TechColors.Amber)
                }

                if (state.vnets.isEmpty()) {
                    item(key = "empty-vnets") {
                        ZeroStateCard(title = "NO SDN VNETS", subtitle = "No SDN virtual networks are configured.")
                    }
                } else {
                    items(state.vnets, key = { "v-${it.vnet}" }) { vnet ->
                        SdnVnetCard(vnet)
                    }
                }

                // 3. Status Section
                item(key = "hdr-status") {
                    TechSectionLabel("Zone Status", count = state.statuses.size, accent = TechColors.LinkGreen)
                }

                if (state.statuses.isEmpty()) {
                    item(key = "empty-status") {
                        ZeroStateCard(title = "NO STATUS REPORTED", subtitle = "No live SDN controller/zone status reported.")
                    }
                } else {
                    items(state.statuses, key = { "s-${it.name}-${it.type}" }) { status ->
                        SdnStatusCard(status)
                    }
                }

                // Read-only note
                item(key = "readonly-note") {
                    Spacer(Modifier.height(4.dp))
                    TechPlate(railColor = TechColors.Mute) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = TechColors.Mute,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "SDN zones, vnets, and status are read-only in PXMX.",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun ZeroStateCard(title: String, subtitle: String) {
    TechPlate(railColor = TechColors.Edge) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = TechColors.Mute,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SdnZoneCard(zone: SdnZoneInfo) {
    val typeUpper = zone.type?.uppercase() ?: "SIMPLE"
    TechPlate(railColor = TechColors.CoolBlue) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (zone.zone ?: "?").uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = TechColors.CoolBlue.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TechColors.CoolBlue.copy(alpha = 0.5f)),
                    ) {
                        Text(
                            text = typeUpper,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TechColors.CoolBlue,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            val meta = listOfNotNull<String>(
                zone.ipam?.let { "IPAM $it" },
                zone.dns?.let { "DNS $it" },
                zone.mtu?.let { "MTU $it" },
                zone.bridge?.let { "BRIDGE $it" },
                zone.tag?.let { "TAG $it" },
                zone.peers?.let { "PEERS $it" },
            ).joinToString(" · ")

            if (meta.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SdnVnetCard(vnet: SdnVnetInfo) {
    TechPlate(railColor = TechColors.Amber) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    (vnet.vnet ?: "?").uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                vnet.tag?.let { tag ->
                    Surface(
                        color = TechColors.Deck,
                        shape = RoundedCornerShape(2.dp),
                    ) {
                        Text(
                            text = "TAG $tag",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            val meta = listOfNotNull<String>(
                vnet.zone?.let { "ZONE $it" },
                if (vnet.vlanaware) "VLAN-AWARE" else null,
                vnet.alias?.takeIf { it.isNotBlank() }?.let { "ALIAS $it" },
            ).joinToString(" · ")

            if (meta.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SdnStatusCard(status: SdnStatusInfo) {
    val isOk = status.isOk
    val statusColor = if (isOk) TechColors.LinkGreen else TechColors.Danger
    val railColor = if (isOk) TechColors.LinkGreen else TechColors.Danger

    TechPlate(railColor = railColor) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        status.name.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    status.type?.takeIf { it.isNotBlank() }?.let { t ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "· $t",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                status.controller?.takeIf { it.isNotBlank() }?.let { ctrl ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "CONTROLLER: $ctrl",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                color = statusColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.6f)),
            ) {
                Text(
                    text = (status.status ?: "UNKNOWN").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
