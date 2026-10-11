package com.pxmx.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.data.model.ServerLampThresholds
import com.pxmx.app.data.session.SessionStore
import com.pxmx.app.ui.components.ServerLamp
import com.pxmx.app.ui.components.ServerLampHue
import com.pxmx.app.ui.components.ServerLampMode
import com.pxmx.app.ui.components.ServerLampState
import com.pxmx.app.ui.components.TechActionPlate
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechSectionLabel
import com.pxmx.app.ui.components.techTopAppBarColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerLampSettingsScreen(
    sessionStore: SessionStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thresholds by sessionStore.serverLampThresholds.collectAsStateWithLifecycle()

    ServerLampSettingsContent(
        thresholds = thresholds,
        onActivityChange = { sessionStore.setServerLampActivity(it) },
        onWarnChange = { sessionStore.setServerLampWarn(it) },
        onReset = { sessionStore.resetServerLampThresholds() },
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerLampSettingsContent(
    thresholds: ServerLampThresholds,
    onActivityChange: (Int) -> Unit,
    onWarnChange: (Int) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = techTopAppBarColors(),
                title = {
                    Column {
                        Text("Server lamps")
                        Text(
                            "THRESHOLDS · ACTIVITY & LOAD",
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TechSectionLabel("CPU thresholds")
            }

            item {
                TechPlate(railColor = TechColors.LinkGreen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "ACTIVITY THRESHOLD",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = TechColors.LinkGreen,
                            )
                            Text(
                                text = "${thresholds.activityPercent}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "CPU usage at which lamps start blinking activity.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = thresholds.activityPercent.toFloat(),
                            onValueChange = { onActivityChange(it.roundToInt()) },
                            valueRange = 0f..99f,
                            colors = SliderDefaults.colors(
                                thumbColor = TechColors.LinkGreen,
                                activeTrackColor = TechColors.LinkGreen,
                            ),
                        )
                    }
                }
            }

            item {
                TechPlate(railColor = TechColors.Amber) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "LOAD THRESHOLD",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = TechColors.Amber,
                            )
                            Text(
                                text = "${thresholds.warnPercent}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "CPU usage at which activity lamps turn amber.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = thresholds.warnPercent.toFloat(),
                            onValueChange = { onWarnChange(it.roundToInt()) },
                            valueRange = 1f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = TechColors.Amber,
                                activeTrackColor = TechColors.Amber,
                            ),
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TechActionPlate(
                        label = "Reset",
                        onClick = onReset,
                    )
                }
            }

            item {
                TechSectionLabel("Lamp legend")
            }

            item {
                TechPlate(railColor = TechColors.Edge) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ServerLamp(
                                state = ServerLampState(ServerLampMode.STEADY, ServerLampHue.GREEN),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "IDLE · Solid green below ${thresholds.activityPercent}% CPU",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ServerLamp(
                                state = ServerLampState(ServerLampMode.ACTIVITY, ServerLampHue.GREEN),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "ACTIVITY · Pulsing green from ${thresholds.activityPercent}% to ${thresholds.warnPercent - 1}% CPU",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ServerLamp(
                                state = ServerLampState(ServerLampMode.ACTIVITY, ServerLampHue.AMBER),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "LOAD · Pulsing amber at or above ${thresholds.warnPercent}% CPU",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
