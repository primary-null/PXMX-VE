package com.pxmx.app.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Saved connection profile. Secrets are stored but never shown in the UI —
 * only [displayUser] / [displayHost] / masks.
 */
@Serializable
data class SavedProfile(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val host: String,
    val port: Int = 8006,
    val authMode: AuthMode = AuthMode.PASSWORD,
    val username: String = "",
    val realm: String = "pam",
    /** Stored secret — never render in UI. */
    val password: String = "",
    /** Stored token — never render full value. */
    val apiToken: String = "",
    val trustSelfSigned: Boolean = false,
    /** Persist password/token for this profile. */
    val saveCredentials: Boolean = true,
    val lastUsedEpochMs: Long = 0L,
    val lastVersion: String? = null,
) {
    val displayHost: String
        get() {
            val h = host.trim().removePrefix("https://").removePrefix("http://")
                .substringBefore('/')
            return if (h.contains(":")) h else "$h:$port"
        }

    val displayUser: String
        get() = when (authMode) {
            AuthMode.PASSWORD -> {
                val u = username.substringBefore('@').ifBlank { username }
                val r = username.substringAfter('@', realm).ifBlank { realm }
                if (u.isBlank()) "—" else "$u@$r"
            }
            AuthMode.API_TOKEN -> {
                val head = apiToken.substringBefore('=').ifBlank { "token" }
                // user@realm!tokenid
                head.ifBlank { "API token" }
            }
        }

    val displayLabel: String
        get() = label.ifBlank { displayHost }

    val hasSavedSecret: Boolean
        get() = when (authMode) {
            AuthMode.PASSWORD -> password.isNotBlank()
            AuthMode.API_TOKEN -> apiToken.isNotBlank()
        }

    val secretMask: String
        get() = if (hasSavedSecret) "••••••••" else "Not saved"

    fun toServerConfig(includeSecrets: Boolean = true): ServerConfig = ServerConfig(
        host = host,
        port = port,
        authMode = authMode,
        username = username,
        password = if (includeSecrets) password else "",
        apiToken = if (includeSecrets) apiToken else "",
        trustSelfSigned = trustSelfSigned,
        realm = realm,
    )

    companion object {
        fun fromConfig(
            config: ServerConfig,
            saveCredentials: Boolean,
            existingId: String? = null,
            label: String = "",
            lastVersion: String? = null,
        ): SavedProfile {
            val user = if (config.username.contains('@')) {
                config.username.substringBefore('@')
            } else config.username
            val realm = if (config.username.contains('@')) {
                config.username.substringAfter('@')
            } else config.realm
            return SavedProfile(
                id = existingId ?: UUID.randomUUID().toString(),
                label = label,
                host = config.host.trim(),
                port = config.port,
                authMode = config.authMode,
                username = user,
                realm = realm,
                password = if (saveCredentials && config.authMode == AuthMode.PASSWORD) config.password else "",
                apiToken = if (saveCredentials && config.authMode == AuthMode.API_TOKEN) config.apiToken else "",
                trustSelfSigned = config.trustSelfSigned,
                saveCredentials = saveCredentials,
                lastUsedEpochMs = System.currentTimeMillis(),
                lastVersion = lastVersion,
            )
        }
    }
}

@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    OLED_DARK,
    ;

    val label: String
        get() = when (this) {
            SYSTEM -> "System"
            LIGHT -> "Light"
            OLED_DARK -> "OLED Dark"
        }
}

/**
 * Snapshot of a connection for “jump back” / last-session UI.
 * No secrets — host, user mask, and timestamps only.
 */
@Serializable
data class SessionResumeInfo(
    val profileId: String,
    val hostDisplay: String,
    val userDisplay: String,
    val version: String? = null,
    val lastLoginEpochMs: Long = 0L,
    /** True if this was the active session when the user switched away. */
    val wasActiveSession: Boolean = false,
)

object ProfileConflictResolver {
    fun findConflict(
        host: String,
        port: Int,
        username: String,
        realm: String,
        authMode: AuthMode,
        activeProfileId: String?,
        existingProfiles: List<SavedProfile>,
    ): SavedProfile? {
        val trimmedHost = host.trim()
        if (trimmedHost.isEmpty() || trimmedHost.equals("demo", ignoreCase = true)) return null

        val sameHostProfiles = existingProfiles.filter {
            it.host.trim().equals(trimmedHost, ignoreCase = true) && it.port == port
        }
        if (sameHostProfiles.isEmpty()) return null

        if (activeProfileId != null) {
            val current = sameHostProfiles.find { it.id == activeProfileId }
            if (current != null) {
                val cleanUser = if (username.contains('@')) username.substringBefore('@') else username
                val cleanRealm = if (username.contains('@')) username.substringAfter('@') else realm
                val sameUser = current.username.trim().equals(cleanUser.trim(), ignoreCase = true) &&
                    current.realm.trim().equals(cleanRealm.trim(), ignoreCase = true) &&
                    current.authMode == authMode
                if (sameUser) {
                    return null
                }
            }
        }

        return sameHostProfiles.first()
    }

    fun generateSuffixLabel(baseLabelOrHost: String, existingProfiles: List<SavedProfile>): String {
        val cleanBase = baseLabelOrHost.trim().replace(Regex("""\s*\(\d+\)$"""), "")
        val existingLabels = existingProfiles.map {
            (if (it.label.isNotBlank()) it.label else it.displayLabel).lowercase()
        }.toSet()
        var index = 2
        while (true) {
            val candidate = "$cleanBase ($index)"
            if (!existingLabels.contains(candidate.lowercase())) {
                return candidate
            }
            index++
        }
    }
}
