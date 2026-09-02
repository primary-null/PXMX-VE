package com.pxmx.app.ui.servers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    viewModel: ServersViewModel,
    onBack: () -> Unit,
    onServerSelected: (String) -> Unit,
    onLoginPrefilled: (String) -> Unit
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Servers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Row(modifier = Modifier.padding(end = 8.dp)) {
                        TechActionPlate(
                            label = "Sync",
                            onClick = { viewModel.refresh() }
                        )
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.servers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No saved servers — add one on the login screen",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.servers, key = { it.profileId }) { server ->
                        ServerCard(
                            server = server,
                            onClick = {
                                if (server.hasSavedSecret) {
                                    onServerSelected(server.profileId)
                                } else {
                                    onLoginPrefilled(server.profileId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(
    server: ServerCardState,
    onClick: () -> Unit
) {
    TechPlate(
        railColor = when {
            server.loading -> TechColors.Mute
            server.online -> TechColors.LinkGreen
            else -> TechColors.Edge
        },
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        server.label.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        server.host,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (server.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    val statusText = when {
                        !server.hasSavedSecret -> "No credentials"
                        server.online -> "Online"
                        else -> "Offline"
                    }
                    TechStatusPlate(status = statusText)
                }
            }

            if (server.online) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "PVE ${server.version ?: "—"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.4.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "RUNNING ${server.running} · STOPPED ${server.stopped}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.4.sp
                    )
                }

                if (server.guests.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        server.guests.take(6).chunked(2).forEach { rowGuests ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowGuests.forEach { (name, status) ->
                                    GuestTag(name, status, Modifier.weight(1f))
                                }
                                if (rowGuests.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    if (server.guests.size > 6) {
                        Text(
                            "+${server.guests.size - 6} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else if (server.errorText != null && server.hasSavedSecret) {
                Spacer(Modifier.height(8.dp))
                Text(
                    server.errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun GuestTag(name: String, status: String, modifier: Modifier = Modifier) {
    val isRunning = status == "running" || status == "online"
    val color = if (isRunning) TechColors.LinkGreen else TechColors.Mute
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace
        )
    }
}
