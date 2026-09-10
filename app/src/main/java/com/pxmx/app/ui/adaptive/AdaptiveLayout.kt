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

enum class SettingsPaneSelection {
    NETWORK,
    SDN,
    FIREWALL,
    UPDATES,
    LOG,
}

fun parseSettingsPaneSelection(value: String?): SettingsPaneSelection? = when (value?.uppercase()) {
    "NETWORK" -> SettingsPaneSelection.NETWORK
    "SDN" -> SettingsPaneSelection.SDN
    "FIREWALL" -> SettingsPaneSelection.FIREWALL
    "UPDATES" -> SettingsPaneSelection.UPDATES
    "LOG" -> SettingsPaneSelection.LOG
    else -> null
}

fun formatLogPriority(pri: Int?): String = when (pri) {
    0 -> "EMERG"
    1 -> "ALERT"
    2 -> "CRIT"
    3 -> "ERR"
    4 -> "WARNING"
    5 -> "NOTICE"
    6 -> "INFO"
    7 -> "DEBUG"
    null -> "UNKNOWN"
    else -> "PRI $pri"
}

