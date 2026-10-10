package com.pxmx.app.data.repo

import com.pxmx.app.data.console.ConsoleUrlBuilder
import com.pxmx.app.data.model.AuthMode
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
     * Password sessions use session tickets; API token sessions open QEMU/LXC with token auth.
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
        val cfg = session.config
        val isDemo = cfg.host.equals("demo", ignoreCase = true)
        val isTokenSession = cfg.authMode == AuthMode.API_TOKEN

        val authCookie: String
        val apiToken: String

        if (isDemo) {
            authCookie = session.ticket ?: "DEMO_TICKET"
            apiToken = ""
        } else if (isTokenSession) {
            if (guestType == GuestType.NODE) {
                throw PveException(
                    "Node shell requires a ticket login (password). API tokens cannot open node shell.",
                )
            }
            authCookie = ""
            apiToken = cfg.apiToken
        } else {
            authCookie = session.ticket
                ?: throw PveException(
                    "Console needs a ticket login (password).",
                )
            apiToken = ""
        }

        val proxy = when (guestType) {
            GuestType.QEMU -> api.qemuVncProxy(node, vmid).data
            GuestType.LXC -> api.lxcVncProxy(node, vmid).data
            GuestType.NODE -> api.nodeTermProxy(node, cmd).data
        } ?: throw PveException("Console proxy returned empty data")

        val port = proxy.port ?: throw PveException("Console proxy missing port")
        val vncticket = proxy.ticket ?: throw PveException("Console proxy missing ticket")

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
            apiToken = apiToken,
        )
    }
}
