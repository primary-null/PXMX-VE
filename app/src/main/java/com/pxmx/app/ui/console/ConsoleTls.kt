package com.pxmx.app.ui.console

import com.pxmx.app.data.api.CertUtils
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The console transport, shared by resource and WebSocket requests. */
internal fun createConsoleClient(
    allowedHost: String,
    trustSelfSigned: Boolean,
    expectedCertPin: String?,
    defaultTm: X509TrustManager = javax.net.ssl.TrustManagerFactory
        .getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(null as java.security.KeyStore?) }
        .trustManagers.filterIsInstance<X509TrustManager>().first(),
): OkHttpClient {
    val tm = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            defaultTm.checkClientTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (trustSelfSigned) {
                val leaf = chain?.firstOrNull() ?: throw CertificateException("Empty certificate chain")
                val pin = expectedCertPin?.takeIf { it.isNotBlank() }
                    ?: throw CertificateException("Console certificate pin missing; sign in again")
                if (CertUtils.normalizeFingerprint(CertUtils.computeSha256Fingerprint(leaf)) !=
                    CertUtils.normalizeFingerprint(pin)
                ) {
                    throw CertificateException("Certificate changed for host — possible MITM attack!")
                }
            } else {
                defaultTm.checkServerTrusted(chain, authType)
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = defaultTm.acceptedIssuers
    }
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf<TrustManager>(tm), SecureRandom())
    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, tm)
        .apply {
            // Strict mode must retain OkHttp's certificate-aware hostname verifier.
            if (trustSelfSigned) hostnameVerifier { hostname, _ -> hostname.equals(allowedHost, ignoreCase = true) }
        }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}

/**
 * Teardowns OkHttp resources (cancels active calls and evicts connection pool) off the main thread.
 * Evicting OkHttp connection pools closes TLS sockets, which must not happen on Android's main thread
 * (avoids [android.os.NetworkOnMainThreadException]).
 */
internal fun teardownConsoleClientAsync(
    client: OkHttpClient,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    scope: CoroutineScope = CoroutineScope(dispatcher),
): Job = scope.launch(dispatcher) {
    try {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        // Best-effort teardown; ignore socket closing errors
    }
}
