package com.pxmx.app.data.api

import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.session.ProbeAuth
import com.pxmx.app.data.session.SessionStore
import com.pxmx.app.data.session.SessionIdentity
import com.pxmx.app.data.session.SessionSnapshot
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.atomic.AtomicReference

/**
 * Injects authentication headers (PVEAPIToken or PVEAuthCookie).
 *
 * Probe clients ([preferBoundConfig] = true) always authenticate with their own
 * bound config and their own private ticket slot ([probeAuthSlot]), so a
 * connection test never borrows the live session's identity and concurrent
 * probes on the same host never swap tickets.
 *
 * Live clients are bound to one login generation and canonical server/account
 * identity. They may observe a ticket renewal, never another login's credentials.
 */
class AuthInterceptor(
    private val sessionStore: SessionStore,
    private val boundConfig: ServerConfig? = null,
    private val preferBoundConfig: Boolean = false,
    private val probeAuthSlot: AtomicReference<ProbeAuth?>? = null,
    private val boundSession: SessionSnapshot? = sessionStore.snapshot(),
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
        val config = boundConfig ?: boundSession?.state?.config
        if (config != null && request.url.isWithinApi(config)) {
            if (preferBoundConfig) {
                applyConfig(builder, config, request)
            } else {
                val expected = boundSession ?: throw IOException("Session changed")
                val current = sessionStore.currentSession(expected) ?: throw IOException("Session changed")
                if (SessionIdentity.of(config) != SessionIdentity.of(expected.state.config)) {
                    throw IOException("Session changed")
                }
                // Read refreshed credentials only within the exact login generation.
                applySession(builder, current, request)
            }
        }

        return chain.proceed(builder.build())
    }

    private fun applySession(builder: Request.Builder, session: SessionState, request: Request) {
        when (session.config.authMode) {
            AuthMode.API_TOKEN -> {
                val token = session.config.apiToken.trim()
                if (token.isNotEmpty()) {
                    builder.header("Authorization", "PVEAPIToken=$token")
                }
            }
            AuthMode.PASSWORD -> {
                val ticket = session.ticket
                if (!ticket.isNullOrBlank()) {
                    builder.header("Cookie", "PVEAuthCookie=$ticket")
                }
                // CSRF header for write operations
                val method = request.method.uppercase()
                if (method != "GET" && method != "HEAD" && method != "OPTIONS") {
                    val csrf = session.csrf
                    if (!csrf.isNullOrBlank()) {
                        builder.header("CSRFPreventionToken", csrf)
                    }
                }
            }
        }
    }

    private fun applyConfig(builder: Request.Builder, config: ServerConfig, request: Request) {
        when (config.authMode) {
            AuthMode.API_TOKEN -> {
                val token = config.apiToken.trim()
                if (token.isNotEmpty()) {
                    builder.header("Authorization", "PVEAPIToken=$token")
                }
            }
            AuthMode.PASSWORD -> {
                val auth = probeAuthSlot?.get()
                if (auth != null && auth.ticket.isNotBlank()) {
                    builder.header("Cookie", "PVEAuthCookie=${auth.ticket}")
                    val method = request.method.uppercase()
                    if (method != "GET" && method != "HEAD" && method != "OPTIONS") {
                        if (!auth.csrf.isNullOrBlank()) {
                            builder.header("CSRFPreventionToken", auth.csrf)
                        }
                    }
                }
            }
        }
    }
}
