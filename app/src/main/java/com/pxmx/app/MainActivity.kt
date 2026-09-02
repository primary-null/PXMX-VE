package com.pxmx.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.ui.navigation.ProxmoxNavGraph
import com.pxmx.app.ui.theme.ProxmoxTheme
import com.pxmx.app.ui.tour.TourOverlay
import com.pxmx.app.ui.util.ToastHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Kill any white flash before first frame (theme is already black; reinforce).
        window.setBackgroundDrawableResource(android.R.color.black)
        // Release only: blocks screenshots / recents / Studio mirror.
        // Debug must stay clear so Android Studio, scrcpy, and layout tools work.
        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ProxmoxApp
        setContent {
            val themeMode by app.sessionStore.themeMode.collectAsStateWithLifecycle()
            ProxmoxTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Black,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        ProxmoxNavGraph()
                        TourOverlay()
                        ToastHost()
                    }
                }
            }
        }
    }
}
