package com.pxmx.app.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.BuildConfig
import kotlinx.coroutines.delay

/**
 * Cold-start splash. Pure black + Proxmox orange mark.
 * Runs [bootstrap] (e.g. auto-connect), keeps splash ≥900ms, then [onFinished].
 */
@Composable
fun SplashScreen(
    statusText: String = "",
    bootstrap: suspend () -> Boolean,
    onFinished: (autoConnected: Boolean) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(450),
        label = "splashAlpha",
    )

    LaunchedEffect(Unit) {
        visible = true
        val start = System.currentTimeMillis()
        val ok = runCatching { bootstrap() }.getOrDefault(false)
        val elapsed = System.currentTimeMillis() - start
        if (elapsed < 900) delay(900 - elapsed)
        onFinished(ok)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE57000)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "PVE",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "PXMX",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                color = Color(0xFF888888),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(
                color = Color(0xFFE57000),
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp),
            )
            if (statusText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = statusText,
                    color = Color(0xFF888888),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
