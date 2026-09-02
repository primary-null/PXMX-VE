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

        @Suppress("DEPRECATION")
        val secure = runCatching {
            EncryptedSharedPreferences.create(
                SECURE_PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            // Leftover ciphertext from a wiped master key. Drop the file and start fresh.
            context.deleteSharedPreferences(SECURE_PREFS_NAME)
            runCatching {
                val prefsDir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
                java.io.File(prefsDir, "$SECURE_PREFS_NAME.xml").delete()
                java.io.File(prefsDir, "$SECURE_PREFS_NAME.xml.bak").delete()
            }
            @Suppress("DEPRECATION")
            EncryptedSharedPreferences.create(
                SECURE_PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        migrateFromLegacy(context, secure)
        return secure
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
