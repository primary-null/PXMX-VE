package com.pxmx.app.data.console

import com.pxmx.app.data.api.DemoShell
import com.pxmx.app.data.model.ConsoleSession
import com.pxmx.app.data.model.GuestType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ConsoleUrlBuilder {

    fun buildCookieHostUrl(hostPort: String): String {
        val clean = hostPort.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        return "https://$clean"
    }

    fun resolveConsoleKind(guestType: GuestType, cmd: String? = null): String = when (guestType) {
        GuestType.NODE -> when (cmd) {
            "upgrade" -> "upgrade"
            "login" -> "login"
            else -> "shell"
        }
        GuestType.QEMU -> "kvm"
        GuestType.LXC -> "lxc"
    }

    fun resolveUiParam(guestType: GuestType): String = when (guestType) {
        GuestType.NODE -> "xtermjs=1"
        GuestType.QEMU, GuestType.LXC -> "novnc=1"
    }

    fun buildRawWebSocketPath(
        node: String,
        guestType: GuestType,
        vmid: Long,
        port: String,
        vncticket: String,
    ): String {
        val ticketEnc = URLEncoder.encode(vncticket, StandardCharsets.UTF_8.toString())
        return if (guestType == GuestType.NODE) {
            "api2/json/nodes/$node/vncwebsocket?port=$port&vncticket=$ticketEnc"
        } else {
            "api2/json/nodes/$node/${guestType.path}/$vmid/vncwebsocket?port=$port&vncticket=$ticketEnc"
        }
    }

    fun buildPageUrl(
        cookieHostUrl: String,
        node: String,
        guestType: GuestType,
        vmid: Long,
        port: String,
        vncticket: String,
        cmd: String? = null,
        isDemo: Boolean = false,
        name: String = "",
    ): String {
        if (isDemo) {
            return DemoShell.generateHtml(node, guestType, vmid, name)
        }
        val rawPath = buildRawWebSocketPath(node, guestType, vmid, port, vncticket)
        val pathEnc = URLEncoder.encode(rawPath, StandardCharsets.UTF_8.toString())
        val consoleKind = resolveConsoleKind(guestType, cmd)
        val uiParam = resolveUiParam(guestType)
        return "$cookieHostUrl/?console=$consoleKind&$uiParam&vmid=$vmid&node=$node&resize=scale&path=$pathEnc"
    }

    fun buildSession(
        hostPort: String,
        authCookie: String,
        node: String,
        guestType: GuestType,
        vmid: Long,
        port: String,
        vncticket: String,
        name: String,
        cmd: String? = null,
        isDemo: Boolean = false,
    ): ConsoleSession {
        val cookieHostUrl = buildCookieHostUrl(hostPort)
        val pageUrl = buildPageUrl(
            cookieHostUrl = cookieHostUrl,
            node = node,
            guestType = guestType,
            vmid = vmid,
            port = port,
            vncticket = vncticket,
            cmd = cmd,
            isDemo = isDemo,
            name = name,
        )
        return ConsoleSession(
            pageUrl = pageUrl,
            cookieHostUrl = cookieHostUrl,
            pveAuthCookie = authCookie,
            guestType = guestType,
            node = node,
            vmid = vmid,
            name = name,
        )
    }
}
