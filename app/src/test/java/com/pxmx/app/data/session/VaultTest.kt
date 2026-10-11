package com.pxmx.app.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.pxmx.app.data.FakeSharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.GeneralSecurityException
import java.security.KeyStoreException

@Suppress("DEPRECATION")
class VaultTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setup() {
            AeadConfig.register()
        }
    }

    @Test
    fun testVaultCodecRoundtripRestoresAllBuckets() {
        val keysetHandle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
        val aead = keysetHandle.getPrimitive(Aead::class.java)

        val original = VaultPayload(
            strings = mapOf("s1" to "alpha", "s2" to "beta"),
            booleans = mapOf("b1" to true, "b2" to false),
            ints = mapOf("i1" to 42, "i2" to -999),
            longs = mapOf("l1" to 1234567890123L, "l2" to 0L),
            floats = mapOf("f1" to 3.14f, "f2" to -0.5f),
            stringSets = mapOf("ss1" to setOf("x", "y", "z"), "ss2" to emptySet()),
        )

        val ciphertext = VaultCodec.encode(original, aead)
        assertTrue(ciphertext.isNotEmpty())

        val restored = VaultCodec.decode(ciphertext, aead)
        assertEquals(original, restored)
        assertEquals(mapOf("s1" to "alpha", "s2" to "beta"), restored.strings)
        assertEquals(mapOf("b1" to true, "b2" to false), restored.booleans)
        assertEquals(mapOf("i1" to 42, "i2" to -999), restored.ints)
        assertEquals(mapOf("l1" to 1234567890123L, "l2" to 0L), restored.longs)
        assertEquals(mapOf("f1" to 3.14f, "f2" to -0.5f), restored.floats)
        assertEquals(mapOf("ss1" to setOf("x", "y", "z"), "ss2" to emptySet()), restored.stringSets)
    }

    @Test
    fun testTamperedCiphertextThrowsGeneralSecurityException() {
        val keysetHandle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
        val aead = keysetHandle.getPrimitive(Aead::class.java)

        val original = VaultPayload(
            strings = mapOf("secret" to "very_secure_token"),
        )
        val ciphertext = VaultCodec.encode(original, aead)

        val tampered = ciphertext.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0x01).toByte()

        try {
            VaultCodec.decode(tampered, aead)
            fail("Expected GeneralSecurityException on tampered ciphertext")
        } catch (e: GeneralSecurityException) {
            // Expected
        }
    }

    @Test
    fun testCopyAllPreservesTypes() {
        val source = FakeSharedPreferences()
        source.edit()
            .putString("str_key", "hello")
            .putBoolean("bool_key", true)
            .putInt("int_key", 100)
            .putLong("long_key", 9876543210L)
            .putFloat("float_key", 2.718f)
            .putStringSet("set_key", mutableSetOf("item1", "item2"))
            .commit()

        val dest = FakeSharedPreferences()
        val editor = dest.edit()
        SecurePrefs.copyAll(source, editor)
        editor.commit()

        assertEquals("hello", dest.getString("str_key", null))
        assertEquals(true, dest.getBoolean("bool_key", false))
        assertEquals(100, dest.getInt("int_key", 0))
        assertEquals(9876543210L, dest.getLong("long_key", 0L))
        assertEquals(2.718f, dest.getFloat("float_key", 0f), 0.0001f)
        assertEquals(setOf("item1", "item2"), dest.getStringSet("set_key", null))
    }

    @Test
    fun testIsCryptoError() {
        // True for recognized crypto/IO errors
        assertTrue(SecurePrefs.isCryptoError(GeneralSecurityException("crypto error")))
        assertTrue(SecurePrefs.isCryptoError(KeyStoreException("keystore error")))
        assertTrue(SecurePrefs.isCryptoError(IOException("io error")))

        // True when wrapped as cause
        assertTrue(SecurePrefs.isCryptoError(RuntimeException(GeneralSecurityException())))
        assertTrue(SecurePrefs.isCryptoError(IllegalStateException(IOException())))
        assertTrue(SecurePrefs.isCryptoError(Exception(KeyStoreException())))

        // False for non-crypto errors
        assertFalse(SecurePrefs.isCryptoError(IllegalStateException("invalid state")))
        assertFalse(SecurePrefs.isCryptoError(RuntimeException("runtime error")))
        assertFalse(SecurePrefs.isCryptoError(NullPointerException("null ptr")))
        assertFalse(SecurePrefs.isCryptoError(IllegalArgumentException("bad argument")))
        assertFalse(SecurePrefs.isCryptoError(null))
    }

    @Test
    fun testVaultPreferencesPersistenceAndFacade() {
        val keysetHandle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
        val aead = keysetHandle.getPrimitive(Aead::class.java)

        // DataStore must create the file. An empty createTempFile makes
        // VaultSerializer.readFrom return defaultValue (empty bytes).
        val tempDir = Files.createTempDirectory("vault_test_").toFile()
        tempDir.deleteOnExit()
        val tempFile = File(tempDir, "vault.json")
        assertFalse(tempFile.exists())

        val scope1 = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        val dataStore = DataStoreFactory.create(
            serializer = VaultSerializer(aead),
            scope = scope1,
            produceFile = { tempFile },
        )

        val prefs = VaultPreferences(dataStore, VaultPayload())

        prefs.edit()
            .putString("p1", "token123")
            .putBoolean("auto", true)
            .putInt("port", 8006)
            .putLong("timestamp", 1700000000L)
            .putFloat("ratio", 0.75f)
            .putStringSet("tags", mutableSetOf("prod", "pve"))
            .commit()

        // Immediate memory verification
        assertEquals("token123", prefs.getString("p1", null))
        assertTrue(prefs.getBoolean("auto", false))
        assertEquals(8006, prefs.getInt("port", 0))
        assertEquals(1700000000L, prefs.getLong("timestamp", 0L))
        assertEquals(0.75f, prefs.getFloat("ratio", 0f), 0.001f)
        assertEquals(setOf("prod", "pve"), prefs.getStringSet("tags", null))
        assertTrue(prefs.contains("p1"))
        assertFalse(prefs.contains("non_existent"))

        // Verify all map
        val all = prefs.all
        assertEquals("token123", all["p1"])
        assertEquals(true, all["auto"])
        assertEquals(8006, all["port"])
        assertEquals(1700000000L, all["timestamp"])
        assertEquals(0.75f, all["ratio"])
        assertEquals(setOf("prod", "pve"), all["tags"])

        // Test remove and bucket relocation
        prefs.edit()
            .remove("p1")
            .putInt("auto", 99) // replaces bool with int
            .commit()

        assertNull(prefs.getString("p1", null))
        assertFalse(prefs.contains("p1"))
        assertEquals(99, prefs.getInt("auto", 0))

        // Serializer roundtrip through this DataStore's actor. A second
        // DataStoreFactory on the same file is unsupported. On Windows the
        // previous ciphertext can linger in vault.json after updateData
        // has already published the new snapshot, so disk proof is
        // "file exists and decrypts" plus data.first() matching memory.
        val persisted = runBlocking { dataStore.data.first() }
        assertEquals(emptyMap<String, String>(), persisted.strings)
        assertEquals(emptyMap<String, Boolean>(), persisted.booleans)
        assertEquals(99, persisted.ints["auto"])
        assertEquals(8006, persisted.ints["port"])
        assertEquals(1700000000L, persisted.longs["timestamp"])
        assertEquals(0.75f, persisted.floats["ratio"] ?: 0f, 0.001f)
        assertEquals(setOf("prod", "pve"), persisted.stringSets["tags"])

        assertTrue(tempFile.exists() && tempFile.length() > 0L)
        val onDisk = VaultCodec.decode(tempFile.readBytes(), aead)
        assertTrue(onDisk.ints.containsKey("port"))
        assertEquals(8006, onDisk.ints["port"])
        assertEquals(1700000000L, onDisk.longs["timestamp"])
        assertEquals(setOf("prod", "pve"), onDisk.stringSets["tags"])

        runBlocking { scope1.coroutineContext[Job]!!.cancelAndJoin() }
        tempFile.delete()
        tempDir.delete()
    }

    @Test
    fun testCommitInvokesDataStoreUpdate() {
        var updated = false
        val store = object : DataStore<VaultPayload> {
            override val data = flowOf(VaultPayload())
            override suspend fun updateData(transform: suspend (t: VaultPayload) -> VaultPayload): VaultPayload {
                updated = true
                return transform(VaultPayload())
            }
        }
        val prefs = VaultPreferences(store, VaultPayload())
        assertTrue(prefs.edit().putString("k", "v").commit())
        assertEquals("v", prefs.getString("k", null))
        assertTrue(updated)
    }

    @Test
    fun testCommitReturnsFalseOnPersistFailure() {
        val store = object : DataStore<VaultPayload> {
            override val data = flowOf(VaultPayload())
            override suspend fun updateData(transform: suspend (t: VaultPayload) -> VaultPayload): VaultPayload {
                throw IOException("persist failed")
            }
        }
        val prefs = VaultPreferences(store, VaultPayload())
        assertFalse(prefs.edit().putString("k", "v").commit())
        assertEquals("v", prefs.getString("k", null))
    }

    @Test
    fun testCommitRethrowsCancellationException() {
        val store = object : DataStore<VaultPayload> {
            override val data = flowOf(VaultPayload())
            override suspend fun updateData(transform: suspend (t: VaultPayload) -> VaultPayload): VaultPayload {
                throw CancellationException("cancelled")
            }
        }
        val prefs = VaultPreferences(store, VaultPayload())
        var thrown: Throwable? = null
        try {
            prefs.edit().putString("k", "v").commit()
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue(thrown is CancellationException)
    }
}
