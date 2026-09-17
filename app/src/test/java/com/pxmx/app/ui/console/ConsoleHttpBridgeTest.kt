package com.pxmx.app.ui.console

import com.pxmx.app.data.api.CertUtils
import com.pxmx.app.data.model.GuestType
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class ConsoleHttpBridgeTest {
    private val formType = "application/x-www-form-urlencoded"
    private val base = "https://pve.example:8006"
    private val proxyPath = "/api2/json/nodes/pve/qemu/100/vncproxy"

    private fun apiTransport(
        origin: String,
        cookie: String,
        client: OkHttpClient,
        type: GuestType = GuestType.QEMU,
        node: String = "pve",
        vmid: Long = 100,
    ) = ConsoleTransport(origin, cookie, client, node = node, guestType = type, vmid = vmid)

    @Test
    fun `pinned bootstrap sends exact form CSRF and only app cookie over real TLS`() {
        val bodies = LinkedBlockingQueue<String>()
        LocalConsoleTlsServer("internal-pve.example", serve = { socket, headers ->
            val length = headers.lineSequence().first { it.startsWith("Content-Length:", true) }.substringAfter(':').trim().toInt()
            bodies.add(ByteArray(length).also { DataInputStream(socket.inputStream).readFully(it) }.toString(Charsets.UTF_8))
            val json = "{\"data\":{\"port\":5901,\"ticket\":\"test-ticket\"}}"
            socket.outputStream.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${json.toByteArray().size}\r\nConnection: close\r\n\r\n$json").toByteArray())
            socket.outputStream.flush()
        }).use { server ->
            val transport = apiTransport(server.url, "app-cookie", createConsoleClient("localhost", true,
                CertUtils.computeSha256Fingerprint(server.certificate)))
            val events = LinkedBlockingQueue<ConsoleHttpResponse>()
            val bridge = ConsoleHttpBridge(transport) { _, response -> events.add(response) }
            try {
                bridge.request("bootstrap", server.url.removeSuffix("/") + proxyPath, "POST", formType, "test-csrf",
                    "websocket=1&width=800&height=600&test=a%2Bb%26c%3D%25")
                val response = events.poll(10, TimeUnit.SECONDS)!!
                assertEquals(200, response.status)
                assertEquals("application/json; charset=utf-8", response.contentType)
                assertTrue(response.body.contains("\"port\":5901"))
                assertEquals("websocket=1&width=800&height=600&test=a%2Bb%26c%3D%25", bodies.poll(1, TimeUnit.SECONDS))
                val headers = server.requests.single()
                assertTrue(headers.startsWith("POST $proxyPath HTTP/1.1"))
                assertTrue(headers.contains("CSRFPreventionToken: test-csrf"))
                assertTrue(headers.contains("Cookie: PVEAuthCookie=app-cookie"))
                assertTrue(headers.contains("Content-Type: $formType"))
            } finally { bridge.dispose() }
        }
    }

    @Test
    fun `API permits only the open console guest`() {
        val transport = apiTransport(base, "app-cookie", OkHttpClient())
        val writes = listOf("vncproxy", "termproxy").map { "/api2/json/nodes/pve/qemu/100/$it" } +
            listOf("start", "shutdown", "stop", "reset", "suspend", "resume").map { "/api2/json/nodes/pve/qemu/100/status/$it" }
        for (path in writes) {
            val call = transport.newConsoleApiCall(base + path, "POST", formType, "test-csrf", "")
            assertNotNull(path, call)
            assertEquals("POST", call!!.request().method)
            assertNotNull(call.request().body)
        }
        for (path in listOf("/api2/json/cluster/resources?type=vm", "/api2/json/nodes/pve/qemu/100/status/current?")) {
            assertNotNull(path, transport.newConsoleApiCall(base + path, "GET", "", "", ""))
        }
        for (path in listOf(
            "/api2/json/nodes/pve/vncshell",
            "/api2/json/nodes/node-2/qemu/100/vncproxy",
            "/api2/json/nodes/pve/qemu/101/status/stop",
            "/api2/json/nodes/pve/lxc/100/status/stop",
            "/api2/json/nodes/pve/lxc/100/config",
        )) {
            assertNull(path, transport.newConsoleApiCall(base + path, "POST", formType, "test-csrf", ""))
        }
        val nodeShell = apiTransport(base, "app-cookie", OkHttpClient(), type = GuestType.NODE, vmid = 0)
        assertNotNull(nodeShell.newConsoleApiCall("$base/api2/json/nodes/pve/vncshell", "POST", formType, "test-csrf", ""))
        assertNull(nodeShell.newConsoleApiCall("$base/api2/json/nodes/pve/qemu/100/status/stop", "POST", formType, "test-csrf", ""))
    }

    @Test
    fun `QEMU and LXC with non positive vmid deny API calls while NODE allows vncshell`() {
        val qemuAllowedAt100 = "$base/api2/json/nodes/pve/qemu/100/vncproxy"
        val qemuAt100 = apiTransport(base, "app-cookie", OkHttpClient(), type = GuestType.QEMU, vmid = 100)
        assertNotNull(qemuAt100.newConsoleApiCall(qemuAllowedAt100, "POST", formType, "test-csrf", ""))

        val qemuZero = apiTransport(base, "app-cookie", OkHttpClient(), type = GuestType.QEMU, vmid = 0)
        assertNull(qemuZero.newConsoleApiCall(qemuAllowedAt100, "POST", formType, "test-csrf", ""))
        assertNull(qemuZero.newConsoleApiCall("$base/api2/json/nodes/pve/qemu/0/vncproxy", "POST", formType, "test-csrf", ""))

        val lxcAllowedAt100 = "$base/api2/json/nodes/pve/lxc/100/vncproxy"
        val lxcAt100 = apiTransport(base, "app-cookie", OkHttpClient(), type = GuestType.LXC, vmid = 100)
        assertNotNull(lxcAt100.newConsoleApiCall(lxcAllowedAt100, "POST", formType, "test-csrf", ""))

        val lxcZero = apiTransport(base, "app-cookie", OkHttpClient(), type = GuestType.LXC, vmid = 0)
        assertNull(lxcZero.newConsoleApiCall(lxcAllowedAt100, "POST", formType, "test-csrf", ""))
        assertNull(lxcZero.newConsoleApiCall("$base/api2/json/nodes/pve/lxc/0/vncproxy", "POST", formType, "test-csrf", ""))

        val nodeShell = apiTransport(base, "app-cookie", OkHttpClient(), type = GuestType.NODE, vmid = 0)
        assertNotNull(nodeShell.newConsoleApiCall("$base/api2/json/nodes/pve/vncshell", "POST", formType, "test-csrf", ""))
    }

    @Test
    fun `API denies foreign origins unsupported endpoints methods and unsafe forms before networking`() {
        val transport = apiTransport(base, "app-cookie", OkHttpClient())
        for (url in listOf("http://pve.example:8006$proxyPath", "https://pve.example:8007$proxyPath",
            "https://evil.example$proxyPath", "https://user@pve.example:8006$proxyPath", "$base/api2/json/access/users",
            "$base/api2/json/nodes/pve/qemu/100/config", "$base/api2/json/nodes/pve/qemu/100/status/delete",
            "$base/api2/json/nodes/pve/lxc/100/status/reset", "$base$proxyPath/extra", "$base$proxyPath?extra=1",
            "$base/api2/json/nodes/pve%2Fother/qemu/100/vncproxy", "$base$proxyPath#fragment")) {
            assertNull(url, transport.newConsoleApiCall(url, "POST", formType, "test-csrf", "websocket=1"))
        }
        for (method in listOf("PUT", "DELETE", "PATCH", "OPTIONS", "HEAD", "post")) {
            assertNull(method, transport.newConsoleApiCall(base + proxyPath, method, formType, "test-csrf", ""))
        }
        for ((type, csrf, body) in listOf(Triple("application/json", "test-csrf", "{}"), Triple(formType, "", ""),
            Triple(formType, "test\r\nInjected: yes", ""), Triple(formType, "test-csrf", "a".repeat(65537)))) {
            assertNull(transport.newConsoleApiCall(base + proxyPath, "POST", type, csrf, body))
        }
        assertNull(transport.newConsoleApiCall("$base/api2/json/cluster/resources", "GET", "", "", "not-empty"))
    }

    @Test
    fun `changed trusted certificate prevents POST cookie CSRF and body disclosure`() {
        LocalConsoleTlsServer("localhost").use { server ->
            val transport = apiTransport(server.url, "app-cookie", createConsoleClient("localhost", true, "00".repeat(32), server.trustManager))
            val events = LinkedBlockingQueue<ConsoleHttpResponse>()
            val bridge = ConsoleHttpBridge(transport) { _, response -> events.add(response) }
            try {
                bridge.request("bootstrap", server.url.removeSuffix("/") + proxyPath, "POST", formType, "test-csrf", "websocket=1")
                assertEquals(502, events.poll(10, TimeUnit.SECONDS)!!.status)
                assertTrue(server.requests.isEmpty())
            } finally { bridge.dispose() }
        }
    }

    @Test
    fun `console control POST is not replayed on service unavailable retry after zero`() {
        LocalConsoleTlsServer(
            "localhost",
            "HTTP/1.1 503 Unavailable\r\nRetry-After: 0\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
        ).use { server ->
            val transport = apiTransport(server.url, "app-cookie", createConsoleClient("localhost", false, null, server.trustManager))
            val events = LinkedBlockingQueue<ConsoleHttpResponse>()
            val bridge = ConsoleHttpBridge(transport) { _, response -> events.add(response) }
            try {
                bridge.request("control", server.url + "api2/json/nodes/pve/qemu/100/status/start", "POST", formType, "test-csrf", "")
                assertEquals(503, events.poll(10, TimeUnit.SECONDS)!!.status)
                assertEquals(1, server.requests.size)
            } finally { bridge.dispose() }
        }
    }

    @Test
    fun `POST redirects are never replayed even to allowed same origin control`() {
        LocalConsoleTlsServer("localhost", "HTTP/1.1 307 Temporary Redirect\r\nLocation: /api2/json/nodes/pve/qemu/100/status/stop\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").use { server ->
            val transport = apiTransport(server.url, "app-cookie", createConsoleClient("localhost", false, null, server.trustManager))
            val events = LinkedBlockingQueue<ConsoleHttpResponse>()
            val bridge = ConsoleHttpBridge(transport) { _, response -> events.add(response) }
            try {
                bridge.request("bootstrap", server.url.removeSuffix("/") + proxyPath, "POST", formType, "test-csrf", "websocket=1")
                assertEquals(403, events.poll(10, TimeUnit.SECONDS)!!.status)
                assertEquals(1, server.requests.size)
            } finally { bridge.dispose() }
        }
    }
}
