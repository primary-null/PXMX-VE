package com.pxmx.app.ui.dex

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.ui.home.HomeListRow
import com.pxmx.app.ui.home.HomeViewModel
import com.pxmx.app.ui.home.ResourceFilter
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechColors
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pxmx.app.ui.components.SystemLogStrip
import com.pxmx.app.ui.home.ResourceCard
import com.pxmx.app.ui.home.StatusSectionHeader
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.ui.adaptive.DetailPaneSelection

@Composable
fun DexDashboardScreen(
    viewModel: HomeViewModel,
    selectedDetail: DetailPaneSelection?,
    onSelectDetail: (DetailPaneSelection?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenServers: () -> Unit,
    onSwitchAccount: () -> Unit,
    onOpenLogs: () -> Unit,
    detailPaneContent: @Composable (DetailPaneSelection) -> Unit,
    consolePaneContent: @Composable (DetailPaneSelection) -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val latestLog by viewModel.latestLog.collectAsStateWithLifecycle()

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Nav Rail
        NavigationRail(
            containerColor = TechColors.Hull,
            modifier = Modifier.width(80.dp).fillMaxHeight()
        ) {
            Spacer(Modifier.height(16.dp))
            NavigationRailItem(
                selected = state.filter == ResourceFilter.ALL,
                onClick = { viewModel.setFilter(ResourceFilter.ALL) },
                icon = { Icon(Icons.Default.Dashboard, contentDescription = "All") },
                label = { Text("All", fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
            )
            NavigationRailItem(
                selected = state.filter == ResourceFilter.GUESTS,
                onClick = { viewModel.setFilter(ResourceFilter.GUESTS) },
                icon = { Icon(Icons.Default.Dns, contentDescription = "Guests") },
                label = { Text("Guests", fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
            )
            NavigationRailItem(
                selected = state.filter == ResourceFilter.NODES,
                onClick = { viewModel.setFilter(ResourceFilter.NODES) },
                icon = { Icon(Icons.Default.Storage, contentDescription = "Nodes") },
                label = { Text("Nodes", fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
            )
            Spacer(Modifier.weight(1f))
            NavigationRailItem(
                selected = false,
                onClick = onOpenServers,
                icon = { Icon(Icons.Default.Computer, contentDescription = "Servers") },
                label = { Text("Servers", fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
            )
            NavigationRailItem(
                selected = false,
                onClick = onOpenSettings,
                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                label = { Text("Config", fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
            )
            NavigationRailItem(
                selected = false,
                onClick = {
                    viewModel.logout()
                    onSwitchAccount()
                },
                icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout") },
                label = { Text("Logout", fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
            )
            Spacer(Modifier.height(16.dp))
        }

        VerticalDivider(color = TechColors.Edge)

        // Main Resource List area
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                singleLine = true,
                placeholder = {
                    Text(
                        "Search resources...",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = TechColors.Edge,
                    focusedContainerColor = TechColors.Hull,
                    unfocusedContainerColor = TechColors.Hull,
                ),
            )

            val visibleRows = state.listRows

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (visibleRows.isEmpty() && !state.loading) {
                    item {
                        Text(
                            "No resources",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(visibleRows) { row ->
                    when (row) {
                        is HomeListRow.Section -> {
                            StatusSectionHeader(
                                title = row.title,
                                count = row.count,
                                collapsed = false,
                                onToggle = {} // Simplified for DeX layout
                            )
                        }
                        is HomeListRow.Item -> {
                            val res = row.resource
                            val isSelected = when (selectedDetail) {
                                is DetailPaneSelection.Guest -> selectedDetail.node == res.node && selectedDetail.vmid == res.vmid
                                is DetailPaneSelection.Node -> selectedDetail.node == res.node && res.type == "node"
                                is DetailPaneSelection.Storage -> selectedDetail.node == res.node && selectedDetail.storage == res.name && res.type == "storage"
                                else -> false
                            }
                            ResourceCard(
                                resource = res,
                                busy = false, // state.busyGuestIds not available easily, default false
                                onClick = {
                                    when (res.type) {
                                        "node" -> res.node?.let { onSelectDetail(DetailPaneSelection.Node(it)) }
                                        "storage" -> if (res.node != null && res.name != null) onSelectDetail(DetailPaneSelection.Storage(res.node, res.name))
                                        else -> if (res.node != null && res.type != null && res.vmid != null && res.name != null) {
                                            onSelectDetail(DetailPaneSelection.Guest(res.node, res.type, res.vmid, res.name))
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Log Strip
            SystemLogStrip(
                entry = latestLog,
                onClick = onOpenLogs,
                modifier = Modifier.padding(16.dp)
            )
        }

        VerticalDivider(color = TechColors.Edge)

        // Right Detail/Console Area
        if (selectedDetail != null) {
            Column(modifier = Modifier.weight(2f).fillMaxHeight()) {
                // Top half: Detail Pane
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    detailPaneContent(selectedDetail)
                }

                VerticalDivider(color = TechColors.Edge, modifier = Modifier.fillMaxWidth().height(1.dp))

                // Bottom half: Console Pane
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    consolePaneContent(selectedDetail)
                }
            }
        } else {
            // Empty State
            Box(
                modifier = Modifier.weight(2f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a resource to view details",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
