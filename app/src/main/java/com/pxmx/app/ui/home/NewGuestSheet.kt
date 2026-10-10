package com.pxmx.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.data.model.NodeStorageEntry
import com.pxmx.app.data.model.StorageContentItem
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechPlateShape
import com.pxmx.app.ui.components.TechSectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGuestSheet(
    templates: List<ClusterResource>,
    nodes: List<String>,
    maxVmid: Long,
    deploying: Boolean,
    storages: List<NodeStorageEntry> = emptyList(),
    osTemplates: List<StorageContentItem> = emptyList(),
    error: String? = null,
    onDismiss: () -> Unit,
    onCloneConfirm: (source: ClusterResource, newId: Long, name: String) -> Unit,
    onBlankConfirm: (node: String, type: GuestType, vmid: Long, name: String, diskSizeG: Int, storage: String, ostemplate: String?) -> Unit,
    onNodeChange: ((String) -> Unit)? = null,
) {
    var guestType by remember { mutableStateOf(GuestType.QEMU) }
    var mode by remember { mutableStateOf("BLANK") } // "BLANK" or "CLONE"
    var newVmid by remember { mutableStateOf((maxVmid + 1).toString()) }
    var selectedNode by remember { mutableStateOf(nodes.firstOrNull() ?: "alpha") }
    var name by remember { mutableStateOf(if (guestType == GuestType.QEMU) "new-vm" else "new-ct") }
    var diskSizeG by remember { mutableStateOf("32") }

    val filteredTemplates = remember(templates, guestType) {
        templates.filter { it.type == guestType.path }
    }
    var selectedTemplate by remember(filteredTemplates) {
        mutableStateOf(filteredTemplates.firstOrNull())
    }

    val validStorages = remember(storages, guestType) {
        val requiredContent = if (guestType == GuestType.QEMU) "images" else "rootdir"
        storages.filter { entry ->
            val hasContent = entry.content?.split(",")?.map { it.trim() }?.contains(requiredContent) == true
            val isEnabled = entry.enabled == null || entry.enabled == 1
            val isActive = entry.active == null || entry.active == 1
            hasContent && isEnabled && isActive
        }
    }
    var selectedStorage by remember { mutableStateOf<String?>(null) }
    val activeStorage = selectedStorage?.takeIf { name -> validStorages.any { it.storage == name } }
        ?: validStorages.firstOrNull()?.storage

    val validOsTemplates = remember(osTemplates) {
        osTemplates.filter { item ->
            val hasContent = item.content == "vztmpl" || item.volid?.contains("vztmpl") == true
            hasContent && !item.volid.isNullOrBlank()
        }
    }
    var selectedOsTemplate by remember { mutableStateOf<String?>(null) }
    val activeOsTemplate = selectedOsTemplate?.takeIf { vol -> validOsTemplates.any { it.volid == vol } }
        ?: validOsTemplates.firstOrNull()?.volid

    var nodeDropdownOpen by remember { mutableStateOf(false) }
    var templateDropdownOpen by remember { mutableStateOf(false) }
    var storageDropdownOpen by remember { mutableStateOf(false) }
    var osTemplateDropdownOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TechColors.Hull,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(TechColors.Edge, RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "NEW GUEST",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Step 1: Type (VM vs Container)
            TechSectionLabel(title = "Guest type")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    GuestType.QEMU to "Virtual Machine (VM)",
                    GuestType.LXC to "Container (LXC)",
                ).forEach { (t, label) ->
                    val selected = guestType == t
                    val shape = CutCornerShape(bottomEnd = 6.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (selected) TechColors.Deck else TechColors.Hull)
                            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else TechColors.Edge, shape)
                            .clickable {
                                guestType = t
                                if (name == "new-vm" || name == "new-ct") {
                                    name = if (t == GuestType.QEMU) "new-vm" else "new-ct"
                                }
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (t == GuestType.QEMU) "VM (QEMU)" else "CONTAINER (LXC)",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Step 2: Name, VMID, Node
            TechSectionLabel(title = "Identity & Target")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newVmid,
                    onValueChange = { if (it.all { c -> c.isDigit() }) newVmid = it },
                    label = { Text("VMID") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.4f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.weight(0.6f),
                    singleLine = true,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Node picker
            ExposedDropdownMenuBox(
                expanded = nodeDropdownOpen,
                onExpandedChange = { nodeDropdownOpen = !nodeDropdownOpen },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = "Node: $selectedNode",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = nodeDropdownOpen) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = nodeDropdownOpen,
                    onDismissRequest = { nodeDropdownOpen = false },
                ) {
                    nodes.forEach { node ->
                        DropdownMenuItem(
                            text = { Text(node) },
                            onClick = {
                                selectedNode = node
                                nodeDropdownOpen = false
                                onNodeChange?.invoke(node)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Step 3: Creation Mode (Blank guest vs Template clone)
            TechSectionLabel(title = "Creation mode")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "BLANK" to "Blank guest",
                    "CLONE" to "Clone template",
                ).forEach { (m, label) ->
                    val selected = mode == m
                    val shape = CutCornerShape(bottomEnd = 6.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (selected) TechColors.Deck else TechColors.Hull)
                            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else TechColors.Edge, shape)
                            .clickable { mode = m }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (mode == "BLANK") {
                // Storage picker
                if (validStorages.isEmpty()) {
                    Text(
                        text = "No storage supporting ${if (guestType == GuestType.QEMU) "VM disks (images)" else "container rootfs (rootdir)"} found on $selectedNode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = storageDropdownOpen,
                        onExpandedChange = { storageDropdownOpen = !storageDropdownOpen },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = activeStorage ?: "Select storage",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (guestType == GuestType.QEMU) "Disk storage" else "Rootfs storage") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = storageDropdownOpen) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = storageDropdownOpen,
                            onDismissRequest = { storageDropdownOpen = false },
                        ) {
                            validStorages.forEach { st ->
                                val stName = st.storage ?: return@forEach
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(stName, fontWeight = FontWeight.Bold)
                                            Text(
                                                "${st.type ?: ""} · ${st.content ?: ""}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedStorage = stName
                                        storageDropdownOpen = false
                                    },
                                )
                            }
                        }
                    }
                }

                // If LXC: OS template picker
                if (guestType == GuestType.LXC) {
                    Spacer(Modifier.height(8.dp))
                    if (validOsTemplates.isEmpty()) {
                        Text(
                            text = "No OS templates (vztmpl) found on $selectedNode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = osTemplateDropdownOpen,
                            onExpandedChange = { osTemplateDropdownOpen = !osTemplateDropdownOpen },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = activeOsTemplate ?: "Select OS template",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("OS template") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = osTemplateDropdownOpen) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = osTemplateDropdownOpen,
                                onDismissRequest = { osTemplateDropdownOpen = false },
                            ) {
                                validOsTemplates.forEach { item ->
                                    val volid = item.volid ?: return@forEach
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(volid.substringAfterLast("/"), fontWeight = FontWeight.Bold)
                                                Text(
                                                    volid,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedOsTemplate = volid
                                            osTemplateDropdownOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = diskSizeG,
                    onValueChange = { if (it.all { c -> c.isDigit() }) diskSizeG = it },
                    label = { Text("Disk size (GB)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            } else {
                if (filteredTemplates.isEmpty()) {
                    Text(
                        text = "No ${guestType.path.uppercase()} templates found on this server",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = templateDropdownOpen,
                        onExpandedChange = { templateDropdownOpen = !templateDropdownOpen },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = selectedTemplate?.name ?: "Select template",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = templateDropdownOpen) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = templateDropdownOpen,
                            onDismissRequest = { templateDropdownOpen = false },
                        ) {
                            filteredTemplates.forEach { template ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(template.displayName, fontWeight = FontWeight.Bold)
                                            Text(
                                                "${template.type?.uppercase()} · ${template.node}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedTemplate = template
                                        templateDropdownOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !deploying) {
                    Text("CANCEL")
                }
                Spacer(Modifier.width(8.dp))
                val isBlankValid = (diskSizeG.toIntOrNull() ?: 0) > 0 &&
                        !activeStorage.isNullOrBlank() &&
                        (guestType != GuestType.LXC || !activeOsTemplate.isNullOrBlank())

                val isValid = newVmid.toLongOrNull() != null && name.isNotBlank() &&
                        (mode != "CLONE" || selectedTemplate != null) &&
                        (mode != "BLANK" || isBlankValid)

                Button(
                    onClick = {
                        val parsedVmid = newVmid.toLongOrNull() ?: return@Button
                        if (mode == "CLONE") {
                            val template = selectedTemplate ?: return@Button
                            onCloneConfirm(template, parsedVmid, name.trim())
                        } else {
                            val storage = activeStorage ?: return@Button
                            val disk = diskSizeG.toIntOrNull() ?: 32
                            val ostemplate = if (guestType == GuestType.LXC) activeOsTemplate else null
                            if (guestType == GuestType.LXC && ostemplate.isNullOrBlank()) return@Button
                            onBlankConfirm(selectedNode, guestType, parsedVmid, name.trim(), disk, storage, ostemplate)
                        }
                    },
                    enabled = isValid && !deploying,
                    shape = CutCornerShape(bottomEnd = 10.dp),
                ) {
                    if (deploying) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (mode == "CLONE") "CLONE" else "CREATE")
                }
            }
        }
    }
}
