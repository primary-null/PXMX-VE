package com.pxmx.app.data.repo

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.*
import com.pxmx.app.data.model.*
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OperationsRegressionTest {
    private val demo = DemoApi()
    private val store = SessionStore(injectedPrefs = FakeSharedPreferences()).apply {
        setSession(SessionState(ServerConfig(host = "entry.example"), ticket = "test-ticket", username = "root@pam"))
    }
    private fun client(api: ProxmoxApi) = PveClient(ContextWrapper(null), store, provider(api))
    private fun provider(api: ProxmoxApi) = object : ProxmoxApiProvider {
        override fun apiFor(config: ServerConfig) = api
        override fun apiForProbe(config: ServerConfig) = ProbeApi(api)
        override fun clear() {}
    }
    private fun repository(api: ProxmoxApi) = ProxmoxRepository(ContextWrapper(null), store, provider(api))

    @Test fun sdnStatusRetainsDistinctNodeIdentity() = runBlocking {
        val api = object : ProxmoxApi by demo {
            override suspend fun nodeSdnZones(node: String) = PveResponse(data = listOf(mapOf<String, Any>("zone" to "shared", "type" to "simple", "status" to "ok")))
        }
        val rows = NetworkRepository(client(api)) { listOf("alpha", "beta") }.listSdnStatus().getOrThrow()
        assertEquals(2, rows.size)
        assertEquals(listOf("alpha", "beta"), rows.map { it.node })
        assertEquals(2, rows.map { it.rowKey }.distinct().size)
        assertNotEquals("Same zone on different nodes must have different identities", rows[0], rows[1])
    }
}
