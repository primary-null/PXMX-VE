package com.pxmx.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechSwitch
import kotlin.math.roundToInt
import com.pxmx.app.data.session.SessionStore
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Named scrim overlays for backdrop rendering behind app content.
 */
val BACKDROP_DARK_SCRIM: Color = Color.Black.copy(alpha = 0.42f)
val BACKDROP_LIGHT_SCRIM: Color = Color.White.copy(alpha = 0.34f)

val LocalThemeBackdrop = compositionLocalOf<ImageBitmap?> { null }
val LocalThemeBackdropEnabled = compositionLocalOf { false }
val LocalThemeBackdropFileExists = compositionLocalOf { false }
val LocalThemeBackdropBlur = compositionLocalOf { 22 }
val LocalThemeBackdropDim = compositionLocalOf { 42 }
val LocalThemeBackdropVignette = compositionLocalOf { 55 }

data class ThemeBackdropActions(
    val pick: () -> Unit,
    val clear: () -> Unit,
    val setEnabled: (Boolean) -> Unit = {},
    val setBlur: (Int) -> Unit = {},
    val setDim: (Int) -> Unit = {},
    val setVignette: (Int) -> Unit = {},
)

val LocalThemeBackdropActions = compositionLocalOf<ThemeBackdropActions?> { null }

/**
 * Calculates largest power-of-two inSampleSize such that the longer edge fits in maxEdge.
 * Inputs <= 0 or already under the cap return 1. Never returns 0.
 */
fun backdropSampleSize(srcW: Int, srcH: Int, maxEdge: Int = 2048): Int {
    if (srcW <= 0 || srcH <= 0 || maxEdge <= 0) return 1
    val longer = maxOf(srcW, srcH)
    if (longer <= maxEdge) return 1
    val ratio = (longer + maxEdge - 1) / maxEdge
    return Integer.highestOneBit(ratio).coerceAtLeast(1)
}

/**
 * Backdrop image file location: context.filesDir/theme/theme-backdrop.jpg
 */
fun themeBackdropFile(context: Context): File {
    return File(context.filesDir, "theme/theme-backdrop.jpg")
}

/**
 * Applies a 3-pass box blur to an ARGB integer pixel array, approximating Gaussian blur.
 * Clamps out-of-bounds samples to the edge. If [radiusPx] <= 0, returns a copy of [pixels] unchanged.
 */
fun blurPixels(
    pixels: IntArray,
    width: Int,
    height: Int,
    radiusPx: Int = 22,
): IntArray {
    if (radiusPx <= 0 || width <= 0 || height <= 0 || pixels.size < width * height) {
        return pixels.copyOf()
    }

    val r = radiusPx
    val div = 2 * r + 1
    val halfDiv = div / 2

    val current = pixels.copyOf()
    val temp = IntArray(width * height)

    repeat(3) {
        // Horizontal pass: current -> temp
        for (y in 0 until height) {
            val rowOffset = y * width
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            for (i in -r..r) {
                val clampedX = i.coerceIn(0, width - 1)
                val p = current[rowOffset + clampedX]
                sumA += (p ushr 24) and 0xFF
                sumR += (p ushr 16) and 0xFF
                sumG += (p ushr 8) and 0xFF
                sumB += p and 0xFF
            }
            for (x in 0 until width) {
                val a = ((sumA + halfDiv) / div).coerceIn(0, 255)
                val red = ((sumR + halfDiv) / div).coerceIn(0, 255)
                val green = ((sumG + halfDiv) / div).coerceIn(0, 255)
                val blue = ((sumB + halfDiv) / div).coerceIn(0, 255)
                temp[rowOffset + x] = (a shl 24) or (red shl 16) or (green shl 8) or blue

                val leaveX = (x - r).coerceIn(0, width - 1)
                val enterX = (x + r + 1).coerceIn(0, width - 1)
                val pLeave = current[rowOffset + leaveX]
                val pEnter = current[rowOffset + enterX]
                sumA += ((pEnter ushr 24) and 0xFF) - ((pLeave ushr 24) and 0xFF)
                sumR += ((pEnter ushr 16) and 0xFF) - ((pLeave ushr 16) and 0xFF)
                sumG += ((pEnter ushr 8) and 0xFF) - ((pLeave ushr 8) and 0xFF)
                sumB += (pEnter and 0xFF) - (pLeave and 0xFF)
            }
        }

        // Vertical pass: temp -> current
        for (x in 0 until width) {
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            for (i in -r..r) {
                val clampedY = i.coerceIn(0, height - 1)
                val p = temp[clampedY * width + x]
                sumA += (p ushr 24) and 0xFF
                sumR += (p ushr 16) and 0xFF
                sumG += (p ushr 8) and 0xFF
                sumB += p and 0xFF
            }
            for (y in 0 until height) {
                val a = ((sumA + halfDiv) / div).coerceIn(0, 255)
                val red = ((sumR + halfDiv) / div).coerceIn(0, 255)
                val green = ((sumG + halfDiv) / div).coerceIn(0, 255)
                val blue = ((sumB + halfDiv) / div).coerceIn(0, 255)
                current[y * width + x] = (a shl 24) or (red shl 16) or (green shl 8) or blue

                val leaveY = (y - r).coerceIn(0, height - 1)
                val enterY = (y + r + 1).coerceIn(0, height - 1)
                val pLeave = temp[leaveY * width + x]
                val pEnter = temp[enterY * width + x]
                sumA += ((pEnter ushr 24) and 0xFF) - ((pLeave ushr 24) and 0xFF)
                sumR += ((pEnter ushr 16) and 0xFF) - ((pLeave ushr 16) and 0xFF)
                sumG += ((pEnter ushr 8) and 0xFF) - ((pLeave ushr 8) and 0xFF)
                sumB += (pEnter and 0xFF) - (pLeave and 0xFF)
            }
        }
    }

    return current
}

/**
 * Mild stack or 3-pass box blur for backdrop images.
 * Pure function so unit tests can exercise it without Android UI.
 * Returns the source unchanged if [radiusPx] <= 0.
 */
fun blurBackdropBitmap(source: Bitmap, radiusPx: Int = 22): Bitmap {
    if (radiusPx <= 0 || source.isRecycled) return source
    val width = source.width
    val height = source.height
    if (width <= 0 || height <= 0) return source
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    val blurred = blurPixels(pixels, width, height, radiusPx)
    val config = source.config ?: Bitmap.Config.ARGB_8888
    val output = Bitmap.createBitmap(width, height, config)
    output.setPixels(blurred, 0, width, 0, 0, width, height)
    return output
}

/**
 * Decodes the backdrop file into an ImageBitmap off the main thread,
 * applying a mild blur to soften background detail behind content.
 * Returns null if the file does not exist or decode fails.
 */
fun loadBackdropBitmap(file: File, blurRadius: Int = 22): ImageBitmap? {
    return try {
        if (!file.exists() || !file.isFile) return null
        val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val blurred = if (blurRadius > 0) {
            try {
                blurBackdropBitmap(decoded, blurRadius)
            } catch (e: CancellationException) {
                decoded.recycle()
                throw e
            } catch (_: Exception) {
                decoded
            }
        } else {
            decoded
        }
        if (blurred != decoded) {
            decoded.recycle()
        }
        blurred.asImageBitmap()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

/**
 * Copies a picked image before the photo-picker grant ends.
 *
 * Call this on the activity-result thread. Photo-picker streams often cannot rewind,
 * so decoding that stream directly reports no size and nothing is saved.
 * Returns the cache file, or null when the bytes could not be read.
 */
fun copyBackdropPick(context: Context, uri: Uri): File? {
    val cacheCopy = File(context.cacheDir, "theme-pick-${System.nanoTime()}.tmp")
    return try {
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(cacheCopy).use { out -> input.copyTo(out) }
        } ?: 0L
        if (copied <= 0L) {
            cacheCopy.delete()
            null
        } else {
            cacheCopy
        }
    } catch (e: CancellationException) {
        cacheCopy.delete()
        throw e
    } catch (_: Exception) {
        cacheCopy.delete()
        null
    }
}

/**
 * Decodes [source], caps the long edge at 2048, writes theme/theme-backdrop.jpg,
 * and enables the backdrop flag only after the file is in place.
 */
suspend fun importCopiedBackdrop(
    context: Context,
    source: File,
    sessionStore: SessionStore,
): Boolean = withContext(Dispatchers.IO) {
    var finalBitmap: Bitmap? = null
    try {
        if (!source.isFile || source.length() <= 0L) return@withContext false
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, boundsOptions)
        val srcW = boundsOptions.outWidth
        val srcH = boundsOptions.outHeight
        if (srcW <= 0 || srcH <= 0) return@withContext false

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = backdropSampleSize(srcW, srcH, 2048)
        }
        val raw = BitmapFactory.decodeFile(source.absolutePath, decodeOptions) ?: return@withContext false
        val oriented = orientBackdrop(source, raw)
        val maxDim = maxOf(oriented.width, oriented.height)
        finalBitmap = if (maxDim > 2048) {
            val scale = 2048f / maxDim
            val targetW = (oriented.width * scale).toInt().coerceAtLeast(1)
            val targetH = (oriented.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(oriented, targetW, targetH, true)
            if (scaled != oriented) oriented.recycle()
            scaled
        } else {
            oriented
        }

        val targetFile = themeBackdropFile(context)
        val parentDir = targetFile.parentFile ?: File(context.filesDir, "theme")
        if (!parentDir.exists() && !parentDir.mkdirs()) return@withContext false
        val tmpFile = File(parentDir, "theme-backdrop.jpg.tmp")
        val ready = finalBitmap ?: return@withContext false
        FileOutputStream(tmpFile).use { out ->
            if (!ready.compress(Bitmap.CompressFormat.JPEG, 85, out)) return@withContext false
        }
        if (!tmpFile.exists() || tmpFile.length() <= 0L) return@withContext false
        if (targetFile.exists()) targetFile.delete()
        val renamed = tmpFile.renameTo(targetFile)
        if (!renamed) {
            tmpFile.copyTo(targetFile, overwrite = true)
            tmpFile.delete()
        }
        if (!targetFile.isFile || targetFile.length() <= 0L) return@withContext false
        sessionStore.setThemeBackdrop(true)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    } finally {
        finalBitmap?.let { if (!it.isRecycled) it.recycle() }
    }
}

private fun orientBackdrop(source: File, bitmap: Bitmap): Bitmap {
    val orientation = runCatching {
        ExifInterface(source.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.preScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.preScale(-1f, 1f)
        }
        else -> return bitmap
    }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated != bitmap) bitmap.recycle()
    return rotated
}

/**
 * Deletes the backdrop file and clears the preference flag.
 */
suspend fun clearThemeBackdrop(
    context: Context,
    sessionStore: SessionStore,
) = withContext(Dispatchers.IO) {
    try {
        val file = themeBackdropFile(context)
        if (file.exists()) {
            file.delete()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // ignore deletion failure
    }
    sessionStore.setThemeBackdrop(false)
}

/**
 * Returns Color.Transparent when a backdrop image is present, otherwise solid.
 */
@Composable
fun themePageColor(solid: Color): Color {
    return if (LocalThemeBackdrop.current != null) Color.Transparent else solid
}

/**
 * Dialog backdrop control section under appearance theme radios.
 */
@Composable
fun ThemeBackdropSection(
    modifier: Modifier = Modifier,
) {
    val actions = LocalThemeBackdropActions.current ?: return
    val enabled = LocalThemeBackdropEnabled.current
    val fileExists = LocalThemeBackdropFileExists.current
    val hasFile = fileExists || LocalThemeBackdrop.current != null || enabled
    val blur = LocalThemeBackdropBlur.current
    val dim = LocalThemeBackdropDim.current
    val vignette = LocalThemeBackdropVignette.current

    Spacer(Modifier.height(16.dp))
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp,
    )
    Spacer(Modifier.height(12.dp))

    Text(
        text = "Image background or console on cards. Turning one on turns the other off.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (hasFile) "Image" else "Solid",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasFile) {
                OutlinedButton(
                    onClick = { actions.pick() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Replace")
                }
                OutlinedButton(
                    onClick = { actions.clear() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Clear")
                }
            } else {
                OutlinedButton(
                    onClick = { actions.pick() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Choose image")
                }
            }
        }
    }

    if (hasFile && !enabled) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "SHOW IMAGE",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TechSwitch(
                checked = false,
                onCheckedChange = { actions.setEnabled(it) },
            )
        }
    }

    if (hasFile) {
        Spacer(Modifier.height(12.dp))
        // BLUR
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "BLUR",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = TechColors.Amber,
            )
            Text(
                text = "$blur px",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = blur.toFloat(),
            onValueChange = { actions.setBlur(it.roundToInt()) },
            valueRange = 0f..48f,
            colors = SliderDefaults.colors(
                thumbColor = TechColors.Amber,
                activeTrackColor = TechColors.Amber,
            ),
        )

        Spacer(Modifier.height(8.dp))
        // DIM
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "DIM",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = TechColors.Amber,
            )
            Text(
                text = "$dim%",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = dim.toFloat(),
            onValueChange = { actions.setDim(it.roundToInt()) },
            valueRange = 0f..80f,
            colors = SliderDefaults.colors(
                thumbColor = TechColors.Amber,
                activeTrackColor = TechColors.Amber,
            ),
        )

        Spacer(Modifier.height(8.dp))
        // VIGNETTE
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "VIGNETTE",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = TechColors.Amber,
            )
            Text(
                text = "$vignette%",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = vignette.toFloat(),
            onValueChange = { actions.setVignette(it.roundToInt()) },
            valueRange = 0f..80f,
            colors = SliderDefaults.colors(
                thumbColor = TechColors.Amber,
                activeTrackColor = TechColors.Amber,
            ),
        )
    }
}
