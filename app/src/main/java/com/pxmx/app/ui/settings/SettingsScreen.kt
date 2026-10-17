package com.pxmx.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.pxmx.app.ui.adaptive.AdaptiveAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.pxmx.app.ui.components.TechSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.data.model.NotificationPrefs
import com.pxmx.app.data.model.ServerLampThresholds
import com.pxmx.app.data.model.ThemeMode
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechPlateShape
import com.pxmx.app.ui.components.TechSectionLabel
import com.pxmx.app.ui.components.techTopAppBarColors
import com.pxmx.app.ui.theme.LocalThemeBackdrop
import com.pxmx.app.ui.theme.LocalThemeBackdropEnabled
import com.pxmx.app.ui.theme.ThemeBackdropSection
import com.pxmx.app.ui.extras.StupidAnimationsRow
import com.pxmx.app.ui.extras.RefreshCogRow
import kotlinx.coroutines.launch

/**
 * “Drawer” for less-daily tools: network, sdn, firewall, updates, appearance, accounts.
 * Reached by scrolling past the main resource list on Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    hostDisplay: String,
    versionDisplay: String,
    themeMode: ThemeMode,
    uiState: SettingsUiState = SettingsUiState(),
    onBack: () -> Unit,
    onOpenNucleus: () -> Unit = {},
    onOpenNetwork: () -> Unit,
    onOpenSdn: () -> Unit,
    onOpenFirewall: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenPermissions: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    lampThresholds: ServerLampThresholds = ServerLampThresholds(),
    onOpenServerLamps: () -> Unit = {},
    notificationPrefs: NotificationPrefs = NotificationPrefs(),
    onOpenNotifications: () -> Unit = {},
    onThemeMode: (ThemeMode) -> Unit,
    onSwitchAccount: () -> Unit,
    onCleanSlate: () -> Unit = {},
    onRandomJump: () -> Unit = {},
    consoleCardFace: Boolean = false,
    onConsoleCardFace: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showTheme by remember { mutableStateOf(false) }

    // Back handling: close theme modal first if open, else navigate back
    BackHandler(enabled = showTheme) {
        showTheme = false
    }
    BackHandler(enabled = !showTheme) {
        onBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = techTopAppBarColors(),
                title = {
                    Column {
                        Text("Settings")
                        Text(
                            "TOOLS · NETWORK · UPDATES",
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
                TechSectionLabel("Cluster tools")
            }
            item {
                SettingsRow(
                    title = "Nucleus",
                    subtitle = "3D map of this server",
                    onClick = onOpenNucleus,
                )
            }
            item {
                SettingsRow(
                    title = "Network",
                    subtitle = "Node interfaces and bridges",
                    onClick = onOpenNetwork,
                )
            }
            item {
                SettingsRow(
                    title = "SDN",
                    subtitle = uiState.sdnSubtitle,
                    onClick = onOpenSdn,
                )
            }
            item {
                SettingsRow(
                    title = "Firewall",
                    subtitle = uiState.firewallSubtitle,
                    onClick = onOpenFirewall,
                )
            }
            item {
                SettingsRow(
                    title = "Updates",
                    subtitle = uiState.updatesSubtitle,
                    onClick = onOpenUpdates,
                )
            }
            item {
                SettingsRow(
                    title = "Logs",
                    subtitle = uiState.logsSubtitle,
                    onClick = onOpenLogs,
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                TechSectionLabel("App")
            }
            item {
                SettingsRow(
                    title = "Server lamps",
                    subtitle = "Activity ${lampThresholds.activityPercent}% · Load ${lampThresholds.warnPercent}%",
                    onClick = onOpenServerLamps,
                )
            }
            item {
                SettingsRow(
                    title = "Notifications",
                    subtitle = if (notificationPrefs.masterEnabled) "Master ON · ${notificationPrefs.activeTriggersCount} triggers" else "Disabled",
                    onClick = onOpenNotifications,
                )
            }
            item {
                SettingsRow(
                    title = "App permissions",
                    subtitle = "INTERNET · NETWORK · VIBRATE · NOTIFICATIONS",
                    onClick = onOpenPermissions,
                )
            }
            item {
                val hasBackdrop = LocalThemeBackdropEnabled.current || LocalThemeBackdrop.current != null
                SettingsRow(
                    title = "Appearance",
                    subtitle = "Current · ${themeMode.label}${if (hasBackdrop) " · Image" else ""}",
                    onClick = { showTheme = true },
                )
            }
            item {
                SettingsRow(
                    title = "Switch account",
                    subtitle = "End session · pick another profile",
                    onClick = onSwitchAccount,
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                TechSectionLabel("Extras")
            }
            item {
                TechPlate(
                    railColor = TechColors.Edge,
                    showRail = true,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CONSOLE ON CARDS",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Last console frame on a running VM card. Image background turns off while this is on.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TechSwitch(
                            checked = consoleCardFace,
                            onCheckedChange = onConsoleCardFace,
                        )
                    }
                    StupidAnimationsRow()
                    RefreshCogRow()
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                TechSectionLabel("Session")
            }
            item {
                TechPlate(railColor = TechColors.Edge) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(
                                    "CONNECTED",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    hostDisplay,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    "PVE $versionDisplay",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Firewall enable and SDN apply write to the cluster; guest power stays on guest cards.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Creator bracket — static public repo link, no user data, always resolves the same way.
            item {
                val context = LocalContext.current
                val haptic = LocalHapticFeedback.current
                val creatorScope = rememberCoroutineScope()
                val creatorProgress = remember { Animatable(0f) }
                val currentOnRandomJump by rememberUpdatedState(onRandomJump)
                val amberColor = TechColors.Amber
                TechPlate(
                    railColor = TechColors.Mute,
                    showRail = true,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                var completedHold = false
                                val pressStartMs = SystemClock.elapsedRealtime()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val animJob = creatorScope.launch {
                                    creatorProgress.snapTo(0f)
                                    creatorProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(durationMillis = 10000, easing = LinearEasing),
                                    )
                                    completedHold = true
                                    currentOnRandomJump()
                                }
                                val released = tryAwaitRelease()
                                val heldMs = SystemClock.elapsedRealtime() - pressStartMs
                                animJob.cancel()
                                creatorScope.launch {
                                    creatorProgress.snapTo(0f)
                                }
                                if (released && !completedHold && heldMs < 400L) {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/primary-null/PXMX-VE"))
                                    )
                                }
                            },
                        )
                    },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawWithContent {
                                if (creatorProgress.value > 0f) {
                                    val edges = creatorBarEdges(size.width, creatorProgress.value)
                                    val fillColor = amberColor.copy(alpha = 0.22f)
                                    val strokePx = 2.dp.toPx()
                                    drawRect(
                                        color = fillColor,
                                        topLeft = Offset.Zero,
                                        size = size.copy(width = edges.leftWidth),
                                    )
                                    drawLine(
                                        color = amberColor,
                                        start = Offset(edges.leftWidth, 0f),
                                        end = Offset(edges.leftWidth, size.height),
                                        strokeWidth = strokePx,
                                    )
                                    drawRect(
                                        color = fillColor,
                                        topLeft = Offset(edges.rightStart, 0f),
                                        size = size.copy(width = size.width - edges.rightStart),
                                    )
                                    drawLine(
                                        color = amberColor,
                                        start = Offset(edges.rightStart, 0f),
                                        end = Offset(edges.rightStart, size.height),
                                        strokeWidth = strokePx,
                                    )
                                }
                                drawContent()
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "CREATED BY",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "primary-null · github.com/primary-null/PXMX-VE",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Task 1: Clean Slate (Strict 10-second hold)
            item {
                Spacer(Modifier.height(8.dp))
                CleanSlatePlate(onPurgeAndExit = onCleanSlate)
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showTheme) {
        AdaptiveAlertDialog(
            onDismissRequest = { showTheme = false },
            title = { Text("Appearance") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeMode(mode)
                                    showTheme = false
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    onThemeMode(mode)
                                    showTheme = false
                                },
                            )
                            Text(mode.label)
                        }
                    }
                    ThemeBackdropSection()
                }
            },
            confirmButton = {
                TextButton(onClick = { showTheme = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun CleanSlatePlate(
    onPurgeAndExit: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var isHolding by remember { mutableStateOf(false) }
    val dangerColor = TechColors.Danger

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TechPlateShape)
            .border(1.dp, dangerColor, TechPlateShape)
            .background(TechColors.Hull, TechPlateShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                        } else {
                            @Suppress("DEPRECATION")
                            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        }
                        if (vibrator != null) {
                            val waveform = cleanSlateWaveform()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(
                                    VibrationEffect.createWaveform(
                                        waveform.timings,
                                        waveform.amplitudes,
                                        -1,
                                    )
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(10000L)
                            }
                        }
                        val animJob = scope.launch {
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 10000, easing = LinearEasing),
                            )
                            // 10s completed exactly
                            onPurgeAndExit()
                        }
                        tryAwaitRelease()
                        isHolding = false
                        animJob.cancel()
                        vibrator?.cancel()
                        scope.launch {
                            progress.snapTo(0f)
                        }
                    }
                )
            }
            .drawWithContent {
                if (progress.value > 0f) {
                    val fillWidth = size.width * progress.value
                    drawRect(
                        color = dangerColor.copy(alpha = 0.22f),
                        size = size.copy(width = fillWidth),
                    )
                    drawLine(
                        color = dangerColor,
                        start = Offset(fillWidth, 0f),
                        end = Offset(fillWidth, size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
                drawContent()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(dangerColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "CLEAN SLATE",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = dangerColor,
                    )
                    if (isHolding && progress.value > 0f) {
                        val secondsLeft = ((1f - progress.value) * 10).toInt() + 1
                        Text(
                            text = "HOLD ${secondsLeft}S",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = dangerColor,
                        )
                    } else {
                        Text(
                            text = "HOLD 10S",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = dangerColor.copy(alpha = 0.7f),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Erases every profile, credential, and setting. The app closes and returns to a first-run state.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    TechPlate(
        railColor = TechColors.Edge,
        showRail = true,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
