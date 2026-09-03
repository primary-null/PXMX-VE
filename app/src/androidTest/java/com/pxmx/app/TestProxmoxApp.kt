package com.pxmx.app

import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.session.SessionStore

/**
 * Instrumented-test Application: swaps the real Proxmox client for a fake
 * backend so UI tests are deterministic and never touch a live cluster.
 * The fake implementation (FakeTourApi) lives in this androidTest source set.
 */
class TestProxmoxApp : ProxmoxApp() {

    override fun onCreate() {
        super.onCreate()
        // Ensure every test starts with a clean slate
        sessionStore.clearSession()
        sessionStore.listProfiles().forEach { sessionStore.deleteProfile(it.id) }
        sessionStore.setAutoConnect(false)
    }

    override fun createRepository(store: SessionStore): ProxmoxRepository =
        ProxmoxRepository(this, store, FakeApiProvider(FakeTourApi()))

    private class FakeApiProvider(private val api: ProxmoxApi) : ProxmoxApiProvider {
        override fun apiFor(config: ServerConfig): ProxmoxApi = api
        override fun apiForProbe(config: ServerConfig): ProbeApi = ProbeApi(api)
        override fun clear() = Unit
    }
}
