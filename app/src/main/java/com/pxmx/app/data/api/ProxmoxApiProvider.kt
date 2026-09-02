package com.pxmx.app.data.api

import com.pxmx.app.data.model.ServerConfig

interface ProxmoxApiProvider {
    fun apiFor(config: ServerConfig): ProxmoxApi
    /** Probe client: bypasses the cache, always authenticates with its own bound config. */
    fun apiForProbe(config: ServerConfig): ProxmoxApi = apiFor(config)
    fun clear()
    fun getCapturedFingerprint(host: String): String? = null
}
