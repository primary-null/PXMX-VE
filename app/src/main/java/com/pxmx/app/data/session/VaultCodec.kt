package com.pxmx.app.data.session

import com.google.crypto.tink.Aead
import com.pxmx.app.data.api.AppJson

/**
 * Encrypts and decrypts [VaultPayload] using Tink AEAD.
 */
object VaultCodec {
    private val ASSOCIATED_DATA = "pxmx_vault_v1".toByteArray(Charsets.UTF_8)

    fun encode(payload: VaultPayload, aead: Aead): ByteArray {
        val json = AppJson.encodeToString(VaultPayload.serializer(), payload)
        return aead.encrypt(json.toByteArray(Charsets.UTF_8), ASSOCIATED_DATA)
    }

    fun decode(ciphertext: ByteArray, aead: Aead): VaultPayload {
        if (ciphertext.isEmpty()) return VaultPayload()
        val plaintext = aead.decrypt(ciphertext, ASSOCIATED_DATA)
        return AppJson.decodeFromString(VaultPayload.serializer(), plaintext.toString(Charsets.UTF_8))
    }
}
