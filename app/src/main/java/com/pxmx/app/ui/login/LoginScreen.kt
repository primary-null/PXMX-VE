package com.pxmx.app.ui.login

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.SavedProfile
import com.pxmx.app.data.model.SessionResumeInfo
import com.pxmx.app.data.net.DiscoveredHost
import com.pxmx.app.ui.components.MetricBar
import com.pxmx.app.ui.components.TechActionPlate
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.util.formatLastLoginMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoggedIn: () -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val previous by viewModel.previousSession.collectAsStateWithLifecycle()
    val last = viewModel.lastResumable
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) {
            viewModel.consumeLoggedIn()
            onLoggedIn()
        }
    }

    // Back handling: dismiss pending conflict profile dialog first if open
    BackHandler(enabled = state.pendingConflictProfile != null) {
        viewModel.resolveConflictCancel()
    }

    if (state.pendingConflictProfile != null) {
        val conflict = state.pendingConflictProfile!!
        AlertDialog(
            onDismissRequest = { viewModel.resolveConflictCancel() },
            title = { Text("Profile conflict") },
            text = {
                Text("A saved profile for ${conflict.host} already exists.")
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.resolveConflictReplace() }) {
                        Text("REPLACE")
                    }
                    TextButton(onClick = { viewModel.resolveConflictKeepBoth() }) {
                        Text("KEEP BOTH")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveConflictCancel() }) {
                    Text("CANCEL")
                }
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Fixed Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "PXMX",
                style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 1.5.sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = viewModel::onTitleClick),
            )
            Text(
                text = "Connect your hypervisor",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Scrollable Form
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            if (state.tfaRequired) {
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            text = "Two-Factor Authentication",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Enter the 6-digit verification code from your authenticator app for ${state.username}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))

                        val tfaBringIntoViewRequester = remember { BringIntoViewRequester() }
                        OutlinedTextField(
                            value = state.tfaCode,
                            onValueChange = { viewModel.setTfaCode(it) },
                            label = { Text("6-digit code") },
                            placeholder = { Text("123456") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 4.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(tfaBringIntoViewRequester)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        scope.launch {
                                            delay(250)
                                            tfaBringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                        )

                        val isDemo = state.host.trim().equals("demo", ignoreCase = true) ||
                            state.username.trim().startsWith("tfa-user", ignoreCase = true)
                        if (isDemo) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Demo hint: code is 123456",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        state.error?.let { err ->
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.submitTfa() },
                            enabled = !state.loading && state.tfaCode.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.loading) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (state.loading) "Verifying…" else "Verify & Connect")
                        }

                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.cancelTfa() },
                            enabled = !state.loading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            } else {
                // Jump back after switch-account
                previous?.let { prev ->
                    Spacer(Modifier.height(16.dp))
                    JumpBackBanner(
                        info = prev,
                        busy = state.loading,
                        onResume = { viewModel.connectWithSavedProfile(prev.profileId) },
                        onDismiss = { viewModel.dismissPreviousBanner() },
                    )
                }

                // Last session resume (when not already showing same as previous)
                if (previous == null && last != null && last.hasSavedSecret) {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Continue where you left off", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${last.displayUser} · ${last.displayHost}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "Last login ${formatLastLoginMs(last.lastUsedEpochMs)}" +
                                    (last.lastVersion?.let { " · PVE $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.connectWithSavedProfile(last.id) },
                                enabled = !state.loading,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Resume session")
                            }
                        }
                    }
                }

                // Saved Connections Section
                if (profiles.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Saved connections", style = MaterialTheme.typography.titleSmall)
                        }
                        TechActionPlate(
                            label = if (state.scanState.isScanning) "STOP SCAN" else "SCAN NETWORK",
                            onClick = {
                                if (state.scanState.isScanning) viewModel.stopNetworkScan()
                                else viewModel.startNetworkScan()
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    profiles.forEach { profile ->
                        val testState = state.profileTests[profile.id]
                        ProfileCard(
                            profile = profile,
                            selected = state.activeProfileId == profile.id,
                            isLast = profile.id == last?.id,
                            testState = testState,
                            onSelect = { viewModel.applyProfile(profile, keepSecretsInForm = false) },
                            onTest = { viewModel.testProfile(profile.id) },
                            onQuickConnect = {
                                if (profile.hasSavedSecret) {
                                    viewModel.connectWithSavedProfile(profile.id)
                                } else {
                                    viewModel.applyProfile(profile, keepSecretsInForm = false)
                                }
                            },
                            onDelete = { viewModel.deleteProfile(profile.id) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = "Secrets are never shown — only host, user, and last login time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.NetworkCheck,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Discover servers", style = MaterialTheme.typography.titleSmall)
                        }
                        TechActionPlate(
                            label = if (state.scanState.isScanning) "STOP SCAN" else "SCAN NETWORK",
                            onClick = {
                                if (state.scanState.isScanning) viewModel.stopNetworkScan()
                                else viewModel.startNetworkScan()
                            },
                        )
                    }
                }

                // Task 1: Network Scan Progress & Discovered PVE Hosts
                val scan = state.scanState
                if (scan.isScanning || scan.discoveredHosts.isNotEmpty() || scan.pveDetectedHosts.isNotEmpty() || scan.unverifiedHosts.isNotEmpty() || scan.error != null || scan.infoMessage != null || (scan.isComplete && scan.discoveredHosts.isEmpty() && scan.unverifiedHosts.isEmpty() && scan.pveDetectedHosts.isEmpty())) {
                    val scanRailColor = when {
                        scan.error != null -> MaterialTheme.colorScheme.error
                        scan.isScanning -> TechColors.Amber
                        scan.discoveredHosts.isNotEmpty() -> TechColors.LinkGreen
                        scan.pveDetectedHosts.isNotEmpty() -> TechColors.Amber
                        scan.unverifiedHosts.isNotEmpty() -> TechColors.Mute
                        else -> TechColors.Edge
                    }
                    val scanHeaderColor = when {
                        scan.error != null -> MaterialTheme.colorScheme.error
                        scan.isScanning -> TechColors.Amber
                        scan.discoveredHosts.isNotEmpty() -> TechColors.LinkGreen
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Spacer(Modifier.height(12.dp))
                    TechPlate(
                        railColor = scanRailColor,
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = when {
                                            scan.isScanning -> "SCANNING"
                                            scan.error != null -> "SCAN ERROR"
                                            else -> "NETWORK DISCOVERY"
                                        },
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = scanHeaderColor,
                                    )
                                    if (scan.isDemoSim) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(2.dp),
                                            color = TechColors.CoolBlue.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, TechColors.CoolBlue.copy(alpha = 0.5f)),
                                            onClick = {
                                                Toast.makeText(context, "Simulated data", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Text(
                                                text = "DEMO SIMULATION",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = TechColors.CoolBlue,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                                if (scan.isScanning) {
                                    TechActionPlate(
                                        label = "STOP",
                                        onClick = { viewModel.stopNetworkScan() },
                                    )
                                } else {
                                    TechActionPlate(
                                        label = "CLEAR",
                                        onClick = { viewModel.clearScanResults() },
                                    )
                                }
                            }

                            if (scan.isScanning) {
                                Spacer(Modifier.height(8.dp))
                                val frac = if (scan.totalCount > 0) {
                                    (scan.scannedCount.toFloat() / scan.totalCount).coerceIn(0f, 1f)
                                } else 0f
                                MetricBar(
                                    label = "SCAN PROGRESS",
                                    valueText = "${scan.scannedCount}/${scan.totalCount}",
                                    progress = frac,
                                    barHeight = 7.dp,
                                    fillColor = TechColors.Amber,
                                    trackColor = TechColors.Deck,
                                    animationDurationMs = 200,
                                )
                            }

                            scan.infoMessage?.let { msg ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = TechColors.CoolBlue,
                                )
                            }

                            scan.error?.let { err ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            if (scan.isComplete && scan.discoveredHosts.isEmpty() && scan.unverifiedHosts.isEmpty() && scan.pveDetectedHosts.isEmpty() && scan.error == null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "No Proxmox VE hosts found on network.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Verified PVE Hosts Section
                    if (scan.discoveredHosts.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "VERIFIED PVE (${scan.discoveredHosts.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TechColors.LinkGreen,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                        scan.discoveredHosts.forEach { candidate ->
                            DiscoveredHostCard(
                                candidate = candidate,
                                isDemo = scan.isDemoSim,
                                onClick = { viewModel.onCandidateSelected(candidate) },
                                onUse = { viewModel.onCandidateSelected(candidate) },
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    // PVE Detected (auth required) Section
                    if (scan.pveDetectedHosts.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "PVE (AUTH REQUIRED) (${scan.pveDetectedHosts.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TechColors.Amber,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                        scan.pveDetectedHosts.forEach { candidate ->
                            DiscoveredHostCard(
                                candidate = candidate,
                                isDemo = scan.isDemoSim,
                                onClick = { viewModel.onCandidateSelected(candidate) },
                                onUse = { viewModel.onCandidateSelected(candidate) },
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    // Unknown Listeners Section
                    if (scan.unverifiedHosts.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "UNKNOWN ON 8006 (${scan.unverifiedHosts.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TechColors.Mute,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                        scan.unverifiedHosts.forEach { ip ->
                            var expandedReason by remember { mutableStateOf(false) }
                            TechPlate(
                                railColor = TechColors.Mute,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "$ip:8006",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(1.dp),
                                                color = TechColors.Mute.copy(alpha = 0.12f),
                                                border = BorderStroke(1.dp, TechColors.Mute.copy(alpha = 0.6f)),
                                                onClick = { expandedReason = !expandedReason }
                                            ) {
                                                Text(
                                                    text = "NO PVE RESPONSE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TechColors.Mute,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                        if (expandedReason) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = "Port open but PVE version probe failed",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("New / edit connection", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.host,
                    onValueChange = { v -> viewModel.update { it.copy(host = v, activeProfileId = null) } },
                    label = { Text("Host / IP") },
                    placeholder = { Text("192.0.2.10") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.port,
                    onValueChange = { v ->
                        viewModel.update { it.copy(port = v.filter { c -> c.isDigit() }, activeProfileId = null) }
                    },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                Text("Authentication", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.authMode == AuthMode.PASSWORD,
                        onClick = { viewModel.update { it.copy(authMode = AuthMode.PASSWORD) } },
                        label = { Text("Password") },
                    )
                    FilterChip(
                        selected = state.authMode == AuthMode.API_TOKEN,
                        onClick = { viewModel.update { it.copy(authMode = AuthMode.API_TOKEN) } },
                        label = { Text("API Token") },
                    )
                }

                Spacer(Modifier.height(12.dp))
                when (state.authMode) {
                    AuthMode.PASSWORD -> {
                        OutlinedTextField(
                            value = state.username,
                            onValueChange = { v -> viewModel.update { it.copy(username = v) } },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.realm,
                            onValueChange = { v -> viewModel.update { it.copy(realm = v) } },
                            label = { Text("Realm") },
                            placeholder = { Text("pam") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = { v ->
                                viewModel.update {
                                    it.copy(password = v, secretPrefillMasked = false)
                                }
                            },
                            label = {
                                Text(
                                    if (state.secretPrefillMasked) "Password (saved — leave blank to reuse)"
                                    else "Password",
                                )
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(bringIntoViewRequester)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        scope.launch {
                                            delay(250)
                                            bringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                        )
                    }
                    AuthMode.API_TOKEN -> {
                        OutlinedTextField(
                            value = state.apiToken,
                            onValueChange = { v ->
                                viewModel.update {
                                    it.copy(apiToken = v, secretPrefillMasked = false)
                                }
                            },
                            label = {
                                Text(
                                    if (state.secretPrefillMasked) "API Token (saved — leave blank to reuse)"
                                    else "API Token",
                                )
                            },
                            placeholder = { Text("user@pam!tokenid=uuid") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(bringIntoViewRequester)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        scope.launch {
                                            delay(250)
                                            bringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                SettingRow(
                    title = "Trust self-signed TLS",
                    subtitle = "Disables cert checks (MITM risk). Lab only.",
                    checked = state.trustSelfSigned,
                    onCheckedChange = { v -> viewModel.update { it.copy(trustSelfSigned = v) } },
                )
                SettingRow(
                    title = "Save credentials",
                    subtitle = "Encrypt password/token on this device for quick reconnect",
                    checked = state.saveCredentials,
                    onCheckedChange = { v -> viewModel.update { it.copy(saveCredentials = v) } },
                )
                SettingRow(
                    title = "Auto-connect on launch",
                    subtitle = "Skip login when a saved secret exists",
                    checked = state.autoConnect,
                    onCheckedChange = { v -> viewModel.update { it.copy(autoConnect = v) } },
                )

                state.error?.let { err ->
                    Spacer(Modifier.height(12.dp))
                    Text(err, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { viewModel.login() },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.loading) "Connecting…" else "Connect")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun JumpBackBanner(
    info: SessionResumeInfo,
    busy: Boolean,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (info.wasActiveSession) "Switched away — jump back?"
                    else "Previous session",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss")
                }
            }
            Text(
                "${info.userDisplay} · ${info.hostDisplay}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Last login ${formatLastLoginMs(info.lastLoginEpochMs)}" +
                    (info.version?.let { " · PVE $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onResume,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Jump back in")
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: SavedProfile,
    selected: Boolean,
    isLast: Boolean,
    testState: com.pxmx.app.ui.login.ProfileTestState?,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onQuickConnect: () -> Unit,
    onDelete: () -> Unit,
) {
    val railColor = when {
        testState?.loading == true -> TechColors.Amber
        testState?.online == true -> TechColors.LinkGreen
        testState?.online == false -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        else -> TechColors.Edge
    }

    TechPlate(
        railColor = railColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.displayLabel.uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (isLast) {
                        Text(
                            "  · LAST",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    profile.displayUser,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    buildString {
                        append(profile.secretMask)
                        append(" · ")
                        append(if (profile.authMode == AuthMode.PASSWORD) "password" else "token")
                        if (profile.lastUsedEpochMs > 0) {
                            append(" · login ")
                            append(formatLastLoginMs(profile.lastUsedEpochMs))
                        }
                        profile.lastVersion?.let {
                            append(" · PVE ")
                            append(it)
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Task 2: Test Result line
                if (testState != null) {
                    Spacer(Modifier.height(4.dp))
                    if (testState.loading) {
                        Text(
                            text = "TESTING CONNECTION…",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TechColors.Amber,
                        )
                    } else if (testState.online) {
                        Text(
                            text = "ONLINE · PVE ${testState.version ?: ""} · ${testState.latencyMs ?: 0}ms",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TechColors.LinkGreen,
                        )
                    } else if (testState.error != null) {
                        Text(
                            text = "OFFLINE · ${testState.error}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // Action affordances: TEST button, Quick connect, Delete
            TechActionPlate(
                label = if (testState?.loading == true) "…" else "TEST",
                onClick = onTest,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            if (profile.hasSavedSecret) {
                IconButton(onClick = onQuickConnect) {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = "Connect")
                }
            } else {
                OutlinedButton(onClick = onSelect) { Text("Fill") }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
        }
    }
}

@Composable
private fun DiscoveredHostCard(
    candidate: DiscoveredHost,
    isDemo: Boolean,
    onClick: () -> Unit,
    onUse: () -> Unit,
) {
    TechPlate(
        railColor = if (candidate.isPveDetectedOnly) TechColors.Amber else TechColors.LinkGreen,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${candidate.ip}:${candidate.port}",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isDemo) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "DEMO",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TechColors.CoolBlue,
                            modifier = Modifier
                                .border(1.dp, TechColors.CoolBlue.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
                                .background(TechColors.CoolBlue.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (candidate.isSavedKnown) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "KNOWN · SAVED",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TechColors.LinkGreen,
                            modifier = Modifier
                                .border(1.dp, TechColors.LinkGreen.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
                                .background(TechColors.LinkGreen.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (candidate.isPveDetectedOnly) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(1.dp),
                            color = TechColors.Amber.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, TechColors.Amber.copy(alpha = 0.6f)),
                            onClick = onUse, // Same as USE on verified cards
                        ) {
                            Text(
                                text = "AUTH REQUIRED",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TechColors.Amber,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "PVE ${candidate.version}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (candidate.latencyMs > 0) {
                        Text(
                            text = " · ${candidate.latencyMs}ms",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = " · tap to prefill",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            if (!candidate.isPveDetectedOnly) {
                TechActionPlate(
                    label = "USE",
                    onClick = onUse,
                )
            }
        }
    }
}
