package com.pxmx.app.data

import android.content.ContextWrapper
import com.pxmx.app.data.api.*
import com.pxmx.app.data.model.*
import com.pxmx.app.data.repo.*
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRenewalTest {
    private val config = ServerConfig(host = "pve.example", username = "alice", password = "fake-password")
    private val store = SessionStore(injectedPrefs = FakeSharedPreferences())
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun unauthorized(): Nothing = throw HttpException(Response.error<Any>(401, "{}".toResponseBody()))
    private fun provider(api: ProxmoxApi) = object : ProxmoxApiProvider {
        override fun apiFor(config: ServerConfig) = api
        override fun apiForProbe(config: ServerConfig) = ProbeApi(api)
        override fun clear() {}
    }
    private fun start() {
        store.saveProfileFromLogin(config, true)
        store.setSession(SessionState(config.copy(password = ""), ticket = "old-ticket"))
    }

    @Test fun savedCredentialFallbackPreservesTheOperationGeneration() = runBlocking {
        start()
        val original = store.snapshot()!!
        val attemptedPasswords = mutableListOf<String>()
        val demo = DemoApi()
        val api = object : ProxmoxApi by demo {
            override suspend fun createTicket(username: String, password: String): PveResponse<TicketData> {
                attemptedPasswords += password
                if (password == "old-ticket") unauthorized()
                return demo.createTicket(username, password)
            }
        }
        val repo = ProxmoxRepository(ContextWrapper(null), store, provider(api))
        var invocations = 0
        val result = repo.pveClient.apiCall { if (++invocations == 1) unauthorized() else "ok" }
        assertTrue(result.toString(), result.isSuccess)
        assertEquals(2, invocations)
        assertEquals(listOf("old-ticket", "fake-password"), attemptedPasswords)
        assertTrue(store.isCurrent(original))
    }

    @Test fun logoutWhileTicketRenewalIsSuspendedCannotResurrectTheSession() = runBlocking {
        start()
        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val demo = DemoApi()
        val api = object : ProxmoxApi by demo {
            override suspend fun createTicket(username: String, password: String): PveResponse<TicketData> {
                reached.complete(Unit)
                release.await()
                return demo.createTicket(username, password)
            }
        }
        val client = PveClient(ContextWrapper(null), store, provider(api))
        var invocations = 0
        val pending = async { client.apiCall { invocations++; unauthorized() } }
        reached.await()
        store.clearSession()
        release.complete(Unit)
        assertTrue(pending.await().isFailure)
        assertEquals(1, invocations)
        assertNull(store.session.value)
    }

    @Test fun accountSwitchDuringRenewalKeepsTheReplacementSession() = runBlocking {
        start()
        val replacement = SessionState(config.copy(username = "bob", password = ""), ticket = "bob-ticket")
        val demo = DemoApi()
        val api = object : ProxmoxApi by demo {
            override suspend fun createTicket(username: String, password: String): PveResponse<TicketData> {
                store.setSession(replacement)
                return demo.createTicket(username, password)
            }
        }
        val client = PveClient(ContextWrapper(null), store, provider(api))
        assertTrue(client.apiCall { unauthorized() }.isFailure)
        assertEquals(replacement, store.session.value)
    }

    @Test fun concurrent401sShareOneRenewalWithinTheSameGeneration() = runBlocking {
        start()
        val bothStarted = CompletableDeferred<Unit>()
        var started = 0
        var renewals = 0
        val demo = DemoApi()
        val api = object : ProxmoxApi by demo {
            override suspend fun createTicket(username: String, password: String): PveResponse<TicketData> {
                renewals++
                return demo.createTicket(username, password)
            }
        }
        val client = PveClient(ContextWrapper(null), store, provider(api))
        val jobs = List(2) {
            async {
                var calls = 0
                client.apiCall {
                    if (++calls == 1) {
                        if (++started == 2) bothStarted.complete(Unit)
                        bothStarted.await()
                        unauthorized()
                    }
                    "ok"
                }
            }
        }
        assertTrue(jobs.awaitAll().all { it.isSuccess })
        assertEquals(1, renewals)
    }
}
