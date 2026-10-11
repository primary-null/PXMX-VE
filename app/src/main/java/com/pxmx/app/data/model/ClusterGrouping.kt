package com.pxmx.app.data.model

import com.pxmx.app.data.net.DiscoveredHost

data class ClusterGroup<T>(
    val cluster: KnownCluster,
    val items: List<T>,
)

data class GroupedResult<T>(
    val clusterGroups: List<ClusterGroup<T>> = emptyList(),
    val unmatched: List<T> = emptyList(),
)

object ClusterGrouping {

    /**
     * Normalizes a host string by stripping scheme (`http://`, `https://`),
     * path, port, and wrapping brackets for IPv6.
     */
    fun normalizeHost(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        val noScheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed.substring(8)
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed.substring(7)
            else -> trimmed
        }.substringBefore('/')

        val hostOnly = if (noScheme.startsWith("[")) {
            noScheme.substringAfter('[').substringBefore(']')
        } else if (noScheme.count { it == ':' } == 1) {
            noScheme.substringBefore(':')
        } else if (noScheme.contains(":") && !noScheme.contains(".")) {
            noScheme
        } else {
            noScheme.substringBefore(':')
        }
        return hostOnly.trim().lowercase()
    }

    /**
     * Checks if a host matches any member of the cluster.
     */
    fun matchesCluster(cluster: KnownCluster, host: String): Boolean {
        val norm = normalizeHost(host)
        if (norm.isEmpty()) return false
        return cluster.members.any { member ->
            val memberHostNorm = normalizeHost(member.host)
            if (memberHostNorm.isNotEmpty() && memberHostNorm == norm) {
                return@any true
            }
            val nodeNameNorm = normalizeHost(member.nodeName)
            if (nodeNameNorm.isNotEmpty() && nodeNameNorm == norm) {
                return@any true
            }
            false
        }
    }

    /**
     * Pure grouping helper.
     * Items are matched against known clusters.
     * - A profile matching two clusters prefers the cluster with more members (first match if equal).
     * - Only clusters with at least [minGroupSize] (default 2) matching items form a cluster group.
     * - Unmatched items (and items in clusters with < [minGroupSize] matches) remain in [unmatched].
     */
    fun <T> group(
        items: List<T>,
        clusters: List<KnownCluster>,
        minGroupSize: Int = 2,
        hostExtractor: (T) -> String,
    ): GroupedResult<T> {
        if (items.isEmpty() || clusters.isEmpty()) {
            return GroupedResult(clusterGroups = emptyList(), unmatched = items)
        }

        // Prefer cluster with more members; Kotlin's sortedByDescending is a stable sort
        val sortedClusters = clusters.sortedByDescending { it.members.size }

        val clusterBuckets = linkedMapOf<String, MutableList<T>>()
        val clusterMap = linkedMapOf<String, KnownCluster>()
        for (c in sortedClusters) {
            clusterBuckets[c.id] = mutableListOf()
            clusterMap[c.id] = c
        }

        for (item in items) {
            val host = hostExtractor(item)
            val matchedCluster = sortedClusters.firstOrNull { c -> matchesCluster(c, host) }
            if (matchedCluster != null) {
                clusterBuckets[matchedCluster.id]?.add(item)
            }
        }

        val finalGroups = mutableListOf<ClusterGroup<T>>()
        for ((clusterId, matchedItems) in clusterBuckets) {
            val cluster = clusterMap[clusterId] ?: continue
            if (matchedItems.size >= minGroupSize) {
                finalGroups.add(ClusterGroup(cluster = cluster, items = matchedItems))
            }
        }

        // Unmatched preserves original item order, including any items from clusters with < minGroupSize
        val matchedSet = finalGroups.flatMap { it.items }.toSet()
        val orderedUnmatched = items.filter { it !in matchedSet }

        return GroupedResult(
            clusterGroups = finalGroups,
            unmatched = orderedUnmatched,
        )
    }

    fun groupProfiles(
        profiles: List<SavedProfile>,
        clusters: List<KnownCluster>,
    ): GroupedResult<SavedProfile> = group(
        items = profiles,
        clusters = clusters,
        minGroupSize = 2,
        hostExtractor = { it.host },
    )

    fun groupDiscoveredHosts(
        hosts: List<DiscoveredHost>,
        clusters: List<KnownCluster>,
    ): GroupedResult<DiscoveredHost> = group(
        items = hosts,
        clusters = clusters,
        minGroupSize = 2,
        hostExtractor = { it.ip },
    )
}
