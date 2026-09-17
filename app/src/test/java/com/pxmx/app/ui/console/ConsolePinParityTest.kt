package com.pxmx.app.ui.console

import com.pxmx.app.data.api.CertUtils
import com.pxmx.app.data.api.TofuTrustManager
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Parity guard: binds both production trust paths — [createConsoleClient] (ConsoleTls.kt)
 * and [TofuTrustManager.checkServerTrusted] (TlsHelpers.kt) — against a real keytool-generated
 * certificate from [LocalConsoleTlsServer] using an independent reference SHA-256 pin calculation.
 *
 * This confirms that both production trust paths honor the canonical colon-hex certificate pin
 * without divergence in fingerprint computation, normalization, or certificate evaluation.
 */
class ConsolePinParityTest {

    /**
     * Reference implementation: raw SHA-256 over cert.encoded formatted as colon-separated
     * uppercase hex. This mirrors what CertUtils.computeSha256Fingerprint does, written
     * independently so any accidental divergence in production is caught.
     */
    private fun referenceSha256Fingerprint(cert: java.security.cert.X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    @Test
    fun `both Console client and TofuTrustManager accept matching independent reference pin`() {
        LocalConsoleTlsServer("localhost").use { server ->
            val cert = server.certificate

            // Independent reference calculation used as the expected pin value.
            // Do not feed CertUtils.computeSha256Fingerprint into createConsoleClient
            // for this test's expected pin (that would hide a ConsoleTls split).
            val referenceFp = referenceSha256Fingerprint(cert)

            // 1. Exercise production Console TLS client path with the reference pin.
            val consoleClient = createConsoleClient(
                allowedHost = "localhost",
                trustSelfSigned = true,
                expectedCertPin = referenceFp,
            )
            consoleClient.newCall(Request.Builder().url(server.url).build()).execute().use { response ->
                assertEquals("OK", response.body!!.string())
            }

            // 2. Exercise production TOFU TrustManager path with the same reference pin.
            val tofuTrustManager = TofuTrustManager(
                host = "localhost",
                trustSelfSigned = true,
                pinLookup = { referenceFp },
            )
            // checkServerTrusted must accept the presented certificate without throwing.
            tofuTrustManager.checkServerTrusted(arrayOf(cert), "RSA")

            // Extra check: CertUtils matches the independent reference SHA-256 calculation.
            val certUtilsFp = CertUtils.computeSha256Fingerprint(cert)
            assertEquals(
                "CertUtils.computeSha256Fingerprint must equal raw SHA-256(cert.encoded) in colon-hex",
                referenceFp,
                certUtilsFp,
            )
            assertEquals(
                "Fingerprint must be 95 chars (64 hex + 31 colons) for a SHA-256 digest",
                95,
                certUtilsFp.length,
            )
            val hexAndColons = certUtilsFp.all { it in '0'..'9' || it in 'A'..'F' || it == ':' }
            assertTrue("Fingerprint must contain only uppercase hex digits and colons", hexAndColons)
        }
    }
}
