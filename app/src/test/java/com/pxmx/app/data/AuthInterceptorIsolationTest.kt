package com.pxmx.app.data

import com.pxmx.app.data.api.AuthInterceptor
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.session.ProbeAuth
import com.pxmx.app.data.session.SessionStore
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * Locks the probe ticket contract: each probe client reads ONLY its own slot,
 * and live traffic never sees a probe slot, even on the same host.
 */
class AuthInterceptorIsolationTest {

    private class FakeChain(private val request: Request) : Interceptor.Chain {
        var interceptedRequest: Request? = null

        override fun request(): Request = request

        override fun proceed(request: Request): Response {
            interceptedRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }

        override fun call(): okhttp3.Call = throw UnsupportedOperationException()
        override fun connection(): okhttp3.Connection? = null
        override fun connectTimeoutMillis(): Int = 10000
        override fun readTimeoutMillis(): Int = 10000
        override fun writeTimeoutMillis(): Int = 10000
        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
    }

    private fun requestFor(baseUrl: String): Request = Request.Builder()
        .url("$baseUrl/version")
        .get()
        .build()

    @Test
    fun twoProbeClientsOnSameHostEachSendOnlyTheirOwnTicket() {
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        val config = ServerConfig(host = "192.0.2.10", port = 8006, authMode = AuthMode.PASSWORD)
        val baseUrl = config.baseUrl

        val slotA = AtomicReference<ProbeAuth?>(ProbeAuth("ticket-A", "csrf-A"))
        val slotB = AtomicReference<ProbeAuth?>(ProbeAuth("ticket-B", "csrf-B"))

        val interceptorA = AuthInterceptor(store, config, preferBoundConfig = true, probeAuthSlot = slotA)
        val interceptorB = AuthInterceptor(store, config, preferBoundConfig = true, probeAuthSlot = slotB)

        val chainA = FakeChain(requestFor(baseUrl))
        val chainB = FakeChain(requestFor(baseUrl))
        interceptorA.intercept(chainA)
        interceptorB.intercept(chainB)

        assertEquals("PVEAuthCookie=ticket-A", chainA.interceptedRequest?.header("Cookie"))
        assertEquals("PVEAuthCookie=ticket-B", chainB.interceptedRequest?.header("Cookie"))
    }

    @Test
    fun liveClientSendsSessionCookieWhileProbeSlotIsPopulated() {
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        val config = ServerConfig(host = "192.0.2.10", port = 8006, authMode = AuthMode.PASSWORD)
        store.setSession(SessionState(config = config, ticket = "session-ticket", csrf = "session-csrf"))

        val probeSlot = AtomicReference<ProbeAuth?>(ProbeAuth("probe-ticket", "probe-csrf"))
        val probeClient = AuthInterceptor(store, config, preferBoundConfig = true, probeAuthSlot = probeSlot)
        val liveClient = AuthInterceptor(store, config)

        val probeChain = FakeChain(requestFor(config.baseUrl))
        val liveChain = FakeChain(requestFor(config.baseUrl))
        probeClient.intercept(probeChain)
        liveClient.intercept(liveChain)

        assertEquals("PVEAuthCookie=probe-ticket", probeChain.interceptedRequest?.header("Cookie"))
        assertEquals("PVEAuthCookie=session-ticket", liveChain.interceptedRequest?.header("Cookie"))
    }

    @Test
    fun probeClientNeverBorrowsLiveSessionIdentity() {
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        val config = ServerConfig(host = "192.0.2.10", port = 8006, authMode = AuthMode.PASSWORD)
        store.setSession(SessionState(config = config, ticket = "session-ticket", csrf = "session-csrf"))

        val emptySlot = AtomicReference<ProbeAuth?>(null)
        val probeClient = AuthInterceptor(store, config, preferBoundConfig = true, probeAuthSlot = emptySlot)

        val chain = FakeChain(requestFor(config.baseUrl))
        probeClient.intercept(chain)

        assertNull(chain.interceptedRequest?.header("Cookie"))
    }
}
