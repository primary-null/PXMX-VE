package com.pxmx.app.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.pxmx.app.data.api.AppJson
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.ClusterGrouping
import com.pxmx.app.data.model.ClusterMember
import com.pxmx.app.data.model.KnownCluster
import com.pxmx.app.data.model.NotificationPrefs
import com.pxmx.app.data.model.SavedProfile
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.ServerLampThresholds
import com.pxmx.app.data.model.SessionResumeInfo
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.model.ThemeMode
import com.pxmx.app.data.model.VersionInfo
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class ProbeAuth(val ticket: String, val csrf: String? = null)

/**
 * Live session + multi-profile store + theme / auto-connect prefs.
 *
 * Secrets are stored only in encrypted vault storage (AES-256 via Keystore).
 * Live PVE tickets stay in memory and are never persisted.
 * UI never renders full secrets (host / user / “••••” masks only).
 */
class SessionStore(
    context: Context? = null,
    injectedPrefs: SharedPreferences? = null,
) {

    private val json = AppJson
    private val prefs: SharedPreferences = injectedPrefs ?: SecurePrefs.open(context!!.applicationContext)

    private val _session = MutableStateFlow<SessionState?>(null)
    val session: StateFlow<SessionState?> = _session.asStateFlow()

    private val _profiles = MutableStateFlow<List<SavedProfile>>(emptyList())
    val profiles: StateFlow<List<SavedProfile>> = _profiles.asStateFlow()

    private val _knownClusters = MutableStateFlow<List<KnownCluster>>(emptyList())
    val knownClusters: StateFlow<List<KnownCluster>> = _knownClusters.asStateFlow()

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _serverLampThresholds = MutableStateFlow(loadServerLampThresholds())
    val serverLampThresholds: StateFlow<ServerLampThresholds> = _serverLampThresholds.asStateFlow()

    private val _notificationPrefs = MutableStateFlow(loadNotificationPrefs())
    val notificationPrefs: StateFlow<NotificationPrefs> = _notificationPrefs.asStateFlow()

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
        _knownClusters.value = loadKnownClusters().sortedByDescending { it.lastSeenEpochMs }
    }

    // ---- session ----

    private var generation = 0L
    private var activeProfileId: String? = null

    /** Reserve a login attempt; newer attempts and logout invalidate it. */
    @Synchronized
    fun beginLogin(): Long = ++generation

    /** Guard publication and its profile/pin side effects under the logout lock. No suspension. */
    @Synchronized
    fun publishIfGeneration(expected: Long, publish: () -> Unit): Boolean {
        if (generation != expected) return false
        publish()
        return true
    }

    @Synchronized
    fun snapshot(): SessionSnapshot? = _session.value?.let { SessionSnapshot(generation, it, activeProfileId) }

    @Synchronized
    fun isCurrent(expected: SessionSnapshot): Boolean = generation == expected.generation &&
        _session.value?.let { SessionIdentity.of(it.config) == SessionIdentity.of(expected.state.config) } == true

    @Synchronized
    fun currentSession(expected: SessionSnapshot): SessionState? = if (isCurrent(expected)) _session.value else null

    /** Atomic guarded renewal; never resurrect logout or replace another login. */
    @Synchronized
    fun renewSession(expected: SessionSnapshot, state: SessionState): Boolean {
        if (!isCurrent(expected) || SessionIdentity.of(state.config) != SessionIdentity.of(expected.state.config)) return false
        _session.value = state
        return true
    }

    @Synchronized
    fun setSession(state: SessionState) {
        generation++
        activeProfileId = lastProfileId()?.takeIf { id ->
            getProfile(id)?.let { SessionIdentity.of(it.toServerConfig()) == SessionIdentity.of(state.config) } == true
        }
        _session.value = state
    }

    @Synchronized
    fun updateVersion(version: VersionInfo?) {
        val current = _session.value ?: return
        _session.value = current.copy(version = version)
    }

    /**
     * End live session. If [rememberAsPrevious], stash a jump-back card for the login screen.
     */
    @Synchronized
    fun clearSession(rememberAsPrevious: Boolean = false) {
        generation++
        activeProfileId = null
        if (rememberAsPrevious) {
            snapshotCurrentAsPrevious()
        }
        _session.value = null
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
        val info = try {
            json.decodeFromString<SessionResumeInfo>(raw)
        } catch (_: Exception) {
            null
        } ?: return null
        // Migration: 0.6.9 could persist a demo jump-back card whose profileId
        // pointed at a real server while the banner said demo. Drop those as
        // corrupt (exact host match, not a prefix).
        if (info.hostDisplay.substringBefore(':').equals("demo", ignoreCase = true)) {
            prefs.edit { remove(KEY_PREVIOUS_SESSION) }
            return null
        }
        return info
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

    // ---- known clusters ----

    fun listKnownClusters(): List<KnownCluster> = _knownClusters.value

    fun getKnownCluster(id: String): KnownCluster? = _knownClusters.value.find { it.id == id }

    @Synchronized
    fun upsertKnownCluster(name: String, members: List<ClusterMember>) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        if (_session.value?.config?.host?.equals("demo", ignoreCase = true) == true) return

        val currentList = _knownClusters.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.name.trim().equals(trimmedName, ignoreCase = true) }

        val updatedCluster = if (existingIndex >= 0) {
            val existing = currentList[existingIndex]
            val mergedMembers = mergeClusterMembers(existing.members, members)
            existing.copy(
                name = trimmedName,
                members = mergedMembers,
                lastSeenEpochMs = System.currentTimeMillis(),
            )
        } else {
            KnownCluster(
                id = UUID.randomUUID().toString(),
                name = trimmedName,
                members = mergeClusterMembers(emptyList(), members),
                lastSeenEpochMs = System.currentTimeMillis(),
            )
        }

        if (existingIndex >= 0) {
            currentList[existingIndex] = updatedCluster
        } else {
            currentList.add(updatedCluster)
        }
        persistKnownClusters(currentList.sortedByDescending { it.lastSeenEpochMs })
    }

    private fun mergeClusterMembers(
        existing: List<ClusterMember>,
        incoming: List<ClusterMember>,
    ): List<ClusterMember> {
        val map = linkedMapOf<String, ClusterMember>()
        for (m in existing) {
            val hostKey = ClusterGrouping.normalizeHost(m.host)
            if (hostKey.isNotEmpty()) {
                map[hostKey] = m
            }
        }
        for (m in incoming) {
            val hostKey = ClusterGrouping.normalizeHost(m.host)
            if (hostKey.isNotEmpty()) {
                val prev = map[hostKey]
                if (prev != null) {
                    val resolvedNode = m.nodeName?.trim()?.ifBlank { null }
                        ?: prev.nodeName?.trim()?.ifBlank { null }
                    map[hostKey] = prev.copy(
                        host = if (m.host.isNotBlank()) m.host else prev.host,
                        nodeName = resolvedNode,
                    )
                } else {
                    map[hostKey] = m
                }
            }
        }
        return map.values.toList()
    }

    private fun persistKnownClusters(list: List<KnownCluster>) {
        prefs.edit { putString(KEY_CLUSTERS_JSON, json.encodeToString(list)) }
        _knownClusters.value = list
    }

    private fun loadKnownClusters(): List<KnownCluster> {
        val raw = prefs.getString(KEY_CLUSTERS_JSON, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<KnownCluster>>(raw)
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

    // ---- server lamp thresholds prefs ----

    fun setServerLampThresholds(thresholds: ServerLampThresholds) {
        val clamped = ServerLampThresholds.clamp(thresholds.activityPercent, thresholds.warnPercent)
        prefs.edit {
            putInt(KEY_SERVER_LAMP_ACTIVITY, clamped.activityPercent)
            putInt(KEY_SERVER_LAMP_WARN, clamped.warnPercent)
        }
        _serverLampThresholds.value = clamped
    }

    fun setServerLampActivity(activityPercent: Int) {
        val current = _serverLampThresholds.value
        setServerLampThresholds(current.slideActivity(activityPercent))
    }

    fun setServerLampWarn(warnPercent: Int) {
        val current = _serverLampThresholds.value
        setServerLampThresholds(current.slideWarn(warnPercent))
    }

    fun resetServerLampThresholds() {
        setServerLampThresholds(ServerLampThresholds())
    }

    private fun loadServerLampThresholds(): ServerLampThresholds {
        if (!prefs.contains(KEY_SERVER_LAMP_ACTIVITY) && !prefs.contains(KEY_SERVER_LAMP_WARN)) {
            return ServerLampThresholds()
        }
        val activity = prefs.getInt(KEY_SERVER_LAMP_ACTIVITY, ServerLampThresholds.DEFAULT_ACTIVITY_PERCENT)
        val warn = prefs.getInt(KEY_SERVER_LAMP_WARN, ServerLampThresholds.DEFAULT_WARN_PERCENT)
        return ServerLampThresholds.clamp(activity, warn)
    }

    fun setNotificationPrefs(notificationPrefs: NotificationPrefs) {
        prefs.edit {
            putBoolean(KEY_NOTIF_MASTER, notificationPrefs.masterEnabled)
            putBoolean(KEY_NOTIF_NEW_GUEST, notificationPrefs.newGuest)
            putBoolean(KEY_NOTIF_BACKUP_FAIL, notificationPrefs.backupFail)
            putBoolean(KEY_NOTIF_FENCING, notificationPrefs.fencing)
            putBoolean(KEY_NOTIF_MIGRATE, notificationPrefs.migrate)
            putBoolean(KEY_NOTIF_PACKAGE_UPDATES, notificationPrefs.packageUpdates)
            putBoolean(KEY_NOTIF_EXTERNAL_START_STOP, notificationPrefs.externalStartStop)
        }
        _notificationPrefs.value = notificationPrefs
    }

    fun updateNotificationPrefs(transform: (NotificationPrefs) -> NotificationPrefs) {
        setNotificationPrefs(transform(_notificationPrefs.value))
    }

    private fun loadNotificationPrefs(): NotificationPrefs {
        val master = prefs.getBoolean(KEY_NOTIF_MASTER, true)
        val newGuest = prefs.getBoolean(KEY_NOTIF_NEW_GUEST, true)
        val backupFail = prefs.getBoolean(KEY_NOTIF_BACKUP_FAIL, true)
        val fencing = prefs.getBoolean(KEY_NOTIF_FENCING, true)
        val migrate = prefs.getBoolean(KEY_NOTIF_MIGRATE, true)
        val packageUpdates = prefs.getBoolean(KEY_NOTIF_PACKAGE_UPDATES, false)
        val externalStartStop = prefs.getBoolean(KEY_NOTIF_EXTERNAL_START_STOP, false)
        return NotificationPrefs(
            masterEnabled = master,
            newGuest = newGuest,
            backupFail = backupFail,
            fencing = fencing,
            migrate = migrate,
            packageUpdates = packageUpdates,
            externalStartStop = externalStartStop,
        )
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
        _knownClusters.value = emptyList()
        _themeMode.value = ThemeMode.OLED_DARK
        _autoConnect.value = true
        _previousSession.value = null
        _tourCompleted.value = false
        _serverLampThresholds.value = ServerLampThresholds()
        _notificationPrefs.value = NotificationPrefs()

        prefs.edit().clear().commit()

        if (context != null) {
            runCatching { com.pxmx.app.data.ops.OpsBoardStore.purge(context) }
            runCatching { (context.applicationContext as? com.pxmx.app.ProxmoxApp)?.consoleStillStore?.clear() }
            // Clear and close the encrypted store BEFORE its key and files go away.
            runCatching { SecurePrefs.open(context).edit().clear().commit() }
            runCatching {
                val vaultFile = SecurePrefs.getVaultFile(context)
                if (vaultFile.exists()) vaultFile.delete()
            }
            runCatching {
                val directVaultFile = java.io.File(context.filesDir, SecurePrefs.VAULT_FILE_NAME)
                if (directVaultFile.exists()) directVaultFile.delete()
            }
            runCatching { context.deleteSharedPreferences(SecurePrefs.TINK_PREFS_NAME) }
            runCatching {
                val prefsDir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
                java.io.File(prefsDir, "${SecurePrefs.TINK_PREFS_NAME}.xml").delete()
                java.io.File(prefsDir, "${SecurePrefs.TINK_PREFS_NAME}.xml.bak").delete()
            }
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
                // Only the pxmx_vault_master and EncryptedSharedPreferences master keys
                // belong to this app; never delete aliases other libraries may own.
                keyStore.deleteEntry(SecurePrefs.KEYSTORE_MASTER_ALIAS)
                keyStore.deleteEntry("_androidx_security_master_key_")
            }
            SecurePrefs.resetInstance()
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
        private const val KEY_CLUSTERS_JSON = "clusters_json"
        private const val KEY_LAST_PROFILE_ID = "last_profile_id"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PREVIOUS_SESSION = "previous_session_json"
        private const val KEY_VERSION_TOAST_SHOWN = "version_toast_shown"
        private const val KEY_TOUR_COMPLETED = "tour_completed"
        private const val KEY_SERVER_LAMP_ACTIVITY = "server_lamp_activity_percent"
        private const val KEY_SERVER_LAMP_WARN = "server_lamp_warn_percent"
        private const val KEY_NOTIF_MASTER = "notif_master_enabled"
        private const val KEY_NOTIF_NEW_GUEST = "notif_new_guest"
        private const val KEY_NOTIF_BACKUP_FAIL = "notif_backup_fail"
        private const val KEY_NOTIF_FENCING = "notif_fencing"
        private const val KEY_NOTIF_MIGRATE = "notif_migrate"
        private const val KEY_NOTIF_PACKAGE_UPDATES = "notif_package_updates"
        private const val KEY_NOTIF_EXTERNAL_START_STOP = "notif_external_start_stop"

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
