package com.pxmx.app.ui.settings

import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.repo.PveException
import com.pxmx.app.data.ssh.SshUpgradeExecutor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class UpdatesRemoteUpgradeTest {

    @Test
    fun isHttp501_detectsVarious501Exceptions() {
        val errorBody = "{\"message\":\"Method not implemented\"}".toResponseBody("application/json".toMediaType())
        val http501 = HttpException(Response.error<Any>(501, errorBody))
        assertTrue(isHttp501(http501))

        val wrapped501 = PveException("HTTP 501: Method not implemented", http501)
        assertTrue(isHttp501(wrapped501))

        val text501 = PveException("HTTP 501: Not Implemented")
        assertTrue(isHttp501(text501))

        val http500 = HttpException(Response.error<Any>(500, errorBody))
        assertFalse(isHttp501(http500))

        val randomError = PveException("Network timeout")
        assertFalse(isHttp501(randomError))
    }

    @Test
    fun isHttp501_avoidsFalsePositivesOnSimilarNumbers() {
        val falsePositiveCandidate = PveException("Task UPID:node:00005010:00000000:66D4:vzdump:100: failed")
        assertFalse(isHttp501(falsePositiveCandidate))

        val port5010 = PveException("Connection refused on port 5010")
        assertFalse(isHttp501(port5010))

        val version50112 = PveException("Package version 50112 mismatch")
        assertFalse(isHttp501(version50112))

        assertFalse(isHttp501Message("UPID 5010 failed"))
        assertFalse(isHttp501Message("Package 50112 installed"))
        assertFalse(isHttp501Message(null))

        assertTrue(isHttp501Message("HTTP 501: Method not implemented"))
        assertTrue(isHttp501Message("501 Not Implemented"))
        assertTrue(isHttp501Message("Error: 501."))
        assertTrue(isHttp501Message("Method not implemented"))
    }

    @Test
    fun sshUpgrade_userDerivation_alwaysConnectsAsRoot() {
        assertEquals("root", ProxmoxRepository.resolveSshUpgradeUser("root@pam"))
        assertEquals("root", ProxmoxRepository.resolveSshUpgradeUser("admin@pam"))
        assertEquals("root", ProxmoxRepository.resolveSshUpgradeUser("auditor@pve"))
        assertEquals("root", ProxmoxRepository.resolveSshUpgradeUser(""))
        assertEquals("root", ProxmoxRepository.resolveSshUpgradeUser(null))
        assertEquals("root", ProxmoxRepository.SSH_UPGRADE_USER)
    }

    @Test
    fun updatesUiState_isRemoteUpgradeRemoved_detectsFlaggedNodes() {
        // Node without 501 / remote upgrade removed flag
        val defaultState = UpdatesUiState(
            pveVersion = "PVE 8.3.0",
            pveVersionMajor = 8,
            sshAvailability = SshUpgradeAvailability.AVAILABLE,
        )
        assertFalse(defaultState.isRemoteUpgradeRemoved("node1"))
        assertFalse(defaultState.isRemoteUpgradeRemoved("node2"))
        assertTrue(defaultState.isPasswordAuth)

        // State with API token authentication
        val apiTokenState = UpdatesUiState(
            sshAvailability = SshUpgradeAvailability.API_TOKEN_AUTH,
            remoteUpgradeRemovedNodes = setOf("node1"),
        )
        assertTrue(apiTokenState.isRemoteUpgradeRemoved("node1"))
        assertFalse(apiTokenState.isRemoteUpgradeRemoved("node2"))
        assertFalse(apiTokenState.isPasswordAuth)
        assertEquals(SshUpgradeAvailability.API_TOKEN_AUTH, apiTokenState.sshAvailability)

        // State with no saved secret
        val noSecretState = UpdatesUiState(
            sshAvailability = SshUpgradeAvailability.NO_SAVED_SECRET,
            progress = mapOf(
                "node1" to NodeRefreshProgress(node = "node1", remoteUpgradeRemoved = true),
            ),
        )
        assertTrue(noSecretState.isRemoteUpgradeRemoved("node1"))
        assertFalse(noSecretState.isRemoteUpgradeRemoved("node2"))
        assertFalse(noSecretState.isPasswordAuth)
        assertEquals(SshUpgradeAvailability.NO_SAVED_SECRET, noSecretState.sshAvailability)
    }

    @Test
    fun sshUpgradeExecutor_hostKeyVerification_tofuAndMismatch() {
        val fakeKey1 = FakePublicKey("server-key-material-alpha".toByteArray())
        val fakeKey2 = FakePublicKey("server-key-material-beta".toByteArray())

        val storedKeys = mutableMapOf<String, String>()

        // 1. Initial connection stores TOFU pin
        val verifiedInitial = SshUpgradeExecutor.verifyHostKey(
            host = "192.0.2.50",
            key = fakeKey1,
            stored = storedKeys["192.0.2.50"],
            onStore = { h, k -> storedKeys[h] = k },
        )
        assertTrue(verifiedInitial)
        assertTrue(storedKeys.containsKey("192.0.2.50"))
        val pin1 = storedKeys["192.0.2.50"]!!
        assertTrue(pin1.startsWith("SHA256:"))

        // 2. Subsequent connection with same key succeeds
        val verifiedRepeat = SshUpgradeExecutor.verifyHostKey(
            host = "192.0.2.50",
            key = fakeKey1,
            stored = storedKeys["192.0.2.50"],
            onStore = { h, k -> storedKeys[h] = k },
        )
        assertTrue(verifiedRepeat)

        // 3. Mismatched key throws MITM exception
        var caughtMitm = false
        try {
            SshUpgradeExecutor.verifyHostKey(
                host = "192.0.2.50",
                key = fakeKey2,
                stored = storedKeys["192.0.2.50"],
                onStore = { h, k -> storedKeys[h] = k },
            )
        } catch (e: RuntimeException) {
            caughtMitm = true
            assertTrue(e.message?.contains("Host key changed — possible MITM attack detected!") == true)
        }
        assertTrue(caughtMitm)
    }

    @Test
    fun sshUpgradeExecutor_exitCodeMapping_successAndFailureWithTail() {
        // Exit code 0 -> success
        val successRes = SshUpgradeExecutor.mapExitStatus(
            exitStatus = 0,
            tailLines = listOf("Setting up openssl...", "Processing triggers...", "Done."),
        )
        assertTrue(successRes.isSuccess)
        assertEquals(0, successRes.getOrNull())

        // Exit code 100 with tail output -> failure with last lines
        val failRes = SshUpgradeExecutor.mapExitStatus(
            exitStatus = 100,
            tailLines = listOf(
                "Reading package lists...",
                "E: Sub-process /usr/bin/dpkg returned an error code (1)",
                "dpkg: error processing package broken-pkg (--configure)",
            ),
        )
        assertTrue(failRes.isFailure)
        val err = failRes.exceptionOrNull()
        assertTrue(err is PveException)
        assertTrue(err?.message?.contains("100") == true)
        assertTrue(err?.message?.contains("Sub-process /usr/bin/dpkg returned an error code") == true)
        assertTrue(err?.message?.contains("dpkg: error processing package broken-pkg") == true)

        // Empty tail fallback
        val emptyTailFail = SshUpgradeExecutor.mapExitStatus(exitStatus = 1, tailLines = emptyList())
        assertTrue(emptyTailFail.isFailure)
        assertEquals("Upgrade command exited with code 1", emptyTailFail.exceptionOrNull()?.message)
    }

    @Test
    fun isHttp403_detectsVarious403ExceptionsAndMessages() {
        val errorBody = "{\"data\":null}".toResponseBody("application/json".toMediaType())
        val http403 = HttpException(Response.error<Any>(403, errorBody))
        assertTrue(isHttp403(http403))

        val wrapped403 = PveException("HTTP 403: permission check failed", http403)
        assertTrue(isHttp403(wrapped403))

        val text403 = PveException("HTTP 403: forbidden")
        assertTrue(isHttp403(text403))

        val permFail = PveException("permission check failed (Sys.Modify on /nodes/pve)")
        assertTrue(isHttp403(permFail))

        assertFalse(isHttp403(PveException("Network error")))
        assertFalse(isHttp403(null))

        assertTrue(isHttp403Message("HTTP 403: forbidden"))
        assertTrue(isHttp403Message("permission check failed"))
        assertFalse(isHttp403Message("HTTP 500: server error"))
        assertFalse(isHttp403Message(null))

        assertEquals(
            "This account lacks package-management privileges on this node. Use an account with Sys.Modify, or run upgrades from the node shell.",
            PRIVILEGE_DENIED_COPY,
        )
    }
}

private class FakePublicKey(private val keyBytes: ByteArray) : java.security.PublicKey {
    override fun getAlgorithm(): String = "RSA"
    override fun getFormat(): String = "X.509"
    override fun getEncoded(): ByteArray = keyBytes
}
