package com.pxmx.app

import com.pxmx.app.data.api.ProxmoxApi
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

class FakeTourApi : ProxmoxApi {

    private val statusOverride = ConcurrentHashMap<Long, String>()
    private val configOverride = ConcurrentHashMap<Long, MutableMap<String, Any>>()
    private val clonedConfigs = ConcurrentHashMap<Long, Map<String, Any>>()

    private val demoLogEntries = CopyOnWriteArrayList<ClusterLogEntry>().apply {
        val nowSec = System.currentTimeMillis() / 1000
        addAll(
            listOf(
                ClusterLogEntry(
                    id = "log-init-1", time = nowSec - 60, node = "beta", tag = "pmxcfs",
                    msg = "cluster configuration synchronized", pri = 6, user = "root@pam", pid = 980,
                ),
                ClusterLogEntry(
                    id = "log-init-2", time = nowSec - 180, node = "alpha", tag = "vzdump",
                    msg = "Backup job finished successfully for VM 100", pri = 6, user = "root@pam", pid = 1420,
                ),
                ClusterLogEntry(
                    id = "log-init-3", time = nowSec - 300, node = "gamma", tag = "pvestatd",
                    msg = "node status update: all storage pools online", pri = 6, user = "root@pam", pid = 1350,
                ),
            )
        )
    }

    private fun statusOf(vmid: Long): String =
        statusOverride[vmid] ?: when (vmid) {
            100L, 102L, 200L, 202L, 204L -> "running"
            203L -> "frozen"
            else -> "stopped"
        }

    private fun guestRow(
        id: String, type: String, node: String, vmid: Long, name: String,
        baseMem: Long, maxMem: Long, cpus: Int,
    ): ClusterResource = ClusterResource(
        id = id, type = type, node = node, vmid = vmid, name = name,
        status = statusOf(vmid),
        mem = if (statusOf(vmid) == "running" || statusOf(vmid) == "frozen") baseMem else 0L,
        maxmem = maxMem, cpus = cpus,
        uptime = 50000L,
    )

    override suspend fun createTicket(username: String, password: String): PveResponse<TicketData> {
        val userOnly = username.substringBefore('@')
        if (userOnly.equals("tfa-user", ignoreCase = true)) {
            val errorBody = "{\"data\":{\"ticket\":\"PVE:tfa-user@pam:TFA-PARTIAL-DEMO-TICKET\",\"NeedTFA\":1}}"
                .toResponseBody("application/json".toMediaType())
            throw HttpException(Response.error<TicketData>(401, errorBody))
        }
        return PveResponse(data = TicketData(
            ticket = "fake-ticket",
            csrfPreventionToken = "fake-csrf",
            username = username,
        ))
    }

    override suspend fun accessTfa(password: String, otp: String): PveResponse<TicketData> {
        if (otp.trim() == "123456") {
            return PveResponse(data = TicketData(
                ticket = "fake-tfa-verified-ticket",
                csrfPreventionToken = "fake-csrf",
                username = "tfa-user@pam",
            ))
        } else {
            val errorBody = "{\"errors\":{\"otp\":\"Invalid one-time password\"},\"message\":\"authentication failure\"}"
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
                "type" to "node", "node" to "alpha", "name" to "alpha", "nodeid" to 1,
                "status" to "online", "online" to 1, "local" to 1, "ip" to "192.0.2.10",
                "cpu" to 0.15, "maxcpu" to 24, "mem" to 35_200_000_000L, "maxmem" to 64_000_000_000L,
            ),
            mapOf(
                "type" to "node", "node" to "beta", "name" to "beta", "nodeid" to 2,
                "status" to "online", "online" to 1, "local" to 0, "ip" to "192.0.2.11",
                "cpu" to 0.28, "maxcpu" to 16, "mem" to 25_600_000_000L, "maxmem" to 32_000_000_000L,
            ),
            mapOf(
                "type" to "node", "node" to "gamma", "name" to "gamma", "nodeid" to 3,
                "status" to "online", "online" to 1, "local" to 0, "ip" to "192.0.2.12",
                "cpu" to 0.08, "maxcpu" to 8, "mem" to 14_400_000_000L, "maxmem" to 16_000_000_000L,
            ),
        ))
    }

    override suspend fun nodes(): PveResponse<List<ClusterResource>> {
        return PveResponse(data = listOf(
            ClusterResource(
                id = "node/alpha", type = "node", node = "alpha", name = "alpha",
                status = "online", cpu = 0.15, maxcpu = 24,
                mem = 35_200_000_000L, maxmem = 64_000_000_000L,
                disk = 120_000_000_000L, maxdisk = 500_000_000_000L,
                uptime = 150000L,
            ),
            ClusterResource(
                id = "node/beta", type = "node", node = "beta", name = "beta",
                status = "online", cpu = 0.28, maxcpu = 16,
                mem = 25_600_000_000L, maxmem = 32_000_000_000L,
                disk = 80_000_000_000L, maxdisk = 250_000_000_000L,
                uptime = 864000L,
            ),
            ClusterResource(
                id = "node/gamma", type = "node", node = "gamma", name = "gamma",
                status = "online", cpu = 0.08, maxcpu = 8,
                mem = 14_400_000_000L, maxmem = 16_000_000_000L,
                disk = 45_000_000_000L, maxdisk = 120_000_000_000L,
                uptime = 1209600L,
            ),
        ))
    }

    override suspend fun nodeStatus(node: String): PveResponse<NodeStatus> {
        val (usedMem, maxMem, cpuVal) = when (node) {
            "beta" -> Triple(25_600_000_000L, 32_000_000_000L, 0.28)
            "gamma" -> Triple(14_400_000_000L, 16_000_000_000L, 0.08)
            else -> Triple(35_200_000_000L, 64_000_000_000L, 0.15)
        }
        return PveResponse(data = NodeStatus(
            uptime = 100000L,
            cpu = cpuVal,
            memory = MemoryInfo(used = usedMem, total = maxMem),
            rootfs = DiskInfo(used = 50_000_000_000L, total = 100_000_000_000L),
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
                "name" to "nova", "ostype" to "l26", "onboot" to 1, "cores" to 2, "memory" to 2048,
                "net0" to "virtio=AA:BB:CC:DD:EE:FF,bridge=vmbr0", "scsi0" to "local-lvm:vm-100-disk-0,size=32G"
            )
            101L -> mapOf(
                "name" to "meteor", "ostype" to "l26", "onboot" to 0, "cores" to 2, "memory" to 2048,
                "net0" to "virtio=11:22:33:44:55:66,bridge=vmbr0", "scsi0" to "local:101/vm-101-disk-0.raw,size=32G"
            )
            102L -> mapOf(
                "name" to "quasar", "ostype" to "l26", "onboot" to 1, "cores" to 4, "memory" to 4096,
                "net0" to "virtio=33:44:55:66:77:88,bridge=vmbr0", "scsi0" to "local-lvm:vm-102-disk-0,size=64G"
            )
            200L -> mapOf(
                "hostname" to "nebula", "ostype" to "ubuntu", "onboot" to 1, "cores" to 1, "memory" to 1024,
                "rootfs" to "local-lvm:subvol-200-disk-0,size=8G"
            )
            201L -> mapOf(
                "hostname" to "galaxy", "ostype" to "debian", "onboot" to 0, "cores" to 1, "memory" to 1024,
                "rootfs" to "local-lvm:subvol-201-disk-0,size=8G"
            )
            202L -> mapOf(
                "hostname" to "pulsar", "ostype" to "debian", "onboot" to 1, "cores" to 2, "memory" to 2048,
                "rootfs" to "zfs-pool:subvol-202-disk-0,size=32G"
            )
            203L -> mapOf(
                "hostname" to "comet", "ostype" to "alpine", "onboot" to 0, "cores" to 1, "memory" to 1024,
                "rootfs" to "zfs-pool:subvol-203-disk-0,size=8G"
            )
            204L -> mapOf(
                "hostname" to "aurora", "ostype" to "ubuntu", "onboot" to 1, "cores" to 1, "memory" to 1024,
                "rootfs" to "local-lvm:subvol-204-disk-0,size=16G"
            )
            9000L -> mapOf(
                "name" to "linux-template", "ostype" to "l26", "onboot" to 0, "cores" to 2, "memory" to 2048,
                "net0" to "virtio=AA:BB:CC:DD:EE:01,bridge=vmbr0", "scsi0" to "local-lvm:vm-9000-disk-0,size=32G"
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
        node: String, type: String, vmid: Long, newid: Long, name: String?, hostname: String?,
    ): PveResponse<String> {
        statusOverride[newid] = "stopped"
        val resolvedName = name ?: hostname ?: "clone-$newid"
        clonedConfigs[newid] = mapOf(
            "name" to resolvedName,
            "hostname" to (hostname ?: name ?: "clone-$newid"),
            "node" to node,
            "ostype" to "l26",
            "cores" to 2,
            "memory" to 2048,
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
                NodeStorageEntry(storage = "local", type = "dir", content = "backup,iso", active = 1, enabled = 1, total = 100_000_000_000L, used = 77_000_000_000L),
                NodeStorageEntry(storage = "local-lvm", type = "lvmthin", content = "rootdir,images", active = 1, enabled = 1, total = 200_000_000_000L, used = 70_000_000_000L),
            )
            "beta" -> listOf(
                NodeStorageEntry(storage = "local", type = "dir", content = "backup,iso", active = 1, enabled = 1, total = 100_000_000_000L, used = 46_000_000_000L),
                NodeStorageEntry(storage = "zfs-pool", type = "zfspool", content = "rootdir,images", active = 1, enabled = 1, total = 1_000_000_000_000L, used = 880_000_000_000L),
            )
            "gamma" -> listOf(
                NodeStorageEntry(storage = "local-lvm", type = "lvmthin", content = "rootdir,images", active = 1, enabled = 1, total = 200_000_000_000L, used = 130_000_000_000L),
                NodeStorageEntry(storage = "nfs-share", type = "nfs", content = "backup,iso,vztmpl", active = 1, enabled = 1, total = 1_000_000_000_000L, used = 520_000_000_000L, shared = 1),
            )
            else -> emptyList()
        }
        return PveResponse(data = list)
    }

    override suspend fun storageContent(node: String, storage: String, content: String?, vmid: Long?): PveResponse<List<StorageContentItem>> {
        val items = when (storage) {
            "local" -> listOf(
                StorageContentItem(volid = "$storage:backup/vzdump-qemu-100.tar.zst", content = "backup", size = 500000000L, vmid = 100L)
            )
            "nfs-share" -> listOf(
                StorageContentItem(volid = "$storage:backup/vzdump-qemu-102.tar.zst", content = "backup", size = 1200000000L, vmid = 102L),
                StorageContentItem(volid = "$storage:backup/vzdump-lxc-200.tar.zst", content = "backup", size = 250000000L, vmid = 200L)
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
        ))
    }

    override suspend fun clusterTasks(): PveResponse<List<Map<String, Any>>> {
        return PveResponse(data = listOf(
            mapOf("upid" to "UPID:alpha:00001234:00000000:66D1B000:vzdump:100:root@pam:", "node" to "alpha", "type" to "vzdump", "user" to "root@pam", "status" to "OK", "starttime" to 1725000000L, "endtime" to 1725000100L),
            mapOf("upid" to "UPID:alpha:00001235:00000000:66D1B001:aptupdate::root@pam:", "node" to "alpha", "type" to "aptupdate", "user" to "root@pam", "status" to "running", "starttime" to 1725000200L),
            mapOf("upid" to "UPID:beta:00001236:00000000:66D1B002:vzdump:202:root@pam:", "node" to "beta", "type" to "vzdump", "user" to "root@pam", "status" to "OK", "starttime" to 1725000000L, "endtime" to 1725000100L),
            mapOf("upid" to "UPID:gamma:00001237:00000000:66D1B003:qmstart:102:root@pam:", "node" to "gamma", "type" to "qmstart", "user" to "root@pam", "status" to "OK", "starttime" to 1725000000L, "endtime" to 1725000100L),
        ))
    }

    override suspend fun nodeTasks(node: String, start: Int?, limit: Int?): PveResponse<List<Map<String, Any>>> =
        PveResponse(data = clusterTasks().data.orEmpty().filter { it["node"] == node })

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
            "start", "resume", "reboot" -> statusOverride[vmid] = "running"
            "stop", "shutdown" -> statusOverride[vmid] = "stopped"
            "suspend" -> statusOverride[vmid] = "paused"
        }
        return PveResponse(data = "UPID:$node:fake:action:$vmid")
    }

    override suspend fun clusterLog(max: Int, since: Long?): PveResponse<List<ClusterLogEntry>> {
        val list = if (since != null) {
            demoLogEntries.filter { (it.time ?: 0L) >= since }
        } else {
            demoLogEntries
        }
        return PveResponse(data = list.take(max))
    }

    override suspend fun taskStatus(node: String, upid: String): PveResponse<TaskStatus> {
        return PveResponse(data = TaskStatus(status = "stopped", exitstatus = "OK"))
    }

    override suspend fun updateGuestConfig(node: String, type: String, vmid: Long, fields: Map<String, String>): PveResponse<String?> {
        val store = configOverride.getOrPut(vmid) { mutableMapOf() }
        fields.forEach { (key, value) ->
            if (key == "onboot") store[key] = value.toIntOrNull() ?: 0 else store[key] = value
        }
        return PveResponse(data = null)
    }

    // --- Defensive stubs for console proxies (return empty response for graceful error handling in test) ---

    override suspend fun qemuVncProxy(node: String, vmid: Long, websocket: Int): PveResponse<ConsoleProxyData> = PveResponse()
    override suspend fun lxcTermProxy(node: String, vmid: Long, websocket: Int): PveResponse<ConsoleProxyData> = PveResponse()
    override suspend fun nodeTermProxy(node: String, cmd: String?, cmdOpts: String?): PveResponse<ConsoleProxyData> = PveResponse()
    override suspend fun lxcVncProxy(node: String, vmid: Long, websocket: Int): PveResponse<ConsoleProxyData> = PveResponse()

    // --- Empty stubs for remaining APIs ---

    override suspend fun clusterResources(type: String?): PveResponse<List<ClusterResource>> = PveResponse()
    override suspend fun createSnapshot(node: String, type: String, vmid: Long, snapname: String, description: String?, vmstate: Int?): PveResponse<String> = PveResponse()
    override suspend fun deleteSnapshot(node: String, type: String, vmid: Long, snap: String, force: Int?): PveResponse<String> = PveResponse()
    override suspend fun rollbackSnapshot(node: String, type: String, vmid: Long, snap: String): PveResponse<String> = PveResponse()
    override suspend fun storageStatus(node: String, storage: String): PveResponse<StorageStatus> = PveResponse()
    override suspend fun createBackup(node: String, vmid: Long, storage: String, mode: String?, compress: String?, remove: Int?, notesTemplate: String?): PveResponse<String> = PveResponse()
    override suspend fun deleteStorageContent(node: String, storage: String, volume: String): PveResponse<String> = PveResponse()
    override suspend fun nodeUsb(node: String): PveResponse<List<HostUsbDevice>> = PveResponse()
    override suspend fun nodeNetwork(node: String): PveResponse<List<Map<String, Any>>> = PveResponse()
    override suspend fun aptUpdateList(node: String): PveResponse<List<Map<String, Any>>> = PveResponse()
    override suspend fun aptVersions(node: String): PveResponse<List<Map<String, Any>>> = PveResponse()
    override suspend fun aptUpdateRefresh(node: String): PveResponse<String> = PveResponse()
    override suspend fun aptUpgrade(node: String): PveResponse<String> = PveResponse()
    override suspend fun taskLog(node: String, upid: String, start: Int?, limit: Int?): PveResponse<List<Map<String, Any>>> = PveResponse()
    override suspend fun sdnZones(): PveResponse<List<Map<String, Any>>> = PveResponse()
    override suspend fun sdnVnets(): PveResponse<List<Map<String, Any>>> = PveResponse()
    override suspend fun sdnStatus(): PveResponse<List<Map<String, Any>>> = PveResponse()
    override suspend fun clusterFirewallOptions(): PveResponse<Map<String, Any>> = PveResponse()
    override suspend fun clusterFirewallRules(): PveResponse<List<Map<String, Any>>> = PveResponse()
    override suspend fun clusterFirewallAliases(): PveResponse<List<Map<String, Any>>> = PveResponse()
    override suspend fun nodeFirewallOptions(node: String): PveResponse<Map<String, Any>> = PveResponse()
    override suspend fun nodeFirewallRules(node: String): PveResponse<List<Map<String, Any>>> = PveResponse()
}
