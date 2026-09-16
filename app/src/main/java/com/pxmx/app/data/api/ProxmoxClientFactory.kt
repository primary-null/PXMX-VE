package com.pxmx.app.data.api

import com.pxmx.app.BuildConfig
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.session.ProbeAuth
import com.pxmx.app.data.session.SessionStore
import com.pxmx.app.data.session.SessionSnapshot
import com.pxmx.app.data.session.SessionIdentity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ProxmoxClientFactory(
    private val sessionStore: SessionStore,
) : ProxmoxApiProvider {
    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedApi: ProxmoxApi? = null


    /** Single demo instance: keeps the simulation's state alive across requests. */
    private val demoApi: ProxmoxApi by lazy { DemoApi() }

    @Synchronized
    override fun apiFor(config: ServerConfig): ProxmoxApi {
        val session = sessionStore.snapshot()
        require(session != null && SessionIdentity.of(session.state.config) == SessionIdentity.of(config)) {
            "No matching active session; use a private login/probe client"
        }
        return apiForSession(session)
    }

    @Synchronized
    override fun apiForSession(session: SessionSnapshot): ProxmoxApi {
        val config = session.state.config
        check(sessionStore.isCurrent(session)) { "Session changed" }
        if (config.host.equals("demo", ignoreCase = true)) return demoApi
        val pin = sessionStore.getCertPin(config.host).orEmpty()
        val key = "${SessionIdentity.of(config)}|${session.generation}|${config.trustSelfSigned}|$pin"
        cachedApi?.let { existing -> if (cachedKey == key) return existing }
        return build(config, preferBoundConfig = false, boundSession = session).also {
            cachedKey = key
            cachedApi = it
        }
    }

    @Synchronized
    override fun apiForProbe(config: ServerConfig): ProbeApi {
        if (config.host.equals("demo", ignoreCase = true)) return ProbeApi(demoApi)
        val slot = AtomicReference<ProbeAuth?>(null)
        val fingerprint = AtomicReference<String?>(null)
        return ProbeApi(build(config, preferBoundConfig = true, probeAuthSlot = slot, fingerprintSlot = fingerprint), slot, fingerprint)
    }

    @Synchronized
    override fun clear() {
        cachedKey = null
        cachedApi = null
    }


    private fun build(
        config: ServerConfig,
        preferBoundConfig: Boolean,
        probeAuthSlot: AtomicReference<ProbeAuth?>? = null,
        fingerprintSlot: AtomicReference<String?>? = null,
        boundSession: SessionSnapshot? = null,
    ): ProxmoxApi {
        val trustManager = TofuTrustManager(
            host = config.host,
            trustSelfSigned = config.trustSelfSigned,
            sessionStore = sessionStore,
            onCertCaptured = { _, fp ->
                fingerprintSlot?.set(fp)
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
            .addInterceptor(AuthInterceptor(sessionStore, config, preferBoundConfig, probeAuthSlot, boundSession))

        // Never log in release — console paths can contain vncticket secrets.
        if (BuildConfig.DEBUG) {
            val defaultLogger = HttpLoggingInterceptor.Logger.DEFAULT
            val sanitizedLogger = HttpLoggingInterceptor.Logger { message ->
                val sanitized = message.replace(
                    Regex("([?&](?:vncticket|ticket|token)=)[^&\\s]+", RegexOption.IGNORE_CASE),
                    "$1[REDACTED]",
                )
                defaultLogger.log(sanitized)
            }
            builder.addInterceptor(
                HttpLoggingInterceptor(sanitizedLogger).apply {
                    // HEADERS: bodies omitted, headers redacted below.
                    level = HttpLoggingInterceptor.Level.HEADERS
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
