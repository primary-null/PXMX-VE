package com.pxmx.app.data.repo

import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.ClusterLogEntry
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.ClusterStatusEntry
import com.pxmx.app.data.model.NetworkIface
import com.pxmx.app.data.model.NodeBundle
import com.pxmx.app.data.model.NodeNetworkSnapshot
import com.pxmx.app.data.model.NodeServiceInfo
import com.pxmx.app.data.model.NodeStatus
import com.pxmx.app.data.model.NodeTaskInfo
import com.pxmx.app.data.model.SiteInfo
import com.pxmx.app.data.model.TaskStatus
import com.pxmx.app.data.model.VersionInfo
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles cluster topology, resource aggregation, node status, services, tasks,
 * syslog retrieval with proxy timeout resilience, and network inspection.
 */
class NodeRepository(
    private val pveClient: PveClient,
    private val sessionStore: SessionStore,
    private val clientFactory: ProxmoxApiProvider,
    private val activeTaskProvider: () -> ActiveAptTask?,
    private val updateTaskLogLine: (node: String, upid: String, type: String, line: String) -> Unit,
    private val onClusterLogUpdated: (List<ClusterLogEntry>) -> Unit,
    private val loadGuests: suspend (ProxmoxApi, String, String) -> List<ClusterResource>,
    private val loadStorage: suspend (ProxmoxApi, String) -> List<ClusterResource>,
) {
    private val clusterNodeIps = ConcurrentHashMap<String, String>()

    fun clearCache() {
        clusterNodeIps.clear()
    }

    /** Extract node IPs from `/cluster/status` entries and cache them for direct-node fallback. */
    fun cacheNodeIps(statusEntries: List<Map<String, Any>>) {
        statusEntries
            .map { ClusterStatusEntry.fromMap(it) }
            .filter { it.isNode }
            .forEach { node ->
                val name = node.name
                val ip = node.ip
                if (!name.isNullOrBlank() && !ip.isNullOrBlank()) {
                    clusterNodeIps[name.lowercase()] = ip
                }
            }
    }

    suspend fun refreshVersion(): Result<VersionInfo> {
        return pveClient.apiCall { api ->
            val v = api.version().data ?: throw PveException("No version data")
            sessionStore.updateVersion(v)
            v
        }
    }

    /**
     * Detect real multi-node cluster vs standalone.
     * Standalone still answers on `/cluster/status` with a single local node
     * and **no** `type=cluster` entry — do not label that as a cluster in UI.
     */
    suspend fun siteInfo(): Result<SiteInfo> {
        return pveClient.apiCall { api ->
            val rawStatus = api.clusterStatus().data.orEmpty()
            cacheNodeIps(rawStatus)
            val status = rawStatus.map { ClusterStatusEntry.fromMap(it) }
            val nodes = status.filter { it.isNode }
            val clusterEntry = status.firstOrNull { it.isCluster }
            val localNode = nodes.firstOrNull { it.isLocal } ?: nodes.firstOrNull()
            val nodeName = localNode?.name
                ?: attemptRead { api.nodes().data.orEmpty().firstOrNull()?.node }.getOrNull()
            val nodeCount = nodes.size.coerceAtLeast(
                attemptRead { api.nodes().data.orEmpty().size }.getOrDefault(1),
            )
            val clusterName = clusterEntry?.name
            val isCluster = clusterEntry != null || nodeCount > 1
            SiteInfo(
                isCluster = isCluster,
                clusterName = clusterName,
                nodeName = nodeName,
                nodeCount = nodeCount,
            )
        }
    }

    /**
     * Build a rich resource list from per-node endpoints.
     * Note: `/cluster/resources` is an API path even on standalone — prefer node APIs.
     */
    suspend fun listResources(type: String? = null): Result<List<ClusterResource>> {
        return pveClient.apiCall { api ->
            val nodeNames = discoverNodeNames(api)
            if (nodeNames.isEmpty()) {
                return@apiCall api.clusterResources(type).data.orEmpty()
            }

            val out = coroutineScope {
                nodeNames.map { nodeName ->
                    async {
                        val nodeRes = loadNodeResource(api, nodeName)
                        val qemu = loadGuests(api, nodeName, "qemu")
                        val lxc = loadGuests(api, nodeName, "lxc")
                        val storage = loadStorage(api, nodeName)
                        listOf(nodeRes) + qemu + lxc + storage
                    }
                }.flatMap { it.await() }
            }

            when (type) {
                null -> out
                "vm" -> out.filter { it.type == "qemu" || it.type == "lxc" }
                else -> out.filter { it.type == type }
            }
        }
    }

    suspend fun discoverNodeNames(api: ProxmoxApi): List<String> {
        val fromNodes = api.nodes().data.orEmpty().mapNotNull { it.node }.filter { it.isNotBlank() }
        if (clusterNodeIps.isEmpty()) {
            attemptRead {
                cacheNodeIps(api.clusterStatus().data.orEmpty())
            }
        }
        if (fromNodes.isNotEmpty()) return fromNodes.distinct()
        return api.clusterResources("node").data.orEmpty()
            .mapNotNull { it.node }
            .filter { it.isNotBlank() }
            .distinct()
    }

    suspend fun loadNodeResource(
        api: ProxmoxApi,
        nodeName: String,
    ): ClusterResource {
        val status = try {
            api.nodeStatus(nodeName).data
        } catch (e: Exception) {
            e.rethrowAuthOrCancellation()
            null
        }
        return ClusterResource(
            id = "node/$nodeName",
            type = "node",
            node = nodeName,
            name = nodeName,
            status = if (status != null) "online" else "unknown",
            uptime = status?.uptime,
            cpu = status?.cpu,
            mem = status?.memory?.used,
            maxmem = status?.memory?.total,
            disk = status?.rootfs?.used,
            maxdisk = status?.rootfs?.total,
        )
    }

    suspend fun nodeStatus(node: String): Result<NodeStatus> {
        return pveClient.apiCall { api ->
            api.nodeStatus(node).data ?: throw PveException("No node status")
        }
    }

    /** Full node ops bundle: status, services, recent tasks. */
    suspend fun loadNodeBundle(node: String): Result<NodeBundle> = pveClient.apiCall { api ->
        val status = api.nodeStatus(node).data
        val services = api.nodeServices(node).data.orEmpty()
            .map { NodeServiceInfo.fromMap(it) }
            .sortedBy { it.name.orEmpty() }
        val tasks = api.nodeTasks(node, start = 0, limit = 25).data.orEmpty()
            .map { NodeTaskInfo.fromMap(it) }
        NodeBundle(node = node, status = status, services = services, tasks = tasks)
    }

    suspend fun listNodeNames(): Result<List<String>> = pveClient.apiCall { api ->
        discoverNodeNames(api)
    }

    suspend fun listNodeStorageNames(node: String): Result<List<String>> = pveClient.apiCall { api ->
        api.nodeStorage(node).data.orEmpty().mapNotNull { it.storage }
    }

    /**
     * Network interfaces for every node (read-only overview).
     */
    suspend fun listClusterNetwork(): Result<List<NodeNetworkSnapshot>> = pveClient.apiCall { api ->
        val nodes = discoverNodeNames(api)
        nodes.map { node ->
            val ifaces = api.nodeNetwork(node).data.orEmpty()
                .map { raw -> NetworkIface.fromMap(raw) }
                .sortedWith(
                    compareBy<NetworkIface> { it.type.orEmpty() }
                        .thenBy { it.iface.orEmpty() },
                )
            NodeNetworkSnapshot(node = node, interfaces = ifaces)
        }
    }

    suspend fun clusterTasks(): Result<List<Map<String, Any>>> = pveClient.apiCall { api ->
        api.clusterTasks().data.orEmpty()
    }

    suspend fun taskStatus(node: String, upid: String): Result<TaskStatus> = pveClient.apiCall { api ->
        api.taskStatus(node, upid).data ?: throw PveException("No task status")
    }

    suspend fun awaitTask(
        node: String,
        upid: String,
        timeoutMs: Long = 60_000,
        intervalMs: Long = 1_000,
    ): Result<TaskStatus> {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val result = taskStatus(node, upid)
            val status = result.getOrElse { return Result.failure(it) }
            if (!status.isRunning) return Result.success(status)
            delay(intervalMs)
        }
        return Result.failure(PveException("Task timed out: $upid"))
    }

    suspend fun taskLog(
        node: String,
        upid: String,
        start: Int? = null,
        limit: Int? = 20,
    ): Result<List<String>> = pveClient.apiCall { api ->
        val lines = api.taskLog(node, upid, start, limit).data.orEmpty()
        lines.mapNotNull { it["t"]?.toString() }
    }

    suspend fun logPoll(max: Int = 10): Result<List<ClusterLogEntry>> = pveClient.apiCall { api ->
        val activeTask = activeTaskProvider()
        if (activeTask != null) {
            val lines = attemptRead {
                api.taskLog(activeTask.node, activeTask.upid, limit = 5).data.orEmpty()
            }.getOrDefault(emptyList())
            val lastLine = lines.lastOrNull()?.get("t")?.toString()
            if (!lastLine.isNullOrBlank()) {
                updateTaskLogLine(activeTask.node, activeTask.upid, activeTask.type, lastLine)
            }
        }
        val entries = api.clusterLog(max = max).data.orEmpty()
        onClusterLogUpdated(entries)
        entries
    }

    suspend fun logHistory(max: Int = 200): Result<List<ClusterLogEntry>> = pveClient.apiCall { api ->
        val entries = api.clusterLog(max = max).data.orEmpty()
        onClusterLogUpdated(entries)
        entries
    }

    suspend fun nodeSyslog(
        node: String,
        start: Int? = null,
        limit: Int? = 50,
    ): Result<List<ClusterLogEntry>> {
        val initialLimit = limit ?: 50

        fun isTimeout(t: Throwable): Boolean {
            if (t is PveClusterProxyTimeoutException) return true
            if (t is java.net.SocketTimeoutException) return true
            if (t is HttpException && (t.code() == 596 || t.code() == 504)) return true
            if (t is PveHttpException && (t.code == 596 || t.code == 504)) return true
            val msg = t.message.orEmpty()
            return msg.contains("HTTP 596", ignoreCase = true) ||
                msg.contains("Connection timed out", ignoreCase = true)
        }

        val firstResult = pveClient.apiCall { api ->
            val rows = api.nodeSyslog(node, start, initialLimit).data.orEmpty()
            rows.map { ClusterLogEntry.fromSyslogMap(node, it) }
        }

        if (firstResult.isSuccess) return firstResult

        val firstError = firstResult.exceptionOrNull() ?: return firstResult
        if (!isTimeout(firstError)) {
            return firstResult
        }

        val reducedLimit = when {
            initialLimit > 50 -> 50
            initialLimit > 25 -> 25
            else -> null
        }

        if (reducedLimit != null) {
            val retryResult = pveClient.apiCall { api ->
                val rows = api.nodeSyslog(node, start, reducedLimit).data.orEmpty()
                rows.map { ClusterLogEntry.fromSyslogMap(node, it) }
            }
            if (retryResult.isSuccess) return retryResult
        }

        val directIp = clusterNodeIps[node.lowercase()]
        val session = sessionStore.session.value
        if (!directIp.isNullOrBlank() && session != null && !session.config.host.equals(directIp, ignoreCase = true)) {
            try {
                val directConfig = session.config.copy(host = directIp)
                val directApi = clientFactory.apiFor(directConfig)
                val rows = directApi.nodeSyslog(node, start, reducedLimit ?: initialLimit).data.orEmpty()
                return Result.success(rows.map { ClusterLogEntry.fromSyslogMap(node, it) })
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fall through to final timeout error
            }
        }

        return Result.failure(
            PveClusterProxyTimeoutException(
                node = node,
                message = "HTTP 596: Inter-node proxy timed out for node '$node'. Target node did not respond within the 30-second Proxmox cluster proxy window.",
                cause = firstError,
            )
        )
    }
}
