package com.pxmx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.data.util.TagUtils
import com.pxmx.app.ui.adaptive.AdaptiveAlertDialog

@Composable
fun TagChip(
    tag: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val shape = CutCornerShape(bottomEnd = 4.dp)
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else TechColors.Edge
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else TechColors.Deck.copy(alpha = 0.6f)
    val textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                fontSize = 11.sp,
                letterSpacing = 0.4.sp,
            )
            if (onRemove != null) {
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove tag $tag",
                        tint = textColor,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun AddTagButton(
    onAddTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    val shape = CutCornerShape(bottomEnd = 4.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(TechColors.Hull)
            .border(1.dp, TechColors.Edge, shape)
            .clickable { showDialog = true }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add tag",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(10.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "TAG",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
            )
        }
    }

    if (showDialog) {
        var inputTag by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AdaptiveAlertDialog(
            onDismissRequest = { showDialog = false },
            shape = TechPlateShape,
            containerColor = TechColors.Hull,
            title = {
                Text(
                    text = "ADD TAG",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a short label (no semicolons):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputTag,
                        onValueChange = {
                            inputTag = it
                            errorMessage = null
                        },
                        singleLine = true,
                        placeholder = { Text("tag") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorMessage != null,
                    )
                    if (errorMessage != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = inputTag.trim()
                        if (!TagUtils.isValidTag(trimmed)) {
                            errorMessage = "Tag cannot be empty or contain ';'"
                        } else {
                            onAddTag(trimmed)
                            showDialog = false
                        }
                    },
                    enabled = inputTag.isNotBlank(),
                ) {
                    Text("ADD")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("CANCEL")
                }
            },
        )
    }
}
