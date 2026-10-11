package com.pxmx.app.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pxmx.app.BuildConfig
import com.pxmx.app.R

/**
 * Cold-start splash. Pure black + current PXMX launcher badge once (wordmark is in the PNG).
 * Runs [bootstrap] (e.g. auto-connect) without artificial delay, then leaves immediately upon completion.
 */
@Composable
fun SplashScreen(
    statusText: String = "",
    bootstrap: suspend () -> Boolean,
    onFinished: (autoConnected: Boolean) -> Unit,
) {
    LaunchedEffect(Unit) {
        val ok = runCatching { bootstrap() }.getOrDefault(false)
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
        ) {
            Image(
                // Raster copy of the tight launcher PNG. R.mipmap.ic_launcher is
                // adaptive-icon XML on API 26+ and painterResource cannot load it.
                painter = painterResource(R.drawable.ic_splash),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(12.dp))
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
