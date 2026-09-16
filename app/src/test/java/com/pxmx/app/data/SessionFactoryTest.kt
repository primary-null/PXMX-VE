package com.pxmx.app.data

import com.pxmx.app.data.api.ProxmoxClientFactory
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.session.SessionStore
import org.junit.Assert.*
import org.junit.Test

class SessionFactoryTest {
    @Test fun liveCacheSurvivesRenewalButNotAnotherLogin() {
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        val config = ServerConfig(host = "PvE.Example", port = 443, username = "alice")
        val original = SessionState(config, ticket = "original")
        store.setSession(original)
        val factory = ProxmoxClientFactory(store)
        val lease = store.snapshot()!!
        val first = factory.apiForSession(lease)
        assertTrue(store.renewSession(lease, original.copy(ticket = "renewed")))
        assertSame(first, factory.apiForSession(lease))
        store.setSession(original.copy(ticket = "another-login"))
        assertNotSame(first, factory.apiFor(config))
        assertThrows(IllegalStateException::class.java) { factory.apiForSession(lease) }
    }

    @Test fun anotherAccountCannotReuseTheActiveClient() {
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        val config = ServerConfig(host = "pve.example", username = "alice")
        store.setSession(SessionState(config, ticket = "alice"))
        val factory = ProxmoxClientFactory(store)
        factory.apiFor(config)
        assertThrows(IllegalArgumentException::class.java) { factory.apiFor(config.copy(username = "bob")) }
    }
}
