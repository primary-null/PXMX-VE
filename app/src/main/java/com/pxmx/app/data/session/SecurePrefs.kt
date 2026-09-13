package com.pxmx.app.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * AES-256 encrypted SharedPreferences backed by Android Keystore.
 * Migrates one-shot from the legacy plaintext [LEGACY_PREFS_NAME] file.
 */
object SecurePrefs {
    const val LEGACY_PREFS_NAME = "proxmox_session"
    const val SECURE_PREFS_NAME = "proxmox_session_secure"

    fun open(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val secure = openSecurePrefs(context, masterKeyAlias)
        migrateFromLegacy(context, secure)
        return secure
    }

    /**
     * Opens [EncryptedSharedPreferences], recovering from known crypto / IO failures only.
     * If the failure is not a recognised KeyStore / crypto / IO cause (e.g. OOM), it is
     * rethrown so we do not silently wipe the user's encrypted store for unrelated errors.
     */
    @Suppress("DEPRECATION")
    private fun openSecurePrefs(context: Context, masterKeyAlias: String): SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                SECURE_PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // Only recover from known crypto / IO causes (KeyStore wipe, corrupted ciphertext).
            // Let unexpected errors propagate — they should not trigger a silent data wipe.
            val isCryptoError = e is java.security.GeneralSecurityException ||
                e is java.security.KeyStoreException ||
                e is java.io.IOException ||
                e.cause is java.security.GeneralSecurityException ||
                e.cause is java.security.KeyStoreException
            if (!isCryptoError) throw e

            // Leftover ciphertext from a wiped master key. Drop the file and start fresh.
            context.deleteSharedPreferences(SECURE_PREFS_NAME)
            runCatching {
                val prefsDir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
                java.io.File(prefsDir, "$SECURE_PREFS_NAME.xml").delete()
                java.io.File(prefsDir, "$SECURE_PREFS_NAME.xml.bak").delete()
            }
            EncryptedSharedPreferences.create(
                SECURE_PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }


    private fun migrateFromLegacy(context: Context, secure: SharedPreferences) {
        if (secure.contains(MIGRATION_DONE_KEY)) return

        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val all = legacy.all
        if (all.isEmpty()) {
            secure.edit { putBoolean(MIGRATION_DONE_KEY, true) }
            return
        }

        secure.edit {
            for ((key, value) in all) {
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        putStringSet(key, value as Set<String>)
                    }
                }
            }
            putBoolean(MIGRATION_DONE_KEY, true)
        }

        // Wipe plaintext file so secrets cannot be recovered from disk.
        legacy.edit().clear().commit()
        runCatching {
            context.deleteSharedPreferences(LEGACY_PREFS_NAME)
        }
    }

    private const val MIGRATION_DONE_KEY = "_secure_migration_v1"
}
