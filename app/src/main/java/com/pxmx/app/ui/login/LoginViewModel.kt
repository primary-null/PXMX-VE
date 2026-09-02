package com.pxmx.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.LoginOutcome
import com.pxmx.app.data.model.ProfileConflictResolver
import com.pxmx.app.data.model.SavedProfile
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionResumeInfo
import com.pxmx.app.data.net.DiscoveredHost
import com.pxmx.app.data.net.SubnetInfo
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileTestState(
    val loading: Boolean = false,
    val online: Boolean = false,
    val version: String? = null,
    val latencyMs: Long? = null,
    val error: String? = null,
)

data class NetworkScanState(
    val isScanning: Boolean = false,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val currentSubnet: String? = null,
    val discoveredHosts: List<DiscoveredHost> = emptyList(),
    val pveDetectedHosts: List<DiscoveredHost> = emptyList(),
    val unverifiedHosts: List<String> = emptyList(),
    val error: String? = null,
    val infoMessage: String? = null,
    val isComplete: Boolean = false,
    val isDemoSim: Boolean = false,
)

data class LoginUiState(
    val host: String = "",
    val port: String = "8006",
    val authMode: AuthMode = AuthMode.PASSWORD,
    val username: String = "root",
    val realm: String = "pam",
    val password: String = "",
    val apiToken: String = "",
    val trustSelfSigned: Boolean = false,
    /** Persist password/token for this profile. */
    val saveCredentials: Boolean = true,
    /** Auto-connect with last profile that has a secret. */
    val autoConnect: Boolean = true,
    val activeProfileId: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
    val secretPrefillMasked: Boolean = false,
    val tfaRequired: Boolean = false,
    val tfaCode: String = "",
    val pendingConflictProfile: SavedProfile? = null,
    val scanState: NetworkScanState = NetworkScanState(),
    val profileTests: Map<String, ProfileTestState> = emptyMap(),
)

class LoginViewModel(
    private val repository: ProxmoxRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        LoginUiState(autoConnect = sessionStore.autoConnect.value),
    )
    val ui: StateFlow<LoginUiState> = _ui.asStateFlow()

    private var pendingTfaConfig: ServerConfig? = null
    private var pendingPartialTicket: String? = null
    private var pendingProfileId: String? = null
    private var pendingSaveCredentials: Boolean = true
    private var pendingEnableAutoConnect: Boolean? = null
    private var pendingLabel: String = ""
    private var pendingForceNewProfile: Boolean = false
    private var scanJob: Job? = null

    val profiles: StateFlow<List<SavedProfile>> = combine(
        sessionStore.profiles,
        sessionStore.session,
        sessionStore.previousSession,
    ) { profs, sess, prev ->
        // Publish mode: while the demo session is active, real saved profiles are
        // hidden from the list. They stay stored and return for non-demo sessions.
        val demoActive = sess?.config?.host?.equals("demo", ignoreCase = true) == true
        if (demoActive) {
            profs.filter { it.host.equals("demo", ignoreCase = true) }
        } else {
            profs
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sessionStore.listProfiles())

    val previousSession: StateFlow<SessionResumeInfo?> = sessionStore.previousSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sessionStore.previousSession.value)

    val lastResumable: SavedProfile?
        get() = sessionStore.lastResumableProfile()

    init {
        // Prefill last profile fields (no auto-login here — splash handles that).
        val last = sessionStore.lastResumableProfile()
            ?: sessionStore.lastProfileId()?.let { sessionStore.getProfile(it) }
            ?: sessionStore.getLastProfile()
        last?.let { applyProfile(it, keepSecretsInForm = false) }
    }

    fun dismissPreviousBanner() {
        sessionStore.clearPreviousSession()
    }

    fun update(transform: (LoginUiState) -> LoginUiState) {
        _ui.update { transform(it).copy(error = null, secretPrefillMasked = false) }
    }

    fun setTfaCode(code: String) {
        val clean = code.filter { it.isDigit() }.take(8)
        _ui.update { it.copy(tfaCode = clean, error = null) }
    }

    fun submitTfa() {
        val code = _ui.value.tfaCode.trim()
        if (code.isBlank()) {
            _ui.update { it.copy(error = "Enter 6-digit code") }
            return
        }
        val config = pendingTfaConfig ?: return cancelTfa()
        val ticket = pendingPartialTicket ?: return cancelTfa()

        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                repository.completeTfa(
                    config = config,
                    partialTicket = ticket,
                    otp = code,
                    saveCredentials = pendingSaveCredentials,
                    profileId = pendingProfileId,
                    enableAutoConnect = pendingEnableAutoConnect,
                    label = pendingLabel,
                    forceNewProfile = pendingForceNewProfile,
                ).fold(
                    onSuccess = {
                        sessionStore.clearPreviousSession()
                        pendingTfaConfig = null
                        pendingPartialTicket = null
                        pendingLabel = ""
                        pendingForceNewProfile = false
                        _ui.update { s ->
                            s.copy(
                                loading = false,
                                loggedIn = true,
                                error = null,
                                password = "",
                                apiToken = "",
                                tfaRequired = false,
                                tfaCode = "",
                                secretPrefillMasked = s.saveCredentials,
                            )
                        }
                    },
                    onFailure = { e ->
                        _ui.update { s ->
                            s.copy(
                                loading = false,
                                error = e.message ?: "Invalid verification code",
                            )
                        }
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Verification failed") }
            }
        }
    }

    fun cancelTfa() {
        pendingTfaConfig = null
        pendingPartialTicket = null
        pendingLabel = ""
        pendingForceNewProfile = false
        _ui.update { it.copy(tfaRequired = false, tfaCode = "", error = null, loading = false) }
    }

    fun applyProfile(profile: SavedProfile, keepSecretsInForm: Boolean = true) {
        _ui.update {
            it.copy(
                host = profile.host,
                port = profile.port.toString(),
                authMode = profile.authMode,
                username = profile.username.ifBlank { "root" },
                realm = profile.realm.ifBlank { "pam" },
                password = if (keepSecretsInForm && profile.authMode == AuthMode.PASSWORD) {
                    profile.password
                } else {
                    ""
                },
                apiToken = if (keepSecretsInForm && profile.authMode == AuthMode.API_TOKEN) {
                    profile.apiToken
                } else {
                    ""
                },
                trustSelfSigned = profile.trustSelfSigned,
                saveCredentials = profile.saveCredentials,
                activeProfileId = profile.id,
                secretPrefillMasked = profile.hasSavedSecret && !keepSecretsInForm,
                tfaRequired = false,
                tfaCode = "",
                error = null,
            )
        }
    }

    fun connectWithSavedProfile(profileId: String) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                when (val outcome = repository.loginWithProfile(profileId)) {
                    is LoginOutcome.Success -> {
                        sessionStore.clearPreviousSession()
                        _ui.update { s -> s.copy(loading = false, loggedIn = true) }
                    }
                    is LoginOutcome.NeedsTfa -> {
                        pendingTfaConfig = outcome.config
                        pendingPartialTicket = outcome.partialTicket
                        pendingProfileId = outcome.profileId
                        pendingSaveCredentials = outcome.saveCredentials
                        pendingEnableAutoConnect = outcome.enableAutoConnect
                        _ui.update { s ->
                            s.copy(
                                loading = false,
                                tfaRequired = true,
                                tfaCode = "",
                                error = null,
                            )
                        }
                    }
                    is LoginOutcome.Failed -> {
                        // Fall back to form prefilled so user can re-enter secret.
                        sessionStore.getProfile(profileId)?.let { applyProfile(it, keepSecretsInForm = false) }
                        _ui.update { s ->
                            s.copy(
                                loading = false,
                                error = outcome.error.message ?: "Could not use saved profile",
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Connection failed") }
            }
        }
    }

    fun deleteProfile(profileId: String) {
        sessionStore.deleteProfile(profileId)
        if (_ui.value.activeProfileId == profileId) {
            _ui.update { it.copy(activeProfileId = null) }
        }
    }

    fun login() {
        val state = _ui.value
        val host = state.host.trim()
        if (host.isEmpty()) {
            _ui.update { it.copy(error = "Host is required") }
            return
        }

        // If secrets were masked and user didn't retype, use stored profile secrets.
        val profile = state.activeProfileId?.let { sessionStore.getProfile(it) }
        val password = state.password.ifBlank {
            if (state.secretPrefillMasked) profile?.password.orEmpty() else ""
        }
        val token = state.apiToken.ifBlank {
            if (state.secretPrefillMasked) profile?.apiToken.orEmpty() else ""
        }

        if (state.authMode == AuthMode.PASSWORD && password.isBlank()) {
            _ui.update { it.copy(error = "Password required") }
            return
        }
        if (state.authMode == AuthMode.API_TOKEN && token.isBlank()) {
            _ui.update { it.copy(error = "API token required") }
            return
        }

        val port = state.port.toIntOrNull() ?: 8006
        val conflict = ProfileConflictResolver.findConflict(
            host = host,
            port = port,
            username = state.username,
            realm = state.realm,
            authMode = state.authMode,
            activeProfileId = state.activeProfileId,
            existingProfiles = sessionStore.listProfiles(),
        )
        if (conflict != null) {
            _ui.update { it.copy(pendingConflictProfile = conflict) }
            return
        }

        performLogin(
            overrideProfileId = state.activeProfileId,
            forceNewProfile = false,
            newLabel = "",
        )
    }

    fun resolveConflictReplace() {
        val conflict = _ui.value.pendingConflictProfile ?: return
        _ui.update { it.copy(pendingConflictProfile = null) }
        performLogin(
            overrideProfileId = conflict.id,
            forceNewProfile = false,
            newLabel = "",
        )
    }

    fun resolveConflictKeepBoth() {
        val conflict = _ui.value.pendingConflictProfile ?: return
        val existingProfiles = sessionStore.listProfiles()
        val base = conflict.label.ifBlank { conflict.host }
        val suffixLabel = ProfileConflictResolver.generateSuffixLabel(base, existingProfiles)
        _ui.update { it.copy(pendingConflictProfile = null) }
        performLogin(
            overrideProfileId = null,
            forceNewProfile = true,
            newLabel = suffixLabel,
        )
    }

    fun resolveConflictCancel() {
        _ui.update { it.copy(pendingConflictProfile = null, loading = false) }
    }

    private fun performLogin(
        overrideProfileId: String?,
        forceNewProfile: Boolean,
        newLabel: String,
    ) {
        val state = _ui.value
        val host = state.host.trim()
        val profile = state.activeProfileId?.let { sessionStore.getProfile(it) }
        val password = state.password.ifBlank {
            if (state.secretPrefillMasked) profile?.password.orEmpty() else ""
        }
        val token = state.apiToken.ifBlank {
            if (state.secretPrefillMasked) profile?.apiToken.orEmpty() else ""
        }

        val config = ServerConfig(
            host = host,
            port = state.port.toIntOrNull() ?: 8006,
            authMode = state.authMode,
            username = state.username.trim(),
            password = password,
            apiToken = token.trim(),
            trustSelfSigned = state.trustSelfSigned,
            realm = state.realm.trim().ifBlank { "pam" },
        )

        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                when (val outcome = repository.login(
                    config = config,
                    saveCredentials = state.saveCredentials,
                    profileId = overrideProfileId,
                    enableAutoConnect = state.autoConnect,
                    label = newLabel,
                    forceNewProfile = forceNewProfile,
                )) {
                    is LoginOutcome.Success -> {
                        // New active session — previous jump-back no longer needed.
                        sessionStore.clearPreviousSession()
                        // Drop secrets from Compose state after successful login.
                        _ui.update { s ->
                            s.copy(
                                loading = false,
                                loggedIn = true,
                                error = null,
                                password = "",
                                apiToken = "",
                                tfaRequired = false,
                                tfaCode = "",
                                secretPrefillMasked = s.saveCredentials,
                            )
                        }
                    }
                    is LoginOutcome.NeedsTfa -> {
                        pendingTfaConfig = outcome.config
                        pendingPartialTicket = outcome.partialTicket
                        pendingProfileId = outcome.profileId
                        pendingSaveCredentials = outcome.saveCredentials
                        pendingEnableAutoConnect = outcome.enableAutoConnect
                        pendingLabel = outcome.label
                        pendingForceNewProfile = outcome.forceNewProfile
                        _ui.update { s ->
                            s.copy(
                                loading = false,
                                tfaRequired = true,
                                tfaCode = "",
                                error = null,
                            )
                        }
                    }
                    is LoginOutcome.Failed -> {
                        _ui.update { s ->
                            s.copy(loading = false, loggedIn = false, error = outcome.error.message ?: "Login failed")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Login failed") }
            }
        }
    }

    fun consumeLoggedIn() {
        _ui.update { it.copy(loggedIn = false) }
    }

    // --- Task 1: Network Scan ---

    fun startNetworkScan() {
        if (_ui.value.scanState.isScanning) return
        scanJob?.cancel()

        val isDemo = _ui.value.host.trim().equals("demo", ignoreCase = true) ||
            sessionStore.session.value?.config?.host?.equals("demo", ignoreCase = true) == true

        scanJob = viewModelScope.launch {
            _ui.update {
                it.copy(
                    scanState = NetworkScanState(
                        isScanning = true,
                        scannedCount = 0,
                        totalCount = 254,
                        currentSubnet = if (isDemo) "192.0.2.0/24" else null,
                        discoveredHosts = emptyList(),
                        error = null,
                        isComplete = false,
                    ),
                )
            }

            if (isDemo) {
                // Simulated demo scan progression: 3 verified + 2 unknown
                val fakeHosts = listOf(
                    DiscoveredHost(ip = "192.0.2.10", port = 8006, version = "8.3.0", latencyMs = 8L),
                    DiscoveredHost(ip = "192.0.2.11", port = 8006, version = "8.2.4", latencyMs = 14L),
                    DiscoveredHost(ip = "192.0.2.12", port = 8006, version = "8.3.1", latencyMs = 11L),
                )
                val fakeUnverified = listOf("192.0.2.200", "192.0.2.201")
                delay(400)
                _ui.update { it.copy(scanState = it.scanState.copy(scannedCount = 43, discoveredHosts = fakeHosts.take(1), isDemoSim = true)) }
                delay(600)
                _ui.update { it.copy(scanState = it.scanState.copy(scannedCount = 128, discoveredHosts = fakeHosts.take(2), unverifiedHosts = fakeUnverified.take(1), isDemoSim = true)) }
                delay(500)
                _ui.update { it.copy(scanState = it.scanState.copy(scannedCount = 210, discoveredHosts = fakeHosts, unverifiedHosts = fakeUnverified, isDemoSim = true)) }
                delay(500)
                _ui.update {
                    it.copy(
                        scanState = it.scanState.copy(
                            scannedCount = 254,
                            isScanning = false,
                            isComplete = true,
                            discoveredHosts = fakeHosts,
                            unverifiedHosts = fakeUnverified,
                            isDemoSim = true,
                        ),
                    )
                }
                return@launch
            }

            try {
                val scannable = repository.localNet.getScannableSubnets()
                if (scannable.isEmpty()) {
                    // Fallback to determine specific reason
                    repository.localNet.scanSubnet(null).collect { progress ->
                         _ui.update { state ->
                            state.copy(
                                scanState = state.scanState.copy(
                                    isScanning = false,
                                    isComplete = true,
                                    error = progress.error,
                                ),
                            )
                        }
                    }
                    return@launch
                }

                repository.localNet.scanSubnet(null).collect { progress ->
                    _ui.update { state ->
                        val currentVerified = progress.verified.toMutableList()
                        val currentDetected = progress.pveDetected.toMutableList()
                        
                        // Process verified hosts for KNOWN badge
                        val verifiedWithBadges = currentVerified.map { discovered ->
                            val profile = sessionStore.listProfiles().find { it.host == discovered.ip && it.port == discovered.port }
                            if (profile != null && profile.hasSavedSecret) {
                                discovered.copy(isSavedKnown = true)
                            } else {
                                discovered
                            }
                        }

                        // Process PVE_DETECTED for elevation or KNOWN badge
                        val detectedFinal = currentDetected.map { discovered ->
                            val profile = sessionStore.listProfiles().find { it.host == discovered.ip && it.port == discovered.port }
                            if (profile != null && profile.hasSavedSecret) {
                                // Task 4: Attempt authenticated verification for elevation
                                // We'll handle elevation in a separate launch to not block the collect
                                discovered.copy(isSavedKnown = true)
                            } else {
                                discovered
                            }
                        }

                        state.copy(
                            scanState = state.scanState.copy(
                                isScanning = !progress.isFinished,
                                scannedCount = progress.scanned,
                                totalCount = progress.total,
                                currentSubnet = progress.currentIp,
                                discoveredHosts = verifiedWithBadges,
                                pveDetectedHosts = detectedFinal,
                                unverifiedHosts = progress.unverified,
                                error = progress.error,
                                infoMessage = progress.infoMessage,
                                isComplete = progress.isFinished,
                                isDemoSim = false,
                            ),
                        )
                    }

                    // Task 4: Launch elevation for PVE_DETECTED with secrets
                    progress.pveDetected.forEach { discovered ->
                        val profile = sessionStore.listProfiles().find { it.host == discovered.ip && it.port == discovered.port }
                        if (profile != null && profile.hasSavedSecret) {
                            viewModelScope.launch {
                                val testResult = repository.testProfileConnection(profile)
                                if (testResult.online && testResult.version != null) {
                                    _ui.update { state ->
                                        val elevated = discovered.copy(
                                            version = testResult.version,
                                            latencyMs = testResult.latencyMs ?: discovered.latencyMs,
                                            isPveDetectedOnly = false,
                                            isSavedKnown = true
                                        )
                                        state.copy(
                                            scanState = state.scanState.copy(
                                                pveDetectedHosts = state.scanState.pveDetectedHosts.filterNot { it.ip == discovered.ip },
                                                discoveredHosts = (state.scanState.discoveredHosts + elevated).distinctBy { it.ip }
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                _ui.update { it.copy(scanState = it.scanState.copy(isScanning = false)) }
                throw e
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        scanState = it.scanState.copy(
                            isScanning = false,
                            isComplete = true,
                            error = e.message ?: "Scan failed",
                        ),
                    )
                }
            }
        }
    }

    fun stopNetworkScan() {
        scanJob?.cancel()
        _ui.update { it.copy(scanState = it.scanState.copy(isScanning = false)) }
    }

    fun clearScanResults() {
        stopNetworkScan()
        _ui.update { it.copy(scanState = NetworkScanState()) }
    }

    fun onCandidateSelected(candidate: DiscoveredHost) {
        _ui.update {
            it.copy(
                host = candidate.ip,
                port = candidate.port.toString(),
                activeProfileId = null,
                error = null,
            )
        }
    }

    // --- Task 2: Test Connection per Profile ---

    fun testProfile(profileId: String) {
        val profile = sessionStore.getProfile(profileId) ?: return

        _ui.update {
            it.copy(
                profileTests = it.profileTests + (profileId to ProfileTestState(loading = true)),
            )
        }

        viewModelScope.launch {
            try {
                if (profile.host.equals("demo", ignoreCase = true)) {
                    delay(300L)
                    _ui.update {
                        it.copy(
                            profileTests = it.profileTests + (profileId to ProfileTestState(
                                loading = false,
                                online = true,
                                version = "8.3.0",
                                latencyMs = 12L,
                                error = null,
                            )),
                        )
                    }
                    return@launch
                }

                val testResult = repository.testProfileConnection(profile)
                _ui.update {
                    it.copy(
                        profileTests = it.profileTests + (profileId to ProfileTestState(
                            loading = false,
                            online = testResult.online,
                            version = testResult.version,
                            latencyMs = testResult.latencyMs,
                            error = testResult.error,
                        )),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        profileTests = it.profileTests + (profileId to ProfileTestState(
                            loading = false,
                            online = false,
                            error = e.message ?: "Test failed",
                        )),
                    )
                }
            }
        }
    }

    // --- Hidden demo mode: 5 quick taps on the "Proxmox VE" title. ---
    private var demoTapCount = 0
    private var demoLastTap = 0L

    fun onTitleClick() {
        val now = SystemClock.elapsedRealtime()
        demoTapCount = if (now - demoLastTap < 1_000L) demoTapCount + 1 else 1
        demoLastTap = now
        if (demoTapCount >= 5) {
            demoTapCount = 0
            enterDemoMode()
        }
    }

    private fun enterDemoMode() {
        _ui.update {
            it.copy(
                host = "demo",
                port = "8006",
                authMode = AuthMode.PASSWORD,
                username = "demo",
                realm = "pam",
                password = "demo",
                apiToken = "",
                trustSelfSigned = false,
                saveCredentials = false,
                autoConnect = it.autoConnect,
                activeProfileId = null,
                error = null,
                secretPrefillMasked = false,
            )
        }
        login()
    }

    class Factory(
        private val repository: ProxmoxRepository,
        private val sessionStore: SessionStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LoginViewModel(repository, sessionStore) as T
        }
    }
}
