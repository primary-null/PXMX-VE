package com.pxmx.app.data.api

import com.pxmx.app.data.model.ServerConfig

interface ProxmoxApiProvider {
    fun apiFor(config: ServerConfig): ProxmoxApi
    fun clear()
    fun getCapturedFingerprint(host: String): String? = null
}
