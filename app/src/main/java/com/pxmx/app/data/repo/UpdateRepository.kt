package com.pxmx.app.data.repo

import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.model.AptPackageUpdate
import com.pxmx.app.data.model.AptPackageVersion
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.NodeUpdateSnapshot
import com.pxmx.app.data.session.SessionStore
import com.pxmx.app.data.ssh.SshUpgradeExecutor
import kotlinx.coroutines.delay

/**
 * Handles package update checks, apt tasks, and interactive SSH upgrades.
 */
class UpdateRepository(
    private val sessionStore: SessionStore,
    private val pveClient: PveClient,
    private val discoverNodeNames: suspend (ProxmoxApi) -> List<String>,
) {

    /** Pending apt updates + key package versions per node. */
    suspend fun listClusterUpdates(): Result<List<NodeUpdateSnapshot>> = pveClient.apiCall { api ->
        val nodes = discoverNodeNames(api)
        nodes.map { node ->
            val updates = runCatching { api.aptUpdateList(node).data.orEmpty() }
                .getOrDefault(emptyList())
                .map { AptPackageUpdate.fromMap(it) }
                .sortedBy { it.packageName.orEmpty() }
            val versions = runCatching { api.aptVersions(node).data.orEmpty() }
                .getOrDefault(emptyList())
                .map { AptPackageVersion.fromMap(it) }
            NodeUpdateSnapshot(node = node, updates = updates, versions = versions)
        }
    }

    /** Refresh apt package list on a node (starts a task). */
    suspend fun refreshAptUpdates(node: String): Result<String> = pveClient.apiCall { api ->
        api.aptUpdateRefresh(node).data ?: "OK"
    }

    /** Upgrade apt packages on a node (runs dist-upgrade, returns UPID task). */
    suspend fun aptUpgrade(node: String): Result<String> = pveClient.apiCall { api ->
        api.aptUpgrade(node).data ?: "OK"
    }

    /** Upgrade a PVE 9 node via direct SSH execution (apt-get update && apt-get full-upgrade -y). */
    suspend fun sshUpgrade(
        node: String,
        onOutputLine: (String) -> Unit = {},
    ): Result<Int> {
        val s = sessionStore.session.value ?: return Result.failure(PveException("No active session"))
        val config = s.config

        if (config.host.equals("demo", ignoreCase = true)) {
            return simulateDemoSshUpgrade(node, onOutputLine)
        }

        val profile = sessionStore.listProfiles().firstOrNull { it.host == config.host }
        if (profile == null || profile.authMode != AuthMode.PASSWORD || !profile.hasSavedSecret || profile.password.isBlank()) {
            return Result.failure(PveException("SSH upgrade needs the saved password for this profile. Reconnect with Save credentials on, or use the node shell."))
        }

        val targetHost = config.host.trim().removePrefix("https://").removePrefix("http://")
            .substringBefore('/')
            .substringBefore(':')
        val sshUser = resolveSshUpgradeUser(config.username)

        val executor = SshUpgradeExecutor(
            getStoredFingerprint = { h -> sessionStore.getHostKey(h) },
            storeFingerprint = { h, k -> sessionStore.saveHostKey(h, k) },
        )

        return executor.executeUpgrade(
            host = targetHost,
            port = 22,
            username = sshUser,
            password = profile.password,
            onOutputLine = onOutputLine,
        )
    }

    private suspend fun simulateDemoSshUpgrade(
        node: String,
        onOutputLine: (String) -> Unit,
    ): Result<Int> {
        val fakeLines = listOf(
            "Connecting to $node:22...",
            "Authenticated as root using password.",
            "Running apt-get update && apt-get full-upgrade -y...",
            "Hit:1 http://deb.debian.org/debian bookworm InRelease",
            "Hit:2 http://deb.debian.org/debian bookworm-updates InRelease",
            "Hit:3 http://security.debian.org/debian-security bookworm-security InRelease",
            "Hit:4 http://download.proxmox.com/debian/pve bookworm InRelease",
            "Reading package lists...",
            "Building dependency tree...",
            "Reading state information...",
            "Calculating upgrade...",
            "The following packages will be upgraded: pve-manager proxmox-kernel-6.8 qemu-server openssl",
            "4 upgraded, 0 newly installed, 0 to remove and 0 not upgraded.",
            "Need to get 0 B/84.2 MB of archives.",
            "Unpacking pve-manager (9.2.11) over (9.2.0)...",
            "Setting up pve-manager (9.2.11)...",
            "Setting up proxmox-kernel-6.8 (6.8.12-1)...",
            "Setting up qemu-server (9.0.2)...",
            "Setting up openssl (3.0.15-1~deb12u1)...",
            "Processing triggers for systemd (252.33-1~deb12u1)...",
            "Upgrade completed successfully (exit code 0).",
        )
        for (line in fakeLines) {
            onOutputLine(line)
            delay(350L)
        }
        return Result.success(0)
    }

    companion object {
        const val SSH_UPGRADE_USER = "root"

        /**
         * PVE API users and Linux SSH users are separate identity systems.
         * apt-get full-upgrade requires root privileges; therefore SSH upgrade always connects as root.
         */
        fun resolveSshUpgradeUser(sessionUsername: String? = null): String = SSH_UPGRADE_USER
    }
}
