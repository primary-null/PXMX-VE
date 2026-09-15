package com.pxmx.app.ui.console

import com.pxmx.app.data.api.CertUtils
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class ConsoleWebSocketBridgeTest {
    @Test
    fun `changed trusted certificate fails WebSocket handshake without sending ticket`() {
        LocalConsoleTlsServer("localhost").use { server ->
            val events = LinkedBlockingQueue<String>()
            val transport = ConsoleTransport(server.url, "test-ticket", createConsoleClient("localhost", true, "00".repeat(32), server.trustManager))
            val bridge = ConsoleWebSocketBridge(transport) { _, event, _ -> events.add(event) }
            try {
                bridge.connect("test", server.url.replace("https:", "wss:"), "binary")
                assertEquals("error", events.poll(10, TimeUnit.SECONDS))
                assertTrue(server.requests.isEmpty())
            } finally { bridge.dispose() }
        }
    }

    @Test
    fun `strict WebSocket rejects trusted wrong hostname before sending ticket`() {
        LocalConsoleTlsServer("wrong.example").use { server ->
            val events = LinkedBlockingQueue<String>()
            val transport = ConsoleTransport(server.url, "test-ticket", createConsoleClient("localhost", false, null, server.trustManager))
            val bridge = ConsoleWebSocketBridge(transport) { _, event, _ -> events.add(event) }
            try {
                bridge.connect("test", server.url.replace("https:", "wss:"), "")
                assertEquals("error", events.poll(10, TimeUnit.SECONDS))
                assertTrue(server.requests.isEmpty())
            } finally { bridge.dispose() }
        }
    }

    @Test
    fun `bridge rejects cross origin and cleartext socket requests`() {
        val events = LinkedBlockingQueue<String>()
        val transport = ConsoleTransport("https://pve.example:8006", "test-ticket", createConsoleClient("pve.example", false, null))
        val bridge = ConsoleWebSocketBridge(transport) { _, event, _ -> events.add(event) }
        try {
            for (url in listOf("ws://pve.example:8006/socket", "wss://pve.example:8007/socket", "wss://evil.example/socket")) {
                bridge.connect("test", url, "")
                assertEquals("error", events.poll(1, TimeUnit.SECONDS))
            }
            bridge.dispose()
            bridge.connect("test", "wss://pve.example:8006/socket", "")
            assertTrue(events.isEmpty())
        } finally { bridge.dispose() }
    }

    @Test
    fun `pinned self signed WebSocket exchanges real binary and text frames`() {
        val clientFrames = LinkedBlockingQueue<Pair<Int, ByteArray>>()
        LocalConsoleTlsServer("internal-pve.example", serve = { socket, headers ->
            val key = headers.lineSequence().first { it.startsWith("Sec-WebSocket-Key:", true) }.substringAfter(':').trim()
            val accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()))
            val output = socket.outputStream
            output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $accept\r\nSec-WebSocket-Protocol: binary\r\n\r\n").toByteArray())
            output.write(byteArrayOf(0x81.toByte(), 2, 'O'.code.toByte(), 'K'.code.toByte()))
            output.write(byteArrayOf(0x82.toByte(), 3, 0, 0x80.toByte(), 0xff.toByte()))
            output.flush()
            val input = java.io.DataInputStream(socket.inputStream)
            repeat(2) {
                val opcode = input.readUnsignedByte() and 0x0f
                val size = input.readUnsignedByte()
                check(size and 0x80 != 0 && size and 0x7f < 126)
                val mask = ByteArray(4).also { input.readFully(it) }
                val data = ByteArray(size and 0x7f).also { input.readFully(it) }
                for (i in data.indices) data[i] = (data[i].toInt() xor mask[i % 4].toInt()).toByte()
                clientFrames.add(opcode to data)
            }
        }).use { server ->
            val events = LinkedBlockingQueue<Pair<String, String>>()
            val pin = CertUtils.computeSha256Fingerprint(server.certificate)
            val transport = ConsoleTransport(server.url, "test-ticket", createConsoleClient("localhost", true, pin))
            val bridge = ConsoleWebSocketBridge(transport) { _, event, data -> events.add(event to data) }
            try {
                bridge.connect("test", server.url.replace("https:", "wss:"), "binary")
                assertEquals("open" to "binary", events.poll(10, TimeUnit.SECONDS))
                assertEquals("text" to "OK", events.poll(10, TimeUnit.SECONDS))
                assertEquals("binary" to "AID/", events.poll(10, TimeUnit.SECONDS))
                assertTrue(bridge.send("test", "input", false))
                assertTrue(bridge.send("test", "AID/", true))
                val text = clientFrames.poll(10, TimeUnit.SECONDS)!!
                val binary = clientFrames.poll(10, TimeUnit.SECONDS)!!
                assertEquals(1, text.first)
                assertEquals("input", text.second.toString(Charsets.UTF_8))
                assertEquals(2, binary.first)
                assertArrayEquals(byteArrayOf(0, 0x80.toByte(), 0xff.toByte()), binary.second)
                assertTrue(server.requests.single().contains("Cookie: PVEAuthCookie=test-ticket"))
            } finally { bridge.dispose() }
        }
    }
}
