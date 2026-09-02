package com.pxmx.app.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Recursively unwraps [ContextWrapper]s to find the underlying [Activity], if any.
 * Works with themed wrappers, Hilt context wrappers, etc.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
