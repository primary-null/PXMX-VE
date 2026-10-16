package com.pxmx.app.data.repo

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.NodeStorageEntry
import com.pxmx.app.data.model.PveResponse
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageRepositoryTest {

    @Test
    fun sanitizeIsoFilename_acceptsValidFilename() {
        assertEquals("alpine-virt.iso", StorageRepository.sanitizeIsoFilename("alpine-virt.iso"))
        assertEquals("alpine-virt.iso", StorageRepository.sanitizeIsoFilename("  alpine-virt.iso  "))
        assertEquals("debian-12.0.0-amd64-netinst.ISO", StorageRepository.sanitizeIsoFilename("debian-12.0.0-amd64-netinst.ISO"))
        assertEquals("Rocky-9.3-x86_64-minimal.iso", StorageRepository.sanitizeIsoFilename("Rocky-9.3-x86_64-minimal.iso"))
    }

    @Test
    fun sanitizeIsoFilename_rejectsTraversalSequences() {
        val ex1 = assertThrows(PveException::class.java) {
            StorageRepository.sanitizeIsoFilename("../x.iso")
        }
        assertTrue(ex1.message?.contains("contains path separators or traversal sequences") == true)

        val ex2 = assertThrows(PveException::class.java) {
            StorageRepository.sanitizeIsoFilename("a/b.iso")
        }
        assertTrue(ex2.message?.contains("contains path separators or traversal sequences") == true)

        val ex3 = assertThrows(PveException::class.java) {
            StorageRepository.sanitizeIsoFilename("a\\b.iso")
        }
        assertTrue(ex3.message?.contains("contains path separators or traversal sequences") == true)
    }

    @Test
    fun sanitizeIsoFilename_rejectsMissingIsoExtension() {
        val ex = assertThrows(PveException::class.java) {
            StorageRepository.sanitizeIsoFilename("nope.txt")
        }
        assertTrue(ex.message?.contains("missing required .iso extension") == true)
    }

    @Test
    fun sanitizeIsoFilename_rejectsEmptyBaseFilename() {
        val ex = assertThrows(PveException::class.java) {
            StorageRepository.sanitizeIsoFilename(".iso")
        }
        assertTrue(ex.message?.contains("filename base is empty") == true)
    }

    @Test
    fun sanitizeIsoFilename_rejectsDisallowedCharacters() {
        val ex = assertThrows(PveException::class.java) {
            StorageRepository.sanitizeIsoFilename("test file;rm.iso")
        }
        assertTrue(ex.message?.contains("contains disallowed characters") == true)
    }

    // -------------------------------------------------------------------------
    // Backup filter & createBackup refusal tests
    // -------------------------------------------------------------------------

    @Test
    fun hasBackupContent_identifiesBackupContentTokens() {
        assertTrue(StorageRepository.hasBackupContent("backup"))
        assertTrue(StorageRepository.hasBackupContent("backup,iso"))
        assertTrue(StorageRepository.hasBackupContent("vztmpl,iso,backup"))
        assertTrue(StorageRepository.hasBackupContent("backup,vztmpl,import,images,iso"))
        assertTrue(StorageRepository.hasBackupContent("  backup  ,  iso  "))

        assertFalse(StorageRepository.hasBackupContent("images,rootdir"))
        assertFalse(StorageRepository.hasBackupContent("rootdir,images"))
        assertFalse(StorageRepository.hasBackupContent("import"))
        assertFalse(StorageRepository.hasBackupContent("iso"))
        assertFalse(StorageRepository.hasBackupContent("not-backup"))
        assertFalse(StorageRepository.hasBackupContent(""))
        assertFalse(StorageRepository.hasBackupContent(null))
    }

    @Test
    fun isBackupStorage_filtersAppropriately() {
        // Valid backup stores
        assertTrue(
            StorageRepository.isBackupStorage(
                NodeStorageEntry(storage = "local", content = "backup,vztmpl,import,images,iso", active = 1, enabled = 1)
            )
        )
        assertTrue(
            StorageRepository.isBackupStorage(
                NodeStorageEntry(storage = "iso-store", content = "vztmpl,iso,backup", active = 1, enabled = 1)
            )
        )

        // Images/rootdir stores excluded
        assertFalse(
            StorageRepository.isBackupStorage(
                NodeStorageEntry(storage = "vm-disks", content = "images,rootdir", active = 1, enabled = 1)
            )
        )
        assertFalse(
            StorageRepository.isBackupStorage(
                NodeStorageEntry(storage = "local-lvm", content = "rootdir,images", active = 1, enabled = 1)
            )
        )
        assertFalse(
            StorageRepository.isBackupStorage(
                NodeStorageEntry(storage = "iso-import", content = "import", active = 1, enabled = 1)
            )
        )

        // Inactive / disabled excluded
        assertFalse(
            StorageRepository.isBackupStorage(
                NodeStorageEntry(storage = "local", content = "backup", active = 0, enabled = 1)
            )
        )
        assertFalse(
            StorageRepository.isBackupStorage(
                NodeStorageEntry(storage = "local", content = "backup", active = 1, enabled = 0)
            )
        )

        // Missing storage name excluded
        assertFalse(
            StorageRepository.isBackupStorage(
                NodeStorageEntry(storage = null, content = "backup", active = 1, enabled = 1)
            )
        )
        assertFalse(
            StorageRepository.isBackupStorage(
                NodeStorageEntry(storage = "", content = "backup", active = 1, enabled = 1)
            )
        )
    }

    @Test
    fun listBackupStorageNames_returnsOnlyBackupCapableStorages() = runBlocking {
        val demo = DemoApi()
        val context = ContextWrapper(null)
        val fakePrefs = FakeSharedPreferences()
        val store = SessionStore(injectedPrefs = fakePrefs)
        store.setSession(SessionState(ServerConfig(host = "192.168.1.100", port = 8006), ticket = "ticket", csrf = "csrf"))
        val client = PveClient(context, store, object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig): ProxmoxApi = object : ProxmoxApi by demo {
                override suspend fun nodeStorage(node: String) = PveResponse(data = listOf(
                    NodeStorageEntry(storage = "local", content = "backup,vztmpl,import,images,iso", active = 1, enabled = 1),
                    NodeStorageEntry(storage = "iso-store", content = "vztmpl,iso,backup", active = 1, enabled = 1),
                    NodeStorageEntry(storage = "local-lvm", content = "rootdir,images", active = 1, enabled = 1),
                    NodeStorageEntry(storage = "iso-import", content = "import", active = 1, enabled = 1),
                    NodeStorageEntry(storage = "vm-disks", content = "images,rootdir", active = 1, enabled = 1),
                ))
            }
            override fun apiForProbe(config: ServerConfig): ProbeApi = ProbeApi(demo)
            override fun clear() {}
        })
        val repo = StorageRepository(context, store, client, { _, _ -> Result.success(com.pxmx.app.data.model.TaskStatus(status = "stopped", exitstatus = "OK")) })

        val storages = repo.listBackupStorageNames("node1").getOrThrow()
        assertEquals(listOf("local", "iso-store"), storages)
    }

    @Test
    fun createBackup_refusesNonBackupStorage_beforePost() = runBlocking {
        val demo = DemoApi()
        val context = ContextWrapper(null)
        val fakePrefs = FakeSharedPreferences()
        val store = SessionStore(injectedPrefs = fakePrefs)
        store.setSession(SessionState(ServerConfig(host = "192.168.1.100", port = 8006), ticket = "ticket", csrf = "csrf"))
        var createBackupCalled = false
        val client = PveClient(context, store, object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig): ProxmoxApi = object : ProxmoxApi by demo {
                override suspend fun nodeStorage(node: String) = PveResponse(data = listOf(
                    NodeStorageEntry(storage = "local", content = "backup,iso", active = 1, enabled = 1),
                    NodeStorageEntry(storage = "vm-disks", content = "images,rootdir", active = 1, enabled = 1),
                ))
                override suspend fun createBackup(
                    node: String, vmid: Long, storage: String,
                    mode: String?, compress: String?, remove: Int?, notesTemplate: String?
                ): PveResponse<String> {
                    createBackupCalled = true
                    return PveResponse(data = "UPID:node1:00001:00001:66D00001:vzdump:100:root@pam:")
                }
            }
            override fun apiForProbe(config: ServerConfig): ProbeApi = ProbeApi(demo)
            override fun clear() {}
        })
        val repo = StorageRepository(context, store, client, { _, _ -> Result.success(com.pxmx.app.data.model.TaskStatus(status = "stopped", exitstatus = "OK")) })

        val result = repo.createBackup("node1", 100, "vm-disks")
        assertTrue(result.isFailure)
        assertFalse("POST createBackup must never be sent to images/rootdir storage", createBackupCalled)
        val ex = result.exceptionOrNull()
        assertTrue(ex is PveException)
        assertTrue(ex?.message?.contains("does not allow backups") == true)
    }

    @Test
    fun createBackup_allowsBackupStorage_andPosts() = runBlocking {
        val demo = DemoApi()
        val context = ContextWrapper(null)
        val fakePrefs = FakeSharedPreferences()
        val store = SessionStore(injectedPrefs = fakePrefs)
        store.setSession(SessionState(ServerConfig(host = "192.168.1.100", port = 8006), ticket = "ticket", csrf = "csrf"))
        var createBackupCalled = false
        val client = PveClient(context, store, object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig): ProxmoxApi = object : ProxmoxApi by demo {
                override suspend fun nodeStorage(node: String) = PveResponse(data = listOf(
                    NodeStorageEntry(storage = "local", content = "backup,iso", active = 1, enabled = 1),
                    NodeStorageEntry(storage = "vm-disks", content = "images,rootdir", active = 1, enabled = 1),
                ))
                override suspend fun createBackup(
                    node: String, vmid: Long, storage: String,
                    mode: String?, compress: String?, remove: Int?, notesTemplate: String?
                ): PveResponse<String> {
                    createBackupCalled = true
                    return PveResponse(data = "UPID:node1:00001:00001:66D00001:vzdump:100:root@pam:")
                }
            }
            override fun apiForProbe(config: ServerConfig): ProbeApi = ProbeApi(demo)
            override fun clear() {}
        })
        val repo = StorageRepository(context, store, client, { _, _ -> Result.success(com.pxmx.app.data.model.TaskStatus(status = "stopped", exitstatus = "OK")) })

        val result = repo.createBackup("node1", 100, "local")
        assertTrue(result.isSuccess)
        assertTrue(createBackupCalled)
        assertEquals("UPID:node1:00001:00001:66D00001:vzdump:100:root@pam:", result.getOrNull())
    }
}
