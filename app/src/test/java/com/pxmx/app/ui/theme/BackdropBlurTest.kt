package com.pxmx.app.ui.theme

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropBlurTest {

    @Test
    fun blurPixelsMath() {
        val width = 8
        val height = 8
        val pixels = IntArray(width * height) { 0xFF000000.toInt() }
        pixels[0] = 0xFFFFFFFF.toInt() // (0,0) is white, rest is black

        val blurred = blurPixels(pixels, width, height, radiusPx = 2)
        assertEquals(64, blurred.size)

        val pixel00 = blurred[0]
        val isPureWhite = (pixel00 and 0x00FFFFFF) == 0x00FFFFFF
        assertFalse("pixel (0,0) should no longer be pure white", isPureWhite)

        val middlePixel = blurred[3 * width + 3]
        val isPureBlack = (middlePixel and 0x00FFFFFF) == 0
        assertFalse("pixel near the middle should not be pure black", isPureBlack)

        val zeroRadius = blurPixels(pixels, width, height, radiusPx = 0)
        assertEquals(64, zeroRadius.size)
        assertEquals(pixels[0], zeroRadius[0])

        val negRadius = blurPixels(pixels, width, height, radiusPx = -1)
        assertEquals(64, negRadius.size)
    }

    @Test
    fun blurBackdropBitmapIfSupported() {
        val bitmap = try {
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            null
        } ?: return // android.graphics.Bitmap stubbed in JVM test runtime; blur math validated above

        bitmap.eraseColor(0xFF000000.toInt())
        bitmap.setPixel(0, 0, 0xFFFFFFFF.toInt())

        val blurred = blurBackdropBitmap(bitmap, 2)
        assertEquals(8, blurred.width)
        assertEquals(8, blurred.height)
        val pixel00 = blurred.getPixel(0, 0)
        val isPureWhite = (pixel00 and 0x00FFFFFF) == 0x00FFFFFF
        assertFalse("bitmap pixel (0,0) should no longer be pure white", isPureWhite)

        val middlePixel = blurred.getPixel(3, 3)
        val isPureBlack = (middlePixel and 0x00FFFFFF) == 0
        assertFalse("bitmap middle pixel should not be pure black", isPureBlack)

        val zeroRadius = blurBackdropBitmap(bitmap, 0)
        assertEquals(8, zeroRadius.width)
        assertEquals(8, zeroRadius.height)
    }
}
