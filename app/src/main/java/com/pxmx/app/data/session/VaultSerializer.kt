package com.pxmx.app.data.session

import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * DataStore Serializer that encrypts and decrypts [VaultPayload] via Tink AEAD.
 */
class VaultSerializer(private val aead: Aead) : Serializer<VaultPayload> {
    override val defaultValue: VaultPayload = VaultPayload()

    override suspend fun readFrom(input: InputStream): VaultPayload {
        val bytes = withContext(Dispatchers.IO) { input.readBytes() }
        if (bytes.isEmpty()) return defaultValue
        return VaultCodec.decode(bytes, aead)
    }

    override suspend fun writeTo(t: VaultPayload, output: OutputStream) {
        val bytes = VaultCodec.encode(t, aead)
        withContext(Dispatchers.IO) { output.write(bytes) }
    }
}
