package com.pxmx.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.data.model.NotificationPrefs
import com.pxmx.app.data.session.SessionStore
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechSectionLabel
import com.pxmx.app.ui.components.techTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    sessionStore: SessionStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs by sessionStore.notificationPrefs.collectAsStateWithLifecycle()

    NotificationSettingsContent(
        prefs = prefs,
        onUpdatePrefs = { sessionStore.setNotificationPrefs(it) },
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsContent(
    prefs: NotificationPrefs,
    onUpdatePrefs: (NotificationPrefs) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onUpdatePrefs(prefs.copy(masterEnabled = true))
        }
    }

    fun handleMasterToggle(enabled: Boolean) {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPerm) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }
            onUpdatePrefs(prefs.copy(masterEnabled = true))
        } else {
            onUpdatePrefs(prefs.copy(masterEnabled = false))
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = techTopAppBarColors(),
                title = {
                    Column {
                        Text("Notifications")
                        Text(
                            "CLUSTER ALERTS · TASKS",
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
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TechSectionLabel("Master")
            }
            item {
                TechPlate(railColor = if (prefs.masterEnabled) TechColors.LinkGreen else TechColors.Mute) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Master notifications",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    if (prefs.masterEnabled) "Posting enabled · ${prefs.activeTriggersCount} active alerts"
                                    else "All cluster notifications paused",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = prefs.masterEnabled,
                                onCheckedChange = { handleMasterToggle(it) },
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                TechSectionLabel("Alert catalog")
            }

            item {
                TechPlate(railColor = TechColors.Edge) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        NotificationToggleRow(
                            title = "New guests",
                            subtitle = "Alert on VM or container creation / clone",
                            checked = prefs.newGuest,
                            enabled = prefs.masterEnabled,
                            onCheckedChange = { onUpdatePrefs(prefs.copy(newGuest = it)) },
                        )
                        NotificationToggleRow(
                            title = "Backup failures",
                            subtitle = "Alert when vzdump backup exits non-OK",
                            checked = prefs.backupFail,
                            enabled = prefs.masterEnabled,
                            onCheckedChange = { onUpdatePrefs(prefs.copy(backupFail = it)) },
                        )
                        NotificationToggleRow(
                            title = "Node fencing",
                            subtitle = "Alert on HA fencing events across cluster",
                            checked = prefs.fencing,
                            enabled = prefs.masterEnabled,
                            onCheckedChange = { onUpdatePrefs(prefs.copy(fencing = it)) },
                        )
                        NotificationToggleRow(
                            title = "Migrations",
                            subtitle = "Watch qmigrate & vzmigrate with live task card",
                            checked = prefs.migrate,
                            enabled = prefs.masterEnabled,
                            onCheckedChange = { onUpdatePrefs(prefs.copy(migrate = it)) },
                        )
                        NotificationToggleRow(
                            title = "Package updates",
                            subtitle = "Alert when APT package updates are available",
                            checked = prefs.packageUpdates,
                            enabled = prefs.masterEnabled,
                            onCheckedChange = { onUpdatePrefs(prefs.copy(packageUpdates = it)) },
                        )
                        NotificationToggleRow(
                            title = "External start / stop",
                            subtitle = "Alert on power events not started from this app",
                            checked = prefs.externalStartStop,
                            enabled = prefs.masterEnabled,
                            onCheckedChange = { onUpdatePrefs(prefs.copy(externalStartStop = it)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}
