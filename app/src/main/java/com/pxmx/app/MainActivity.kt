package com.pxmx.app

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.data.model.ThemeMode
import com.pxmx.app.ui.navigation.ProxmoxNavGraph
import com.pxmx.app.ui.theme.BACKDROP_DARK_SCRIM
import com.pxmx.app.ui.theme.BACKDROP_LIGHT_SCRIM
import com.pxmx.app.ui.theme.LocalThemeBackdrop
import com.pxmx.app.ui.theme.LocalThemeBackdropActions
import com.pxmx.app.ui.theme.LocalThemeBackdropEnabled
import com.pxmx.app.ui.theme.LocalThemeBackdropFileExists
import com.pxmx.app.ui.theme.LocalThemeBackdropBlur
import com.pxmx.app.ui.theme.LocalThemeBackdropDim
import com.pxmx.app.ui.theme.LocalThemeBackdropVignette
import com.pxmx.app.ui.theme.ProxmoxTheme
import com.pxmx.app.ui.theme.ThemeBackdropActions
import com.pxmx.app.ui.extras.LocalStupidAnimations
import com.pxmx.app.ui.extras.LocalStupidAnimationActions
import com.pxmx.app.ui.extras.StupidAnimationActions
import com.pxmx.app.ui.extras.LocalRefreshCog
import com.pxmx.app.ui.extras.LocalRefreshCogActions
import com.pxmx.app.ui.extras.RefreshCogActions
import android.widget.Toast
import com.pxmx.app.ui.theme.clearThemeBackdrop
import com.pxmx.app.ui.theme.copyBackdropPick
import com.pxmx.app.ui.theme.importCopiedBackdrop
import com.pxmx.app.ui.theme.loadBackdropBitmap
import com.pxmx.app.ui.theme.themeBackdropFile
import com.pxmx.app.ui.tour.TourOverlay
import com.pxmx.app.ui.util.ToastHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
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
            val themeBackdropEnabled by app.sessionStore.themeBackdrop.collectAsStateWithLifecycle()
            val themeBackdropBlur by app.sessionStore.themeBackdropBlur.collectAsStateWithLifecycle()
            val themeBackdropDim by app.sessionStore.themeBackdropDim.collectAsStateWithLifecycle()
            val themeBackdropVignette by app.sessionStore.themeBackdropVignette.collectAsStateWithLifecycle()
            val stupidAnimationsEnabled by app.sessionStore.stupidAnimations.collectAsStateWithLifecycle()
            val refreshCogEnabled by app.sessionStore.refreshCog.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var backdropVersion by remember { mutableIntStateOf(0) }
            var backdropBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
            var backdropFileExists by remember { mutableStateOf(themeBackdropFile(context).isFile) }
            var lastLoadedBlur by remember { mutableIntStateOf(-1) }

            LaunchedEffect(backdropVersion, themeBackdropEnabled) {
                backdropFileExists = withContext(Dispatchers.IO) {
                    themeBackdropFile(context).isFile
                }
            }

            LaunchedEffect(themeBackdropEnabled, backdropVersion, themeBackdropBlur) {
                if (themeBackdropEnabled) {
                    if (backdropBitmap != null && themeBackdropBlur != lastLoadedBlur) {
                        delay(250)
                    }
                    val file = themeBackdropFile(context)
                    backdropBitmap = withContext(Dispatchers.IO) {
                        loadBackdropBitmap(file, themeBackdropBlur)
                    }
                    lastLoadedBlur = themeBackdropBlur
                } else {
                    backdropBitmap = null
                }
            }

            val pickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia(),
            ) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                val copied = copyBackdropPick(context, uri)
                if (copied == null) {
                    Toast.makeText(context, "Couldn't use that image", Toast.LENGTH_SHORT).show()
                    return@rememberLauncherForActivityResult
                }
                scope.launch {
                    val ok = try {
                        importCopiedBackdrop(context, copied, app.sessionStore)
                    } finally {
                        copied.delete()
                    }
                    if (ok) {
                        backdropVersion++
                    } else {
                        Toast.makeText(context, "Couldn't use that image", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val backdropActions = remember(pickerLauncher) {
                ThemeBackdropActions(
                    pick = {
                        pickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    clear = {
                        scope.launch {
                            clearThemeBackdrop(context, app.sessionStore)
                            backdropVersion++
                        }
                    },
                    setEnabled = { enabled ->
                        app.sessionStore.setThemeBackdrop(enabled)
                    },
                    setBlur = { blur ->
                        app.sessionStore.setThemeBackdropBlur(blur)
                    },
                    setDim = { dim ->
                        app.sessionStore.setThemeBackdropDim(dim)
                    },
                    setVignette = { vignette ->
                        app.sessionStore.setThemeBackdropVignette(vignette)
                    },
                )
            }

            val stupidAnimationActions = remember {
                StupidAnimationActions(
                    set = { app.sessionStore.setStupidAnimations(it) },
                )
            }

            val refreshCogActions = remember {
                RefreshCogActions(
                    set = { app.sessionStore.setRefreshCog(it) },
                )
            }

            ProxmoxTheme(themeMode = themeMode) {
                val systemDark = isSystemInDarkTheme()
                val isDark = when (themeMode) {
                    ThemeMode.SYSTEM -> systemDark
                    ThemeMode.LIGHT -> false
                    ThemeMode.OLED_DARK -> true
                }
                val effectiveColorScheme = if (backdropBitmap != null) {
                    MaterialTheme.colorScheme.copy(background = Color.Transparent)
                } else {
                    MaterialTheme.colorScheme
                }

                MaterialTheme(
                    colorScheme = effectiveColorScheme,
                ) {
                    CompositionLocalProvider(
                        LocalThemeBackdrop provides backdropBitmap,
                        LocalThemeBackdropEnabled provides themeBackdropEnabled,
                        LocalThemeBackdropFileExists provides backdropFileExists,
                        LocalThemeBackdropBlur provides themeBackdropBlur,
                        LocalThemeBackdropDim provides themeBackdropDim,
                        LocalThemeBackdropVignette provides themeBackdropVignette,
                        LocalThemeBackdropActions provides backdropActions,
                        LocalStupidAnimations provides stupidAnimationsEnabled,
                        LocalStupidAnimationActions provides stupidAnimationActions,
                        LocalRefreshCog provides refreshCogEnabled,
                        LocalRefreshCogActions provides refreshCogActions,
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .semantics { testTagsAsResourceId = true },
                            color = if (backdropBitmap != null) Color.Transparent else MaterialTheme.colorScheme.background,
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                if (backdropBitmap != null) {
                                    Image(
                                        bitmap = backdropBitmap!!,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    val dimAlpha = (themeBackdropDim / 100f).coerceIn(0f, 1f)
                                    val vignetteAlpha = (themeBackdropVignette / 100f).coerceIn(0f, 1f)
                                    val scrimColor = if (dimAlpha > 0f) {
                                        if (isDark) Color.Black.copy(alpha = dimAlpha) else Color.White.copy(alpha = dimAlpha)
                                    } else {
                                        Color.Transparent
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(scrimColor)
                                            .drawBehind {
                                                if (vignetteAlpha > 0f) {
                                                    val longerSide = maxOf(size.width, size.height)
                                                    drawRect(
                                                        brush = Brush.radialGradient(
                                                            0.0f to Color.Transparent,
                                                            0.55f to Color.Transparent,
                                                            1.0f to Color.Black.copy(alpha = vignetteAlpha),
                                                            center = center,
                                                            radius = longerSide * 0.72f,
                                                        ),
                                                    )
                                                }
                                            },
                                    )
                                }
                                ProxmoxNavGraph()
                                TourOverlay()
                                ToastHost()
                            }
                        }
                    }
                }
            }
        }
    }
}
