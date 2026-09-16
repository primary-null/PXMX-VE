package com.pxmx.app.ui.console

import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.nio.file.Files
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class ConsoleTlsTest {
    @Test
    fun `strict matching hostname sends cookie over trusted TLS`() {
        LocalConsoleTlsServer("localhost").use { server ->
            val client = createConsoleClient("localhost", false, null, server.trustManager)
            client.newCall(Request.Builder().url(server.url).header("Cookie", "PVEAuthCookie=test-ticket").build())
                .execute().use { assertEquals("OK", it.body!!.string()) }
            assertTrue(server.requests.single().contains("Cookie: PVEAuthCookie=test-ticket"))
        }
    }

    @Test
    fun `matching pin permits self signed certificate with configured host exception`() {
        LocalConsoleTlsServer("pve-internal.example").use { server ->
            val pin = com.pxmx.app.data.api.CertUtils.computeSha256Fingerprint(server.certificate)
            val client = createConsoleClient("localhost", true, pin.lowercase())
            client.newCall(Request.Builder().url(server.url).build()).execute().use { assertEquals("OK", it.body!!.string()) }
        }
    }

    @Test
    fun `strict mode rejects untrusted self signed certificate`() {
        LocalConsoleTlsServer("localhost").use { server ->
            val client = createConsoleClient("localhost", false, null)
            assertTrue(runCatching { client.newCall(Request.Builder().url(server.url).build()).execute().close() }
                .exceptionOrNull() is IOException)
            assertTrue(server.requests.isEmpty())
        }
    }

    @Test
    fun `self signed console without login pin is rejected before sending cookie`() {
        LocalConsoleTlsServer("localhost").use { server ->
            val client = createConsoleClient("localhost", true, null)
            val failure = runCatching {
                client.newCall(Request.Builder().url(server.url)
                    .header("Cookie", "PVEAuthCookie=test-ticket").build()).execute().use { it.body?.string() }
            }.exceptionOrNull()
            assertTrue("Missing pin must fail TLS, got $failure", failure is IOException)
            assertTrue(server.requests.isEmpty())
        }
    }

    @Test
    fun `strict client rejects trusted certificate for wrong hostname before sending cookie`() {
        LocalConsoleTlsServer("wrong-host.example").use { server ->
            val client = createConsoleClient("localhost", false, null, server.trustManager)
            val failure = runCatching {
                client.newCall(Request.Builder().url(server.url)
                    .header("Cookie", "PVEAuthCookie=test-ticket").build()).execute().use { it.body?.string() }
            }.exceptionOrNull()
            assertTrue("Trusted wrong-host certificate must fail TLS, got $failure, requests=${server.requests}", failure is IOException)
            assertTrue("Credentials must not cross a rejected TLS connection", server.requests.isEmpty())
        }
    }
}

/** Real loopback TLS with a keytool-generated test identity; no server credentials or extra libraries. */
internal class LocalConsoleTlsServer(host: String, private val response: String =
    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK",
    private val serve: ((SSLSocket, String) -> Unit)? = null,
) : Closeable {
    private val directory = Files.createTempDirectory("console-tls-test").toFile()
    val certificate: X509Certificate
    val trustManager: X509TrustManager
    val requests = CopyOnWriteArrayList<String>()
    private val executor = Executors.newSingleThreadExecutor()
    private val socket: SSLServerSocket
    val url: String get() = "https://localhost:${socket.localPort}/"

    init {
        val keyStoreFile = directory.resolve("identity.p12")
        val executable = if (System.getProperty("os.name").orEmpty().startsWith("Windows")) "keytool.exe" else "keytool"
        val process = ProcessBuilder(
            java.io.File(System.getProperty("java.home"), "bin/$executable").path,
            "-genkeypair", "-alias", "test", "-keyalg", "RSA", "-keysize", "2048",
            "-dname", "CN=$host", "-ext", "SAN=dns:$host", "-validity", "2",
            "-storetype", "PKCS12", "-keystore", keyStoreFile.path,
            "-storepass", "console-test-only", "-keypass", "console-test-only", "-noprompt",
        ).redirectErrorStream(true).start()
        check(process.waitFor(30, TimeUnit.SECONDS)) { "keytool timed out" }
        check(process.exitValue() == 0) { process.inputStream.bufferedReader().readText() }
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            keyStoreFile.inputStream().use { load(it, "console-test-only".toCharArray()) }
        }
        certificate = keyStore.getCertificate("test") as X509Certificate
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("test-root", certificate)
        }
        trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(trustStore)
        }.trustManagers.filterIsInstance<X509TrustManager>().single()
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, "console-test-only".toCharArray())
        }.keyManagers
        socket = SSLContext.getInstance("TLS").apply { init(keyManagers, null, null) }
            .serverSocketFactory.createServerSocket(0, 10, InetAddress.getByName("127.0.0.1")) as SSLServerSocket
        socket.soTimeout = 5000
        executor.submit {
            try {
                (socket.accept() as SSLSocket).use { connection ->
                    connection.soTimeout = 5000
                    // Do not buffer past the header boundary: serve reads POST bodies
                    // and WebSocket frames directly from the same socket stream.
                    val endOfHeaders = listOf(13, 10, 13, 10).map { it.toChar() }.joinToString("")
                    val headers = buildString {
                        while (!endsWith(endOfHeaders)) {
                            val next = connection.inputStream.read()
                            if (next < 0) break
                            append(next.toChar())
                        }
                    }.replace(13.toChar().toString(), "").trimEnd()
                    if (headers.isNotEmpty()) {
                        requests.add(headers)
                        if (serve != null) serve.invoke(connection, headers)
                        else {
                            connection.outputStream.write(response.toByteArray(Charsets.UTF_8))
                            connection.outputStream.flush()
                        }
                    }
                }
            } catch (_: IOException) {
                // A failed TLS handshake is expected in rejection tests.
            }
        }
    }

    override fun close() {
        socket.close()
        executor.shutdownNow()
        check(executor.awaitTermination(10, TimeUnit.SECONDS))
        directory.deleteRecursively()
    }
}
