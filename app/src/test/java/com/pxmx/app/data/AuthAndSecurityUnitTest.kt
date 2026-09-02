package com.pxmx.app.data

import com.pxmx.app.data.api.AuthInterceptor
import com.pxmx.app.data.api.CertUtils
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.ProfileConflictResolver
import com.pxmx.app.data.model.SavedProfile
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.repo.PveException
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.session.SessionStore
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthAndSecurityUnitTest {

    private class FakeChain(private val request: Request) : Interceptor.Chain {
        var interceptedRequest: Request? = null

        override fun request(): Request = request

        override fun proceed(request: Request): Response {
            interceptedRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }

        override fun call(): okhttp3.Call = throw UnsupportedOperationException()
        override fun connection(): okhttp3.Connection? = null
        override fun connectTimeoutMillis(): Int = 10000
        override fun readTimeoutMillis(): Int = 10000
        override fun writeTimeoutMillis(): Int = 10000
        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
    }

    @Test
    fun authInterceptor_passwordMode_postHasCsrfAndCookie() {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        val config = ServerConfig(host = "192.0.2.10", port = 8006, authMode = AuthMode.PASSWORD)
        val initialSession = SessionState(config = config, ticket = "initial-ticket-123", csrf = "initial-csrf-token")
        sessionStore.setSession(initialSession)

        val interceptor = AuthInterceptor(sessionStore)

        val postRequest = Request.Builder()
            .url("https://192.0.2.10:8006/api2/json/nodes/alpha/qemu/100/status/start")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        val chain = FakeChain(postRequest)
        interceptor.intercept(chain)

        val intercepted = chain.interceptedRequest
        assertNotNull(intercepted)
        assertEquals("PVEAuthCookie=initial-ticket-123", intercepted?.header("Cookie"))
        assertEquals("initial-csrf-token", intercepted?.header("CSRFPreventionToken"))
        assertNull(intercepted?.header("Authorization"))
    }

    @Test
    fun authInterceptor_passwordMode_getDoesNotIncludeCsrf() {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        val config = ServerConfig(host = "192.0.2.10", port = 8006, authMode = AuthMode.PASSWORD)
        val session = SessionState(config = config, ticket = "my-ticket", csrf = "my-csrf")
        sessionStore.setSession(session)

        val interceptor = AuthInterceptor(sessionStore)

        val getRequest = Request.Builder()
            .url("https://192.0.2.10:8006/api2/json/nodes/alpha/status")
            .get()
            .build()

        val chain = FakeChain(getRequest)
        interceptor.intercept(chain)

        val intercepted = chain.interceptedRequest
        assertNotNull(intercepted)
        assertEquals("PVEAuthCookie=my-ticket", intercepted?.header("Cookie"))
        assertNull(intercepted?.header("CSRFPreventionToken"))
    }

    @Test
    fun authInterceptor_passwordMode_sessionRemintUpdatesCsrfHeader() {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        val config = ServerConfig(host = "192.0.2.10", port = 8006, authMode = AuthMode.PASSWORD)
        sessionStore.setSession(SessionState(config = config, ticket = "stale-ticket", csrf = "stale-csrf"))

        val interceptor = AuthInterceptor(sessionStore)

        // Session re-mint occurs after 401
        val remintedSession = SessionState(config = config, ticket = "reminted-ticket", csrf = "fresh-new-csrf")
        sessionStore.setSession(remintedSession)

        val retryPostRequest = Request.Builder()
            .url("https://192.0.2.10:8006/api2/json/nodes/alpha/qemu/100/status/stop")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        val chain = FakeChain(retryPostRequest)
        interceptor.intercept(chain)

        val intercepted = chain.interceptedRequest
        assertNotNull(intercepted)
        assertEquals("PVEAuthCookie=reminted-ticket", intercepted?.header("Cookie"))
        assertEquals("fresh-new-csrf", intercepted?.header("CSRFPreventionToken"))
    }

    @Test
    fun authInterceptor_apiTokenMode_usesAuthorizationHeader() {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        val config = ServerConfig(
            host = "192.0.2.10",
            port = 8006,
            authMode = AuthMode.API_TOKEN,
            apiToken = "root@pam!token=11111111-2222-3333-4444-555555555555"
        )
        sessionStore.setSession(SessionState(config = config))

        val interceptor = AuthInterceptor(sessionStore)

        val postRequest = Request.Builder()
            .url("https://192.0.2.10:8006/api2/json/nodes/alpha/qemu/100/status/start")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        val chain = FakeChain(postRequest)
        interceptor.intercept(chain)

        val intercepted = chain.interceptedRequest
        assertNotNull(intercepted)
        assertEquals("PVEAPIToken=root@pam!token=11111111-2222-3333-4444-555555555555", intercepted?.header("Authorization"))
        assertNull(intercepted?.header("Cookie"))
        assertNull(intercepted?.header("CSRFPreventionToken"))
    }

    @Test
    fun sanitizeBackupFilename_acceptsSafeFilenames() {
        val valid = listOf(
            "vzdump-qemu-100-2026_08_31.vma.zst",
            "backup_123-abc.tar.gz",
            "node.backup.01.tar",
            "vzdump-lxc-200.tar"
        )
        for (name in valid) {
            val result = ProxmoxRepository.sanitizeBackupFilename(name)
            assertEquals(name, result)
        }
    }

    @Test
    fun sanitizeBackupFilename_rejectsUnsafeFilenames() {
        val dangerous = listOf(
            "../../etc/passwd",
            "../backup.tar",
            "a/b\\c",
            "/absolute/path/backup.tar",
            "backup;rm -rf /",
            "backup`whoami`",
            "backup\$id",
            "",
            "   ",
            "test..file"
        )
        for (name in dangerous) {
            try {
                ProxmoxRepository.sanitizeBackupFilename(name)
                fail("Expected PveException for dangerous filename: $name")
            } catch (e: PveException) {
                assertTrue(e.message?.contains("Invalid backup filename") == true)
            }
        }
    }

    @Test
    fun redactSecrets_redactsTicketsTokensAndCookies() {
        val sample1 = "Error connecting: PVE:root@pam:4A7B8C9D0E1F2A3B4C5D6E7F8A9B0C1D:invalid"
        val clean1 = ProxmoxRepository.redactSecrets(sample1)
        assertFalse(clean1!!.contains("4A7B8C9D0E1F2A3B4C5D6E7F8A9B0C1D"))
        assertTrue(clean1.contains("[REDACTED_TICKET]"))

        val sample2 = "Failed request with Cookie: PVEAuthCookie=PVE:root@pam:SECRET_VAL and PVEAPIToken=root@pam!token=SECRET_API_KEY"
        val clean2 = ProxmoxRepository.redactSecrets(sample2)
        assertTrue(clean2!!.contains("PVEAuthCookie=[REDACTED]"))
        assertTrue(clean2.contains("PVEAPIToken=[REDACTED]"))

        val sampleJson = """{"ticket":"TICKET123","CSRFPreventionToken":"CSRF456","password":"mypassword","secret":"topsecret"}"""
        val cleanJson = ProxmoxRepository.redactSecrets(sampleJson)
        assertFalse(cleanJson!!.contains("TICKET123"))
        assertFalse(cleanJson.contains("CSRF456"))
        assertFalse(cleanJson.contains("mypassword"))
        assertFalse(cleanJson.contains("topsecret"))
    }

    @Test
    fun certUtils_normalizeFingerprint_caseInsensitiveAndColonStripped() {
        val colonSeparated = "aa:bb:cc:dd:ee:ff:00:11:22:33:44:55:66:77:88:99:aa:bb:cc:dd:ee:ff:00:11:22:33:44:55:66:77:88:99"
        val expected = "AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899"
        assertEquals(expected, CertUtils.normalizeFingerprint(colonSeparated))

        val spaceSeparated = "AA BB CC DD EE FF 00 11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF 00 11 22 33 44 55 66 77 88 99"
        assertEquals(expected, CertUtils.normalizeFingerprint(spaceSeparated))

        val mixedCase = "Aa:Bb:Cc:Dd:ee:ff:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
        assertEquals(expected, CertUtils.normalizeFingerprint(mixedCase))
    }

    @Test
    fun formatHttpError_maps401NullBodyToAuthenticationFailure() {
        val result1 = ProxmoxRepository.formatHttpError(401, "{\"data\":null}", "")
        assertEquals("HTTP 401: authentication failure", result1)

        val result2 = ProxmoxRepository.formatHttpError(401, null, null)
        assertEquals("HTTP 401: authentication failure", result2)

        val result3 = ProxmoxRepository.formatHttpError(401, "{\"message\":\"invalid credentials\"}", "")
        assertEquals("HTTP 401: invalid credentials", result3)

        val result4 = ProxmoxRepository.formatHttpError(403, "{\"data\":null}", "Forbidden")
        assertEquals("HTTP 403: forbidden", result4)

        val result5 = ProxmoxRepository.formatHttpError(401, "", "")
        assertEquals("HTTP 401: authentication failure", result5)
        assertFalse(result5.endsWith(":"))
    }

    @Test
    fun profileConflictResolver_detectsConflictAndGeneratesSuffixLabel() {
        val existingProfile = SavedProfile(
            id = "profile-1",
            label = "pve-primary",
            host = "192.0.2.100",
            port = 8006,
            authMode = AuthMode.PASSWORD,
            username = "root",
            realm = "pam",
        )
        val profiles = listOf(existingProfile)

        // Same host & port with fresh form (activeProfileId = null) -> conflict
        val conflict = ProfileConflictResolver.findConflict(
            host = "192.0.2.100",
            port = 8006,
            username = "admin",
            realm = "pve",
            authMode = AuthMode.PASSWORD,
            activeProfileId = null,
            existingProfiles = profiles,
        )
        assertNotNull(conflict)
        assertEquals("profile-1", conflict?.id)

        // Same profile being edited (activeProfileId = profile-1, same user/realm/mode) -> no conflict
        val selfEdit = ProfileConflictResolver.findConflict(
            host = "192.0.2.100",
            port = 8006,
            username = "root",
            realm = "pam",
            authMode = AuthMode.PASSWORD,
            activeProfileId = "profile-1",
            existingProfiles = profiles,
        )
        assertNull(selfEdit)

        // Different host -> no conflict
        val diffHost = ProfileConflictResolver.findConflict(
            host = "192.0.2.101",
            port = 8006,
            username = "root",
            realm = "pam",
            authMode = AuthMode.PASSWORD,
            activeProfileId = null,
            existingProfiles = profiles,
        )
        assertNull(diffHost)

        // Suffix label generation
        val suffix1 = ProfileConflictResolver.generateSuffixLabel("pve-primary", profiles)
        assertEquals("pve-primary (2)", suffix1)

        val profilesWith2 = profiles + existingProfile.copy(id = "profile-2", label = "pve-primary (2)")
        val suffix2 = ProfileConflictResolver.generateSuffixLabel("pve-primary", profilesWith2)
        assertEquals("pve-primary (3)", suffix2)
    }
}
