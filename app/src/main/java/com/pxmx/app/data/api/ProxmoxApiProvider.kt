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
    /** Probe client: bypasses the cache, always authenticates with its own bound config. */
    fun apiForProbe(config: ServerConfig): ProbeApi = ProbeApi(apiFor(config))
    fun clear()
    fun getCapturedFingerprint(host: String): String? = null
}
