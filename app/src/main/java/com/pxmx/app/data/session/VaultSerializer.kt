package com.pxmx.app.data.session

import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import java.io.InputStream
import java.io.OutputStream

/**
 * DataStore Serializer that encrypts and decrypts [VaultPayload] via Tink AEAD.
 *
 * Read/write run on DataStore's own actor. Do not hop [Dispatchers.IO] here —
 * that can race with DataStore closing the stream after writeTo returns.
 */
class VaultSerializer(private val aead: Aead) : Serializer<VaultPayload> {
    override val defaultValue: VaultPayload = VaultPayload()

    override suspend fun readFrom(input: InputStream): VaultPayload {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return VaultCodec.decode(bytes, aead)
    }

    override suspend fun writeTo(t: VaultPayload, output: OutputStream) {
        output.write(VaultCodec.encode(t, aead))
        output.flush()
    }
}
