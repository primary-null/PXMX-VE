package com.pxmx.app.data

import android.content.ContextWrapper
import com.pxmx.app.data.api.*
import com.pxmx.app.data.model.*
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.Assert.*
import org.junit.Test

/** Regression adapted from the external review probe; no network or real credentials. */
class LoginPublicationTest {
    @Test
    fun logoutWhileLoginIsSuspendedMustNotPublishSessionOrProfile() = runBlocking {
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        val reachedVersion = kotlinx.coroutines.CompletableDeferred<Unit>()
        val finishVersion = kotlinx.coroutines.CompletableDeferred<Unit>()
        val demo = DemoApi()
        val api = object : ProxmoxApi by demo {
            override suspend fun version(): PveResponse<VersionInfo> {
                reachedVersion.complete(Unit)
                finishVersion.await()
                return demo.version()
            }
        }
        val provider = object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig) = api
            override fun apiForProbe(config: ServerConfig) = ProbeApi(api)
            override fun clear() {}
        }
        val repo = ProxmoxRepository(ContextWrapper(null), store, provider)
        val pending = async {
            repo.login(ServerConfig(host = "pve.example", authMode = AuthMode.API_TOKEN,
                apiToken = "review@pve!test=fake"))
        }
        reachedVersion.await()
        repo.logout()
        finishVersion.complete(Unit)
        assertTrue(pending.await() is LoginOutcome.Failed)
        assertNull(store.session.value)
        assertTrue(store.listProfiles().isEmpty())
    }

    @Test
    fun logoutDuringTfaVerificationCannotPublishTheCompletedLogin() = runBlocking {
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        val demo = DemoApi()
        val api = object : ProxmoxApi by demo {
            override suspend fun createTicketTfa(username: String, password: String, tfaChallenge: String): PveResponse<TicketData> {
                store.clearSession()
                return demo.createTicket("alice@pam", "fake")
            }
        }
        val provider = object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig) = api
            override fun apiForProbe(config: ServerConfig) = ProbeApi(api)
            override fun clear() {}
        }
        val repo = ProxmoxRepository(ContextWrapper(null), store, provider)
        val outcome = repo.completeTfa(ServerConfig(host = "pve.example", username = "alice"), "fake-partial", "000000")
        assertTrue(outcome.isFailure)
        assertNull(store.session.value)
        assertTrue(store.listProfiles().isEmpty())
    }

    @Test
    fun olderLoginFailureDoesNotClearANewerSuccessfulLogin() = runBlocking {
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        val demo = DemoApi()
        lateinit var repo: ProxmoxRepository
        val newer = ServerConfig(host = "new.example", authMode = AuthMode.API_TOKEN, apiToken = "bob@pve!t=fake")
        val oldApi = object : ProxmoxApi by demo {
            override suspend fun version(): PveResponse<VersionInfo> {
                assertTrue(repo.login(newer) is LoginOutcome.Success)
                throw java.io.IOException("old login failed")
            }
        }
        val provider = object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig) = if (config.host == newer.host) demo else oldApi
            override fun apiForProbe(config: ServerConfig) = ProbeApi(apiFor(config))
            override fun clear() {}
        }
        repo = ProxmoxRepository(ContextWrapper(null), store, provider)
        assertTrue(repo.login(newer.copy(host = "old.example")) is LoginOutcome.Failed)
        assertEquals(newer, store.session.value?.config)
        assertEquals(listOf("new.example"), store.listProfiles().map { it.host })
    }

    @Test
    fun olderLoginSuccessDoesNotReplaceANewerSuccessfulLogin() = runBlocking {
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        val demo = DemoApi()
        lateinit var repo: ProxmoxRepository
        val newer = ServerConfig(host = "new.example", authMode = AuthMode.API_TOKEN, apiToken = "bob@pve!t=fake")
        val oldApi = object : ProxmoxApi by demo {
            override suspend fun version(): PveResponse<VersionInfo> {
                assertTrue(repo.login(newer) is LoginOutcome.Success)
                return demo.version()
            }
        }
        val provider = object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig) = if (config.host == newer.host) demo else oldApi
            override fun apiForProbe(config: ServerConfig) = ProbeApi(apiFor(config))
            override fun clear() {}
        }
        repo = ProxmoxRepository(ContextWrapper(null), store, provider)
        assertTrue(repo.login(newer.copy(host = "old.example")) is LoginOutcome.Failed)
        assertEquals(newer, store.session.value?.config)
        assertEquals(listOf("new.example"), store.listProfiles().map { it.host })
    }

    @Test
    fun passwordLoginAuthenticatesPrivatelyBeforePublishing() = runBlocking {
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        val demo = DemoApi()
        val slot = java.util.concurrent.atomic.AtomicReference<com.pxmx.app.data.session.ProbeAuth?>(null)
        val api = object : ProxmoxApi by demo {
            override suspend fun version(): PveResponse<VersionInfo> {
                assertNotNull(slot.get()?.ticket)
                assertNull(store.session.value)
                assertTrue(store.listProfiles().isEmpty())
                return demo.version()
            }
        }
        val provider = object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig) = api
            override fun apiForProbe(config: ServerConfig) = ProbeApi(api, slot)
            override fun clear() {}
        }
        val repo = ProxmoxRepository(ContextWrapper(null), store, provider)
        assertTrue(repo.login(ServerConfig(host = "pve.example", username = "alice", password = "fake")) is LoginOutcome.Success)
        assertEquals("", store.session.value?.config?.password)
        assertNull(slot.get())
    }

    @Test
    fun failedPrivateLoginDoesNotClearTheSharedClientUnderTheSessionLock() = runBlocking {
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        val api = object : ProxmoxApi by DemoApi() {
            override suspend fun version(): PveResponse<VersionInfo> = throw java.io.IOException("offline")
        }
        val provider = object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig) = api
            override fun apiForProbe(config: ServerConfig) = ProbeApi(api)
            override fun clear() { assertFalse("Avoid factory/session lock inversion", Thread.holdsLock(store)) }
        }
        val repo = ProxmoxRepository(ContextWrapper(null), store, provider)
        assertTrue(repo.login(ServerConfig(host = "pve.example", authMode = AuthMode.API_TOKEN,
            apiToken = "review@pve!test=fake")) is LoginOutcome.Failed)
    }

    @Test
    fun passwordLoginPinsTheFirstCertificateBeforeTheNextRequest() = runBlocking {
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        val fingerprint = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val demo = DemoApi()
        val api = object : ProxmoxApi by demo {
            override suspend fun createTicket(username: String, password: String): PveResponse<TicketData> {
                fingerprint.set("FIRST-CERT")
                return demo.createTicket(username, password)
            }
            override suspend fun version(): PveResponse<VersionInfo> {
                assertEquals("FIRST-CERT", store.getCertPin("pve.example"))
                return demo.version()
            }
        }
        val provider = object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig) = api
            override fun apiForProbe(config: ServerConfig) = ProbeApi(api, capturedFingerprint = fingerprint)
            override fun clear() {}
        }
        val repo = ProxmoxRepository(ContextWrapper(null), store, provider)
        assertTrue(repo.login(ServerConfig(host = "pve.example", username = "alice", password = "fake",
            trustSelfSigned = true)) is LoginOutcome.Success)
    }

    @Test
    fun loginNeverPinsAFingerprintCapturedByAnotherClient() = runBlocking {
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        val provider = object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig) = DemoApi()
            override fun apiForProbe(config: ServerConfig) = ProbeApi(DemoApi())
            override fun getCapturedFingerprint(host: String) = "STALE-OTHER-CLIENT"
            override fun clear() {}
        }
        val repo = ProxmoxRepository(ContextWrapper(null), store, provider)
        assertTrue(repo.login(ServerConfig(host = "pve.example", authMode = AuthMode.API_TOKEN,
            apiToken = "review@pve!test=fake", trustSelfSigned = true)) is LoginOutcome.Success)
        assertNull(store.getCertPin("pve.example"))
    }

    @Test
    fun apiTokenLoginMustPersistFingerprintCapturedByFirstRequest() = runBlocking {
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        val fingerprint = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val demo = DemoApi()
        val api = object : ProxmoxApi by demo {
            override suspend fun version(): PveResponse<VersionInfo> {
                fingerprint.set("AA:BB:CC")
                return demo.version()
            }
        }
        val provider = object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig) = api
            override fun apiForProbe(config: ServerConfig) = ProbeApi(api, capturedFingerprint = fingerprint)
            override fun clear() {}
        }
        val repo = ProxmoxRepository(ContextWrapper(null), store, provider)
        val outcome = repo.login(ServerConfig(host = "pve.example", authMode = AuthMode.API_TOKEN,
            apiToken = "review@pve!test=not-a-real-secret", trustSelfSigned = true))
        assertTrue(outcome is LoginOutcome.Success)
        assertEquals("AA:BB:CC", store.getCertPin("pve.example"))
    }
}
