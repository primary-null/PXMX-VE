package com.pxmx.app.data.repo

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.PveResponse
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NodeRepositoryTest {

    private lateinit var sessionStore: SessionStore
    private lateinit var demoApi: DemoApi

    @Before
    fun setup() {
        demoApi = DemoApi()
        val fakePrefs = FakeSharedPreferences()
        sessionStore = SessionStore(injectedPrefs = fakePrefs)
        sessionStore.setSession(
            SessionState(
                config = ServerConfig(host = "192.0.2.10", port = 8006),
                ticket = "fake-ticket",
            )
        )
    }

    private fun createRepository(api: ProxmoxApi): ProxmoxRepository {
        val provider = object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig): ProxmoxApi = api
            override fun apiForProbe(config: ServerConfig): ProbeApi = ProbeApi(api)
            override fun clear() {}
        }
        return ProxmoxRepository(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            clientFactory = provider,
        )
    }

    @Test
    fun siteInfo_multiNodeCluster_detectsClusterAndExtractsMembers() = runBlocking {
        val repo = createRepository(demoApi)
        val result = repo.nodeRepo.siteInfo()

        assertTrue(result.isSuccess)
        val site = result.getOrNull()
        assertNotNull(site)
        assertTrue(site!!.isCluster)
        assertEquals("demo-cluster", site.clusterName)
        assertTrue(site.members.size >= 3)
        assertTrue(site.members.any { it.host == "192.0.2.10" && it.nodeName == "alpha" })
    }

    @Test
    fun siteInfo_singleNode_treatedAsStandalone() = runBlocking {
        val standaloneApi = object : ProxmoxApi by demoApi {
            override suspend fun clusterStatus(): PveResponse<List<Map<String, Any>>> {
                return PveResponse(
                    data = listOf(
                        mapOf(
                            "type" to "node",
                            "name" to "node-standalone",
                            "ip" to "192.0.2.50",
                            "local" to 1,
                        )
                    )
                )
            }
        }
        val repo = createRepository(standaloneApi)
        val result = repo.nodeRepo.siteInfo()

        assertTrue(result.isSuccess)
        val site = result.getOrNull()
        assertNotNull(site)
        assertFalse("Single node without type=cluster must be standalone", site!!.isCluster)
        assertEquals(1, site.nodeCount)
        assertEquals("node-standalone", site.nodeName)
        assertEquals(1, site.members.size)
        assertEquals("192.0.2.50", site.members[0].host)
    }

    @Test
    fun siteInfo_skipsBlankIpsInMembers() = runBlocking {
        val clusterWithBlankIp = object : ProxmoxApi by demoApi {
            override suspend fun clusterStatus(): PveResponse<List<Map<String, Any>>> {
                return PveResponse(
                    data = listOf(
                        mapOf(
                            "type" to "cluster",
                            "name" to "test-cluster",
                        ),
                        mapOf(
                            "type" to "node",
                            "name" to "node1",
                            "ip" to "192.0.2.10",
                        ),
                        mapOf(
                            "type" to "node",
                            "name" to "node2",
                            "ip" to "   ",
                        ),
                    )
                )
            }
        }
        val repo = createRepository(clusterWithBlankIp)
        val result = repo.nodeRepo.siteInfo()

        assertTrue(result.isSuccess)
        val site = result.getOrNull()
        assertNotNull(site)
        assertTrue(site!!.isCluster)
        assertEquals(1, site.members.size)
        assertEquals("192.0.2.10", site.members[0].host)
    }
}
