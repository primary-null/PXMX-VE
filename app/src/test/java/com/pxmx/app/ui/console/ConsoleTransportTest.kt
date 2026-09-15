package com.pxmx.app.ui.console

import org.junit.Assert.*
import org.junit.Test

class ConsoleTransportTest {
    @Test
    fun `HTML installs socket bridge before server scripts without exposing native cookies`() {
        val html = "<!DOCTYPE html><html><head><script>consoleUi()</script></head><body>noVNC</body></html>"
        LocalConsoleTlsServer("localhost", "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nSet-Cookie: PVEAuthCookie=native\r\nContent-Length: ${html.toByteArray().size}\r\nConnection: close\r\n\r\n$html").use { server ->
            val transport = ConsoleTransport(server.url, "test-ticket", createConsoleClient("localhost", false, null, server.trustManager), "secureSocketBootstrap()")
            val result = transport.fetch(server.url, "GET", emptyMap())
            val body = result.body.toString(Charsets.UTF_8)
            assertTrue(body.startsWith("<!DOCTYPE html>"))
            assertTrue("Bootstrap must precede all server scripts", body.indexOf("secureSocketBootstrap()") in 0 until body.indexOf("consoleUi()"))
            assertFalse(result.headers.keys.any { it.equals("Set-Cookie", true) })
            assertEquals("no-store", result.headers["Cache-Control"])
            assertTrue(result.headers["Content-Security-Policy"]!!.contains("worker-src 'none'"))
        }
    }

    @Test
    fun `redirect to another port never receives the cookie`() {
        LocalConsoleTlsServer("localhost").use { destination ->
            LocalConsoleTlsServer("localhost", "HTTP/1.1 302 Found\r\nLocation: ${destination.url}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").use { source ->
                val trustBoth = object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = error("unused")
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {
                        try { source.trustManager.checkServerTrusted(chain, authType) }
                        catch (_: java.security.cert.CertificateException) { destination.trustManager.checkServerTrusted(chain, authType) }
                    }
                    override fun getAcceptedIssuers() = source.trustManager.acceptedIssuers + destination.trustManager.acceptedIssuers
                }
                val transport = ConsoleTransport(source.url, "test-ticket", createConsoleClient("localhost", false, null, trustBoth))
                assertEquals(403, transport.fetch(source.url, "GET", emptyMap()).status)
                assertTrue("Cross-origin redirect leaked cookie: ${destination.requests}", destination.requests.isEmpty())
            }
        }
    }

    @Test
    fun `non intercepted requests are denied rather than delegated to native networking`() {
        val transport = ConsoleTransport("https://pve.example:8006", "test-ticket",
            createConsoleClient("pve.example", false, null))
        for ((url, method) in listOf(
            "https://pve.example:8006.evil.example/" to "GET",
            "https://pve.example:8007/" to "GET",
            "https://pve.example:8006@evil.example/" to "GET",
            "http://pve.example:8006/" to "GET",
            "https://elsewhere.example/" to "GET",
            "file:///etc/passwd" to "GET",
            "https://pve.example:8006/" to "POST",
        )) {
            val result = transport.fetch(url, method, emptyMap())
            assertNotNull("Native fallback for $method $url", result)
            assertEquals("Must block $method $url", 403, result!!.status)
        }
    }

    @Test
    fun `changed pin returns explicit failure instead of WebView native fallback`() {
        LocalConsoleTlsServer("localhost").use { server ->
            // Even a platform-trusted, hostname-valid replacement must not fall back to native TLS.
            val client = createConsoleClient("localhost", true, "00".repeat(32), server.trustManager)
            val transport = ConsoleTransport(server.url, "test-ticket", client)
            val result = transport.fetch(server.url, "GET", emptyMap())
            assertNotNull("null delegates to WebView, bypassing the pin", result)
            assertEquals(502, result!!.status)
            assertTrue(server.requests.isEmpty())
        }
    }
}
