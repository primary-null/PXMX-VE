package com.pxmx.app

import android.app.Application
import android.webkit.CookieManager
import com.pxmx.app.data.api.ProxmoxClientFactory
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.session.SessionStore
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

open class ProxmoxApp : Application() {
    lateinit var sessionStore: SessionStore
        private set
    lateinit var repository: ProxmoxRepository
        private set

    /** Seam for instrumented tests: override to inject a fake repository. */
    protected open fun createRepository(store: SessionStore): ProxmoxRepository =
        ProxmoxRepository(this, store, ProxmoxClientFactory(store))

    override fun onCreate() {
        super.onCreate()
        
        // Register modern BouncyCastle for SSH/X25519 support.
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)

        // Purge any leftover WebView cookies (PVE tickets) on startup.
        runCatching {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
        }
        sessionStore = SessionStore(this)
        repository = createRepository(sessionStore)
    }
}
