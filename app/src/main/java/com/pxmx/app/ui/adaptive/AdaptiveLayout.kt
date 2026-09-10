package com.pxmx.app.ui.adaptive

const val OPERATOR_TWO_PANE_MIN_WIDTH_DP = 600

enum class TabletopIntent {
    KEEP_CONSOLE,
    OFFER_NODE_SHELL,
    NONE,
}

fun isOperatorTwoPane(widthDp: Int): Boolean = widthDp >= OPERATOR_TWO_PANE_MIN_WIDTH_DP

fun isWideViewport(widthPx: Int, heightPx: Int): Boolean = widthPx > heightPx

fun isTabletop(foldState: String, foldOrientation: String): Boolean =
    foldState.equals("HALF_OPENED", ignoreCase = true) &&
    foldOrientation.equals("HORIZONTAL", ignoreCase = true)

fun tabletopIntent(
    isTabletop: Boolean,
    consoleOpen: Boolean,
    consoleIsNode: Boolean = false,
): TabletopIntent = when {
    isTabletop && consoleOpen -> TabletopIntent.KEEP_CONSOLE
    isTabletop && !consoleOpen -> TabletopIntent.OFFER_NODE_SHELL
    else -> TabletopIntent.NONE
}
