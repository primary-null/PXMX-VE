package com.pxmx.app.data.api

import com.pxmx.app.data.model.ClusterLogEntry
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.ConsoleProxyData
import com.pxmx.app.data.model.DiskInfo
import com.pxmx.app.data.model.GuestStatus
import com.pxmx.app.data.model.HostUsbDevice
import com.pxmx.app.data.model.MemoryInfo
import com.pxmx.app.data.model.NodeStatus
import com.pxmx.app.data.model.NodeStorageEntry
import com.pxmx.app.data.model.PveResponse
import com.pxmx.app.data.model.SnapshotInfo
import com.pxmx.app.data.model.StorageContentItem
import com.pxmx.app.data.model.StorageStatus
import com.pxmx.app.data.model.TaskStatus
import com.pxmx.app.data.model.TicketData
import com.pxmx.app.data.model.VersionInfo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Demo-mode backend: a full canned Proxmox 3-node cluster so the app is fully browsable
 * offline — no real machines, no network, no credentials. Activated by logging
 * in with host == "demo" (see LoginViewModel's hidden 5-tap title gesture).
 *
 * 1:1 ambitions: this is a LIVE simulation, not a static dump.
 * - Multi-node cluster: alpha (24C/64G), beta (16C/32G), gamma (8C/16G) in quorate "demo-cluster".
 * - Uptimes tick (each guest and node has a fixed boot epoch, computed from first access).
 * - Memory/CPU wobble slightly over time (±2% every 10s).
 * - Power actions actually mutate guest state (start/stop/shutdown/reboot/
 *   suspend/resume), and the app's next poll reflects the change.
 * - Storage rows show full color range (green < 60%, amber 60-85%, red > 85%).
 * - Background tasks transition to OK after ~30s.
 * - Console proxy calls fail gracefully (needs live websocket).
 *
 * State is per-instance; ProxmoxClientFactory hands out ONE instance so the
 * simulation persists across app requests.
 */
class DemoApi : ProxmoxApi {

    private val bootEpochMs = ConcurrentHashMap<Long, Long>()
    private val nodeBootEpoch = ConcurrentHashMap<String, Long>()
    private val statusOverride = ConcurrentHashMap<Long, String>()
    private val configOverride = ConcurrentHashMap<Long, MutableMap<String, Any>>()
    private val clonedConfigs = ConcurrentHashMap<Long, Map<String, Any>>()

    private fun now(): Long = System.currentTimeMillis()

    private val demoLogEntries = CopyOnWriteArrayList<ClusterLogEntry>().apply {
        val nowSec = System.currentTimeMillis() / 1000
        addAll(
            listOf(
                ClusterLogEntry(
                    id = "log-init-1",
                    time = nowSec - 60,
                    node = "beta",
                    tag = "pmxcfs",
                    msg = "cluster configuration synchronized",
                    pri = 6,
                    user = "root@pam",
                    pid = 980,
                ),
                ClusterLogEntry(
                    id = "log-init-2",
                    time = nowSec - 180,
                    node = "alpha",
                    tag = "vzdump",
                    msg = "Backup job finished successfully for VM 100",
                    pri = 6,
                    user = "root@pam",
                    pid = 1420,
                ),
                ClusterLogEntry(
                    id = "log-init-3",
                    time = nowSec - 300,
                    node = "gamma",
                    tag = "pvestatd",
                    msg = "node status update: all storage pools online",
                    pri = 6,
                    user = "root@pam",
                    pid = 1350,
                ),
                ClusterLogEntry(
                    id = "log-init-4",
                    time = nowSec - 420,
                    node = "beta",
                    tag = "systemd",
                    msg = "Started Proxmox VE replication runner",
                    pri = 5,
                    user = "root@pam",
                    pid = 1205,
                ),
                ClusterLogEntry(
                    id = "log-init-5",
                    time = nowSec - 600,
                    node = "alpha",
                    tag = "pvedaemon",
                    msg = "successful auth for user 'root@pam'",
                    pri = 6,
                    user = "root@pam",
                    pid = 1120,
                ),
                ClusterLogEntry(
                    id = "log-init-6",
                    time = nowSec - 900,
                    node = "alpha",
                    tag = "corosync",
                    msg = "Cluster quorum established (3 nodes)",
                    pri = 6,
                    user = "root@pam",
                    pid = 1042,
                ),
            )
        )
    }

    private fun nodeUptime(node: String): Long {
        val bootBase = when (node) {
            "beta" -> 864_000_000L   // ~10 days
            "gamma" -> 1_209_600_000L // ~14 days
            else -> 150_000_000L     // ~41 hours (alpha)
        }
        val epoch = nodeBootEpoch.computeIfAbsent(node) { now() - bootBase }
        return (now() - epoch) / 1000
    }

    /** Fixed boot epochs so uptimes look alive at first glance, then tick. */
    private fun bootedAt(vmid: Long): Long = bootEpochMs.computeIfAbsent(vmid) {
        when (it) {
            100L -> now() - 50_000_000L        // ~13.9h — "nova" (alpha)
            102L -> now() - 14L * 86_400_000L  // ~14.0d — "quasar" (gamma)
            200L -> now() - 20_000_000L        // ~5.6h  — "nebula" (alpha)
            202L -> now() - 172_800_000L       // ~2.0d  — "pulsar" (beta)
            203L -> now() - 259_200_000L       // ~3.0d  — "comet" (beta frozen)
            204L -> now() - 432_000_000L       // ~5.0d  — "aurora" (gamma)
            else -> now()
        }
    }

    private fun statusOf(vmid: Long): String =
        statusOverride[vmid] ?: when (vmid) {
            100L, 102L, 200L, 202L, 204L -> "running"
            203L -> "frozen"
            else -> "stopped"
        }

    private fun uptimeOf(vmid: Long): Long = when (statusOf(vmid)) {
        "running", "paused", "suspended", "frozen" -> (now() - bootedAt(vmid)) / 1000
        else -> 0L
    }

    /** ±2% wobble every 10s so live views never look frozen. */
    private fun memUsed(base: Long): Long {
        val wobble = ((now() / 10_000) % 5 - 2) * base / 100
        return (base + wobble).coerceAtLeast(0L)
    }

    private fun cpuWobble(base: Double): Double {
        val wobble = ((now() / 10_000) % 5 - 2) * 0.01
        return (base + wobble).coerceIn(0.01, 0.99)
    }

    private fun guestRow(
        id: String,
        type: String,
        node: String,
        vmid: Long,
        name: String,
        baseMem: Long,
        maxMem: Long,
        cpus: Int,
    ): ClusterResource {
        val status = statusOf(vmid)
        val isAlive = status == "running" || status == "paused" || status == "suspended" || status == "frozen"
        return ClusterResource(
            id = id, type = type, node = node, vmid = vmid, name = name,
            status = status,
            mem = if (isAlive) memUsed(baseMem) else 0L,
            maxmem = maxMem, cpus = cpus,
            uptime = uptimeOf(vmid),
        )
    }

    override suspend fun createTicket(username: String, password: String): PveResponse<TicketData> {
        val userOnly = username.substringBefore('@')
        if (userOnly.equals("tfa-user", ignoreCase = true)) {
            return PveResponse(data = TicketData(
                ticket = "PVE:tfa-user@pam!tfa!DEMO-PARTIAL",
                csrfPreventionToken = "demo-csrf-partial",
                username = username,
                needTfa = 1,
            ))
        }
        return PveResponse(data = TicketData(
            ticket = "demo-ticket",
            csrfPreventionToken = "demo-csrf",
            username = username,
        ))
    }

    override suspend fun createTicketTfa(username: String, password: String, tfaChallenge: String): PveResponse<TicketData> {
        if (password.trim() == "totp:123456") {
            return PveResponse(data = TicketData(
                ticket = "demo-ticket-tfa-verified",
                csrfPreventionToken = "demo-csrf",
                username = username,
            ))
        } else {
            val errorBody = "{\"errors\":{\"password\":\"Invalid one-time password\"},\"message\":\"authentication failure\"}"
                .toResponseBody("application/json".toMediaType())
            throw HttpException(Response.error<TicketData>(401, errorBody))
        }
    }

    override suspend fun version(): PveResponse<VersionInfo> {
        return PveResponse(data = VersionInfo(version = "8.3.0", release = "8.3", repoid = "pve"))
    }

    override suspend fun clusterStatus(): PveResponse<List<Map<String, Any>>> {
        return PveResponse(data = listOf(
            mapOf(
                "type" to "cluster",
                "name" to "demo-cluster",
                "id" to "cluster",
                "nodes" to 3,
                "quorate" to 1,
                "version" to 3,
            ),
            mapOf(
                "type" to "node",
                "node" to "alpha",
                "name" to "alpha",
                "nodeid" to 1,
                "status" to "online",
                "online" to 1,
                "local" to 1,
                "ip" to "192.0.2.10",
                "level" to "",
                "cpu" to cpuWobble(0.15),
                "maxcpu" to 24,
                "mem" to memUsed(35_200_000_000L),
                "maxmem" to 64_000_000_000L,
            ),
            mapOf(
                "type" to "node",
                "node" to "beta",
                "name" to "beta",
                "nodeid" to 2,
                "status" to "online",
                "online" to 1,
                "local" to 0,
                "ip" to "192.0.2.11",
                "level" to "",
                "cpu" to cpuWobble(0.28),
                "maxcpu" to 16,
                "mem" to memUsed(25_600_000_000L),
                "maxmem" to 32_000_000_000L,
            ),
            mapOf(
                "type" to "node",
                "node" to "gamma",
                "name" to "gamma",
                "nodeid" to 3,
                "status" to "online",
                "online" to 1,
                "local" to 0,
                "ip" to "192.0.2.12",
                "level" to "",
                "cpu" to cpuWobble(0.08),
                "maxcpu" to 8,
                "mem" to memUsed(14_400_000_000L),
                "maxmem" to 16_000_000_000L,
            ),
        ))
    }

    override suspend fun nodes(): PveResponse<List<ClusterResource>> {
        return PveResponse(data = listOf(
            ClusterResource(
                id = "node/alpha", type = "node", node = "alpha", name = "alpha",
                status = "online", cpu = cpuWobble(0.15), maxcpu = 24,
                mem = memUsed(35_200_000_000L), maxmem = 64_000_000_000L,
                disk = 120_000_000_000L, maxdisk = 500_000_000_000L,
                uptime = nodeUptime("alpha"),
            ),
            ClusterResource(
                id = "node/beta", type = "node", node = "beta", name = "beta",
                status = "online", cpu = cpuWobble(0.28), maxcpu = 16,
                mem = memUsed(25_600_000_000L), maxmem = 32_000_000_000L,
                disk = 80_000_000_000L, maxdisk = 250_000_000_000L,
                uptime = nodeUptime("beta"),
            ),
            ClusterResource(
                id = "node/gamma", type = "node", node = "gamma", name = "gamma",
                status = "online", cpu = cpuWobble(0.08), maxcpu = 8,
                mem = memUsed(14_400_000_000L), maxmem = 16_000_000_000L,
                disk = 45_000_000_000L, maxdisk = 120_000_000_000L,
                uptime = nodeUptime("gamma"),
            ),
        ))
    }

    private data class NodeSpecs(
        val maxcpu: Int,
        val maxmem: Long,
        val baseMem: Long,
        val usedDisk: Long,
        val totalDisk: Long,
        val baseCpu: Double,
    )

    override suspend fun nodeStatus(node: String): PveResponse<NodeStatus> {
        val specs = when (node) {
            "beta" -> NodeSpecs(16, 32_000_000_000L, 25_600_000_000L, 80_000_000_000L, 250_000_000_000L, 0.28)
            "gamma" -> NodeSpecs(8, 16_000_000_000L, 14_400_000_000L, 45_000_000_000L, 120_000_000_000L, 0.08)
            else -> NodeSpecs(24, 64_000_000_000L, 35_200_000_000L, 120_000_000_000L, 500_000_000_000L, 0.15)
        }
        return PveResponse(data = NodeStatus(
            uptime = nodeUptime(node),
            cpu = cpuWobble(specs.baseCpu),
            memory = MemoryInfo(used = memUsed(specs.baseMem), total = specs.maxmem),
            rootfs = DiskInfo(used = specs.usedDisk, total = specs.totalDisk),
            pveversion = "8.3.0",
        ))
    }

    override suspend fun nodeQemu(node: String): PveResponse<List<ClusterResource>> {
        val base = when (node) {
            "alpha" -> mutableListOf(
                guestRow("qemu/100", "qemu", "alpha", 100L, "nova", 1024000000L, 2048000000L, 2),
                ClusterResource(
                    id = "qemu/9000", type = "qemu", node = "alpha", vmid = 9000L, name = "linux-template",
                    status = "stopped", mem = 0L, maxmem = 2048000000L, cpus = 2, template = 1,
                ),
            )
            "beta" -> mutableListOf(
                guestRow("qemu/101", "qemu", "beta", 101L, "meteor", 1024000000L, 2048000000L, 2),
            )
            "gamma" -> mutableListOf(
                guestRow("qemu/102", "qemu", "gamma", 102L, "quasar", 2048000000L, 4096000000L, 4),
            )
            else -> mutableListOf()
        }
        clonedConfigs.forEach { (vmid, cfg) ->
            val cloneNode = (cfg["node"] as? String) ?: "alpha"
            if (cloneNode == node) {
                base.add(
                    guestRow(
                        "qemu/$vmid", "qemu", node, vmid, (cfg["name"] as? String) ?: "clone-$vmid",
                        1024000000L, 2048000000L, 2,
                    )
                )
            }
        }
        return PveResponse(data = base)
    }

    override suspend fun nodeLxc(node: String): PveResponse<List<ClusterResource>> {
        val list = when (node) {
            "alpha" -> listOf(
                guestRow("lxc/200", "lxc", "alpha", 200L, "nebula", 512000000L, 1024000000L, 1),
            )
            "beta" -> listOf(
                guestRow("lxc/202", "lxc", "beta", 202L, "pulsar", 1536000000L, 2048000000L, 2),
                guestRow("lxc/203", "lxc", "beta", 203L, "comet", 512000000L, 1024000000L, 1),
            )
            "gamma" -> listOf(
                guestRow("lxc/204", "lxc", "gamma", 204L, "aurora", 512000000L, 1024000000L, 1),
                guestRow("lxc/201", "lxc", "gamma", 201L, "galaxy", 512000000L, 1024000000L, 1),
            )
            else -> emptyList()
        }
        return PveResponse(data = list)
    }

    override suspend fun guestConfig(node: String, type: String, vmid: Long, current: Int?): PveResponse<Map<String, Any>> {
        clonedConfigs[vmid]?.let { base ->
            val overrides = configOverride[vmid] ?: return PveResponse(data = base)
            val merged = (base as Map<String, Any>).toMutableMap().apply { putAll(overrides) }
            return PveResponse(data = merged)
        }
        val config = when (vmid) {
            100L -> mapOf(
                "name" to "nova",
                "ostype" to "l26",
                "onboot" to 1,
                "cores" to 2,
                "memory" to 2048,
                "net0" to "virtio=AA:BB:CC:DD:EE:FF,bridge=vmbr0",
                "scsi0" to "local-lvm:vm-100-disk-0,size=32G",
            )
            101L -> mapOf(
                "name" to "meteor",
                "ostype" to "l26",
                "onboot" to 0,
                "cores" to 2,
                "memory" to 2048,
                "net0" to "virtio=11:22:33:44:55:66,bridge=vmbr0",
                "scsi0" to "local:101/vm-101-disk-0.raw,size=32G",
            )
            102L -> mapOf(
                "name" to "quasar",
                "ostype" to "l26",
                "onboot" to 1,
                "cores" to 4,
                "memory" to 4096,
                "net0" to "virtio=33:44:55:66:77:88,bridge=vmbr0",
                "scsi0" to "local-lvm:vm-102-disk-0,size=64G",
            )
            200L -> mapOf(
                "hostname" to "nebula",
                "ostype" to "ubuntu",
                "onboot" to 1,
                "cores" to 1,
                "memory" to 1024,
                "rootfs" to "local-lvm:subvol-200-disk-0,size=8G",
            )
            201L -> mapOf(
                "hostname" to "galaxy",
                "ostype" to "debian",
                "onboot" to 0,
                "cores" to 1,
                "memory" to 1024,
                "rootfs" to "local-lvm:subvol-201-disk-0,size=8G",
            )
            202L -> mapOf(
                "hostname" to "pulsar",
                "ostype" to "debian",
                "onboot" to 1,
                "cores" to 2,
                "memory" to 2048,
                "rootfs" to "zfs-pool:subvol-202-disk-0,size=32G",
            )
            203L -> mapOf(
                "hostname" to "comet",
                "ostype" to "alpine",
                "onboot" to 0,
                "cores" to 1,
                "memory" to 1024,
                "rootfs" to "zfs-pool:subvol-203-disk-0,size=8G",
            )
            204L -> mapOf(
                "hostname" to "aurora",
                "ostype" to "ubuntu",
                "onboot" to 1,
                "cores" to 1,
                "memory" to 1024,
                "rootfs" to "local-lvm:subvol-204-disk-0,size=16G",
            )
            9000L -> mapOf(
                "name" to "linux-template",
                "ostype" to "l26",
                "onboot" to 0,
                "cores" to 2,
                "memory" to 2048,
                "net0" to "virtio=AA:BB:CC:DD:EE:01,bridge=vmbr0",
                "scsi0" to "local-lvm:vm-9000-disk-0,size=32G",
            )
            else -> emptyMap()
        }
        val overrides = configOverride[vmid] ?: return PveResponse(data = config)
        val merged = (config as Map<String, Any>).toMutableMap().apply { putAll(overrides) }
        return PveResponse(data = merged)
    }

    override suspend fun guestStatus(node: String, type: String, vmid: Long): PveResponse<GuestStatus> {
        val name = clonedConfigs[vmid]?.let { it["name"] as? String ?: it["hostname"] as? String } ?: when (vmid) {
            100L -> "nova"
            101L -> "meteor"
            102L -> "quasar"
            200L -> "nebula"
            201L -> "galaxy"
            202L -> "pulsar"
            203L -> "comet"
            204L -> "aurora"
            9000L -> "linux-template"
            else -> null
        }
        return PveResponse(data = GuestStatus(status = statusOf(vmid), name = name))
    }

    override suspend fun cloneGuest(
        node: String,
        type: String,
        vmid: Long,
        newid: Long,
        name: String?,
        hostname: String?
    ): PveResponse<String> {
        statusOverride[newid] = "stopped"
        val resolvedName = name ?: hostname ?: "clone-$newid"
        clonedConfigs[newid] = mapOf(
            "name" to resolvedName,
            "hostname" to (hostname ?: name ?: "clone-$newid"),
            "node" to node,
            "ostype" to "l26",
            "cores" to 2,
            "memory" to 2048
        )
        demoLogEntries.add(
            0,
            ClusterLogEntry(
                id = "log-${System.currentTimeMillis()}-$newid",
                time = System.currentTimeMillis() / 1000,
                node = node,
                tag = if (type == "qemu") "qm" else "pct",
                msg = "clone $vmid to $newid ($resolvedName): clone finished",
                pri = 5,
                user = "root@pam",
                pid = (1000L..9999L).random(),
            )
        )
        return PveResponse(data = "UPID:$node:demo:clone:$newid")
    }

    override suspend fun guestSnapshots(node: String, type: String, vmid: Long): PveResponse<List<SnapshotInfo>> {
        return if (vmid == 100L) {
            PveResponse(data = listOf(SnapshotInfo(name = "snap1", description = "Pre-deploy baseline")))
        } else {
            PveResponse(data = emptyList())
        }
    }

    override suspend fun nodeStorage(node: String): PveResponse<List<NodeStorageEntry>> {
        val list = when (node) {
            "alpha" -> listOf(
                NodeStorageEntry(
                    storage = "local", type = "dir", content = "backup,iso",
                    active = 1, enabled = 1, total = 100_000_000_000L, used = 77_000_000_000L,
                ),
                NodeStorageEntry(
                    storage = "local-lvm", type = "lvmthin", content = "rootdir,images",
                    active = 1, enabled = 1, total = 200_000_000_000L, used = 70_000_000_000L,
                ),
            )
            "beta" -> listOf(
                NodeStorageEntry(
                    storage = "local", type = "dir", content = "backup,iso",
                    active = 1, enabled = 1, total = 100_000_000_000L, used = 46_000_000_000L,
                ),
                NodeStorageEntry(
                    storage = "zfs-pool", type = "zfspool", content = "rootdir,images",
                    active = 1, enabled = 1, total = 1_000_000_000_000L, used = 880_000_000_000L,
                ),
            )
            "gamma" -> listOf(
                NodeStorageEntry(
                    storage = "local-lvm", type = "lvmthin", content = "rootdir,images",
                    active = 1, enabled = 1, total = 200_000_000_000L, used = 130_000_000_000L,
                ),
                NodeStorageEntry(
                    storage = "nfs-share", type = "nfs", content = "backup,iso,vztmpl",
                    active = 1, enabled = 1, total = 1_000_000_000_000L, used = 520_000_000_000L, shared = 1,
                ),
            )
            else -> emptyList()
        }
        return PveResponse(data = list)
    }

    override suspend fun storageContent(node: String, storage: String, content: String?, vmid: Long?): PveResponse<List<StorageContentItem>> {
        val items = when (storage) {
            "local" -> listOf(
                StorageContentItem(
                    volid = "$storage:backup/vzdump-qemu-100-2026_08_30-12_00_01.tar.zst",
                    content = "backup", size = 500000000L, vmid = 100L,
                    path = "/var/lib/vz/dump/vzdump-qemu-100-2026_08_30-12_00_01.tar.zst"
                ),
            )
            "nfs-share" -> listOf(
                StorageContentItem(
                    volid = "$storage:backup/vzdump-qemu-102-2026_08_31-00_00_00.tar.zst",
                    content = "backup", size = 1200000000L, vmid = 102L,
                    path = "/mnt/pve/nfs-share/dump/vzdump-qemu-102-2026_08_31-00_00_00.tar.zst"
                ),
                StorageContentItem(
                    volid = "$storage:backup/vzdump-lxc-200-2026_08_31-04_00_00.tar.zst",
                    content = "backup", size = 250000000L, vmid = 200L,
                    path = "/mnt/pve/nfs-share/dump/vzdump-lxc-200-2026_08_31-04_00_00.tar.zst"
                ),
            )
            else -> emptyList()
        }
        val filtered = if (content != null) items.filter { it.content == content } else items
        return PveResponse(data = if (vmid != null) filtered.filter { it.vmid == vmid } else filtered)
    }

    override suspend fun storageVolume(node: String, storage: String, volume: String): PveResponse<StorageContentItem> {
        return PveResponse(data = StorageContentItem(
            volid = "$storage:$volume",
            content = "backup",
            size = 500000000L,
            vmid = 100L,
            path = "/var/lib/vz/dump/${volume.substringAfterLast('/')}"
        ))
    }

    // A task that starts "running" and completes ~30s after first sight.
    private var aptTaskStart: Long = 0L

    private fun aptTaskStatus(): String =
        if (aptTaskStart == 0L || now() - aptTaskStart < 30_000L) "running" else "OK"

    override suspend fun clusterTasks(): PveResponse<List<Map<String, Any>>> {
        if (aptTaskStart == 0L) aptTaskStart = now()
        val starttime = aptTaskStart / 1000
        return PveResponse(data = listOf(
            mapOf(
                "upid" to "UPID:alpha:00001234:00000000:66D1B000:vzdump:100:root@pam:",
                "node" to "alpha", "type" to "vzdump", "user" to "root@pam",
                "status" to "OK", "starttime" to starttime - 120L, "endtime" to starttime - 110L,
            ),
            mapOf(
                "upid" to "UPID:alpha:00001235:00000000:66D1B001:aptupdate::root@pam:",
                "node" to "alpha", "type" to "aptupdate", "user" to "root@pam",
                "status" to aptTaskStatus(), "starttime" to starttime,
            ),
            mapOf(
                "upid" to "UPID:beta:00001236:00000000:66D1B002:vzdump:202:root@pam:",
                "node" to "beta", "type" to "vzdump", "user" to "root@pam",
                "status" to "OK", "starttime" to starttime - 300L, "endtime" to starttime - 280L,
            ),
            mapOf(
                "upid" to "UPID:gamma:00001237:00000000:66D1B003:qmstart:102:root@pam:",
                "node" to "gamma", "type" to "qmstart", "user" to "root@pam",
                "status" to "OK", "starttime" to starttime - 600L, "endtime" to starttime - 595L,
            ),
        ))
    }

    override suspend fun nodeTasks(node: String, start: Int?, limit: Int?): PveResponse<List<Map<String, Any>>> {
        val all = clusterTasks().data.orEmpty()
        return PveResponse(data = all.filter { it["node"] == node })
    }

    override suspend fun nodeServices(node: String): PveResponse<List<Map<String, Any>>> {
        return PveResponse(data = listOf(
            mapOf("name" to "pveproxy", "desc" to "PVE API Proxy", "state" to "running"),
            mapOf("name" to "pvedaemon", "desc" to "PVE Daemon", "state" to "running"),
            mapOf("name" to "pve-cluster", "desc" to "Cluster Engine", "state" to "running"),
            mapOf("name" to "corosync", "desc" to "Corosync Cluster Engine", "state" to "running"),
        ))
    }

    override suspend fun guestAction(node: String, type: String, vmid: Long, action: String): PveResponse<String> {
        when (action) {
            "start" -> {
                statusOverride[vmid] = "running"
                bootEpochMs[vmid] = now() // fresh uptime, like a real boot
            }
            "stop", "shutdown" -> statusOverride[vmid] = "stopped"
            "reboot" -> {
                statusOverride[vmid] = "running"
                bootEpochMs[vmid] = now() // stays running, uptime resets
            }
            "suspend" -> statusOverride[vmid] = "paused"
            "resume" -> statusOverride[vmid] = "running"
            // Unknown actions: accept silently (matches a tolerant API surface).
        }
        val tag = if (type == "qemu") "qm" else "pct"
        val pri = when (action) {
            "stop", "shutdown" -> 4 // warning
            else -> 6 // info
        }
        demoLogEntries.add(
            0,
            ClusterLogEntry(
                id = "log-${System.currentTimeMillis()}-$vmid-$action",
                time = System.currentTimeMillis() / 1000,
                node = node,
                tag = tag,
                msg = "$action $type $vmid: task started",
                pri = pri,
                user = "root@pam",
                pid = (1000L..9999L).random(),
            )
        )
        return PveResponse(data = "UPID:$node:demo:${action}:${vmid}:root@pam:")
    }

    private var logPollCount = 0

    private fun getRotatingDemoLogs(): List<ClusterLogEntry> {
        val nowSec = System.currentTimeMillis() / 1000
        val pool = listOf(
            ClusterLogEntry(
                id = "demo-syslog-1",
                time = nowSec - 5,
                node = "alpha",
                tag = "systemd",
                msg = "Started apt-daily.timer - Daily apt download activities.",
                pri = 6,
                user = "root@pam",
                pid = 1050,
            ),
            ClusterLogEntry(
                id = "demo-task-admin",
                time = nowSec - 15,
                node = "gamma",
                tag = "vzdump",
                msg = "UPID:gamma:0000184A:00000000:66D1B010:vzdump:102:admin@pam: backup successful (102.vma.zst)",
                pri = 6,
                user = "admin@pam",
                pid = 2100,
            ),
            ClusterLogEntry(
                id = "demo-corosync",
                time = nowSec - 45,
                node = "beta",
                tag = "corosync",
                msg = "Totem: ring 0 active with 3 nodes (alpha, beta, gamma)",
                pri = 6,
                user = "root@pam",
                pid = 980,
            ),
            ClusterLogEntry(
                id = "demo-storage",
                time = nowSec - 90,
                node = "alpha",
                tag = "pvestatd",
                msg = "node status update: all storage pools online",
                pri = 6,
                user = "root@pam",
                pid = 1350,
            ),
            ClusterLogEntry(
                id = "demo-sync",
                time = nowSec - 150,
                node = "beta",
                tag = "pmxcfs",
                msg = "cluster configuration synchronized",
                pri = 6,
                user = "root@pam",
                pid = 875,
            ),
        )
        val offset = (logPollCount % pool.size)
        return pool.drop(offset) + pool.take(offset)
    }

    override suspend fun clusterLog(max: Int, since: Long?): PveResponse<List<ClusterLogEntry>> {
        logPollCount++
        val combined = getRotatingDemoLogs() + demoLogEntries
        val list = if (since != null) {
            combined.filter { (it.time ?: 0L) >= since }
        } else {
            combined
        }
        return PveResponse(data = list.take(max))
    }

    private var aptUpgradeTaskStart: Long = 0L
    private var demoUpgraded: Boolean = false

    override suspend fun taskStatus(node: String, upid: String): PveResponse<TaskStatus> {
        if (upid.contains("aptupdate")) {
            val isRunning = aptTaskStart != 0L && now() - aptTaskStart < 2_500L
            val status = if (isRunning) "running" else "stopped"
            val exitstatus = if (!isRunning) "OK" else null
            return PveResponse(data = TaskStatus(status = status, exitstatus = exitstatus))
        }
        if (upid.contains("aptupgrade")) {
            val isRunning = aptUpgradeTaskStart != 0L && now() - aptUpgradeTaskStart < 8_000L
            val status = if (isRunning) "running" else "stopped"
            val exitstatus = if (!isRunning) {
                demoUpgraded = true
                "OK"
            } else null
            return PveResponse(data = TaskStatus(status = status, exitstatus = exitstatus))
        }
        return PveResponse(data = TaskStatus(status = "stopped", exitstatus = "OK"))
    }

    override suspend fun taskLog(node: String, upid: String, start: Int?, limit: Int?): PveResponse<List<Map<String, Any>>> {
        val lines = if (upid.contains("aptupgrade")) {
            val elapsed = if (aptUpgradeTaskStart == 0L) 10_000L else now() - aptUpgradeTaskStart
            when {
                elapsed < 800L -> listOf(
                    mapOf("n" to 1, "t" to "Reading package lists..."),
                    mapOf("n" to 2, "t" to "Building dependency tree..."),
                )
                elapsed < 1600L -> listOf(
                    mapOf("n" to 1, "t" to "Reading package lists..."),
                    mapOf("n" to 2, "t" to "Building dependency tree..."),
                    mapOf("n" to 3, "t" to "Calculating upgrade..."),
                    mapOf("n" to 4, "t" to "The following packages will be upgraded: pve-manager proxmox-kernel-6.8 qemu-server openssl"),
                )
                elapsed < 2400L -> listOf(
                    mapOf("n" to 4, "t" to "The following packages will be upgraded: pve-manager proxmox-kernel-6.8 qemu-server openssl"),
                    mapOf("n" to 5, "t" to "Unpacking pve-manager (8.3.3) over (8.3.1)..."),
                    mapOf("n" to 6, "t" to "Setting up pve-manager (8.3.3)..."),
                )
                else -> listOf(
                    mapOf("n" to 7, "t" to "Setting up openssl (3.0.15-1~deb12u1)..."),
                    mapOf("n" to 8, "t" to "Processing triggers for systemd..."),
                    mapOf("n" to 9, "t" to "TASK OK"),
                )
            }
        } else if (upid.contains("aptupdate")) {
            val elapsed = if (aptTaskStart == 0L) 10_000L else now() - aptTaskStart
            when {
                elapsed < 1000L -> listOf(
                    mapOf("n" to 1, "t" to "Hit:1 http://download.proxmox.com/debian/pve bookworm InRelease"),
                    mapOf("n" to 2, "t" to "Get:2 http://security.debian.org/debian-security bookworm-security InRelease"),
                )
                elapsed < 2000L -> listOf(
                    mapOf("n" to 2, "t" to "Get:2 http://security.debian.org/debian-security bookworm-security InRelease"),
                    mapOf("n" to 3, "t" to "Reading package lists..."),
                )
                else -> listOf(
                    mapOf("n" to 3, "t" to "Reading package lists..."),
                    mapOf("n" to 4, "t" to "Building dependency tree..."),
                    mapOf("n" to 5, "t" to "TASK OK"),
                )
            }
        } else {
            emptyList()
        }
        return PveResponse(data = lines)
    }

    // --- Console proxies: simulated demo proxy tickets

    override suspend fun qemuVncProxy(node: String, vmid: Long, websocket: Int): PveResponse<ConsoleProxyData> {
        return PveResponse(
            data = ConsoleProxyData(
                port = "5900",
                ticket = "DEMO_VNC_TICKET",
                user = "root@pam",
                upid = "UPID:$node:00001234:00000000:66D1B000:vncproxy:root@pam:",
            )
        )
    }

    override suspend fun lxcTermProxy(node: String, vmid: Long, websocket: Int): PveResponse<ConsoleProxyData> {
        return PveResponse(
            data = ConsoleProxyData(
                port = "5900",
                ticket = "DEMO_TERM_TICKET",
                user = "root@pam",
                upid = "UPID:$node:00001234:00000000:66D1B000:termproxy:root@pam:",
            )
        )
    }

    override suspend fun nodeTermProxy(node: String, cmd: String?, cmdOpts: String?): PveResponse<ConsoleProxyData> {
        return PveResponse(
            data = ConsoleProxyData(
                port = "5900",
                ticket = "DEMO_NODE_TICKET",
                user = "root@pam",
                upid = "UPID:$node:00001234:00000000:66D1B000:termproxy:root@pam:",
            )
        )
    }

    override suspend fun lxcVncProxy(node: String, vmid: Long, websocket: Int): PveResponse<ConsoleProxyData> {
        return PveResponse(
            data = ConsoleProxyData(
                port = "5900",
                ticket = "DEMO_VNC_TICKET",
                user = "root@pam",
                upid = "UPID:$node:00001234:00000000:66D1B000:vncproxy:root@pam:",
            )
        )
    }

    // --- Defensive stubs for the rest (the app parses defensively; empty is fine).

    override suspend fun clusterResources(type: String?): PveResponse<List<ClusterResource>> = PveResponse()
    override suspend fun createSnapshot(node: String, type: String, vmid: Long, snapname: String, description: String?, vmstate: Int?): PveResponse<String> = PveResponse()
    override suspend fun deleteSnapshot(node: String, type: String, vmid: Long, snap: String, force: Int?): PveResponse<String> = PveResponse()
    override suspend fun rollbackSnapshot(node: String, type: String, vmid: Long, snap: String): PveResponse<String> = PveResponse()
    override suspend fun storageStatus(node: String, storage: String): PveResponse<StorageStatus> {
        val entry = nodeStorage(node).data.orEmpty().firstOrNull { it.storage == storage }
        return PveResponse(
            data = StorageStatus(
                total = entry?.total ?: 100_000_000_000L,
                used = entry?.used ?: 50_000_000_000L,
                avail = (entry?.total ?: 100_000_000_000L) - (entry?.used ?: 50_000_000_000L),
                active = 1,
                enabled = 1,
                type = entry?.type ?: "dir",
            )
        )
    }
    override suspend fun createBackup(node: String, vmid: Long, storage: String, mode: String?, compress: String?, remove: Int?, notesTemplate: String?): PveResponse<String> {
        demoLogEntries.add(
            0,
            ClusterLogEntry(
                id = "log-${System.currentTimeMillis()}-backup-$vmid",
                time = System.currentTimeMillis() / 1000,
                node = node,
                tag = "vzdump",
                msg = "starting backup of VM $vmid to storage '$storage'",
                pri = 6,
                user = "root@pam",
                pid = (1000L..9999L).random(),
            )
        )
        return PveResponse(data = "UPID:$node:00001238:00000000:66D1B004:vzdump:$vmid:root@pam:")
    }
    override suspend fun deleteStorageContent(node: String, storage: String, volume: String): PveResponse<String> = PveResponse(data = "OK")
    override suspend fun nodeUsb(node: String): PveResponse<List<HostUsbDevice>> = PveResponse()
    override suspend fun updateGuestConfig(node: String, type: String, vmid: Long, fields: Map<String, String>): PveResponse<String?> {
        // Persist config changes in the simulation (e.g. the AUTO/onboot toggle).
        val store = configOverride.getOrPut(vmid) { mutableMapOf() }
        fields.forEach { (key, value) ->
            if (key == "onboot") store[key] = value.toIntOrNull() ?: 0 else store[key] = value
        }
        return PveResponse(data = null)
    }
    override suspend fun nodeNetwork(node: String): PveResponse<List<Map<String, Any>>> = PveResponse()

    override suspend fun aptUpdateList(node: String): PveResponse<List<Map<String, Any>>> {
        if (demoUpgraded) {
            return PveResponse(data = emptyList())
        }
        return PveResponse(
            data = listOf(
                mapOf(
                    "Package" to "pve-manager",
                    "Title" to "Proxmox VE Management Daemon",
                    "OldVersion" to "8.3.1",
                    "Version" to "8.3.3",
                    "Priority" to "important",
                    "Section" to "admin",
                    "Origin" to "Proxmox",
                    "Arch" to "amd64",
                ),
                mapOf(
                    "Package" to "proxmox-kernel-6.8",
                    "Title" to "Proxmox Kernel 6.8 (6.8.12-1-pve)",
                    "OldVersion" to "6.8.8-1-pve",
                    "Version" to "6.8.12-1-pve",
                    "Priority" to "important",
                    "Section" to "admin",
                    "Origin" to "Proxmox",
                    "Arch" to "amd64",
                ),
                mapOf(
                    "Package" to "qemu-server",
                    "Title" to "QEMU Server Tools",
                    "OldVersion" to "8.2.2",
                    "Version" to "8.2.4",
                    "Priority" to "standard",
                    "Section" to "admin",
                    "Origin" to "Proxmox",
                    "Arch" to "amd64",
                ),
                mapOf(
                    "Package" to "openssl",
                    "Title" to "Secure Sockets Layer toolkit",
                    "OldVersion" to "3.0.13-1~deb12u1",
                    "Version" to "3.0.15-1~deb12u1",
                    "Priority" to "required",
                    "Section" to "security",
                    "Origin" to "Debian-Security",
                    "Arch" to "amd64",
                ),
            )
        )
    }

    override suspend fun aptVersions(node: String): PveResponse<List<Map<String, Any>>> = PveResponse(
        data = listOf(
            mapOf("Package" to "pve-manager", "Version" to if (demoUpgraded) "8.3.3" else "8.3.1", "CurrentState" to "installed"),
            mapOf("Package" to "proxmox-kernel-6.8", "Version" to if (demoUpgraded) "6.8.12-1-pve" else "6.8.8-1-pve", "CurrentState" to "installed"),
            mapOf("Package" to "qemu-server", "Version" to if (demoUpgraded) "8.2.4" else "8.2.2", "CurrentState" to "installed"),
            mapOf("Package" to "pve-qemu-kvm", "Version" to "8.2.2-1", "CurrentState" to "installed"),
            mapOf("Package" to "corosync", "Version" to "3.1.7-pve3", "CurrentState" to "installed"),
        )
    )

    override suspend fun aptUpdateRefresh(node: String): PveResponse<String> {
        aptTaskStart = now()
        demoUpgraded = false
        return PveResponse(data = "UPID:$node:00001235:00000000:66D1B001:aptupdate::root@pam:")
    }

    override suspend fun aptUpgrade(node: String): PveResponse<String> {
        aptUpgradeTaskStart = now()
        demoLogEntries.add(
            0,
            ClusterLogEntry(
                id = "log-${System.currentTimeMillis()}-upgrade-$node",
                time = System.currentTimeMillis() / 1000,
                node = node,
                tag = "apt-upgrade",
                msg = "starting apt dist-upgrade on $node",
                pri = 6,
                user = "root@pam",
                pid = (1000L..9999L).random(),
            ),
        )
        return PveResponse(data = "UPID:$node:00001239:00000000:66D1B005:aptupgrade::root@pam:")
    }

    override suspend fun sdnZones(): PveResponse<List<Map<String, Any>>> = PveResponse(
        data = listOf(
            mapOf("zone" to "localnet", "type" to "simple", "ipam" to "pve", "mtu" to 1500),
            mapOf("zone" to "vlan10", "type" to "vlan", "bridge" to "vmbr0", "tag" to 10),
            mapOf("zone" to "vxlan-mesh", "type" to "vxlan", "peers" to "192.0.2.11,192.0.2.12", "ipam" to "pve"),
        )
    )

    override suspend fun sdnVnets(): PveResponse<List<Map<String, Any>>> = PveResponse(
        data = listOf(
            mapOf("vnet" to "vnet-mgmt", "zone" to "localnet", "tag" to 10, "vlanaware" to 1),
            mapOf("vnet" to "vnet-dmz", "zone" to "vlan10", "tag" to 20),
            mapOf("vnet" to "vnet-db", "zone" to "vxlan-mesh", "tag" to 100),
        )
    )

    override suspend fun sdnStatus(): PveResponse<List<Map<String, Any>>> = PveResponse(
        data = listOf(
            mapOf("zone" to "localnet", "type" to "zone", "status" to "ok", "state" to "ok"),
            mapOf("zone" to "vlan10", "type" to "zone", "status" to "ok", "state" to "ok"),
            mapOf("zone" to "vxlan-mesh", "type" to "zone", "status" to "ok", "state" to "ok", "controller" to "evpn-ctrl"),
        )
    )

    override suspend fun clusterFirewallOptions(): PveResponse<Map<String, Any>> = PveResponse(
        data = mapOf(
            "enable" to 1,
            "policy_in" to "DROP",
            "policy_out" to "ACCEPT",
            "log_ratelimit" to "1/second",
        )
    )

    override suspend fun clusterFirewallRules(): PveResponse<List<Map<String, Any>>> = PveResponse(
        data = listOf(
            mapOf("pos" to 0, "type" to "in", "action" to "ACCEPT", "enable" to 1, "iface" to "vmbr0", "proto" to "tcp", "dport" to "8006", "comment" to "Proxmox Web UI", "log" to "nolog"),
            mapOf("pos" to 1, "type" to "in", "action" to "ACCEPT", "enable" to 1, "macro" to "SSH", "proto" to "tcp", "dport" to "22", "comment" to "Admin SSH", "log" to "info"),
            mapOf("pos" to 2, "type" to "in", "action" to "DROP", "enable" to 1, "iface" to "wan0", "comment" to "Block unsolicited inbound", "log" to "warning"),
            mapOf("pos" to 3, "type" to "out", "action" to "ACCEPT", "enable" to 1, "comment" to "Default outbound access"),
            mapOf("pos" to 4, "type" to "in", "action" to "REJECT", "enable" to 0, "proto" to "tcp", "dport" to "23", "comment" to "Telnet disabled legacy rule"),
        )
    )

    override suspend fun clusterFirewallAliases(): PveResponse<List<Map<String, Any>>> = PveResponse(
        data = listOf(
            mapOf("name" to "local_subnet", "cidr" to "192.0.2.0/24", "comment" to "LAN devices"),
            mapOf("name" to "admin_workstation", "cidr" to "192.0.2.50/32", "comment" to "Primary NOC host"),
        )
    )

    override suspend fun nodeFirewallOptions(node: String): PveResponse<Map<String, Any>> = PveResponse(
        data = mapOf(
            "enable" to 1,
            "policy_in" to "ACCEPT",
            "policy_out" to "ACCEPT",
        )
    )

    override suspend fun nodeFirewallRules(node: String): PveResponse<List<Map<String, Any>>> = PveResponse(
        data = listOf(
            mapOf("pos" to 0, "type" to "in", "action" to "ACCEPT", "enable" to 1, "proto" to "udp", "dport" to "5405", "macro" to "Corosync", "comment" to "Cluster heartbeat"),
            mapOf("pos" to 1, "type" to "in", "action" to "ACCEPT", "enable" to 1, "proto" to "tcp", "dport" to "8006", "comment" to "API listener"),
            mapOf("pos" to 2, "type" to "in", "action" to "DROP", "enable" to 1, "proto" to "tcp", "dport" to "111", "comment" to "RPCbind blocked"),
        )
    )
}
