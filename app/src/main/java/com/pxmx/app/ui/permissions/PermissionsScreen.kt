package com.pxmx.app.ui.permissions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechSectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Permissions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TechSectionLabel(title = "Required permissions")
            }

            item {
                PermissionCard(
                    name = "INTERNET",
                    reason = "Used to connect to your Proxmox VE server API and console websockets."
                )
            }

            item {
                PermissionCard(
                    name = "ACCESS_NETWORK_STATE",
                    reason = "Used to detect active network interfaces for local subnet scanning."
                )
            }

            item {
                TechPlate(railColor = TechColors.CoolBlue) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = "PRIVACY NOTE",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TechColors.CoolBlue
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "No runtime permission prompts. These two are granted at install; nothing else is ever requested.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    name: String,
    reason: String
) {
    TechPlate(railColor = TechColors.Edge) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
