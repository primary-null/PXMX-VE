package com.pxmx.app.data.model

import com.pxmx.app.data.api.AppJson
import com.pxmx.app.data.api.ProxmoxClientFactory
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleProxyDataTest {

    private val json = AppJson

    @Test
    fun consoleProxyData_serializationRoundtrip_withBothFields() {
        val original = ConsoleProxyData(
            port = "5900",
            ticket = "PVE:root@pam:TICKET123",
            user = "root@pam",
            cert = "CERT_DATA",
            upid = "UPID:alpha:123",
            password = "VNC_PASSWORD_456",
        )

        val encoded = json.encodeToString(original)
        assertTrue(encoded.contains("\"ticket\":\"PVE:root@pam:TICKET123\""))
        assertTrue(encoded.contains("\"password\":\"VNC_PASSWORD_456\""))

        val decoded = json.decodeFromString<ConsoleProxyData>(encoded)
        assertEquals(original, decoded)
        assertEquals("VNC_PASSWORD_456", decoded.password)
        assertEquals("PVE:root@pam:TICKET123", decoded.ticket)
    }

    @Test
    fun consoleProxyData_deserializesLegacyResponseWithoutPassword() {
        val legacyJson = """{"port":"5900","ticket":"PVE:root@pam:TICKET123","user":"root@pam"}"""
        val decoded = json.decodeFromString<ConsoleProxyData>(legacyJson)

        assertEquals("5900", decoded.port)
        assertEquals("PVE:root@pam:TICKET123", decoded.ticket)
        assertNull(decoded.password)
        assertEquals("PVE:root@pam:TICKET123", decoded.rfbPassword)
    }

    @Test
    fun consoleProxyData_deserializesPve9ResponseWithDistinctPassword() {
        val pve9Json = """{
            "cert":"CERT_PEM",
            "password":"DIFFERENT_PASSWORD",
            "port":"5900",
            "ticket":"DIFFERENT_TICKET",
            "upid":"UPID:pve:1",
            "user":"root@pam"
        }"""
        val decoded = json.decodeFromString<ConsoleProxyData>(pve9Json)

        assertEquals("DIFFERENT_PASSWORD", decoded.password)
        assertEquals("DIFFERENT_TICKET", decoded.ticket)
        assertEquals("DIFFERENT_PASSWORD", decoded.rfbPassword)
    }

    @Test
    fun proxmoxClientFactory_sanitizeUrl_redactsPasswordAndTickets() {
        val url = "https://pve.lan:8006/api2/json/nodes/pve/vncwebsocket?port=5900&vncticket=secret_vnc&password=secret_pwd&token=secret_tok&ticket=secret_tick&other=visible"
        val sanitized = ProxmoxClientFactory.sanitizeUrl(url)

        assertTrue(sanitized.contains("vncticket=[REDACTED]"))
        assertTrue(sanitized.contains("password=[REDACTED]"))
        assertTrue(sanitized.contains("token=[REDACTED]"))
        assertTrue(sanitized.contains("ticket=[REDACTED]"))
        assertTrue(sanitized.contains("other=visible"))
        assertFalse(sanitized.contains("secret_vnc"))
        assertFalse(sanitized.contains("secret_pwd"))
        assertFalse(sanitized.contains("secret_tok"))
        assertFalse(sanitized.contains("secret_tick"))
    }
}
