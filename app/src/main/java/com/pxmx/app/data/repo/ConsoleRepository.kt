package com.pxmx.app.data.repo

import com.pxmx.app.data.console.ConsoleUrlBuilder
import com.pxmx.app.data.model.ConsoleSession
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.data.session.SessionStore

/**
 * Handles console ticket minting and noVNC/xterm session URL building.
 */
class ConsoleRepository(
    private val sessionStore: SessionStore,
    private val pveClient: PveClient,
) {

    /**
     * Open the same console the Proxmox web UI uses (noVNC for QEMU / LXC, xterm.js for node).
     * Requires an active password/ticket session (API tokens often lack console rights).
     */
    suspend fun openConsole(
        node: String,
        guestType: GuestType,
        vmid: Long,
        name: String,
        cmd: String? = null,
    ): Result<ConsoleSession> = pveClient.apiCall { api ->
        val session = sessionStore.session.value
            ?: throw PveException("Not connected")
        val isDemo = session.config.host.equals("demo", ignoreCase = true)
        val authCookie = session.ticket
            ?: if (isDemo) "DEMO_TICKET"
            else throw PveException(
                "Console needs a ticket login (password). API-token sessions usually cannot open noVNC.",
            )

        val proxy = when (guestType) {
            GuestType.QEMU -> api.qemuVncProxy(node, vmid).data
            GuestType.LXC -> api.lxcVncProxy(node, vmid).data
            GuestType.NODE -> api.nodeTermProxy(node, cmd).data
        } ?: throw PveException("Console proxy returned empty data")

        val port = proxy.port ?: throw PveException("Console proxy missing port")
        val vncticket = proxy.ticket ?: throw PveException("Console proxy missing ticket")

        val cfg = session.config
        val hostPort = cfg.displayHost
        ConsoleUrlBuilder.buildSession(
            hostPort = hostPort,
            authCookie = authCookie,
            node = node,
            guestType = guestType,
            vmid = vmid,
            port = port,
            vncticket = vncticket,
            name = name,
            cmd = cmd,
            isDemo = isDemo,
        )
    }
}
