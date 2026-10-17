package com.pxmx.app.data.model

/**
 * Stable ids for watched guests and nodes.
 * Guest: `guest|{node}|{vmid}`. Node: `node|{node}`.
 * Storage is not watchable.
 */
object WatchIds {
    const val NOTIFICATION_ID_BASE = 0x57000000
    private const val NOTIFICATION_ID_MASK = 0xFFFF

    private val guestId = Regex("^guest\\|[^|]+\\|\\d+$")
    private val nodeId = Regex("^node\\|[^|]+$")

    fun guest(node: String, vmid: Long): String = "guest|$node|$vmid"

    fun node(node: String): String = "node|$node"

    fun of(resource: ClusterResource): String? {
        val nodeName = resource.node?.takeIf { it.isNotBlank() } ?: return null
        return when {
            resource.isGuest -> resource.vmid?.let { guest(nodeName, it) }
            resource.type == "node" -> node(nodeName)
            else -> null
        }
    }

    fun isValid(id: String): Boolean = guestId.matches(id) || nodeId.matches(id)

    /** Dedicated range so watch rows never share a slot with task notifications. */
    fun notificationId(watchId: String): Int {
        return NOTIFICATION_ID_BASE or (watchId.hashCode() and NOTIFICATION_ID_MASK)
    }
}
