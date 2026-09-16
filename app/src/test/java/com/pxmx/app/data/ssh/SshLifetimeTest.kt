package com.pxmx.app.data.ssh

import kotlinx.coroutines.*
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import org.junit.Assert.*
import org.junit.Test
import java.io.InputStream
import java.lang.reflect.Proxy
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SshLifetimeTest {
    private class HangingClient(private val blockConnect: Boolean = false) : SSHClient() {
        val reading = CountDownLatch(1)
        val released = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val socketClosed = AtomicBoolean(false)
        private val fakeSocket = object : Socket() {
            override fun close() { socketClosed.set(true); released.countDown() }
        }
        private val stream = object : InputStream() {
            override fun read(): Int {
                reading.countDown()
                while (released.count > 0) {
                    try { released.await() } catch (_: InterruptedException) { /* socket close is required */ }
                }
                return -1
            }
        }
        private val command = Proxy.newProxyInstance(Session.Command::class.java.classLoader, arrayOf(Session.Command::class.java)) { _, method, _ ->
            when (method.name) {
                "getInputStream", "getErrorStream" -> stream
                "getExitStatus" -> 0
                "isOpen" -> true
                "close" -> { released.countDown(); null }
                else -> null
            }
        } as Session.Command
        private val session = Proxy.newProxyInstance(Session::class.java.classLoader, arrayOf(Session::class.java)) { _, method, _ ->
            when (method.name) {
                "exec" -> command
                "close" -> { released.countDown(); null }
                else -> null
            }
        } as Session
        override fun connect(hostname: String, port: Int) {
            if (blockConnect) stream.read()
        }
        override fun authPassword(username: String, password: String) {}
        override fun startSession(): Session = session
        override fun newSFTPClient(): net.schmizz.sshj.sftp.SFTPClient = throw CancellationException("test transfer not started")
        override fun getSocket(): Socket = fakeSocket
        override fun disconnect() { fakeSocket.close(); disconnected.countDown() }
    }

    @Test fun cancellationClosesSocketAndReturnsWithoutWaitingForOpenOutput() = runBlocking {
        val client = HangingClient()
        val executor = SshUpgradeExecutor({ null }, { _, _ -> }, { client })
        val job = launch(Dispatchers.Default) { executor.executeUpgrade("fake.invalid", password = "fake") }
        try {
            assertTrue(client.reading.await(3, TimeUnit.SECONDS))
            job.cancel()
            val stopped = withTimeoutOrNull(1_000) { job.join(); true } ?: false
            assertTrue("Cancellation must not wait for EOF", stopped)
            assertTrue("Cancellation must close the SSH socket", client.socketClosed.get())
        } finally { client.released.countDown(); job.cancelAndJoin() }
    }

    @Test fun sftpCancellationClosesBlockedConnectBeforeAuthentication() = runBlocking {
        val client = HangingClient(blockConnect = true)
        val downloader = SftpDownloader({ null }, { _, _ -> }, { client })
        val job = launch(Dispatchers.Default) {
            downloader.download("fake.invalid", username = "root", password = "fake", remotePath = "/fake", localSink = java.io.ByteArrayOutputStream()) { _, _ -> }
        }
        try {
            assertTrue(client.reading.await(3, TimeUnit.SECONDS))
            job.cancel()
            val stopped = withTimeoutOrNull(1_000) { job.join(); true } ?: false
            assertTrue("SFTP cancellation must close a blocked connect", stopped)
            assertTrue(client.socketClosed.get())
        } finally { client.released.countDown(); job.cancelAndJoin() }
    }

    @Test fun operationDeadlineIncludesOpenOutputStreams() = runBlocking {
        val client = HangingClient()
        val executor = SshUpgradeExecutor({ null }, { _, _ -> }, { client }, timeoutMs = 150)
        val job = async(Dispatchers.Default) { executor.executeUpgrade("fake.invalid", password = "fake") }
        try {
            val result = withTimeoutOrNull(1_500) { job.await() }
            assertNotNull("Deadline must include stream draining, not start after it", result)
            assertTrue(result!!.isFailure)
            assertTrue(client.socketClosed.get())
        } finally { client.released.countDown(); job.cancelAndJoin() }
    }
}
