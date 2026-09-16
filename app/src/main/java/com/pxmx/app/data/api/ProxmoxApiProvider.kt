package com.pxmx.app.data.api

import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.session.ProbeAuth
import java.util.concurrent.atomic.AtomicReference

/** A probe client plus its private ticket slot, never shared with live traffic. */
data class ProbeApi(
    val api: ProxmoxApi,
    val probeAuth: AtomicReference<ProbeAuth?> = AtomicReference(null),
    val capturedFingerprint: AtomicReference<String?> = AtomicReference(null),
)

interface ProxmoxApiProvider {
    fun apiFor(config: ServerConfig): ProxmoxApi
    /** Live HTTP implementations must bind interception to this exact generation. */
    fun apiForSession(session: com.pxmx.app.data.session.SessionSnapshot): ProxmoxApi = apiFor(session.state.config)
    /**
     * Probe client: bypasses the cache, always authenticates with its own bound
     * config. Abstract on purpose: every HTTP provider must wire the same
     * ticket slot into both the handle and its interceptor.
     */
    fun apiForProbe(config: ServerConfig): ProbeApi
    fun clear()
    fun getCapturedFingerprint(host: String): String? = null
}
