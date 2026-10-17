package com.pxmx.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Runtime notification permission helper for Android 13+ (API 33+).
 * Evaluates permission state without requiring Android context in core logic.
 */
object RuntimeNotifyPermission {
    const val POST_NOTIFICATIONS_MIN_SDK = 33

    fun needsPrompt(sdkInt: Int, granted: Boolean): Boolean {
        return sdkInt >= POST_NOTIFICATIONS_MIN_SDK && !granted
    }

    fun isGranted(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        return if (sdkInt >= POST_NOTIFICATIONS_MIN_SDK) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun needsPrompt(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        return needsPrompt(sdkInt, isGranted(context, sdkInt))
    }
}

/**
 * Compose helper that requests POST_NOTIFICATIONS when armed.
 * Launches once per composition / visit when armed and needsPrompt is true.
 * Does not loop and does not crash if already granted.
 */
@Suppress("FunctionNaming")
@Composable
fun RequestPostNotificationsOnUse(
    armed: Boolean,
    onPrompted: () -> Unit = {},
    onResult: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var askedThisVisit by rememberSaveable { mutableStateOf(false) }
    val resultRef = rememberUpdatedState(onResult)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        resultRef.value(granted)
    }

    LaunchedEffect(armed) {
        if (!armed) return@LaunchedEffect
        if (!RuntimeNotifyPermission.needsPrompt(context)) return@LaunchedEffect
        if (askedThisVisit) {
            resultRef.value(false)
            return@LaunchedEffect
        }
        askedThisVisit = true
        onPrompted()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
