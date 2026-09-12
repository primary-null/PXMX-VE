package com.pxmx.app.ui.util

import android.content.Context
import android.content.res.Configuration

object DeXUtils {

    /**
     * Checks if Samsung DeX mode (Desktop Mode) is currently active.
     */
    fun isDeXMode(context: Context): Boolean {
        val config = context.resources.configuration
        return try {
            val field = config.javaClass.getField("semDesktopModeEnabled")
            val semDesktopModeEnabled = field.getInt(config)
            semDesktopModeEnabled == 1 || semDesktopModeEnabled == 0x01
        } catch (e: Exception) {
            // Fallback: check uiMode bitmask for Samsung Desktop Mode flag (0x08)
            try {
                val semDesktopFlag = Configuration::class.java
                    .getField("UI_MODE_TYPE_SEM_DESKTOP")
                    .getInt(null)
                (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == semDesktopFlag
            } catch (e2: Exception) {
                false
            }
        }
    }
}
