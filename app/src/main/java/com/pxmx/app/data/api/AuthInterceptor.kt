package com.pxmx.app.data.api

import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.session.ProbeAuth
import com.pxmx.app.data.session.SessionStore
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
 * Live clients prefer the active [sessionStore] session; requests that don't
 * match the active session fall back to the bound config (background probes).
 */
class AuthInterceptor(
    private val sessionStore: SessionStore,
    private val boundConfig: ServerConfig? = null,
    private val preferBoundConfig: Boolean = false,
    private val probeAuthSlot: AtomicReference<ProbeAuth?>? = null,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val session = sessionStore.session.value
        val request = chain.request()
        val builder = request.newBuilder()
        val matchesBound = boundConfig != null &&
            request.url.toString().startsWith(boundConfig.baseUrl)

        if (preferBoundConfig && matchesBound) {
            // Probe client: the identity under test is the bound config, always.
            applyConfig(builder, boundConfig!!, request)
        } else if (session != null && request.url.toString().startsWith(session.config.baseUrl)) {
            // Live client: the active session wins.
            applySession(builder, session, request)
        } else if (matchesBound) {
            // Live client with no matching session: fall back to the bound config.
            applyConfig(builder, boundConfig!!, request)
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
