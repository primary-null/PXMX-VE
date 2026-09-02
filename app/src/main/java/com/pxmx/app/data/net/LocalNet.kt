package com.pxmx.app.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.pxmx.app.data.api.TofuTrustManager
import com.pxmx.app.data.api.createTofuHostnameVerifier
import com.pxmx.app.data.api.createTofuSslSocketFactory
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Information about a detected or specified IPv4 subnet.
 */
data class SubnetInfo(
    val ip: String,
    val prefixLength: Int,
    val networkCidr: String,
)

/**
 * Verification tiers for a discovered host.
 */
sealed class ProbeResult {
    data class Verified(val version: String) : ProbeResult()
    object PveDetected : ProbeResult()
    object NotPve : ProbeResult()
    object Unreachable : ProbeResult()
}

/**
 * A Proxmox VE host discovered during network scan.
 */
data class DiscoveredHost(
    val ip: String,
    val port: Int = 8006,
    val version: String,
    val latencyMs: Long = 0L,
    val isPveDetectedOnly: Boolean = false,
    val isSavedKnown: Boolean = false,
)

/**
 * Real-time progress update from a subnet scan.
 */
data class ScanProgress(
    val scanned: Int,
    val total: Int,
    val currentIp: String,
    val verified: List<DiscoveredHost>,
    val pveDetected: List<DiscoveredHost> = emptyList(),
    val unverified: List<String> = emptyList(),
    val isFinished: Boolean = false,
    val error: String? = null,
    val infoMessage: String? = null,
)

/**
 * Result of testing a server connection (TOFU HTTPS handshake + GET /api2/json/version).
 */
data class ConnectionTestResult(
    val online: Boolean,
    val version: String? = null,
    val latencyMs: Long? = null,
    val error: String? = null,
)

/**
 * Gateway for all local network operations (subnet discovery, IP generation, TCP port checks,
 * unauthenticated PVE version probing, and profile latency tests).
 *
 * SDK-37 Readiness Note:
 * In Android 17 (API 37), accessing local network resources / subnet scanning will require
 * the runtime permission `android.permission.ACCESS_LOCAL_NETWORK`.
 * By routing all subnet discovery and local probing operations through this [LocalNet] gateway,
 * future runtime permission checks and user consent dialogs can be cleanly hooked in this
 * single class without modifying callers across ViewModels or UI components.
 */
open class LocalNet(
    private val context: Context?,
    private val sessionStore: SessionStore?,
) {
    // ... rest of the class

    /**
     * Finds all scannable IPv4 subnets across all active network interfaces.
     * Prioritizes RFC1918 subnets. Recognizes Tailscale/VPN (100.64.0.0/10) to report but not sweep.
     */
    fun getScannableSubnets(): List<SubnetInfo> {
        val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return emptyList()
        
        val subnets = mutableListOf<SubnetInfo>()
        val seenCidrs = mutableSetOf<String>()

        cm.allNetworks.forEach { network ->
            val lp = cm.getLinkProperties(network) ?: return@forEach
            for (linkAddress in lp.linkAddresses) {
                val address = linkAddress.address
                if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                    val ipStr = address.hostAddress ?: continue
                    val prefix = linkAddress.prefixLength.coerceIn(1, 32)
                    val ipLong = parseIpv4(ipStr) ?: continue
                    
                    // Slice large subnets to /24
                    val effectivePrefix = if (prefix < 24) 24 else prefix
                    val mask = if (effectivePrefix == 0) 0L else (0xFFFFFFFFL shl (32 - effectivePrefix)) and 0xFFFFFFFFL
                    val networkIp = ipLong and mask
                    val cidr = "${formatIpv4(networkIp)}/$effectivePrefix"
                    
                    if (seenCidrs.add(cidr)) {
                        subnets.add(SubnetInfo(ip = ipStr, prefixLength = effectivePrefix, networkCidr = cidr))
                    }
                }
            }
        }
        return subnets
    }

    /**
     * Checks if an IPv4 address belongs to a private network (RFC 1918 or loopback).
     */
    open fun isPrivateSubnet(ip: String): Boolean {
        val parts = ip.trim().split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        val b0 = parts[0]
        val b1 = parts[1]
        return when {
            b0 == 10 -> true
            b0 == 172 && b1 in 16..31 -> true
            b0 == 192 && b1 == 168 -> true
            b0 == 127 -> true
            else -> false
        }
    }

    /**
     * Checks if an IPv4 address is in the Tailscale/CGNAT range (100.64.0.0/10).
     */
    fun isTailscaleSubnet(ip: String): Boolean {
        val ipLong = parseIpv4(ip) ?: return false
        val tailscaleNet = parseIpv4("100.64.0.0")!!
        val mask = (0xFFFFFFFFL shl (32 - 10)) and 0xFFFFFFFFL
        return (ipLong and mask) == (tailscaleNet and mask)
    }

    /**
     * Generates candidate IPv4 addresses for scanning.
     * Caps wide subnets (prefix < 24) to the surrounding /24 slice (max 254 hosts)
     * so scans complete in reasonable time.
     */
    open fun generateSubnetIps(ip: String, prefixLength: Int): List<String> {
        val ipLong = parseIpv4(ip) ?: return emptyList()
        // If prefix is larger than /24 (e.g. /16), sweep only the device's own /24 slice.
        val effectivePrefix = if (prefixLength < 24) 24 else prefixLength.coerceAtMost(30)
        val mask = (0xFFFFFFFFL shl (32 - effectivePrefix)) and 0xFFFFFFFFL
        val network = ipLong and mask
        val broadcast = network or (mask.inv() and 0xFFFFFFFFL)

        val ips = mutableListOf<String>()
        var curr = network + 1
        while (curr < broadcast) {
            ips.add(formatIpv4(curr))
            curr++
        }
        return ips
    }

    /**
     * TCP connect check to port 8006 with 400ms timeout.
     */
    open suspend fun checkPortOpen(ip: String, port: Int = 8006, timeoutMs: Int = 400): Boolean =
        withContext(Dispatchers.IO) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            } finally {
                try {
                    socket.close()
                } catch (_: Exception) {
                }
            }
        }

    /**
     * Unauthenticated GET /api2/json/version through the TOFU client without saving cert pins.
     * Maps response to a [ProbeResult] tier.
     */
    open suspend fun probePveVersion(
        host: String,
        port: Int = 8006,
        timeoutMs: Long = 10000L
    ): ProbeResult =
        withContext(Dispatchers.IO) {
            if (host.equals("demo", ignoreCase = true)) {
                return@withContext ProbeResult.Verified("8.3.0")
            }

            try {
                val trustManager = TofuTrustManager(
                    host = host,
                    trustSelfSigned = true,
                    sessionStore = sessionStore!!,
                    onCertCaptured = null, // No cert pin save on probe
                )
                val sslSocketFactory = createTofuSslSocketFactory(trustManager)
                val hostnameVerifier = createTofuHostnameVerifier(host, trustSelfSigned = true)

                val client = OkHttpClient.Builder()
                    .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .sslSocketFactory(sslSocketFactory, trustManager)
                    .hostnameVerifier(hostnameVerifier)
                    .build()

                val request = Request.Builder()
                    .url("https://$host:$port/api2/json/version")
                    .get()
                    .build()

                val response = try {
                    client.newCall(request).execute()
                } catch (e: IOException) {
                    return@withContext ProbeResult.Unreachable
                }

                val serverHeader = response.header("Server") ?: ""
                val isPveDaemon = serverHeader.contains("pve-api-daemon", ignoreCase = true)

                if (response.isSuccessful) {
                    val body = response.body?.string()
                        ?: return@withContext if (isPveDaemon) ProbeResult.PveDetected else ProbeResult.NotPve
                    val json = try {
                        JSONObject(body)
                    } catch (_: Exception) {
                        null
                    }
                    val data = json?.optJSONObject("data")
                    val version = data?.optString("version")
                    if (version.isNullOrBlank()) {
                        return@withContext if (isPveDaemon) ProbeResult.PveDetected else ProbeResult.NotPve
                    }
                    val release = data.optString("release")
                    val display = if (release.isNotBlank()) "$version-$release" else version
                    ProbeResult.Verified(display)
                } else {
                    // Modern PVE (post CVE-2025-62577) returns 401 for anonymous /version
                    if (isPveDaemon) ProbeResult.PveDetected else ProbeResult.NotPve
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                ProbeResult.Unreachable
            }
        }

    /**
     * Tests a server connection, measuring round-trip latency and verifying PVE version.
     */
    suspend fun testConnection(
        host: String,
        port: Int = 8006,
        timeoutMs: Long = 8000L
    ): ConnectionTestResult {
        if (host.equals("demo", ignoreCase = true)) {
            return ConnectionTestResult(
                online = true,
                version = "8.3.0",
                latencyMs = 12L,
                error = null,
            )
        }

        val start = SystemClock.elapsedRealtime()
        val result = probePveVersion(host, port, timeoutMs)
        val latency = SystemClock.elapsedRealtime() - start

        return when (result) {
            is ProbeResult.Verified -> ConnectionTestResult(
                online = true,
                version = result.version,
                latencyMs = latency,
                error = null,
            )

            is ProbeResult.PveDetected -> ConnectionTestResult(
                online = true,
                version = "login to confirm",
                latencyMs = latency,
                error = "Authentication required",
            )

            is ProbeResult.NotPve -> ConnectionTestResult(
                online = false,
                version = null,
                latencyMs = null,
                error = "Not a Proxmox VE host",
            )

            is ProbeResult.Unreachable -> ConnectionTestResult(
                online = false,
                version = null,
                latencyMs = null,
                error = "Host unreachable",
            )
        }
    }

    /**
     * Sweeps local subnets for PVE hosts on port 8006.
     * Respects scan guard: skips 100.64.0.0/10, caps large subnets to /24.
     */
    fun scanSubnet(
        subnet: SubnetInfo? = null,
        port: Int = 8006,
        concurrency: Int = 32,
    ): Flow<ScanProgress> = channelFlow {
        val targets = if (subnet != null) listOf(subnet) else getScannableSubnets()

        if (targets.isEmpty()) {
            val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNet = cm?.activeNetwork
            val isCellular = activeNet?.let {
                cm.getNetworkCapabilities(it)?.hasTransport(
                    NetworkCapabilities.TRANSPORT_CELLULAR
                )
            } ?: false

            val msg = if (isCellular) "On cellular data — no local network to scan."
            else "No scannable local network found."
            send(
                ScanProgress(
                    scanned = 0,
                    total = 0,
                    currentIp = "",
                    verified = emptyList(),
                    isFinished = true,
                    error = msg
                )
            )
            return@channelFlow
        }

        val scannable = targets.filter { isPrivateSubnet(it.ip) }
        val tailscale = targets.filter { isTailscaleSubnet(it.ip) }

        val infoMsg = if (tailscale.isNotEmpty()) {
            "Tailscale/VPN (100.64.x) — not swept. Add the host manually or test a saved profile."
        } else null

        if (scannable.isEmpty()) {
            send(
                ScanProgress(
                    scanned = 0,
                    total = 0,
                    currentIp = "",
                    verified = emptyList(),
                    isFinished = true,
                    error = infoMsg ?: "No scannable private network found."
                )
            )
            return@channelFlow
        }

        val allIps = scannable.flatMap { generateSubnetIps(it.ip, it.prefixLength) }
        val total = allIps.size
        val scanned = AtomicInteger(0)
        val verifiedList = mutableListOf<DiscoveredHost>()
        val pveDetectedList = mutableListOf<DiscoveredHost>()
        val unverifiedList = mutableListOf<String>()
        val semaphore = Semaphore(concurrency)

        send(
            ScanProgress(
                scanned = 0,
                total = total,
                currentIp = allIps.firstOrNull() ?: "",
                verified = emptyList(),
                infoMessage = infoMsg
            )
        )

        coroutineScope {
            allIps.forEach { ip ->
                launch {
                    semaphore.withPermit {
                        val isOpen = checkPortOpen(ip, port, timeoutMs = 400)
                        if (isOpen) {
                            val probeStart = SystemClock.elapsedRealtime()
                            var result = probePveVersion(ip, port, timeoutMs = 10000L)
                            if (result is ProbeResult.Unreachable) {
                                // One retry for patient probe
                                result = probePveVersion(ip, port, timeoutMs = 10000L)
                            }
                            val latency = SystemClock.elapsedRealtime() - probeStart

                            when (result) {
                                is ProbeResult.Verified -> {
                                    synchronized(verifiedList) {
                                        verifiedList.add(
                                            DiscoveredHost(
                                                ip = ip,
                                                port = port,
                                                version = result.version,
                                                latencyMs = latency
                                            )
                                        )
                                    }
                                }

                                is ProbeResult.PveDetected -> {
                                    synchronized(pveDetectedList) {
                                        pveDetectedList.add(
                                            DiscoveredHost(
                                                ip = ip,
                                                port = port,
                                                version = "login to confirm",
                                                latencyMs = latency,
                                                isPveDetectedOnly = true
                                            )
                                        )
                                    }
                                }

                                else -> {
                                    synchronized(unverifiedList) {
                                        if (unverifiedList.size < 32) unverifiedList.add(ip)
                                    }
                                }
                            }
                        }
                        val currentScanned = scanned.incrementAndGet()
                        val currentVerified = synchronized(verifiedList) { verifiedList.toList() }
                        val currentDetected =
                            synchronized(pveDetectedList) { pveDetectedList.toList() }
                        val currentUnverified =
                            synchronized(unverifiedList) { unverifiedList.toList() }

                        send(
                            ScanProgress(
                                scanned = currentScanned,
                                total = total,
                                currentIp = ip,
                                verified = currentVerified,
                                pveDetected = currentDetected,
                                unverified = currentUnverified,
                                infoMessage = infoMsg
                            )
                        )
                    }
                }
            }
        }

        val finalVerified = synchronized(verifiedList) { verifiedList.toList() }
        val finalDetected = synchronized(pveDetectedList) { pveDetectedList.toList() }
        val finalUnverified = synchronized(unverifiedList) { unverifiedList.toList() }
        send(
            ScanProgress(
                scanned = total,
                total = total,
                currentIp = "",
                verified = finalVerified,
                pveDetected = finalDetected,
                unverified = finalUnverified,
                isFinished = true,
                infoMessage = infoMsg
            )
        )
    }.flowOn(Dispatchers.IO)

    companion object {
        fun parseIpv4(ip: String): Long? {
            val parts = ip.trim().split('.').mapNotNull { it.toLongOrNull() }
            if (parts.size != 4 || parts.any { it !in 0..255 }) return null
            return (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
        }

        fun formatIpv4(ipLong: Long): String {
            val b0 = (ipLong ushr 24) and 0xFF
            val b1 = (ipLong ushr 16) and 0xFF
            val b2 = (ipLong ushr 8) and 0xFF
            val b3 = ipLong and 0xFF
            return "$b0.$b1.$b2.$b3"
        }
    }
}
