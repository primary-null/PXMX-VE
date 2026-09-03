package com.pxmx.app.data.api

import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.session.ProbeAuth
import java.util.concurrent.atomic.AtomicReference

/** A probe client plus its private ticket slot, never shared with live traffic. */
data class ProbeApi(
    val api: ProxmoxApi,
    val probeAuth: AtomicReference<ProbeAuth?> = AtomicReference(null),
)

interface ProxmoxApiProvider {
    fun apiFor(config: ServerConfig): ProxmoxApi
    /**
     * Probe client: bypasses the cache, always authenticates with its own bound
     * config. Abstract on purpose: every HTTP provider must wire the same
     * ticket slot into both the handle and its interceptor.
     */
    fun apiForProbe(config: ServerConfig): ProbeApi
    fun clear()
    fun getCapturedFingerprint(host: String): String? = null
}
