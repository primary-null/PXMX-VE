package com.pxmx.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.data.model.FirewallAlias
import com.pxmx.app.data.model.FirewallRule
import com.pxmx.app.data.model.FirewallSnapshot
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechDeck
import com.pxmx.app.ui.components.TechMetaLine
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechPlateShape
import com.pxmx.app.ui.components.TechSectionLabel
import com.pxmx.app.ui.components.TechStatusPlate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirewallScreen(
    viewModel: FirewallViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Firewall")
                        Text(
                            "DC RULES · NODE RULES · READ-ONLY",
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
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading && state.cluster == null && state.nodeSnapshots.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Scope selector (CLUSTER / NODES)
                item(key = "scope-selector") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val isClusterSelected = state.selectedTarget == "cluster"
                        ScopeTab(
                            label = "CLUSTER",
                            selected = isClusterSelected,
                            onClick = { viewModel.selectTarget("cluster") },
                        )
                        state.nodeNames.forEach { nodeName ->
                            val isNodeSelected = state.selectedTarget == nodeName
                            ScopeTab(
                                label = "NODE: ${nodeName.uppercase()}",
                                selected = isNodeSelected,
                                onClick = { viewModel.selectTarget(nodeName) },
                            )
                        }
                    }
                }

                state.error?.let { err ->
                    item(key = "error-plate") {
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

                val currentSnap = state.currentSnapshot
                if (currentSnap != null) {
                    item(key = "header-${currentSnap.scope}") {
                        FirewallHeader(currentSnap)
                    }

                    if (state.selectedTarget == "cluster" && currentSnap.aliases.isNotEmpty()) {
                        item(key = "aliases-header") {
                            TechSectionLabel("Aliases", count = currentSnap.aliases.size)
                        }
                        items(currentSnap.aliases, key = { "alias-${it.name}" }) { alias ->
                            AliasCard(alias)
                        }
                    }

                    item(key = "rules-header-${currentSnap.scope}") {
                        TechSectionLabel("Rules", count = currentSnap.rules.size)
                    }

                    if (currentSnap.rules.isEmpty()) {
                        item(key = "no-rules-${currentSnap.scope}") {
                            TechPlate(railColor = TechColors.Edge) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = "NO RULES",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = TechColors.Mute,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "No firewall rules configured for ${currentSnap.scope}.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        items(currentSnap.rules, key = { "rule-${it.pos}-${it.summary}-${it.enable}" }) { rule ->
                            RuleCard(rule)
                        }
                    }
                } else if (!state.loading) {
                    item(key = "no-snap") {
                        TechPlate(railColor = TechColors.Edge) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    text = "NO DATA",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TechColors.Mute,
                                )
                                Text(
                                    text = "Unable to fetch firewall data for this scope.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Read-only info note
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
                                text = "Firewall rules are read-only in PXMX.",
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
private fun ScopeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = CutCornerShape(bottomEnd = 8.dp)
    val border = if (selected) MaterialTheme.colorScheme.primary else TechColors.Edge
    val bg = if (selected) TechColors.Deck else TechColors.Hull
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(shape)
            .border(1.dp, border, shape)
            .background(bg, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun FirewallHeader(snap: FirewallSnapshot) {
    TechPlate(
        railColor = if (snap.enabled) TechColors.LinkGreen else TechColors.Mute,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    snap.scope.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(4.dp))
                TechMetaLine("Rules", "${snap.rules.size} configured")
                TechMetaLine(
                    "Policy",
                    listOfNotNull(
                        snap.options["policy_in"]?.toString()?.let { "IN: $it" },
                        snap.options["policy_out"]?.toString()?.let { "OUT: $it" },
                    ).joinToString(" · ").ifBlank { "—" },
                )
            }
            TechStatusPlate(status = if (snap.enabled) "enabled" else "disabled")
        }
    }
}

@Composable
private fun AliasCard(alias: FirewallAlias) {
    TechPlate(railColor = TechColors.CoolBlue) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    (alias.name ?: "?").uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TechColors.CoolBlue,
                )
                Text(
                    alias.cidr ?: "—",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            alias.comment?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RuleCard(rule: FirewallRule) {
    val actionUpper = rule.action?.uppercase() ?: "—"
    val isAccept = actionUpper == "ACCEPT"
    val isDrop = actionUpper == "DROP"
    val isReject = actionUpper == "REJECT"

    val actionBadgeColor = when {
        !rule.enable -> TechColors.Mute
        isAccept -> TechColors.LinkGreen
        isDrop -> TechColors.Mute
        isReject -> TechColors.Danger
        else -> TechColors.Amber
    }

    val railColor = when {
        !rule.enable -> TechColors.Mute
        isAccept -> TechColors.LinkGreen
        isDrop -> TechColors.Mute
        isReject -> TechColors.Danger
        else -> TechColors.Amber
    }

    val cardModifier = if (!rule.enable) {
        Modifier.alpha(0.45f)
    } else {
        Modifier
    }

    TechPlate(
        railColor = railColor,
        modifier = cardModifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Action badge (ACCEPT green, DROP gray, REJECT red)
                    Surface(
                        color = actionBadgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, actionBadgeColor.copy(alpha = 0.6f)),
                    ) {
                        Text(
                            text = actionUpper,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = actionBadgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Rule number
                    Text(
                        text = "#${rule.pos ?: "?"}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.width(8.dp))

                    // Direction badge (IN / OUT)
                    val dirText = rule.type?.uppercase() ?: "IN"
                    Surface(
                        color = TechColors.Deck,
                        shape = RoundedCornerShape(2.dp),
                    ) {
                        Text(
                            text = dirText,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }

                    // Interface badge when set
                    rule.iface?.takeIf { it.isNotBlank() }?.let { iface ->
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = TechColors.Deck,
                            shape = RoundedCornerShape(2.dp),
                        ) {
                            Text(
                                text = "IFACE $iface",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = TechColors.CoolBlue,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Log flag icon / badge when logging
                    if (rule.hasLog) {
                        Surface(
                            color = TechColors.Amber.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(2.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TechColors.Amber.copy(alpha = 0.5f)),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.List,
                                    contentDescription = "Logging enabled",
                                    tint = TechColors.Amber,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = rule.log?.uppercase() ?: "LOG",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = TechColors.Amber,
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                    }

                    // Disabled badge
                    if (!rule.enable) {
                        Text(
                            text = "DISABLED",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            color = TechColors.Mute,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Macro name or proto/dport/source/dest summary
            val summaryDetails = buildString {
                rule.macro?.let { append("[$it] ") }
                if (rule.proto != null || rule.dport != null || rule.sport != null) {
                    rule.proto?.let { append(it) }
                    rule.dport?.let { append(" :$it") }
                    rule.sport?.let { append(" (sport $it)") }
                }
                rule.source?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append(" · ")
                    append("src $it")
                }
                rule.dest?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append(" · ")
                    append("dst $it")
                }
            }

            if (summaryDetails.isNotBlank()) {
                Text(
                    text = summaryDetails,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Comment
            rule.comment?.takeIf { it.isNotBlank() }?.let { comment ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = comment,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
