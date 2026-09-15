package com.pxmx.app.data.repo

import android.content.Context
import com.pxmx.app.data.api.AppJson
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.LoginOutcome
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.session.SessionStore
import com.pxmx.app.ui.util.Toasts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException

/**
 * Low-level communication seam for Proxmox VE API calls.
 * Handles auth token retry, 401 interception, thread-safe Mutex locking,
 * error mapping, and sensitive data redaction.
 */
class PveClient(
    private val context: Context,
    private val sessionStore: SessionStore,
    private val clientFactory: ProxmoxApiProvider,
    private val reAuthHandler: (suspend (profileId: String, expected: com.pxmx.app.data.session.SessionSnapshot) -> LoginOutcome)? = null,
) {
    private val authMutex = Mutex()

    suspend fun <T> apiCall(block: suspend (ProxmoxApi) -> T): Result<T> {
        val snapshot = sessionStore.snapshot()
            ?: return Result.failure(PveException("Not connected"))
        val session = snapshot.state

        try {
            val api = clientFactory.apiForSession(snapshot)
            return Result.success(block(api))
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            val is401 = e is HttpException && e.code() == 401
            val isPassword = session.config.authMode == AuthMode.PASSWORD

            if (is401 && isPassword) {
                val profileId = snapshot.profileId
                val profile = profileId?.let { sessionStore.getProfile(it) }

                val (newSession, reAuthMethod) = authMutex.withLock {
                    if (!sessionStore.isCurrent(snapshot)) return@withLock null to null
                    val currentSession = sessionStore.session.value
                    if (currentSession?.ticket != session.ticket && currentSession != null) {
                        // Already re-logged in by another concurrent call
                        currentSession to null
                    } else {
                        // 1. Attempt PVE ticket renewal with OLD ticket as password
                        var renewed: SessionState? = null
                        if (!session.ticket.isNullOrBlank()) {
                            try {
                                val api = clientFactory.apiForSession(snapshot)
                                val user = normalizeUsername(session.config.username, session.config.realm)
                                val resp = api.createTicket(user, session.ticket)
                                val newTicket = resp.data?.ticket
                                val newCsrf = resp.data?.csrfPreventionToken
                                if (!newTicket.isNullOrBlank()) {
                                    val updated = session.copy(ticket = newTicket, csrf = newCsrf)
                                    if (sessionStore.renewSession(snapshot, updated)) renewed = updated
                                }
                            } catch (renewE: Exception) {
                                if (renewE is CancellationException) throw renewE
                                // Ticket renewal failed; fall through to loginWithProfile
                            }
                        }

                        if (renewed != null) {
                            renewed to "renewal"
                        } else if (sessionStore.isCurrent(snapshot) && profile?.hasSavedSecret == true && reAuthHandler != null) {
                            when (val outcome = reAuthHandler.invoke(profile.id, snapshot)) {
                                is LoginOutcome.Success -> outcome.session to "profile"
                                else -> null to null
                            }
                        } else {
                            null to null
                        }
                    }
                }

                if (newSession != null && sessionStore.isCurrent(snapshot)) {
                    if (reAuthMethod != null) {
                        withContext(Dispatchers.Main) {
                            Toasts.show(context, "Session refreshed")
                        }
                    }
                    return try {
                        if (!sessionStore.isCurrent(snapshot)) return Result.failure(PveException("Session changed"))
                        val newApi = clientFactory.apiForSession(snapshot)
                        // Invoke the SAME block lambda directly for the retry
                        Result.success(block(newApi))
                    } catch (retryE: Exception) {
                        if (retryE is CancellationException) throw retryE
                        Result.failure(mapError(retryE))
                    }
                }
            }
            return Result.failure(mapError(e))
        }
    }

    fun mapError(e: Exception): Exception = when (e) {
        is PveClusterProxyTimeoutException -> e // preserve; node context set by caller
        is PveHttpException -> if (e.code == 596) {
            PveClusterProxyTimeoutException(
                node = "",
                message = "HTTP 596: Cluster proxy timed out. Target node did not respond within the 30-second Proxmox cluster proxy window.",
                cause = e,
            )
        } else {
            PveException(formatHttpError(e.code, e.errorBody, e.httpMessage), e)
        }
        is PveException -> PveException(redactSecrets(e.message) ?: e.message ?: "Unknown error", e.cause)
        is HttpException -> {
            val body = try {
                e.response()?.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            if (e.code() == 596) {
                PveClusterProxyTimeoutException(
                    node = "",
                    message = "HTTP 596: Cluster proxy timed out. Target node did not respond within the 30-second Proxmox cluster proxy window.",
                    cause = e,
                )
            } else {
                PveException(formatHttpError(e.code(), body, e.message()), e)
            }
        }
        is IOException -> {
            val rootCertEx = generateSequence<Throwable>(e) { it.cause }
                .firstOrNull { it is java.security.cert.CertificateException }
            if (rootCertEx != null) {
                PveException(redactSecrets(rootCertEx.message) ?: "TLS certificate validation failed", e)
            } else {
                PveException("Network error: ${redactSecrets(e.message)}", e)
            }
        }
        else -> PveException(redactSecrets(e.message) ?: e::class.java.simpleName, e)
    }

    companion object {
        fun normalizeUsername(username: String, realm: String): String {
            val u = username.trim()
            return if (u.contains('@')) u else "$u@$realm"
        }

        fun formatHttpError(code: Int, rawBody: String?, httpMessage: String?): String {
            val cleanBody = redactSecrets(rawBody)?.trim()
            var detail: String? = null

            if (!cleanBody.isNullOrBlank()) {
                val isNullDataOnly = cleanBody == "{\"data\":null}" ||
                    cleanBody == "{\"data\": null}" ||
                    cleanBody == "{\"data\":null}\n" ||
                    cleanBody == "data:null"

                if (cleanBody.startsWith("{") && cleanBody.endsWith("}")) {
                    try {
                        val elem = AppJson.parseToJsonElement(cleanBody)
                        if (elem is JsonObject) {
                            val msg = elem["message"]?.jsonPrimitive?.contentOrNull
                            val errors = elem["errors"]
                            val data = elem["data"]

                            if (!msg.isNullOrBlank()) {
                                detail = msg
                            } else if (errors is JsonObject && errors.isNotEmpty()) {
                                detail = errors.values.mapNotNull {
                                    if (it is JsonPrimitive) it.contentOrNull else it.toString()
                                }.firstOrNull { !it.isNullOrBlank() }
                            } else if (elem.keys == setOf("data") && (data is JsonNull || data == null)) {
                                detail = when (code) {
                                    401 -> "authentication failure"
                                    403 -> "forbidden"
                                    else -> null
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Keep fallback
                    }
                }

                if (detail.isNullOrBlank()) {
                    if (isNullDataOnly) {
                        detail = when (code) {
                            401 -> "authentication failure"
                            403 -> "forbidden"
                            else -> null
                        }
                    } else {
                        detail = cleanBody
                    }
                }
            }

            if (detail.isNullOrBlank()) {
                val msg = httpMessage?.trim()
                if (!msg.isNullOrBlank() && !msg.startsWith("HTTP $code") && !msg.equals("Response.error()", ignoreCase = true)) {
                    detail = msg.lowercase(java.util.Locale.US)
                }
            }

            if (detail.isNullOrBlank()) {
                detail = when (code) {
                    401 -> "authentication failure"
                    403 -> "forbidden"
                    404 -> "not found"
                    500 -> "internal server error"
                    501 -> "not implemented"
                    else -> "request failed"
                }
            }

            return "HTTP $code: $detail"
        }

        fun redactSecrets(raw: String?): String? {
            if (raw.isNullOrBlank()) return raw
            var sanitized = raw
            sanitized = sanitized.replace(
                Regex(""""(?:ticket|CSRFPreventionToken|password|secret|apiToken|vncticket)"\s*:\s*"[^"]*"""", RegexOption.IGNORE_CASE)
            ) {
                val key = it.value.substringBefore(':')
                "$key:\"[REDACTED]\""
            }
            sanitized = sanitized.replace(Regex("""PVE:[A-Za-z0-9@_.:+/%=-]+"""), "[REDACTED_TICKET]")
            sanitized = sanitized.replace(Regex("""PVEAuthCookie=[^;\s]+"""), "PVEAuthCookie=[REDACTED]")
            sanitized = sanitized.replace(Regex("""PVEAPIToken=[^\s]+"""), "PVEAPIToken=[REDACTED]")
            return sanitized
        }
    }
}

class PveHttpException(
    val code: Int,
    val errorBody: String?,
    val httpMessage: String?,
    cause: Throwable? = null,
) : Exception("HTTP $code: ${errorBody ?: httpMessage}", cause)

open class PveException(message: String, cause: Throwable? = null) : Exception(message, cause)

class PveClusterProxyTimeoutException(
    val node: String,
    message: String = if (node.isNotBlank()) {
        "HTTP 596: Inter-node cluster proxy timeout for node '$node'. Target node did not respond within the 30-second Proxmox cluster proxy window."
    } else {
        "HTTP 596: Cluster proxy timed out. Target node did not respond within the 30-second Proxmox cluster proxy window."
    },
    cause: Throwable? = null,
) : PveException(message, cause)
