package com.pxmx.app.ui.watch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pxmx.app.data.model.WatchIds
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.rememberReduceMotion
import com.pxmx.app.ui.icons.InstrumentGlyphs
import com.pxmx.app.ui.permissions.RequestPostNotificationsOnUse
import com.pxmx.app.ui.permissions.RuntimeNotifyPermission
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val BLINK_COUNT = 3
private const val BLINK_OUT_MS = 60
private const val BLINK_IN_MS = 90

/**
 * One prompt for the screen. Turning watch on asks only when notifications
 * are not granted yet. Turning off does not ask. A denial leaves the eye hollow.
 */
@Composable
fun rememberWatchToggle(
    setWatched: (String, Boolean) -> Unit,
): (watchId: String, watchedNow: Boolean) -> Unit {
    val context = LocalContext.current
    val setRef = rememberUpdatedState(setWatched)
    var arm by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }

    RequestPostNotificationsOnUse(
        armed = arm,
        onPrompted = { arm = false },
        onResult = { granted ->
            val id = pending
            pending = null
            arm = false
            if (granted && id != null) setRef.value(id, true)
        },
    )

    return { watchId, watchedNow ->
        if (WatchIds.isValid(watchId)) {
            if (watchedNow) {
                setRef.value(watchId, false)
            } else if (RuntimeNotifyPermission.needsPrompt(context)) {
                pending = watchId
                arm = true
            } else {
                setRef.value(watchId, true)
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
fun WatchEye(
    watched: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val alpha = remember { Animatable(1f) }
    var blinkJob by remember { mutableStateOf<Job?>(null) }
    val description = if (watched) "Watching $label" else "Watch $label"
    IconButton(
        onClick = {
            onClick()
            if (reduceMotion) return@IconButton
            blinkJob?.cancel()
            blinkJob = scope.launch {
                repeat(BLINK_COUNT) {
                    alpha.animateTo(0.15f, tween(BLINK_OUT_MS))
                    alpha.animateTo(1f, tween(BLINK_IN_MS))
                }
            }
        },
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (watched) InstrumentGlyphs.EyeWatched else InstrumentGlyphs.Eye,
            contentDescription = description,
            tint = if (watched) TechColors.Amber else TechColors.Mute,
            modifier = Modifier
                .size(22.dp)
                .alpha(alpha.value),
        )
    }
}
