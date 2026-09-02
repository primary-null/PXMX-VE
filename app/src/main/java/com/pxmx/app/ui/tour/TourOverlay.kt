package com.pxmx.app.ui.tour

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.ProxmoxApp
import com.pxmx.app.ui.components.TechActionPlate
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechPlate

@Composable
fun TourOverlay(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as? ProxmoxApp ?: return
    val currentStep by TourController.currentStep.collectAsStateWithLifecycle()

    val step = currentStep
    val isTop = step?.placement == TourPanelPlacement.TOP

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (isTop) Alignment.TopCenter else Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = step != null,
            enter = fadeIn() + slideInVertically { if (isTop) -it else it },
            exit = fadeOut() + slideOutVertically { if (isTop) -it else it },
        ) {
            val visibleStep = currentStep ?: return@AnimatedVisibility
            androidx.compose.runtime.key(visibleStep) {
                val stepIsTop = visibleStep.placement == TourPanelPlacement.TOP
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (stepIsTop) {
                                Modifier
                                    .statusBarsPadding()
                                    .padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 16.dp)
                            } else {
                                Modifier
                                    .navigationBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 48.dp)
                            }
                        ),
                ) {
                    TechPlate(
                        railColor = if (visibleStep == TourStep.SUMMARY) TechColors.LinkGreen else TechColors.Amber,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (visibleStep == TourStep.SUMMARY) "QUICK TOUR · COMPLETE"
                                        else "QUICK TOUR · STEP ${visibleStep.stepNumber}/5",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = if (visibleStep == TourStep.SUMMARY) TechColors.LinkGreen else TechColors.Amber,
                                    )
                                }
                                if (visibleStep != TourStep.SUMMARY) {
                                    TechActionPlate(
                                        label = "SKIP",
                                        onClick = { TourController.skip(app.sessionStore) },
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = visibleStep.instruction,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            if (visibleStep == TourStep.SUMMARY) {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TechActionPlate(
                                        label = "DONE",
                                        emphasized = true,
                                        onClick = { TourController.complete(app.sessionStore) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
