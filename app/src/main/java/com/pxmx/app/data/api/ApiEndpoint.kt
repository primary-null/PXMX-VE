package com.pxmx.app.data.api

import com.pxmx.app.data.model.ServerConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Compare canonical origins and a complete API path boundary, never raw URL prefixes. */
internal fun HttpUrl.isWithinApi(config: ServerConfig): Boolean {
    val base = config.baseUrl.toHttpUrl()
    return scheme == base.scheme && host == base.host && port == base.port &&
        encodedPath.startsWith(base.encodedPath)
}
