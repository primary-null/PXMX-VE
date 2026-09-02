package com.pxmx.app.data.api

import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.session.SessionStore
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Injects authentication headers (PVEAPIToken or PVEAuthCookie).
 * Prioritizes the active [sessionStore] session. If the request URL doesn't match
 * the active session, falls back to the [boundConfig] (used for background probes).
 */
class AuthInterceptor(
    private val sessionStore: SessionStore,
    private val boundConfig: ServerConfig? = null,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val session = sessionStore.session.value
        val request = chain.request()
        val builder = request.newBuilder()

        // 1. Probe credentials win while a connection test is running, otherwise
        //    the live session on the same host would shadow the identity under test.
        val boundProbe = boundConfig
            ?.takeIf { request.url.toString().startsWith(it.baseUrl) }
            ?.let { sessionStore.getProbeAuth(it.baseUrl) }
        if (boundConfig != null && boundProbe != null) {
            applyConfig(builder, boundConfig, request)
        }
        // 2. Use active session if it matches this request's host/port.
        else if (session != null && request.url.toString().startsWith(session.config.baseUrl)) {
            applySession(builder, session, request)
        }
        // 3. Fallback to bound config (for probes without a live probe ticket).
        else if (boundConfig != null && request.url.toString().startsWith(boundConfig.baseUrl)) {
            applyConfig(builder, boundConfig, request)
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
                val auth = sessionStore.getProbeAuth(config.baseUrl)
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
