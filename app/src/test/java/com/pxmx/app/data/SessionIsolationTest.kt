package com.pxmx.app.data

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.repo.PveClient
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** Regressions use synthetic credentials and in-process APIs only. */
class SessionIsolationTest {
    @Test
    fun anOldRequestMustNotReplayOnTheNewServer() = runBlocking {
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        val original = SessionState(ServerConfig(host = "server-a.example"), ticket = "fake-a")
        val replacement = SessionState(ServerConfig(host = "server-b.example"), ticket = "fake-b")
        store.setSession(original)
        val constructedFor = mutableListOf<String>()
        val provider = object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig): ProxmoxApi {
                constructedFor.add(config.host)
                return DemoApi()
            }
            override fun apiForProbe(config: ServerConfig) = ProbeApi(DemoApi())
            override fun clear() {}
        }
        val client = PveClient(ContextWrapper(null), store, provider)
        var invocations = 0
        val result = client.apiCall {
            invocations++
            if (invocations == 1) {
                // Simulate server selection while the original HTTP request is in flight.
                store.setSession(replacement)
                throw HttpException(Response.error<Any>(401, "{}".toResponseBody()))
            }
            "operation replayed"
        }
        assertEquals("A request must remain bound to its original server", listOf("server-a.example"), constructedFor)
    }
}