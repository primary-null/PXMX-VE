package com.pxmx.app.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.Principal
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

/**
 * Pure-logic tests for the TOFU trust decision and fingerprint helpers.
 * The production code only reads cert.encoded; a minimal fake certificate
 * keeps these tests plain-JVM with no extra dependencies.
 */
class TlsHelpersTest {

    private fun fakeCert(derBytes: ByteArray): X509Certificate = object : X509Certificate() {
        override fun getEncoded(): ByteArray = derBytes
        override fun checkValidity() {}
        override fun checkValidity(date: Date) {}
        override fun getVersion(): Int = 3
        override fun getSerialNumber(): BigInteger = BigInteger.ONE
        override fun getIssuerDN(): Principal = X500Principal("CN=pxmx-test")
        override fun getSubjectDN(): Principal = X500Principal("CN=pxmx-test")
        override fun getNotBefore(): Date = Date(0)
        override fun getNotAfter(): Date = Date(Long.MAX_VALUE)
        override fun getSigAlgName(): String = "SHA256withRSA"
        override fun getSigAlgOID(): String = "1.2.840.113549.1.1.11"
        override fun getSigAlgParams(): ByteArray? = null
        override fun getSignature(): ByteArray = byteArrayOf(1, 2, 3)
        override fun getIssuerUniqueID(): BooleanArray? = null
        override fun getSubjectUniqueID(): BooleanArray? = null
        override fun getKeyUsage(): BooleanArray? = null
        override fun getExtendedKeyUsage(): List<String>? = null
        override fun getBasicConstraints(): Int = -1
        override fun getTBSCertificate(): ByteArray = derBytes
        override fun verify(key: PublicKey) {}
        override fun verify(key: PublicKey, sigProvider: String) {}
        override fun toString(): String = "fake-cert"
        override fun getPublicKey(): PublicKey = throw UnsupportedOperationException()
        override fun hasUnsupportedCriticalExtension(): Boolean = false
        override fun getCriticalExtensionOIDs(): Set<String> = HashSet()
        override fun getNonCriticalExtensionOIDs(): Set<String> = HashSet()
        override fun getExtensionValue(oid: String): ByteArray? = null
    }

    @Test
    fun fingerprintIsStableColonSeparatedSha256() {
        val cert = fakeCert(ByteArray(32) { it.toByte() })
        val fp = CertUtils.computeSha256Fingerprint(cert)
        // 32 bytes -> 64 hex chars + 31 colons
        assertEquals(95, fp.length)
        assertEquals(fp, CertUtils.computeSha256Fingerprint(cert))
        val hexOnly = CertUtils.normalizeFingerprint(fp)
        assertEquals(64, hexOnly.length)
        assertTrue(hexOnly.all { it in '0'..'9' || it in 'A'..'F' })
    }

    @Test
    fun decisionDelegatesToSystemWhenTrustSelfSignedOff() {
        val verdict = TofuDecision.evaluate(
            trustSelfSigned = false,
            pinnedFp = null,
            presentedFp = "AA:BB",
        )
        assertTrue(verdict is TofuVerdict.DelegateToSystem)
    }

    @Test
    fun decisionAllowsFirstUseWhenUnpinned() {
        val verdict = TofuDecision.evaluate(
            trustSelfSigned = true,
            pinnedFp = null,
            presentedFp = "AA:BB",
        )
        assertTrue(verdict is TofuVerdict.Allow)
    }

    @Test
    fun decisionAllowsMatchingPinIgnoringFormatting() {
        val verdict = TofuDecision.evaluate(
            trustSelfSigned = true,
            pinnedFp = "aa:bb:cc",
            presentedFp = "AA BB CC",
        )
        assertTrue(verdict is TofuVerdict.Allow)
    }

    @Test
    fun decisionRejectsChangedCertificate() {
        val verdict = TofuDecision.evaluate(
            trustSelfSigned = true,
            pinnedFp = "AA:BB",
            presentedFp = "CC:DD",
        )
        assertTrue(verdict is TofuVerdict.Reject)
        assertTrue((verdict as TofuVerdict.Reject).reason.contains("changed", ignoreCase = true))
    }

    @Test
    fun realCertFingerprintFeedsDecisionRoundTrip() {
        val der = ByteArray(32) { (it * 7).toByte() }
        val cert = fakeCert(der)
        val fp = CertUtils.computeSha256Fingerprint(cert)
        val verdict = TofuDecision.evaluate(
            trustSelfSigned = true,
            pinnedFp = fp,
            presentedFp = CertUtils.computeSha256Fingerprint(cert),
        )
        assertTrue(verdict is TofuVerdict.Allow)
    }
}
