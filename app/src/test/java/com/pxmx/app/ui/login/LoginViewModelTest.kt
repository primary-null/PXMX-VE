package com.pxmx.app.ui.login

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.ProfileConflictResolver
import com.pxmx.app.data.model.SavedProfile
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionResumeInfo
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private lateinit var sessionStore: SessionStore
    private lateinit var repository: ProxmoxRepository
    private lateinit var demoApi: DemoApi
    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        demoApi = DemoApi()
        fakePrefs = FakeSharedPreferences()
        sessionStore = SessionStore(injectedPrefs = fakePrefs)
        val apiProvider = object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig): ProxmoxApi = demoApi
            override fun apiForProbe(config: ServerConfig): ProbeApi = ProbeApi(demoApi)
            override fun clear() {}
        }
        repository = ProxmoxRepository(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            clientFactory = apiProvider,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // 1. Initial State & Profile Prefill
    // -------------------------------------------------------------------------

    @Test
    fun initialState_withoutSavedProfile_hasDefaults() {
        val vm = LoginViewModel(repository, sessionStore)
        val state = vm.ui.value

        assertEquals("", state.host)
        assertEquals("8006", state.port)
        assertEquals(AuthMode.PASSWORD, state.authMode)
        assertEquals("root", state.username)
        assertEquals("pam", state.realm)
        assertEquals("", state.password)
        assertEquals("", state.apiToken)
        assertFalse(state.secretPrefillMasked)
        assertFalse(state.loading)
        assertFalse(state.loggedIn)
        assertNull(state.error)
    }

    @Test
    fun initialState_withSavedProfile_prefillsFieldsAndMasksSecrets() {
        val profile = SavedProfile(
            id = "pve-prod-id",
            label = "Production PVE",
            host = "192.168.1.50",
            port = 8006,
            authMode = AuthMode.PASSWORD,
            username = "admin",
            realm = "pve",
            password = "secret-password",
            lastUsedEpochMs = 1_000_000L,
        )
        sessionStore.upsertProfile(profile)
        fakePrefs.edit().putString("pxmx_last_profile_id", profile.id).apply()

        val vm = LoginViewModel(repository, sessionStore)
        val state = vm.ui.value

        assertEquals("192.168.1.50", state.host)
        assertEquals("8006", state.port)
        assertEquals(AuthMode.PASSWORD, state.authMode)
        assertEquals("admin", state.username)
        assertEquals("pve", state.realm)
        // Secrets are not exposed in plaintext in the form, masked flag is set
        assertEquals("", state.password)
        assertTrue(state.secretPrefillMasked)
        assertEquals(profile.id, state.activeProfileId)
    }

    // -------------------------------------------------------------------------
    // 2. Profile Selection & Secret Visibility
    // -------------------------------------------------------------------------

    @Test
    fun applyProfile_withKeepSecretsInForm_populatesPasswordDirectly() {
        val vm = LoginViewModel(repository, sessionStore)
        val profile = SavedProfile(
            id = "test-1",
            host = "pve.internal",
            port = 8006,
            authMode = AuthMode.PASSWORD,
            username = "root",
            realm = "pam",
            password = "mypassword123",
        )

        vm.applyProfile(profile, keepSecretsInForm = true)
        val state = vm.ui.value

        assertEquals("pve.internal", state.host)
        assertEquals("mypassword123", state.password)
        assertFalse(state.secretPrefillMasked)
    }

    @Test
    fun applyProfile_withoutKeepSecretsInForm_masksSecret() {
        val vm = LoginViewModel(repository, sessionStore)
        val profile = SavedProfile(
            id = "test-token",
            host = "pve.internal",
            port = 8006,
            authMode = AuthMode.API_TOKEN,
            username = "monitoring",
            realm = "pve",
            apiToken = "USER@REALM!TOKENID=UUID",
        )

        vm.applyProfile(profile, keepSecretsInForm = false)
        val state = vm.ui.value

        assertEquals("pve.internal", state.host)
        assertEquals(AuthMode.API_TOKEN, state.authMode)
        assertEquals("", state.apiToken)
        assertTrue(state.secretPrefillMasked)
    }

    @Test
    fun deleteProfile_removesFromStore_andClearsActiveProfileIfMatches() {
        val profile = SavedProfile(id = "delete-me", host = "10.0.0.1", port = 8006)
        sessionStore.upsertProfile(profile)

        val vm = LoginViewModel(repository, sessionStore)
        vm.applyProfile(profile)
        assertEquals("delete-me", vm.ui.value.activeProfileId)

        vm.deleteProfile("delete-me")
        assertNull(sessionStore.getProfile("delete-me"))
        assertNull(vm.ui.value.activeProfileId)
    }

    @Test
    fun deleteProfile_otherProfile_keepsActiveProfileIntact() {
        val active = SavedProfile(id = "active-id", host = "10.0.0.1", port = 8006)
        val other = SavedProfile(id = "other-id", host = "10.0.0.2", port = 8006)
        sessionStore.upsertProfile(active)
        sessionStore.upsertProfile(other)

        val vm = LoginViewModel(repository, sessionStore)
        vm.applyProfile(active)

        vm.deleteProfile("other-id")
        assertNull(sessionStore.getProfile("other-id"))
        assertEquals("active-id", vm.ui.value.activeProfileId)
    }

    // -------------------------------------------------------------------------
    // 3. Validation on Login
    // -------------------------------------------------------------------------

    @Test
    fun login_emptyHost_setsError() {
        val vm = LoginViewModel(repository, sessionStore)
        vm.update { it.copy(host = "   ") }

        vm.login()

        assertEquals("Host is required", vm.ui.value.error)
        assertFalse(vm.ui.value.loggedIn)
    }

    @Test
    fun login_passwordMode_emptyPassword_setsError() {
        val vm = LoginViewModel(repository, sessionStore)
        vm.update { it.copy(host = "192.168.1.1", authMode = AuthMode.PASSWORD, password = "") }

        vm.login()

        assertEquals("Password required", vm.ui.value.error)
        assertFalse(vm.ui.value.loggedIn)
    }

    @Test
    fun login_apiTokenMode_emptyToken_setsError() {
        val vm = LoginViewModel(repository, sessionStore)
        vm.update { it.copy(host = "192.168.1.1", authMode = AuthMode.API_TOKEN, apiToken = "") }

        vm.login()

        assertEquals("API token required", vm.ui.value.error)
        assertFalse(vm.ui.value.loggedIn)
    }

    // -------------------------------------------------------------------------
    // 4. Two-Factor Authentication (TFA/TOTP) State Handling
    // -------------------------------------------------------------------------

    @Test
    fun setTfaCode_sanitizesNonDigits_and_truncatesTo8Digits() {
        val vm = LoginViewModel(repository, sessionStore)

        vm.setTfaCode("123-456 abc 789 000")
        assertEquals("12345678", vm.ui.value.tfaCode)

        vm.setTfaCode("999999")
        assertEquals("999999", vm.ui.value.tfaCode)
    }

    @Test
    fun submitTfa_emptyCode_setsError() {
        val vm = LoginViewModel(repository, sessionStore)
        vm.update { it.copy(tfaRequired = true, tfaCode = "   ") }

        vm.submitTfa()

        assertEquals("Enter 6-digit code", vm.ui.value.error)
    }

    @Test
    fun cancelTfa_resetsTfaStateAndError() {
        val vm = LoginViewModel(repository, sessionStore)
        vm.update { it.copy(tfaRequired = true, tfaCode = "123456", error = "some err") }

        vm.cancelTfa()

        assertFalse(vm.ui.value.tfaRequired)
        assertEquals("", vm.ui.value.tfaCode)
        assertNull(vm.ui.value.error)
        assertFalse(vm.ui.value.loading)
    }

    // -------------------------------------------------------------------------
    // 5. Banner Dismissal & State Updates
    // -------------------------------------------------------------------------

    @Test
    fun dismissPreviousBanner_clearsSessionStorePreviousSession() {
        val resumeInfo = SessionResumeInfo(
            profileId = "prof-1",
            hostDisplay = "192.168.1.10:8006",
            userDisplay = "root@pam",
        )
        fakePrefs.edit().putString(
            "previous_session_json",
            com.pxmx.app.data.api.AppJson.encodeToString(com.pxmx.app.data.model.SessionResumeInfo.serializer(), resumeInfo),
        ).apply()

        val vm = LoginViewModel(repository, sessionStore)
        assertNotNull(sessionStore.loadPreviousSession())

        vm.dismissPreviousBanner()

        assertNull(sessionStore.previousSession.value)
        assertNull(sessionStore.loadPreviousSession())
    }

    @Test
    fun update_clearsErrorAndSecretPrefillMask() {
        val vm = LoginViewModel(repository, sessionStore)
        // Trigger validation error
        vm.login()
        assertEquals("Host is required", vm.ui.value.error)

        // Typing updates host and clears error
        vm.update { it.copy(host = "10.0.0.5") }
        assertNull(vm.ui.value.error)
        assertFalse(vm.ui.value.secretPrefillMasked)
        assertEquals("10.0.0.5", vm.ui.value.host)
    }

    // -------------------------------------------------------------------------
    // 6. Profile Conflict Resolution
    // -------------------------------------------------------------------------

    @Test
    fun profileConflictResolver_detectsConflictOnSameHostDifferentUser() {
        val existing = listOf(
            SavedProfile(
                id = "prof-1",
                host = "192.168.1.10",
                port = 8006,
                username = "admin",
                realm = "pve",
                authMode = AuthMode.PASSWORD,
            )
        )

        // Attempting to log into same host as root@pam while editing prof-1
        val conflict = ProfileConflictResolver.findConflict(
            host = "192.168.1.10",
            port = 8006,
            username = "root",
            realm = "pam",
            authMode = AuthMode.PASSWORD,
            activeProfileId = "prof-1",
            existingProfiles = existing,
        )

        assertNotNull(conflict)
        assertEquals("prof-1", conflict?.id)
    }

    @Test
    fun profileConflictResolver_noConflictWhenUserAndRealmMatchActiveProfile() {
        val existing = listOf(
            SavedProfile(
                id = "prof-1",
                host = "192.168.1.10",
                port = 8006,
                username = "admin",
                realm = "pve",
                authMode = AuthMode.PASSWORD,
            )
        )

        val conflict = ProfileConflictResolver.findConflict(
            host = "192.168.1.10",
            port = 8006,
            username = "admin@pve",
            realm = "pve",
            authMode = AuthMode.PASSWORD,
            activeProfileId = "prof-1",
            existingProfiles = existing,
        )

        assertNull(conflict)
    }

    @Test
    fun profileConflictResolver_generateSuffixLabel_incrementsNumber() {
        val profiles = listOf(
            SavedProfile(id = "1", label = "Home Lab", host = "10.0.0.1"),
            SavedProfile(id = "2", label = "Home Lab (2)", host = "10.0.0.2"),
        )

        val suffix = ProfileConflictResolver.generateSuffixLabel("Home Lab", profiles)
        assertEquals("Home Lab (3)", suffix)
    }
}
