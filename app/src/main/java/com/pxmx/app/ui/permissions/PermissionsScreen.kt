package com.pxmx.app.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechSectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isNotifyGranted by remember {
        mutableStateOf(RuntimeNotifyPermission.isGranted(context))
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        isNotifyGranted = granted
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNotifyGranted = RuntimeNotifyPermission.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                PermissionCard(
                    name = "VIBRATE",
                    reason = "Haptic feedback for actions on home, guest, node, and settings."
                )
            }

            item {
                val rail = if (isNotifyGranted) TechColors.LinkGreen else TechColors.Amber
                PermissionCard(
                    name = "POST_NOTIFICATIONS",
                    reason = "Task alerts, watched guests and nodes, and optional external guest events. Requested at runtime on Android 13+.",
                    status = if (isNotifyGranted) "GRANTED" else "NOT GRANTED",
                    statusColor = if (isNotifyGranted) TechColors.LinkGreen else TechColors.Amber,
                    railColor = rail,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && !isNotifyGranted) {
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }

            item {
                PermissionCard(
                    name = "POST_PROMOTED_NOTIFICATIONS",
                    reason = "Android 16 Live Updates for in-progress backup and migrate tasks."
                )
            }

            item {
                PermissionCard(
                    name = "FILES (SYSTEM PICKER)",
                    reason = "ISO upload opens the Android system file picker for a single file. " +
                        "Android will not show an all-files toggle in app settings because the picker " +
                        "itself is the file manager—PXMX never requests all-files or shared storage access.",
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
                            text = "INTERNET, ACCESS_NETWORK_STATE, and VIBRATE are granted at install. " +
                                "POST_NOTIFICATIONS is a runtime prompt on Android 13+. " +
                                "POST_PROMOTED_NOTIFICATIONS is used for Android 16 Live Updates. " +
                                "ISO upload uses the system file picker for a single file " +
                                "(no all-files or READ_MEDIA permissions; the picker is the file manager, " +
                                "so Android will not show an all-files toggle). " +
                                "No analytics, no extra SDK permissions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun PermissionCard(
    name: String,
    reason: String,
    status: String? = null,
    statusColor: Color? = null,
    railColor: Color = TechColors.Edge,
    onClick: (() -> Unit)? = null,
) {
    val baseModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    TechPlate(
        modifier = baseModifier,
        railColor = railColor,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = statusColor ?: MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
