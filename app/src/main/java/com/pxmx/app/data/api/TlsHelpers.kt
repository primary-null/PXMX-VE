package com.pxmx.app.data.api

import android.net.http.SslCertificate
import android.os.Build
import com.pxmx.app.data.session.SessionStore
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object CertUtils {
    /**
     * Computes the colon-separated uppercase SHA-256 fingerprint of an X509 certificate.
     */
    fun computeSha256Fingerprint(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    fun normalizeFingerprint(fp: String): String =
        fp.replace(":", "").replace(" ", "").trim().uppercase()

    /**
     * Extracts an [X509Certificate] from Android WebView's [SslCertificate].
     */
    fun getX509Certificate(sslCert: SslCertificate): X509Certificate? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return sslCert.x509Certificate
        }
        return try {
            val bundle = SslCertificate.saveState(sslCert)
            val bytes = bundle.getByteArray("x509-certificate") ?: return null
            val factory = CertificateFactory.getInstance("X.509")
            factory.generateCertificate(ByteArrayInputStream(bytes)) as? X509Certificate
        } catch (_: Exception) {
            null
        }
    }
}

/** Outcome of the TOFU pin decision; pure logic, unit-tested in TlsHelpersTest. */
sealed interface TofuVerdict {
    data object DelegateToSystem : TofuVerdict
    data object Allow : TofuVerdict
    data class Reject(val reason: String) : TofuVerdict
}

object TofuDecision {
    /**
     * Decides how to handle a presented server certificate.
     * - trustSelfSigned off -> delegate to standard system CA validation.
     * - trustSelfSigned on, no pin -> first use, allow (pin saved after login).
     * - pin present and matching -> allow.
     * - pin present and mismatching -> reject (MITM protection).
     */
    fun evaluate(trustSelfSigned: Boolean, pinnedFp: String?, presentedFp: String): TofuVerdict {
        if (!trustSelfSigned) return TofuVerdict.DelegateToSystem
        val pin = pinnedFp
        if (pin != null) {
            return if (CertUtils.normalizeFingerprint(presentedFp) == CertUtils.normalizeFingerprint(pin)) {
                TofuVerdict.Allow
            } else {
                TofuVerdict.Reject(
                    "Certificate changed for this host — possible MITM attack! " +
                        "(pinned: $pin, presented: $presentedFp)"
                )
            }
        }
        // First connection with trustSelfSigned: TOFU allows handshake, pin saved upon successful login
        return TofuVerdict.Allow
    }
}

/**
 * Trust-On-First-Use (TOFU) trust manager:
 * - If [trustSelfSigned] is false: delegates fully to standard system CA validation.
 * - If [trustSelfSigned] is true:
 *     - If host has a pinned fingerprint in [sessionStore], ensures presented cert matches it.
 *     - If mismatched -> throws CertificateException (MITM protection).
 *     - If unpinned (first connection) -> captures fingerprint via [onCertCaptured] and allows handshake.
 */
class TofuTrustManager(
    val host: String,
    val trustSelfSigned: Boolean,
    private val sessionStore: SessionStore,
    private val onCertCaptured: ((X509Certificate, String) -> Unit)? = null,
) : X509TrustManager {

    private val defaultTrustManager: X509TrustManager by lazy {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        defaultTrustManager.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) {
            throw CertificateException("Empty server certificate chain for $host")
        }
        val serverCert = chain[0]
        val presentedFp = CertUtils.computeSha256Fingerprint(serverCert)
        onCertCaptured?.invoke(serverCert, presentedFp)

        when (val verdict = TofuDecision.evaluate(trustSelfSigned, sessionStore.getCertPin(host), presentedFp)) {
            TofuVerdict.DelegateToSystem -> defaultTrustManager.checkServerTrusted(chain, authType)
            TofuVerdict.Allow -> Unit
            is TofuVerdict.Reject -> throw CertificateException(verdict.reason)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = defaultTrustManager.acceptedIssuers
}

fun createTofuSslSocketFactory(trustManager: X509TrustManager): SSLSocketFactory {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
    return sslContext.socketFactory
}

fun createTofuHostnameVerifier(host: String, trustSelfSigned: Boolean): HostnameVerifier {
    val defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
    return HostnameVerifier { hostname, session ->
        if (trustSelfSigned) {
            val cleanHost = host.trim().removePrefix("https://").removePrefix("http://")
                .substringBefore('/').substringBefore(':')
            val cleanReq = hostname.trim().substringBefore(':')
            if (cleanHost.equals(cleanReq, ignoreCase = true)) {
                true
            } else {
                defaultVerifier.verify(hostname, session)
            }
        } else {
            defaultVerifier.verify(hostname, session)
        }
    }
}
