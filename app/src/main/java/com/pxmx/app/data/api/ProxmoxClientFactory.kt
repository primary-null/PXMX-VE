package com.pxmx.app.data.api

import com.pxmx.app.BuildConfig
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.session.SessionStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ProxmoxClientFactory(
    private val sessionStore: SessionStore,
) : ProxmoxApiProvider {
    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedApi: ProxmoxApi? = null

    private val capturedFingerprints = ConcurrentHashMap<String, String>()

    /** Single demo instance: keeps the simulation's state alive across requests. */
    private val demoApi: ProxmoxApi by lazy { DemoApi() }

    @Synchronized
    override fun apiFor(config: ServerConfig): ProxmoxApi {
        // Demo mode: canned offline backend, never a real network call.
        if (config.host.equals("demo", ignoreCase = true)) return demoApi
        val pin = sessionStore.getCertPin(config.host).orEmpty()
        val key = "${config.baseUrl}|${config.trustSelfSigned}|$pin"
        cachedApi?.let { existing ->
            if (cachedKey == key) return existing
        }
        return build(config).also {
            cachedKey = key
            cachedApi = it
        }
    }

    @Synchronized
    override fun clear() {
        cachedKey = null
        cachedApi = null
    }

    override fun getCapturedFingerprint(host: String): String? =
        capturedFingerprints[host.trim().lowercase()]

    private fun build(config: ServerConfig): ProxmoxApi {
        val trustManager = TofuTrustManager(
            host = config.host,
            trustSelfSigned = config.trustSelfSigned,
            sessionStore = sessionStore,
            onCertCaptured = { _, fp ->
                capturedFingerprints[config.host.trim().lowercase()] = fp
            },
        )
        val sslSocketFactory = createTofuSslSocketFactory(trustManager)
        val hostnameVerifier = createTofuHostnameVerifier(config.host, config.trustSelfSigned)

        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .sslSocketFactory(sslSocketFactory, trustManager)
            .hostnameVerifier(hostnameVerifier)
            .addInterceptor(AuthInterceptor(sessionStore, config))

        // Never log in release — console paths can contain vncticket secrets.
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Cookie")
                    redactHeader("Authorization")
                    redactHeader("CSRFPreventionToken")
                    redactHeader("Set-Cookie")
                },
            )
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(builder.build())
            .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(ProxmoxApi::class.java)
    }
}
