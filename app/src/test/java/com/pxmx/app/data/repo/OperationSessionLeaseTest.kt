package com.pxmx.app.data.repo

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.*
import com.pxmx.app.data.model.*
import com.pxmx.app.data.session.SessionStore
import com.pxmx.app.data.ssh.SshCredentialPolicy
import com.pxmx.app.data.ssh.SshUpgradeExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OperationSessionLeaseTest {
    private val demo = DemoApi()
    private val store = SessionStore(injectedPrefs = FakeSharedPreferences())
    private val config = ServerConfig(host = "entry.example", username = "root", password = "synthetic-password-a")
    private fun establish(): SessionState {
        store.saveProfileFromLogin(config, saveCredentials = true)
        return SessionState(config.copy(password = ""), ticket = "original-ticket", username = "root@pam").also(store::setSession)
    }
    private fun provider(api: ProxmoxApi) = object : ProxmoxApiProvider {
        override fun apiFor(config: ServerConfig) = api
        override fun apiForProbe(config: ServerConfig) = ProbeApi(api)
        override fun clear() {}
    }
    private fun client(api: ProxmoxApi) = PveClient(ContextWrapper(null), store, provider(api))

    @Test fun sameProfileReloginDuringMetadataCannotStartSsh() = runBlocking {
        val original = establish()
        var contacted = false
        val api = object : ProxmoxApi by demo {
            override suspend fun clusterStatus(): PveResponse<List<Map<String, Any>>> {
                store.setSession(original.copy(ticket = "replacement-ticket"))
                return demo.clusterStatus()
            }
        }
        val executor = object : SshUpgradeExecutor({ null }, { _, _ -> }) {
            override suspend fun executeUpgrade(host: String, port: Int, username: String, password: String, command: String, onOutputLine: (String) -> Unit): Result<Int> {
                contacted = true
                return Result.success(0)
            }
        }
        assertTrue(UpdateRepository(store, client(api), { listOf("beta") }, executor).sshUpgrade("beta").isFailure)
        assertFalse("Old generation must never launch root SSH", contacted)
    }

    @Test fun backupCannotAdoptNewGenerationBetweenResolutionAndMutation() = runBlocking {
        val original = establish()
        var backups = 0
        val api = object : ProxmoxApi by demo {
            override suspend fun createBackup(node: String, vmid: Long, storage: String, mode: String?, compress: String?, remove: Int?, notesTemplate: String?): PveResponse<String> {
                backups++
                throw PveException("probe stops before any transport")
            }
        }
        val repository = ProxmoxRepository(ContextWrapper(null), store, provider(api))
        repository.backupToDevice("beta", "qemu", 100, "local") { message ->
            if (message == "Backing up on server...") store.setSession(original.copy(ticket = "replacement-ticket"))
        }
        assertEquals("A later stage must not take a new session lease", 0, backups)
    }

    @Test fun savedProfileSelectionCannotReplaceActiveSshCredentials() {
        val original = establish()
        val activeId = store.snapshot()!!.profileId
        store.saveProfileFromLogin(config.copy(password = "synthetic-password-b"), saveCredentials = true, forceNewProfile = true)
        assertEquals("Use profile latched at login, not last selected profile", activeId,
            SshCredentialPolicy.rootProfile(store, original)?.id)
    }
}
