package com.pxmx.app.data.ssh

import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.SavedProfile
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.repo.PveClient
import com.pxmx.app.data.session.SessionStore

/** API accounts are not Linux accounts. Only a verified root PAM login can reuse its password. */
object SshCredentialPolicy {
    fun rootProfile(store: SessionStore, session: SessionState): SavedProfile? {
        val config = session.config
        if (config.authMode != AuthMode.PASSWORD || session.ticket.isNullOrBlank() ||
            session.username != "root@pam" ||
            PveClient.normalizeUsername(config.username, config.realm) != "root@pam") return null
        val profile = store.lastProfileId()?.let(store::getProfile) ?: return null
        return profile.takeIf {
            it.host == config.host && it.port == config.port &&
                it.realm == config.realm && it.authMode == config.authMode &&
                PveClient.normalizeUsername(it.username, it.realm) == PveClient.normalizeUsername(config.username, config.realm) &&
                it.saveCredentials && it.hasSavedSecret && it.password.isNotBlank()
        }
    }
}
