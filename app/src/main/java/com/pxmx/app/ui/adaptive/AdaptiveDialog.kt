package com.pxmx.app.ui.adaptive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechPlateShape

/**
 * Full-screen dialog container that positions its content within the active pane
 * (Primary / Detail) on two-pane / foldable screens, preventing dialogs from straddling
 * the physical folding crease. On compact screens, it centers normally.
 */
@Composable
fun PaneScopedDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
    ),
    targetPane: DialogPane = LocalDialogPane.current,
    primaryPaneWidth: Dp = LocalPrimaryPaneWidth.current,
    content: @Composable () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTwoPane = isOperatorTwoPane(configuration.screenWidthDp)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        // Scrim click interceptor (clicking outside dismisses the dialog)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
        ) {
            val paneModifier = when {
                !isTwoPane || targetPane == DialogPane.FULL -> {
                    Modifier.fillMaxSize()
                }
                targetPane == DialogPane.PRIMARY -> {
                    Modifier
                        .width(primaryPaneWidth)
                        .fillMaxHeight()
                        .align(Alignment.CenterStart)
                }
                targetPane == DialogPane.DETAIL -> {
                    Modifier
                        .fillMaxHeight()
                        .padding(start = primaryPaneWidth + 1.dp)
                        .fillMaxWidth()
                        .align(Alignment.CenterEnd)
                }
                else -> Modifier.fillMaxSize()
            }

            Box(
                modifier = paneModifier,
                contentAlignment = Alignment.Center,
            ) {
                // Wrapper to consume inner clicks and prevent dismissing on dialog body touch
                Box(
                    modifier = modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * Posture-safe, pane-scoped AlertDialog styled to match PXMX TechPlate aesthetics.
 * Centers within the calling pane on two-pane layouts to avoid the physical crease.
 */
@Composable
fun AdaptiveAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = TechPlateShape,
    containerColor: Color = TechColors.Hull,
    iconContentColor: Color = LocalContentColor.current,
    titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
    textContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    targetPane: DialogPane = LocalDialogPane.current,
    primaryPaneWidth: Dp = LocalPrimaryPaneWidth.current,
) {
    PaneScopedDialog(
        onDismissRequest = onDismissRequest,
        targetPane = targetPane,
        primaryPaneWidth = primaryPaneWidth,
    ) {
        Surface(
            modifier = modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(min = 280.dp, max = 400.dp),
            shape = shape,
            color = containerColor,
            tonalElevation = tonalElevation,
            border = BorderStroke(1.dp, TechColors.Edge),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                if (icon != null) {
                    Box(
                        Modifier
                            .padding(bottom = 16.dp)
                            .align(Alignment.CenterHorizontally),
                    ) {
                        CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                            icon()
                        }
                    }
                }
                if (title != null) {
                    Box(Modifier.padding(bottom = if (text == null) 24.dp else 16.dp)) {
                        CompositionLocalProvider(
                            LocalTextStyle provides MaterialTheme.typography.headlineSmall,
                            LocalContentColor provides titleContentColor,
                        ) {
                            title()
                        }
                    }
                }
                if (text != null) {
                    Box(Modifier.padding(bottom = 24.dp)) {
                        CompositionLocalProvider(
                            LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                            LocalContentColor provides textContentColor,
                        ) {
                            text()
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dismissButton != null) {
                        dismissButton()
                        Spacer(Modifier.width(8.dp))
                    }
                    confirmButton()
                }
            }
        }
    }
}
