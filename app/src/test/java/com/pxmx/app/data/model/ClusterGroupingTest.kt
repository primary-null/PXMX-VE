package com.pxmx.app.data.model

import com.pxmx.app.data.net.DiscoveredHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterGroupingTest {

    private val clusterAlpha = KnownCluster(
        id = "c-alpha",
        name = "Alpha",
        members = listOf(
            ClusterMember(host = "192.0.2.10", nodeName = "alpha1"),
            ClusterMember(host = "192.0.2.11", nodeName = "alpha2"),
            ClusterMember(host = "192.0.2.12", nodeName = "alpha3"),
        ),
    )

    private val clusterSmall = KnownCluster(
        id = "c-small",
        name = "Small",
        members = listOf(
            ClusterMember(host = "192.0.2.10", nodeName = "small1"),
            ClusterMember(host = "192.0.2.20", nodeName = "small2"),
        ),
    )

    @Test
    fun normalizeHost_handlesSchemesPortsAndPaths() {
        assertEquals("192.0.2.10", ClusterGrouping.normalizeHost("192.0.2.10"))
        assertEquals("192.0.2.10", ClusterGrouping.normalizeHost("192.0.2.10:8006"))
        assertEquals("192.0.2.10", ClusterGrouping.normalizeHost("https://192.0.2.10:8006"))
        assertEquals("192.0.2.10", ClusterGrouping.normalizeHost("https://192.0.2.10:8006/api2/json"))
        assertEquals("192.0.2.10", ClusterGrouping.normalizeHost("http://192.0.2.10"))
        assertEquals("192.0.2.10", ClusterGrouping.normalizeHost("[192.0.2.10]:8006"))
        assertEquals("example.com", ClusterGrouping.normalizeHost("https://example.com:8006/"))
        assertEquals("example.com", ClusterGrouping.normalizeHost("EXAMPLE.COM"))
        assertEquals("", ClusterGrouping.normalizeHost(""))
        assertEquals("", ClusterGrouping.normalizeHost(null))
    }

    @Test
    fun matchesCluster_matchesByHostOrNodeName() {
        assertTrue(ClusterGrouping.matchesCluster(clusterAlpha, "192.0.2.10"))
        assertTrue(ClusterGrouping.matchesCluster(clusterAlpha, "https://192.0.2.11:8006"))
        assertTrue(ClusterGrouping.matchesCluster(clusterAlpha, "alpha3"))
        assertFalse(ClusterGrouping.matchesCluster(clusterAlpha, "192.0.2.99"))
        assertFalse(ClusterGrouping.matchesCluster(clusterAlpha, ""))
    }

    @Test
    fun groupProfiles_groupsMatchingMembersWhenAtLeastTwo() {
        val p1 = SavedProfile(id = "p1", host = "192.0.2.10", username = "root")
        val p2 = SavedProfile(id = "p2", host = "https://192.0.2.11:8006", username = "root")
        val standalone = SavedProfile(id = "p3", host = "192.0.2.99", username = "root")

        val result = ClusterGrouping.groupProfiles(
            profiles = listOf(p1, p2, standalone),
            clusters = listOf(clusterAlpha),
        )

        assertEquals(1, result.clusterGroups.size)
        assertEquals("Alpha", result.clusterGroups[0].cluster.name)
        assertEquals(listOf(p1, p2), result.clusterGroups[0].items)
        assertEquals(listOf(standalone), result.unmatched)
    }

    @Test
    fun groupProfiles_singleMatchingProfileStaysUnmatched() {
        val p1 = SavedProfile(id = "p1", host = "192.0.2.10", username = "root")
        val standalone = SavedProfile(id = "p2", host = "192.0.2.99", username = "root")

        val result = ClusterGrouping.groupProfiles(
            profiles = listOf(p1, standalone),
            clusters = listOf(clusterAlpha),
        )

        assertTrue(result.clusterGroups.isEmpty())
        assertEquals(listOf(p1, standalone), result.unmatched)
    }

    @Test
    fun groupProfiles_prefersClusterWithMoreMembers() {
        // 192.0.2.10 is in both clusterAlpha (3 members) and clusterSmall (2 members)
        // 192.0.2.11 is only in clusterAlpha
        val p1 = SavedProfile(id = "p1", host = "192.0.2.10", username = "root")
        val p2 = SavedProfile(id = "p2", host = "192.0.2.11", username = "root")

        // Pass clusterSmall first to verify it still prefers clusterAlpha with more members
        val result = ClusterGrouping.groupProfiles(
            profiles = listOf(p1, p2),
            clusters = listOf(clusterSmall, clusterAlpha),
        )

        assertEquals(1, result.clusterGroups.size)
        assertEquals("Alpha", result.clusterGroups[0].cluster.name)
        assertEquals(listOf(p1, p2), result.clusterGroups[0].items)
        assertTrue(result.unmatched.isEmpty())
    }

    @Test
    fun groupProfiles_emptyClustersOrEmptyProfiles_returnsNoGroups() {
        val p1 = SavedProfile(id = "p1", host = "192.0.2.10", username = "root")
        val p2 = SavedProfile(id = "p2", host = "192.0.2.11", username = "root")

        val emptyClustersResult = ClusterGrouping.groupProfiles(
            profiles = listOf(p1, p2),
            clusters = emptyList(),
        )
        assertTrue(emptyClustersResult.clusterGroups.isEmpty())
        assertEquals(listOf(p1, p2), emptyClustersResult.unmatched)

        val emptyProfilesResult = ClusterGrouping.groupProfiles(
            profiles = emptyList(),
            clusters = listOf(clusterAlpha),
        )
        assertTrue(emptyProfilesResult.clusterGroups.isEmpty())
        assertTrue(emptyProfilesResult.unmatched.isEmpty())
    }

    @Test
    fun groupDiscoveredHosts_groupsByIp() {
        val h1 = DiscoveredHost(ip = "192.0.2.10", version = "8.2", latencyMs = 5)
        val h2 = DiscoveredHost(ip = "192.0.2.11", version = "8.2", latencyMs = 6)
        val hOther = DiscoveredHost(ip = "192.0.2.99", version = "8.2", latencyMs = 7)

        val result = ClusterGrouping.groupDiscoveredHosts(
            hosts = listOf(h1, h2, hOther),
            clusters = listOf(clusterAlpha),
        )

        assertEquals(1, result.clusterGroups.size)
        assertEquals("Alpha", result.clusterGroups[0].cluster.name)
        assertEquals(listOf(h1, h2), result.clusterGroups[0].items)
        assertEquals(listOf(hOther), result.unmatched)
    }
}
