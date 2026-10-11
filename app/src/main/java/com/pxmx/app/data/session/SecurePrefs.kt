package com.pxmx.app.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStoreFactory
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * AES-256 encrypted storage backed by Google Tink and Android Keystore.
 * Exposes a synchronous [SharedPreferences] facade with in-memory caching.
 * Migrates one-shot from legacy EncryptedSharedPreferences (ESP) and plaintext files.
 */
object SecurePrefs {
    const val LEGACY_PREFS_NAME = "proxmox_session"
    const val SECURE_PREFS_NAME = "proxmox_session_secure"
    const val VAULT_FILE_NAME = "pxmx_vault.json"
    const val TINK_PREFS_NAME = "pxmx_vault_keyset"
    const val TINK_KEYSET_KEY = "pxmx_vault_keyset_key"
    const val KEYSTORE_MASTER_ALIAS = "pxmx_vault_master"
    const val MASTER_KEY_URI = "android-keystore://pxmx_vault_master"
    const val VAULT_MIGRATION_MARKER = "_vault_migration_v1"
    const val LEGACY_MIGRATION_KEY = "_secure_migration_v1"

    @Volatile
    private var cachedInstance: SharedPreferences? = null
    private var vaultScope: kotlinx.coroutines.CoroutineScope? = null
    private val instanceLock = Any()

    fun getVaultFile(context: Context): File {
        val dir = context.filesDir ?: File(context.applicationInfo?.dataDir ?: ".", "files")
        return File(dir, "datastore/$VAULT_FILE_NAME")
    }

    fun open(context: Context, testAead: Aead? = null): SharedPreferences {
        if (testAead != null) {
            return createVaultPrefs(context, testAead)
        }
        cachedInstance?.let { return it }
        return synchronized(instanceLock) {
            cachedInstance ?: createVaultPrefs(context, null).also { cachedInstance = it }
        }
    }

    fun resetInstance() {
        synchronized(instanceLock) {
            vaultScope?.let {
                it.cancel()
                vaultScope = null
            }
            cachedInstance = null
        }
    }

    private fun createVaultPrefs(context: Context, injectedAead: Aead?): SharedPreferences {
        val aead = injectedAead ?: getOrCreateAead(context)
        val vaultFile = getVaultFile(context)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        synchronized(instanceLock) {
            vaultScope?.cancel()
            vaultScope = scope
        }
        val dataStore = DataStoreFactory.create(
            serializer = VaultSerializer(aead),
            scope = scope,
            produceFile = { vaultFile },
        )

        val initialPayload = try {
            runBlocking { dataStore.data.first() }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            if (!isCryptoError(e)) throw e

            // Recognised crypto or IO failure on vault ciphertext.
            // Delete only the vault payload file, start empty.
            deleteVaultPayloadFile(context)
            val empty = VaultPayload()
            runCatching {
                runBlocking { dataStore.updateData { empty } }
            }
            empty
        }

        val vault = VaultPreferences(dataStore, initialPayload)
        performMigration(context, vault)
        return vault
    }

    private fun getOrCreateAead(context: Context): Aead {
        AeadConfig.register()
        return try {
            createProductionAead(context)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            if (!isCryptoError(e)) throw e

            // Recognised crypto or IO failure in keyset.
            // Delete tink keyset, master key, and vault payload, then retry.
            deleteKeysetAndMaster(context)
            deleteVaultPayloadFile(context)
            createProductionAead(context)
        }
    }

    @Suppress("DEPRECATION")
    private fun createProductionAead(context: Context): Aead {
        val manager = AndroidKeysetManager.Builder()
            .withSharedPref(context, TINK_KEYSET_KEY, TINK_PREFS_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
        return manager.keysetHandle.getPrimitive(Aead::class.java)
    }

    private fun deleteKeysetAndMaster(context: Context) {
        runCatching { context.deleteSharedPreferences(TINK_PREFS_NAME) }
        runCatching {
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            File(prefsDir, "$TINK_PREFS_NAME.xml").delete()
            File(prefsDir, "$TINK_PREFS_NAME.xml.bak").delete()
        }
        runCatching {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(KEYSTORE_MASTER_ALIAS)
        }
    }

    private fun deleteVaultPayloadFile(context: Context) {
        runCatching {
            val vaultFile = getVaultFile(context)
            if (vaultFile.exists()) vaultFile.delete()
        }
    }

    private fun performMigration(context: Context, vault: SharedPreferences) {
        if (!vault.contains(VAULT_MIGRATION_MARKER)) {
            val espFile = File(context.applicationInfo.dataDir, "shared_prefs/$SECURE_PREFS_NAME.xml")
            if (!espFile.exists()) {
                // ESP is absent. Run legacy migration if legacy file exists.
                migrateFromLegacy(context, vault)
                vault.edit().putBoolean(VAULT_MIGRATION_MARKER, true).commit()
            } else {
                // ESP exists.
                val esp = openSecurePrefs(context)
                val espAll = esp.all
                if (espAll.isEmpty()) {
                    migrateFromLegacy(context, vault)
                    vault.edit().putBoolean(VAULT_MIGRATION_MARKER, true).commit()
                } else {
                    val editor = vault.edit()
                    copyAll(esp, editor)
                    editor.putBoolean(VAULT_MIGRATION_MARKER, true)
                    editor.putBoolean(LEGACY_MIGRATION_KEY, true)
                    editor.commit()
                }
                context.deleteSharedPreferences(SECURE_PREFS_NAME)
                runCatching {
                    espFile.delete()
                    File(context.applicationInfo.dataDir, "shared_prefs/$SECURE_PREFS_NAME.xml.bak").delete()
                }
            }
        } else {
            // Already marked migrated; check for leftover plaintext file.
            if (!vault.contains(LEGACY_MIGRATION_KEY)) {
                migrateFromLegacy(context, vault)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun openSecurePrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return try {
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            if (!isCryptoError(e)) throw e

            context.deleteSharedPreferences(SECURE_PREFS_NAME)
            runCatching {
                val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                File(prefsDir, "$SECURE_PREFS_NAME.xml").delete()
                File(prefsDir, "$SECURE_PREFS_NAME.xml.bak").delete()
            }
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }

    private fun migrateFromLegacy(context: Context, secure: SharedPreferences) {
        if (secure.contains(LEGACY_MIGRATION_KEY)) return

        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val all = legacy.all
        if (all.isEmpty()) {
            secure.edit().putBoolean(LEGACY_MIGRATION_KEY, true).commit()
            return
        }

        val editor = secure.edit()
        copyAll(legacy, editor)
        editor.putBoolean(LEGACY_MIGRATION_KEY, true)
        editor.commit()

        // Wipe plaintext file so secrets cannot be recovered from disk.
        legacy.edit().clear().commit()
        runCatching {
            context.deleteSharedPreferences(LEGACY_PREFS_NAME)
        }
    }

    fun copyAll(source: SharedPreferences, target: SharedPreferences.Editor) {
        for ((key, value) in source.all) {
            when (value) {
                is String -> target.putString(key, value)
                is Boolean -> target.putBoolean(key, value)
                is Int -> target.putInt(key, value)
                is Long -> target.putLong(key, value)
                is Float -> target.putFloat(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    target.putStringSet(key, (value as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet())
                }
            }
        }
    }

    fun isCryptoError(e: Throwable?): Boolean {
        var cur = e
        while (cur != null) {
            if (cur is java.security.GeneralSecurityException ||
                cur is java.security.KeyStoreException ||
                cur is java.io.IOException
            ) {
                return true
            }
            cur = cur.cause
        }
        return false
    }
}
