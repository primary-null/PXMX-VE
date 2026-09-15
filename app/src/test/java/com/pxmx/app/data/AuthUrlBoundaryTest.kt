package com.pxmx.app.data

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.*
import com.pxmx.app.data.model.*
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class AuthUrlBoundaryTest {
    @Test
    fun defaultHttpsPortMustRetainAuthentication() {
        val config = ServerConfig(host = "pve.example", port = 443)
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        store.setSession(SessionState(config, ticket = "fake-ticket"))
        val chain = FakeChain(Request.Builder().url(config.baseUrl + "version").build())
        AuthInterceptor(store, config).intercept(chain)
        assertEquals("PVEAuthCookie=fake-ticket", chain.sent?.header("Cookie"))
    }

    @Test
    fun mixedCaseDnsRetainsAuthentication() {
        val config = ServerConfig(host = "PvE.Example", port = 443)
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        store.setSession(SessionState(config, ticket = "fake-ticket"))
        val chain = FakeChain(Request.Builder().url(config.baseUrl + "version").build())
        AuthInterceptor(store, config).intercept(chain)
        assertEquals("PVEAuthCookie=fake-ticket", chain.sent?.header("Cookie"))
    }

    @Test
    fun originAndPathLookalikesNeverReceiveAuthentication() {
        val config = ServerConfig(host = "pve.example", port = 443)
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        store.setSession(SessionState(config, ticket = "fake-ticket"))
        for (url in listOf("http://pve.example/api2/json/version",
            "https://pve.example:8006/api2/json/version", "https://pve.example.evil/api2/json/version",
            "https://pve.example/api2/jsonish/version", "https://pve.example/api2/json/../version")) {
            val chain = FakeChain(Request.Builder().url(url).build())
            AuthInterceptor(store, config).intercept(chain)
            assertNull(url, chain.sent?.header("Cookie"))
        }
    }

    private class FakeChain(private val req: Request) : Interceptor.Chain {
        var sent: Request? = null
        override fun request() = req
        override fun proceed(request: Request): Response {
            sent = request
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body("{}".toResponseBody()).build()
        }
        override fun call(): Call = throw UnsupportedOperationException()
        override fun connection(): Connection? = null
        override fun connectTimeoutMillis() = 1000
        override fun readTimeoutMillis() = 1000
        override fun writeTimeoutMillis() = 1000
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
    }
}