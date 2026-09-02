package com.pxmx.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.ui.components.TechSectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeployDialog(
    templates: List<ClusterResource>,
    maxVmid: Long,
    deploying: Boolean,
    error: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (source: ClusterResource, newId: Long, name: String) -> Unit,
) {
    var selectedTemplate by remember { mutableStateOf(templates.firstOrNull()) }
    var newVmid by remember { mutableStateOf((maxVmid + 1).toString()) }
    var name by remember { mutableStateOf(selectedTemplate?.name?.let { "$it-clone" } ?: "") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Deploy from template") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                if (templates.isEmpty()) {
                    Text(
                        "No templates on this server",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    TechSectionLabel(title = "Source template")
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedTemplate?.name ?: "Select template",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            templates.forEach { template ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(template.displayName, fontWeight = FontWeight.Bold)
                                            Text(
                                                "${template.type?.uppercase()} · ${template.node}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedTemplate = template
                                        name = "${template.name}-clone"
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    TechSectionLabel(title = "New configuration")

                    OutlinedTextField(
                        value = newVmid,
                        onValueChange = { if (it.all { c -> c.isDigit() }) newVmid = it },
                        label = { Text("New VMID") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                if (error != null) {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val template = selectedTemplate ?: return@Button
                    val vmid = newVmid.toLongOrNull() ?: return@Button
                    onConfirm(template, vmid, name)
                },
                enabled = !deploying && selectedTemplate != null && newVmid.isNotBlank() && name.isNotBlank()
            ) {
                if (deploying) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Deploy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deploying) {
                Text("Cancel")
            }
        }
    )
}
