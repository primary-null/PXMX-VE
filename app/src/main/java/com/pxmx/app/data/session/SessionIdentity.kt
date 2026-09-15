package com.pxmx.app.data.session

import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import okhttp3.HttpUrl.Companion.toHttpUrl

/** No secrets: account names are case-sensitive, DNS names and default ports are canonical. */
data class SessionIdentity(val endpoint: String, val authMode: AuthMode, val account: String) {
    companion object {
        fun of(config: ServerConfig): SessionIdentity {
            val account = when (config.authMode) {
                AuthMode.PASSWORD -> config.username.trim().let {
                    if ('@' in it) it else "$it@${config.realm}"
                }
                AuthMode.API_TOKEN -> config.apiToken.trim().substringBefore('=')
            }
            return SessionIdentity(config.baseUrl.toHttpUrl().toString(), config.authMode, account)
        }
    }
}

/** A lease on one login generation. Ticket renewal does not replace the generation. */
data class SessionSnapshot(val generation: Long, val state: SessionState, val profileId: String?)
