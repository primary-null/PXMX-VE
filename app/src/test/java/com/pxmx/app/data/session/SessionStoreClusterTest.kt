package com.pxmx.app.data.session

import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.model.ClusterMember
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionStoreClusterTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var store: SessionStore

    @Before
    fun setup() {
        prefs = FakeSharedPreferences()
        store = SessionStore(injectedPrefs = prefs)
    }

    @Test
    fun upsertKnownCluster_persistsAndMergesCaseInsensitiveName() {
        store.upsertKnownCluster(
            name = "Cluster-Alpha",
            members = listOf(
                ClusterMember(host = "192.0.2.10", nodeName = "node1"),
            ),
        )

        assertEquals(1, store.listKnownClusters().size)
        assertEquals("Cluster-Alpha", store.listKnownClusters()[0].name)
        assertEquals(1, store.listKnownClusters()[0].members.size)

        // Upsert same cluster with lower-case name and an additional member
        store.upsertKnownCluster(
            name = "cluster-alpha",
            members = listOf(
                ClusterMember(host = "192.0.2.10", nodeName = "node1-updated"),
                ClusterMember(host = "192.0.2.11", nodeName = "node2"),
            ),
        )

        val clusters = store.listKnownClusters()
        assertEquals(1, clusters.size)
        assertEquals("cluster-alpha", clusters[0].name)
        assertEquals(2, clusters[0].members.size)

        val hosts = clusters[0].members.map { it.host }
        assertTrue(hosts.contains("192.0.2.10"))
        assertTrue(hosts.contains("192.0.2.11"))

        // Verify loaded on fresh SessionStore instance from same prefs
        val freshStore = SessionStore(injectedPrefs = prefs)
        assertEquals(1, freshStore.listKnownClusters().size)
        assertEquals(2, freshStore.listKnownClusters()[0].members.size)
    }

    @Test
    fun upsertKnownCluster_demoSessionDoesNotPersist() {
        store.setSession(
            SessionState(
                config = ServerConfig(host = "demo", port = 8006),
                ticket = "demo-ticket",
            )
        )

        store.upsertKnownCluster(
            name = "Demo-Cluster",
            members = listOf(
                ClusterMember(host = "192.0.2.10", nodeName = "d1"),
                ClusterMember(host = "192.0.2.11", nodeName = "d2"),
            ),
        )

        assertTrue(store.listKnownClusters().isEmpty())
    }

    @Test
    fun purgeAll_clearsKnownClusters() {
        store.upsertKnownCluster(
            name = "Cluster-One",
            members = listOf(ClusterMember(host = "192.0.2.10")),
        )
        assertEquals(1, store.listKnownClusters().size)

        store.purgeAll()

        assertTrue(store.listKnownClusters().isEmpty())
        val freshStore = SessionStore(injectedPrefs = prefs)
        assertTrue(freshStore.listKnownClusters().isEmpty())
    }
}
