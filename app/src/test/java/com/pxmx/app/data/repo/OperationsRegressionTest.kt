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

    @Test fun usbDigestConflictRereadsAndReallocates() = runBlocking {
        var reads = 0
        val writes = mutableListOf<Map<String, String>>()
        val api = object : ProxmoxApi by demo {
            override suspend fun guestConfig(node: String, type: String, vmid: Long, current: Int?): PveResponse<Map<String, Any>> {
                reads++
                return PveResponse(data = if (reads == 1) mapOf("digest" to "first") else mapOf("usb0" to "host=other", "digest" to "second"))
            }
            override suspend fun updateGuestConfig(node: String, type: String, vmid: Long, fields: Map<String, String>): PveResponse<String?> {
                writes += fields
                if (writes.size == 1) throw PveHttpException(500, "checksum mismatch (file change by other user?)", null)
                return PveResponse(data = "OK")
            }
        }
        val result = repository(api).attachUsb("alpha", GuestType.QEMU, 100, "1234:5678")
        assertTrue("A digest conflict should trigger a fresh allocation", result.isSuccess)
        assertEquals(2, reads)
        assertEquals("second", writes.last()["digest"])
        assertTrue(writes.last().containsKey("usb1"))
    }

    @Test fun usbAllocationIncludesPendingConfigAndDigest() = runBlocking {
        var fields: Map<String, String>? = null
        val api = object : ProxmoxApi by demo {
            override suspend fun guestConfig(node: String, type: String, vmid: Long, current: Int?) =
                PveResponse(data = if (current == 0) mapOf<String, Any>("usb0" to "host=old", "digest" to "version1") else mapOf("digest" to "version1"))
            override suspend fun updateGuestConfig(node: String, type: String, vmid: Long, values: Map<String, String>): PveResponse<String?> {
                fields = values
                return PveResponse(data = "OK")
            }
        }
        repository(api).attachUsb("alpha", GuestType.QEMU, 100, "1234:5678").getOrThrow()
        assertEquals("host=1234:5678,usb3=1", fields?.get("usb1"))
        assertEquals("version1", fields?.get("digest"))
        assertFalse(fields.orEmpty().containsKey("usb0"))
    }

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
