package com.pxmx.app.data.repo

import android.content.Context
import android.os.SystemClock
import android.webkit.CookieManager
import com.pxmx.app.data.api.AppJson
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.LoginOutcome
import com.pxmx.app.data.model.SavedProfile
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.ServerProbe
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.model.VersionInfo
import com.pxmx.app.data.net.ConnectionTestResult
import com.pxmx.app.data.net.LocalNet
import com.pxmx.app.data.session.ProbeAuth
import com.pxmx.app.data.session.SessionStore
import com.pxmx.app.data.session.SessionSnapshot
import com.pxmx.app.data.session.SessionIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handles authentication, ticket negotiation, two-factor challenge completion,
 * server probing, and session lifecycle management.
 */
class AuthRepository(
    private val context: Context,
    private val sessionStore: SessionStore,
    private val clientFactory: ProxmoxApiProvider,
    private val pveClient: PveClient,
    private val localNet: LocalNet,
    private val onLogout: () -> Unit = {},
) {

    suspend fun login(
        config: ServerConfig,
        saveCredentials: Boolean = true,
        profileId: String? = null,
        enableAutoConnect: Boolean? = null,
        label: String = "",
        forceNewProfile: Boolean = false,
        silent: Boolean = false,
        renewalOf: SessionSnapshot? = null,
    ): LoginOutcome {
        if (renewalOf != null && (!sessionStore.isCurrent(renewalOf) ||
                profileId != renewalOf.profileId || SessionIdentity.of(config) != SessionIdentity.of(renewalOf.state.config))) {
            return LoginOutcome.Failed(PveException("Session changed"))
        }
        val attempt = renewalOf?.generation ?: sessionStore.beginLogin()
        return try {
            val loginApi = clientFactory.apiForProbe(config)
            val api = loginApi.api
            var ticket: String? = null
            var csrf: String? = null
            var username: String? = null

            when (config.authMode) {
                AuthMode.PASSWORD -> {
                    val user = PveClient.normalizeUsername(config.username, config.realm)
                    val resp = try {
                        api.createTicket(user, config.password)
                    } catch (e: retrofit2.HttpException) {
                        val errorBody = try {
                            e.response()?.errorBody()?.string()
                        } catch (_: Exception) {
                            null
                        }
                        if (e.code() == 401) {
                            val partialTicket = extractTfaTicket(errorBody)
                            if (partialTicket != null) {
                                return LoginOutcome.NeedsTfa(
                                    partialTicket = partialTicket,
                                    config = config,
                                    saveCredentials = saveCredentials,
                                    profileId = profileId,
                                    enableAutoConnect = enableAutoConnect,
                                    label = label,
                                    forceNewProfile = forceNewProfile,
                                )
                            }
                        }
                        throw PveHttpException(e.code(), errorBody, e.message(), e)
                    }
                    val data = resp.data
                        ?: return LoginOutcome.Failed(PveException("Login failed: empty ticket response"))
                    val rawTicket = data.ticket
                        ?: return LoginOutcome.Failed(PveException("Login failed: no ticket"))
                    if ((data.needTfa ?: 0) != 0 || rawTicket.startsWith("PVE:tfa!") || rawTicket.contains("TFA:") || rawTicket.contains("TFA-PARTIAL") || data.cap?.containsKey("NeedTFA") == true) {
                        return LoginOutcome.NeedsTfa(
                            partialTicket = rawTicket,
                            config = config,
                            saveCredentials = saveCredentials,
                            profileId = profileId,
                            enableAutoConnect = enableAutoConnect,
                            label = label,
                            forceNewProfile = forceNewProfile,
                        )
                    }
                    ticket = rawTicket
                    csrf = data.csrfPreventionToken
                    username = data.username ?: user
                }
                AuthMode.API_TOKEN -> {
                    if (config.apiToken.isBlank()) {
                        return LoginOutcome.Failed(PveException("API token is empty"))
                    }
                    username = config.apiToken.substringBefore('!').ifBlank { null }
                }
            }

            // Keep credentials private until every login request succeeds.
            ticket?.let {
                pinAuthenticatedCertificate(config, loginApi, attempt)
                loginApi.probeAuth.set(ProbeAuth(it, csrf))
            }
            val version = try { api.version().data } finally { loginApi.probeAuth.set(null) }
            val full = SessionState(config.withoutEphemeralSecrets(), ticket, csrf, username, version)
            val published = sessionStore.publishIfGeneration(attempt) {
                sessionStore.saveProfileFromLogin(config, saveCredentials, profileId, label, forceNewProfile, version?.display)
                if (config.trustSelfSigned) {
                    loginApi.capturedFingerprint.get()?.let { sessionStore.saveCertPin(config.host, it) }
                }
                if (renewalOf == null) sessionStore.setSession(full) else sessionStore.renewSession(renewalOf, full)
                commitLoginSideEffects(config, version, enableAutoConnect)
            }
            if (published) LoginOutcome.Success(full) else LoginOutcome.Failed(PveException("Session changed"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!silent) {
                sessionStore.publishIfGeneration(attempt) { sessionStore.clearSession() }
            }
            LoginOutcome.Failed(pveClient.mapError(e))
        }
    }

    suspend fun completeTfa(
        config: ServerConfig,
        partialTicket: String,
        otp: String,
        saveCredentials: Boolean = true,
        profileId: String? = null,
        enableAutoConnect: Boolean? = null,
        label: String = "",
        forceNewProfile: Boolean = false,
    ): Result<SessionState> {
        val attempt = sessionStore.beginLogin()
        return try {
            val loginApi = clientFactory.apiForProbe(config)
            val api = loginApi.api
            val resp = api.createTicketTfa(
                username = PveClient.normalizeUsername(config.username, config.realm),
                password = "totp:${otp.trim()}",
                tfaChallenge = partialTicket,
            )
            val data = resp.data
                ?: return Result.failure(PveException("TFA verification failed: empty response"))
            val ticket = data.ticket
                ?: return Result.failure(PveException("TFA verification failed: no ticket"))
            val csrf = data.csrfPreventionToken
            val user = data.username ?: PveClient.normalizeUsername(config.username, config.realm)

            pinAuthenticatedCertificate(config, loginApi, attempt)
            loginApi.probeAuth.set(ProbeAuth(ticket, csrf))
            val version = try { api.version().data } finally { loginApi.probeAuth.set(null) }
            val full = SessionState(config.withoutEphemeralSecrets(), ticket, csrf, user, version)
            val published = sessionStore.publishIfGeneration(attempt) {
                sessionStore.saveProfileFromLogin(config, saveCredentials, profileId, label, forceNewProfile, version?.display)
                if (config.trustSelfSigned) {
                    loginApi.capturedFingerprint.get()?.let { sessionStore.saveCertPin(config.host, it) }
                }
                sessionStore.setSession(full)
                commitLoginSideEffects(config, version, enableAutoConnect)
            }
            if (published) Result.success(full) else Result.failure(PveException("Session changed"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            sessionStore.publishIfGeneration(attempt) { sessionStore.clearSession() }
            Result.failure(pveClient.mapError(e))
        }
    }

    private fun pinAuthenticatedCertificate(config: ServerConfig, client: com.pxmx.app.data.api.ProbeApi, attempt: Long) {
        if (!config.trustSelfSigned) return
        val fingerprint = client.capturedFingerprint.get() ?: return
        sessionStore.publishIfGeneration(attempt) {
            sessionStore.saveCertPin(config.host, fingerprint)
        }
    }

    private fun commitLoginSideEffects(
        config: ServerConfig,
        version: VersionInfo?,
        enableAutoConnect: Boolean?,
    ) {
        if (config.host.equals("demo", ignoreCase = true)) return
        sessionStore.lastProfileId()?.let { id ->
            sessionStore.touchProfile(id, version = version?.display)
        }
        enableAutoConnect?.let { sessionStore.setAutoConnect(it) }
    }

    private fun extractTfaTicket(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            val json = AppJson.parseToJsonElement(errorBody).jsonObject
            val dataElem = json["data"]
            if (dataElem is JsonObject) {
                val ticket = dataElem["ticket"]?.jsonPrimitive?.contentOrNull
                if (!ticket.isNullOrBlank()) return ticket
            } else if (dataElem is JsonPrimitive && dataElem.isString) {
                val str = dataElem.content
                if (str.isNotBlank()) return str
            }
            val ticket = json["ticket"]?.jsonPrimitive?.contentOrNull
            if (!ticket.isNullOrBlank()) return ticket
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun ServerConfig.withoutEphemeralSecrets(): ServerConfig = when (authMode) {
        AuthMode.PASSWORD -> copy(password = "")
        AuthMode.API_TOKEN -> this
    }

    suspend fun loginWithProfile(profileId: String, silent: Boolean = false, renewalOf: SessionSnapshot? = null): LoginOutcome {
        val profile = sessionStore.getProfile(profileId)
            ?: return LoginOutcome.Failed(PveException("Profile not found"))
        if (!profile.hasSavedSecret) {
            return LoginOutcome.Failed(PveException("No saved credentials for this profile"))
        }
        return login(
            config = profile.toServerConfig(includeSecrets = true),
            saveCredentials = profile.saveCredentials,
            profileId = profile.id,
            silent = silent,
            renewalOf = renewalOf,
        )
    }

    suspend fun probeProfile(profile: SavedProfile): Result<ServerProbe> {
        val config = profile.toServerConfig(includeSecrets = true)
        if (!profile.hasSavedSecret && !config.host.equals("demo", ignoreCase = true)) {
            return Result.failure(PveException("No saved credentials"))
        }

        val probeApi = clientFactory.apiForProbe(config)
        val api = probeApi.api

        return try {
            if (config.authMode == AuthMode.PASSWORD) {
                val user = PveClient.normalizeUsername(config.username, config.realm)
                val resp = api.createTicket(user, config.password)
                val ticket = resp.data?.ticket ?: throw PveException("Login failed")
                val csrf = resp.data?.csrfPreventionToken
                probeApi.probeAuth.set(ProbeAuth(ticket, csrf))
            }

            val version = api.version().data?.display
            val nodes = api.nodes().data.orEmpty()
            if (nodes.isEmpty()) throw PveException("No nodes found")

            val all = nodes.flatMap { n ->
                val nodeName = n.node ?: return@flatMap emptyList()
                val qemu = api.nodeQemu(nodeName).data.orEmpty()
                val lxc = api.nodeLxc(nodeName).data.orEmpty()
                qemu + lxc
            }

            val running = all.count { it.status == "running" || it.status == "online" }
            val stopped = all.count { it.status == "stopped" || it.status == "offline" }
            val guestPairs = all.map { (it.name ?: it.vmid?.toString() ?: "unknown") to (it.status ?: "unknown") }

            Result.success(
                ServerProbe(
                    host = config.displayHost,
                    version = version,
                    running = running,
                    stopped = stopped,
                    guests = guestPairs
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        } finally {
            probeApi.probeAuth.set(null)
        }
    }

    suspend fun testConnection(host: String, port: Int = 8006, timeoutMs: Long = 8000L): ConnectionTestResult =
        localNet.testConnection(host, port, timeoutMs)

    suspend fun testProfileConnection(profile: SavedProfile): ConnectionTestResult {
        if (profile.host.equals("demo", ignoreCase = true)) {
            return ConnectionTestResult(online = true, version = "8.3.0", latencyMs = 12L)
        }

        val start = SystemClock.elapsedRealtime()
        val config = profile.toServerConfig(includeSecrets = true)
        val probeApi = clientFactory.apiForProbe(config)
        val api = probeApi.api
        return try {
            val version = if (config.authMode == AuthMode.PASSWORD) {
                val user = PveClient.normalizeUsername(config.username, config.realm)
                val ticketResp = api.createTicket(user, config.password)
                val ticket = ticketResp.data?.ticket ?: throw PveException("Authentication failed")
                val csrf = ticketResp.data?.csrfPreventionToken

                probeApi.probeAuth.set(ProbeAuth(ticket, csrf))
                try {
                    api.version().data
                } finally {
                    probeApi.probeAuth.set(null)
                }
            } else {
                api.version().data
            }

            val latency = SystemClock.elapsedRealtime() - start
            ConnectionTestResult(
                online = true,
                version = version?.display,
                latencyMs = latency
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val latency = SystemClock.elapsedRealtime() - start
            val msg = pveClient.mapError(e).message ?: "Connection failed"
            ConnectionTestResult(online = false, error = msg, latencyMs = latency)
        }
    }

    suspend fun tryAutoConnect(): Result<SessionState> {
        if (!sessionStore.autoConnect.value) {
            return Result.failure(PveException("Auto-connect disabled"))
        }
        val profile = sessionStore.lastProfileId()?.let { sessionStore.getProfile(it) }
            ?: sessionStore.getLastProfile()
            ?: return Result.failure(PveException("No saved profile"))
        if (!profile.hasSavedSecret) {
            return Result.failure(PveException("No saved credentials"))
        }
        return when (val outcome = loginWithProfile(profile.id)) {
            is LoginOutcome.Success -> Result.success(outcome.session)
            is LoginOutcome.NeedsTfa -> Result.failure(PveException("Two-factor authentication required"))
            is LoginOutcome.Failed -> Result.failure(outcome.error)
        }
    }

    fun logout(rememberAsPrevious: Boolean = true) {
        sessionStore.clearSession(rememberAsPrevious = rememberAsPrevious)
        clientFactory.clear()
        clearWebCookies()
        onLogout()
    }

    private fun clearWebCookies() {
        runCatching {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
        }
    }
}
