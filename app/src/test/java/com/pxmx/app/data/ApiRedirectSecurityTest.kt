package com.pxmx.app.data

import com.pxmx.app.data.api.ProxmoxClientFactory
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.session.SessionStore
import com.pxmx.app.ui.console.LocalConsoleTlsServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

class ApiRedirectSecurityTest {
    @Test
    fun authenticatedClientsNeverFollowRedirectsToAnotherOrigin() = runBlocking {
        for (mode in listOf("live", "upload", "probe")) {
            LocalConsoleTlsServer("localhost").use { destination ->
                val redirect = "HTTP/1.1 302 Found\r\nLocation: ${destination.url}api2/json/version\r\n" +
                    "Content-Length: 0\r\nConnection: close\r\n\r\n"
                LocalConsoleTlsServer("localhost", redirect).use { source ->
                    val config = ServerConfig(
                        host = "localhost",
                        port = java.net.URI(source.url).port,
                        trustSelfSigned = true,
                    )
                    val store = SessionStore(injectedPrefs = FakeSharedPreferences())
                    store.setSession(SessionState(config, ticket = "synthetic-ticket", csrf = "synthetic-csrf"))
                    val factory = ProxmoxClientFactory(store)
                    val session = store.snapshot()!!
                    val api = when (mode) {
                        "live" -> factory.apiForSession(session)
                        "upload" -> factory.apiForUpload(session)
                        else -> factory.apiForProbe(config).api
                    }
                    val failure = runCatching { api.version() }.exceptionOrNull()
                    assertTrue("Redirect must be returned to the caller", failure is HttpException)
                    assertEquals(302, (failure as HttpException).code())
                    if (mode != "probe") {
                        assertTrue(source.requests.any { it.contains("Cookie: PVEAuthCookie=synthetic-ticket") })
                    }
                    assertTrue("Redirect destination must receive no request", destination.requests.isEmpty())
                }
            }
        }
    }
}
