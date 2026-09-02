package com.pxmx.app.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.pxmx.app.data.api.AppJson
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.SavedProfile
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionResumeInfo
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.model.ThemeMode
import com.pxmx.app.data.model.VersionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class ProbeAuth(val ticket: String, val csrf: String? = null)

/**
 * Live session + multi-profile store + theme / auto-connect prefs.
 *
 * Secrets are stored only in [EncryptedSharedPreferences] (AES-256 via Keystore).
 * Live PVE tickets stay in memory and are never persisted.
 * UI never renders full secrets (host / user / “••••” masks only).
 */
class SessionStore(
    context: Context? = null,
    injectedPrefs: SharedPreferences? = null,
) {

    private val json = AppJson
    private val prefs: SharedPreferences = injectedPrefs ?: SecurePrefs.open(context!!.applicationContext)

    private val probeAuthMap = ConcurrentHashMap<String, ProbeAuth>()

    private val _session = MutableStateFlow<SessionState?>(null)
    val session: StateFlow<SessionState?> = _session.asStateFlow()

    private val _profiles = MutableStateFlow<List<SavedProfile>>(emptyList())
    val profiles: StateFlow<List<SavedProfile>> = _profiles.asStateFlow()

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _autoConnect = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CONNECT, true))
    val autoConnect: StateFlow<Boolean> = _autoConnect.asStateFlow()

    /** Profile you just left (switch account) — for jump-back banner. */
    private val _previousSession = MutableStateFlow(loadPreviousSession())
    val previousSession: StateFlow<SessionResumeInfo?> = _previousSession.asStateFlow()

    private val _tourCompleted = MutableStateFlow(prefs.getBoolean(KEY_TOUR_COMPLETED, false))
    val tourCompleted: StateFlow<Boolean> = _tourCompleted.asStateFlow()

    init {
        migrateLegacyIfNeeded()
        _profiles.value = loadProfiles().sortedByDescending { it.lastUsedEpochMs }
    }

    // ---- probe auth ----

    fun setProbeAuth(baseUrl: String, auth: ProbeAuth) {
        probeAuthMap[baseUrl] = auth
    }

    fun getProbeAuth(baseUrl: String): ProbeAuth? = probeAuthMap[baseUrl]

    fun removeProbeAuth(baseUrl: String) {
        probeAuthMap.remove(baseUrl)
    }

    fun clearProbeAuth() {
        probeAuthMap.clear()
    }

    // ---- session ----

    fun setSession(state: SessionState) {
        _session.value = state
    }

    fun updateVersion(version: VersionInfo?) {
        val current = _session.value ?: return
        _session.value = current.copy(version = version)
    }

    /**
     * End live session. If [rememberAsPrevious], stash a jump-back card for the login screen.
     */
    fun clearSession(rememberAsPrevious: Boolean = false) {
        if (rememberAsPrevious) {
            snapshotCurrentAsPrevious()
        }
        _session.value = null
        clearProbeAuth()
    }

    fun snapshotCurrentAsPrevious() {
        val s = _session.value ?: return
        // Demo sessions never produce a jump-back card: resuming "demo" would
        // hide the real saved profiles behind a publish-mode filter.
        if (s.config.host.equals("demo", ignoreCase = true)) return
        val profileId = lastProfileId() ?: return
        val profile = getProfile(profileId)
        val info = SessionResumeInfo(
            profileId = profileId,
            hostDisplay = s.config.displayHost,
            userDisplay = s.username
                ?: profile?.displayUser
                ?: s.config.username,
            version = s.version?.display ?: profile?.lastVersion,
            lastLoginEpochMs = profile?.lastUsedEpochMs ?: System.currentTimeMillis(),
            wasActiveSession = true,
        )
        prefs.edit { putString(KEY_PREVIOUS_SESSION, json.encodeToString(info)) }
        _previousSession.value = info
    }

    fun clearPreviousSession() {
        prefs.edit { remove(KEY_PREVIOUS_SESSION) }
        _previousSession.value = null
    }

    fun loadPreviousSession(): SessionResumeInfo? {
        val raw = prefs.getString(KEY_PREVIOUS_SESSION, null) ?: return null
        return try {
            json.decodeFromString<SessionResumeInfo>(raw)
        } catch (_: Exception) {
            null
        }
    }

    /** Last successfully used profile that still has saved credentials (for auto-login / resume). */
    fun lastResumableProfile(): SavedProfile? {
        val last = lastProfileId()?.let { getProfile(it) } ?: getLastProfile()
        return last?.takeIf { it.hasSavedSecret }
    }

    fun isAuthenticated(): Boolean {
        val s = _session.value ?: return false
        return when (s.config.authMode) {
            AuthMode.API_TOKEN -> s.config.apiToken.isNotBlank()
            AuthMode.PASSWORD -> !s.ticket.isNullOrBlank()
        }
    }

    // ---- profiles ----

    fun listProfiles(): List<SavedProfile> = _profiles.value

    fun getProfile(id: String): SavedProfile? = _profiles.value.find { it.id == id }

    fun getLastProfile(): SavedProfile? = _profiles.value.maxByOrNull { it.lastUsedEpochMs }

    /**
     * Upsert profile after successful login.
     * If [saveCredentials] is false, secrets are wiped for that profile.
     */
    fun saveProfileFromLogin(
        config: ServerConfig,
        saveCredentials: Boolean,
        profileId: String? = null,
        label: String = "",
        forceNewProfile: Boolean = false,
        version: String? = null,
    ): SavedProfile {
        if (config.host.equals("demo", ignoreCase = true)) {
            // Demo is an in-memory playground. Never persist it as a resumable profile.
            return SavedProfile.fromConfig(
                config = config,
                saveCredentials = false,
                existingId = null,
                label = label,
                lastVersion = version,
            )
        }
        val existing = if (forceNewProfile) null else (profileId?.let { getProfile(it) } ?: findMatching(config))
        val profile = SavedProfile.fromConfig(
            config = config,
            saveCredentials = saveCredentials,
            existingId = if (forceNewProfile) null else existing?.id,
            label = label.ifBlank { existing?.label.orEmpty() },
            lastVersion = version ?: existing?.lastVersion,
        )
        upsertProfile(profile)
        prefs.edit { putString(KEY_LAST_PROFILE_ID, profile.id) }
        return profile
    }

    fun upsertProfile(profile: SavedProfile) {
        val list = _profiles.value.toMutableList()
        val idx = list.indexOfFirst { it.id == profile.id }
        if (idx >= 0) list[idx] = profile else list.add(profile)
        persistProfiles(list.sortedByDescending { it.lastUsedEpochMs })
    }

    fun deleteProfile(id: String) {
        val target = getProfile(id)
        if (target != null) {
            val hostStillUsed = _profiles.value.any { it.id != id && it.host.equals(target.host, ignoreCase = true) && it.trustSelfSigned }
            if (!hostStillUsed) removeCertPin(target.host)
        }
        persistProfiles(_profiles.value.filterNot { it.id == id })
        if (prefs.getString(KEY_LAST_PROFILE_ID, null) == id) {
            prefs.edit { remove(KEY_LAST_PROFILE_ID) }
        }
    }

    fun touchProfile(id: String, version: String? = null) {
        val p = getProfile(id) ?: return
        upsertProfile(
            p.copy(
                lastUsedEpochMs = System.currentTimeMillis(),
                lastVersion = version ?: p.lastVersion,
            ),
        )
        prefs.edit { putString(KEY_LAST_PROFILE_ID, id) }
    }

    fun lastProfileId(): String? = prefs.getString(KEY_LAST_PROFILE_ID, null)

    private fun findMatching(config: ServerConfig): SavedProfile? {
        val host = config.host.trim().lowercase()
        val user = config.username.substringBefore('@').lowercase()
        return _profiles.value.find {
            it.host.trim().lowercase() == host &&
                it.port == config.port &&
                it.authMode == config.authMode &&
                it.username.lowercase() == user
        }
    }

    private fun persistProfiles(list: List<SavedProfile>) {
        prefs.edit { putString(KEY_PROFILES_JSON, json.encodeToString(list)) }
        _profiles.value = list
    }

    private fun loadProfiles(): List<SavedProfile> {
        val raw = prefs.getString(KEY_PROFILES_JSON, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<SavedProfile>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ---- prefs ----

    fun setAutoConnect(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_CONNECT, enabled) }
        _autoConnect.value = enabled
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
        _themeMode.value = mode
    }

    private fun loadThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, ThemeMode.OLED_DARK.name)
        return ThemeMode.entries.find { it.name == raw } ?: ThemeMode.OLED_DARK
    }

    // ---- host keys ----

    fun getHostKey(host: String): String? = prefs.getString(hostKeyPref(host), null)

    fun saveHostKey(host: String, key: String) {
        prefs.edit { putString(hostKeyPref(host), key) }
    }

    private fun hostKeyPref(host: String) = "hostkey_$host"

    // ---- certificate pins (TOFU) ----

    fun getCertPin(host: String): String? = prefs.getString(certPinPref(host), null)

    fun saveCertPin(host: String, fingerprint: String) {
        prefs.edit { putString(certPinPref(host), fingerprint) }
    }

    fun removeCertPin(host: String) {
        prefs.edit { remove(certPinPref(host)) }
    }

    private fun certPinPref(host: String) = "certpin_${host.trim().lowercase()}"

    // ---- home sort prefs (survive process death) ----

    fun loadSort(key: String, default: String): String =
        prefs.getString(sortPrefKey(key), default) ?: default

    fun saveSort(key: String, value: String) {
        prefs.edit { putString(sortPrefKey(key), value) }
    }

    private fun sortPrefKey(key: String) = "sort_$key"

    // ---- version toast ----

    fun hasShownVersionToast(): Boolean = prefs.getBoolean(KEY_VERSION_TOAST_SHOWN, false)

    fun markVersionToastShown() {
        prefs.edit { putBoolean(KEY_VERSION_TOAST_SHOWN, true) }
    }

    // ---- tour ----

    fun markTourCompleted() {
        prefs.edit { putBoolean(KEY_TOUR_COMPLETED, true) }
        _tourCompleted.value = true
    }

    fun resetTour() {
        prefs.edit { remove(KEY_TOUR_COMPLETED) }
        _tourCompleted.value = false
    }

    // ---- clean slate purge ----

    /**
     * Complete wipe of all session state, stored credentials, TOFU cert pins,
     * Keystore entries, theme prefs, and tour completion flags.
     */
    fun purgeAll(context: Context? = null) {
        clearSession(rememberAsPrevious = false)
        _profiles.value = emptyList()
        _themeMode.value = ThemeMode.OLED_DARK
        _autoConnect.value = true
        _previousSession.value = null
        _tourCompleted.value = false

        prefs.edit().clear().commit()

        if (context != null) {
            // Clear and close the encrypted store BEFORE its key and files go away.
            runCatching { SecurePrefs.open(context).edit().clear().commit() }
            runCatching { context.deleteSharedPreferences(SecurePrefs.SECURE_PREFS_NAME) }
            runCatching {
                val prefsDir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
                java.io.File(prefsDir, "${SecurePrefs.SECURE_PREFS_NAME}.xml").delete()
                java.io.File(prefsDir, "${SecurePrefs.SECURE_PREFS_NAME}.xml.bak").delete()
            }
            runCatching { context.deleteSharedPreferences(SecurePrefs.LEGACY_PREFS_NAME) }
            runCatching {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                // Only the EncryptedSharedPreferences master key belongs to
                // this app; never delete aliases other libraries may own.
                keyStore.deleteEntry("_androidx_security_master_key_")
            }
        }
    }

    // ---- legacy migration (single saved server inside secure store) ----

    private fun migrateLegacyIfNeeded() {
        if (prefs.contains(KEY_PROFILES_JSON)) return
        val host = prefs.getString(LEGACY_HOST, null) ?: return
        val cfg = ServerConfig(
            host = host,
            port = prefs.getInt(LEGACY_PORT, 8006),
            authMode = AuthMode.entries.getOrElse(prefs.getInt(LEGACY_AUTH_MODE, 0)) { AuthMode.PASSWORD },
            username = prefs.getString(LEGACY_USERNAME, "") ?: "",
            password = prefs.getString(LEGACY_PASSWORD, "") ?: "",
            apiToken = prefs.getString(LEGACY_API_TOKEN, "") ?: "",
            trustSelfSigned = prefs.getBoolean(LEGACY_TRUST, false),
            realm = prefs.getString(LEGACY_REALM, "pam") ?: "pam",
        )
        val profile = SavedProfile.fromConfig(cfg, saveCredentials = true)
        prefs.edit {
            putString(KEY_PROFILES_JSON, json.encodeToString(listOf(profile)))
            putString(KEY_LAST_PROFILE_ID, profile.id)
            putBoolean(KEY_AUTO_CONNECT, true)
            remove(LEGACY_HOST)
            remove(LEGACY_PORT)
            remove(LEGACY_AUTH_MODE)
            remove(LEGACY_USERNAME)
            remove(LEGACY_PASSWORD)
            remove(LEGACY_API_TOKEN)
            remove(LEGACY_TRUST)
            remove(LEGACY_REALM)
        }
    }

    companion object {
        private const val KEY_PROFILES_JSON = "profiles_json"
        private const val KEY_LAST_PROFILE_ID = "last_profile_id"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PREVIOUS_SESSION = "previous_session_json"
        private const val KEY_VERSION_TOAST_SHOWN = "version_toast_shown"
        private const val KEY_TOUR_COMPLETED = "tour_completed"

        private const val LEGACY_HOST = "host"
        private const val LEGACY_PORT = "port"
        private const val LEGACY_AUTH_MODE = "auth_mode"
        private const val LEGACY_USERNAME = "username"
        private const val LEGACY_PASSWORD = "password"
        private const val LEGACY_API_TOKEN = "api_token"
        private const val LEGACY_TRUST = "trust_self_signed"
        private const val LEGACY_REALM = "realm"
    }
}
