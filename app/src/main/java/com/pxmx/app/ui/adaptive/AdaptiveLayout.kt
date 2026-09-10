package com.pxmx.app.ui.adaptive

import androidx.compose.runtime.saveable.Saver

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

sealed interface DetailPaneSelection {
    data class Guest(val node: String, val type: String, val vmid: Long, val name: String) : DetailPaneSelection
    data class Node(val node: String) : DetailPaneSelection
    data class Storage(val node: String, val storage: String) : DetailPaneSelection
}

fun restoreDetailPaneSelection(value: Any?): DetailPaneSelection? {
    val map = value as? Map<*, *> ?: return null
    return when (map["kind"] as? String) {
        "guest" -> {
            val node = map["node"] as? String ?: return null
            val type = map["type"] as? String ?: return null
            val vmid = (map["vmid"] as? Number)?.toLong() ?: return null
            val name = map["name"] as? String ?: return null
            DetailPaneSelection.Guest(
                node = node,
                type = type,
                vmid = vmid,
                name = name,
            )
        }
        "node" -> {
            val node = map["node"] as? String ?: return null
            DetailPaneSelection.Node(
                node = node,
            )
        }
        "storage" -> {
            val node = map["node"] as? String ?: return null
            val storage = map["storage"] as? String ?: return null
            DetailPaneSelection.Storage(
                node = node,
                storage = storage,
            )
        }
        else -> null
    }
}

val DetailPaneSelectionSaver: Saver<DetailPaneSelection?, Any> = Saver(
    save = { sel ->
        when (sel) {
            is DetailPaneSelection.Guest -> mapOf(
                "kind" to "guest",
                "node" to sel.node,
                "type" to sel.type,
                "vmid" to sel.vmid,
                "name" to sel.name,
            )
            is DetailPaneSelection.Node -> mapOf(
                "kind" to "node",
                "node" to sel.node,
            )
            is DetailPaneSelection.Storage -> mapOf(
                "kind" to "storage",
                "node" to sel.node,
                "storage" to sel.storage,
            )
            null -> emptyMap<String, Any>()
        }
    },
    restore = { restoreDetailPaneSelection(it) }
)

