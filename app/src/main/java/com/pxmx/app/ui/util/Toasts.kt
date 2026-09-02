package com.pxmx.app.ui.util

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechPlateShape
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Locale

enum class ToastKind(val railColor: Color) {
    INFO(Color(0xFFFF9800)),      // Amber
    SUCCESS(Color(0xFF69F0AE)),   // LinkGreen
    ERROR(Color(0xFFE53935)),     // Red
}

data class ToastEvent(
    val id: Long,
    val message: String,
    val kind: ToastKind,
)

object AppEvents {
    private val _toasts = MutableSharedFlow<ToastEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val toasts: SharedFlow<ToastEvent> = _toasts.asSharedFlow()

    fun emitToast(msg: String, kind: ToastKind): Boolean {
        if (_toasts.subscriptionCount.value > 0) {
            _toasts.tryEmit(ToastEvent(System.nanoTime(), msg, kind))
            return true
        }
        return false
    }
}

object Toasts {
    fun show(context: Context, msg: String, kind: ToastKind? = null) {
        val resolvedKind = kind ?: inferKind(msg)
        val handled = AppEvents.emitToast(msg, resolvedKind)
        if (!handled) {
            try {
                Toast.makeText(context.applicationContext ?: context, msg, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
    }

    private fun inferKind(msg: String): ToastKind {
        val lower = msg.lowercase(Locale.US)
        return when {
            lower.contains("fail") || lower.contains("error") || lower.contains("lost") ||
                lower.contains("expired") || lower.contains("unsupported") || lower.contains("refused") ||
                lower.contains("403") || lower.contains("401") || lower.contains("500") || lower.contains("501") ||
                lower.contains("denied") || lower.contains("forbidden") -> ToastKind.ERROR
            lower.contains("saved") || lower.contains("created") || lower.contains("refreshed") ||
                lower.contains("connected") || lower.contains("sent") || lower.contains("enabled") ||
                lower.contains("disabled") -> ToastKind.SUCCESS
            else -> ToastKind.INFO
        }
    }
}

enum class AppToast(val format: String) {
    CONNECTED("Connected"),
    CONNECTION_LOST("Connection lost"),
    SESSION_EXPIRED("Session expired — reconnect"),
    VERSION_PVE8("PVE 8"),
    VERSION_PVE9("PVE 9"),
    VERSION_UNSUPPORTED("Sorry — PVE 8 or 9 required"),
    GUEST_STARTED("%s: start sent"),
    GUEST_STOPPED("%s: stop sent"),
    GUEST_REBOOTED("%s: reboot sent"),
    GUEST_SHUTDOWN("%s: shutdown sent"),
    GUEST_SUSPENDED("%s: suspend sent"),
    GUEST_RESUMED("%s: resume sent"),
    AUTOSTART_ENABLED("Auto-start enabled for %s"),
    AUTOSTART_DISABLED("Auto-start disabled for %s"),
    BACKUP_SERVER_STARTED("Backup started on server"),
    BACKUP_DEVICE_STARTED("Backing up to device — this can take minutes"),
    BACKUP_SAVED("Backup saved to Downloads/Proxmox"),
    BACKUP_FAILED("Backup failed: %s"),
    DEPLOY_STARTED("Deploying %s…"),
    DEPLOY_CREATED("Guest %s (VMID %s) created"),
    ACTION_FAILED("%s failed: %s"),
    ;

    fun text(vararg args: Any): String {
        return if (args.isEmpty()) format else String.format(Locale.US, format, *args)
    }

    val kind: ToastKind
        get() = when (this) {
            CONNECTED, BACKUP_SAVED, DEPLOY_CREATED -> ToastKind.SUCCESS
            CONNECTION_LOST, SESSION_EXPIRED, VERSION_UNSUPPORTED, BACKUP_FAILED, ACTION_FAILED -> ToastKind.ERROR
            else -> if (name.contains("FAILED") || name.contains("LOST") || name.contains("EXPIRED")) ToastKind.ERROR
            else if (name.contains("STARTED") || name.contains("SAVED") || name.contains("CREATED")) ToastKind.SUCCESS
            else ToastKind.INFO
        }

    fun show(context: Context, vararg args: Any) {
        Toasts.show(context, text(*args), kind)
    }
}

@Composable
fun ToastHost(
    modifier: Modifier = Modifier,
) {
    var currentToast by remember { mutableStateOf<ToastEvent?>(null) }

    LaunchedEffect(Unit) {
        AppEvents.toasts.collect { event ->
            currentToast = event
            val duration = when (event.kind) {
                ToastKind.ERROR -> 6000L
                ToastKind.INFO -> 2500L
                ToastKind.SUCCESS -> 2200L
            }
            delay(duration)
            if (currentToast?.id == event.id) {
                currentToast = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = currentToast != null,
            enter = slideInVertically(tween(250)) { -it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(200)),
        ) {
            currentToast?.let { toast ->
                TechToastBanner(
                    toast = toast,
                    onDismiss = { currentToast = null },
                )
            }
        }
    }
}

@Composable
fun TechToastBanner(
    toast: ToastEvent,
    onDismiss: () -> Unit = {},
) {
    val shape = TechPlateShape
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TechColors.Hull)
            .border(1.dp, TechColors.Edge, shape)
            .clickable { onDismiss() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(toast.kind.railColor),
            )
            Text(
                text = toast.message.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            if (toast.kind == ToastKind.ERROR) {
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 14.dp)
                        .clickable { onDismiss() },
                )
            }
        }
    }
}
