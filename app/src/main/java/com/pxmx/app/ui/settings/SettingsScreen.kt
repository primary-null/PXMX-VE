package com.pxmx.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.pxmx.app.data.model.ThemeMode
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechIconBay
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechPlateShape
import com.pxmx.app.ui.components.TechSectionLabel
import kotlinx.coroutines.delay
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
    onBack: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenSdn: () -> Unit,
    onOpenFirewall: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenPermissions: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onThemeMode: (ThemeMode) -> Unit,
    onSwitchAccount: () -> Unit,
    onCleanSlate: () -> Unit = {},
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
        topBar = {
            TopAppBar(
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
                    title = "Network",
                    subtitle = "Node interfaces and bridges",
                    onClick = onOpenNetwork,
                )
            }
            item {
                SettingsRow(
                    title = "SDN",
                    subtitle = "Software-defined zones, vnets, and status",
                    onClick = onOpenSdn,
                )
            }
            item {
                SettingsRow(
                    title = "Firewall",
                    subtitle = "Datacenter and node rules (read-only)",
                    onClick = onOpenFirewall,
                )
            }
            item {
                SettingsRow(
                    title = "Updates",
                    subtitle = "Pending packages with live apt job status",
                    onClick = onOpenUpdates,
                )
            }
            item {
                SettingsRow(
                    title = "Logs",
                    subtitle = "Cluster syslog and event feed",
                    onClick = onOpenLogs,
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                TechSectionLabel("App")
            }
            item {
                SettingsRow(
                    title = "App permissions",
                    subtitle = "INTERNET · NETWORK_STATE",
                    onClick = onOpenPermissions,
                )
            }
            item {
                SettingsRow(
                    title = "Appearance",
                    subtitle = "Current · ${themeMode.label}",
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
                TechSectionLabel("Session")
            }
            item {
                TechPlate(railColor = TechColors.Edge) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TechIconBay(
                                icon = Icons.Default.Info,
                                accent = MaterialTheme.colorScheme.primary,
                                size = 36.dp,
                                iconSize = 20.dp,
                            )
                            Spacer(Modifier.width(10.dp))
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
                            "Network & Updates are read-first. Destructive actions stay on guest cards and power menus.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Creator bracket — static public repo link, no user data, always resolves the same way.
            item {
                val context = LocalContext.current
                TechPlate(
                    railColor = TechColors.Mute,
                    showRail = true,
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/primary-null/PXMX-VE"))
                        )
                    },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
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
        AlertDialog(
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
                            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                        } else {
                            @Suppress("DEPRECATION")
                            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        }
                        val animJob = scope.launch {
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 10000, easing = LinearEasing),
                            )
                            // 10s completed exactly
                            onPurgeAndExit()
                        }
                        // Continuous escalating vibration: one strong pulse per second,
                        // ramping in amplitude across all ten seconds. Stops on release.
                        val buzzJob = scope.launch {
                            for (s in 1..10) {
                                val amp = (60 + s * 19).coerceAtMost(255)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(
                                        VibrationEffect.createWaveform(
                                            longArrayOf(0, 900),
                                            intArrayOf(amp, 0),
                                            0,
                                        )
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(900)
                                }
                                delay(1000)
                            }
                        }
                        tryAwaitRelease()
                        isHolding = false
                        animJob.cancel()
                        buzzJob.cancel()
                        vibrator.cancel()
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
