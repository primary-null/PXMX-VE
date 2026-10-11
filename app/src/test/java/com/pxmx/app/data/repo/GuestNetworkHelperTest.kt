package com.pxmx.app.data.repo

import com.pxmx.app.data.model.ConfigNet
import com.pxmx.app.data.model.ParsedGuestConfig
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestNetworkHelperTest {

    @Test
    fun parseAgentInterfaces_skipsLoopbackAndParsesAddresses() {
        val json = buildJsonObject {
            put("result", buildJsonArray {
                add(buildJsonObject {
                    put("name", "eth0")
                    put("hardware-address", "AA:BB:CC:DD:EE:FF")
                    put("ip-addresses", buildJsonArray {
                        add(buildJsonObject {
                            put("ip-address", "192.168.1.100")
                            put("ip-address-type", "ipv4")
                            put("prefix", 24)
                        })
                        add(buildJsonObject {
                            put("ip-address", "fe80::1")
                            put("ip-address-type", "ipv6")
                            put("prefix", 64)
                        })
                    })
                })
                add(buildJsonObject {
                    put("name", "lo")
                    put("hardware-address", "00:00:00:00:00:00")
                    put("ip-addresses", buildJsonArray {
                        add(buildJsonObject {
                            put("ip-address", "127.0.0.1")
                            put("ip-address-type", "ipv4")
                            put("prefix", 8)
                        })
                    })
                })
            })
        }

        val parsed = GuestNetworkHelper.parseAgentInterfaces(json)
        assertEquals(1, parsed.size)
        val iface = parsed[0]
        assertEquals("eth0", iface.name)
        assertEquals("AA:BB:CC:DD:EE:FF", iface.mac)
        assertEquals(2, iface.addresses.size)
        assertEquals("192.168.1.100", iface.addresses[0].ip)
        assertEquals(24, iface.addresses[0].prefix)
        assertEquals("ipv4", iface.addresses[0].type)
        assertEquals("fe80::1", iface.addresses[1].ip)
        assertEquals(64, iface.addresses[1].prefix)
        assertEquals("ipv6", iface.addresses[1].type)
    }

    @Test
    fun parseLxcInterfaces_skipsLoopbackAndParsesInetAndInet6() {
        val json = buildJsonArray {
            add(buildJsonObject {
                put("name", "eth0")
                put("hwaddr", "BC:24:11:AA:BB:01")
                put("inet", "192.168.1.100/24")
                put("inet6", "fe80::2/64")
            })
            add(buildJsonObject {
                put("name", "lo")
                put("hwaddr", "00:00:00:00:00:00")
                put("inet", "127.0.0.1/8")
            })
        }

        val parsed = GuestNetworkHelper.parseLxcInterfaces(json)
        assertEquals(1, parsed.size)
        val iface = parsed[0]
        assertEquals("eth0", iface.name)
        assertEquals("BC:24:11:AA:BB:01", iface.mac)
        assertEquals(2, iface.addresses.size)
        assertEquals("192.168.1.100", iface.addresses[0].ip)
        assertEquals(24, iface.addresses[0].prefix)
        assertEquals("ipv4", iface.addresses[0].type)
        assertEquals("fe80::2", iface.addresses[1].ip)
        assertEquals(64, iface.addresses[1].prefix)
        assertEquals("ipv6", iface.addresses[1].type)
    }

    @Test
    fun parseInterfaces_nullData_returnsEmpty() {
        assertTrue(GuestNetworkHelper.parseAgentInterfaces(null).isEmpty())
        assertTrue(GuestNetworkHelper.parseLxcInterfaces(null).isEmpty())
    }

    @Test
    fun buildUpdatedNicValue_togglesFirewallAndLinkDown_preservesMac() {
        val initialNet = ConfigNet(
            key = "net0",
            raw = "virtio=AA:BB:CC:DD:EE:FF,bridge=vmbr0,firewall=1",
            mac = "AA:BB:CC:DD:EE:FF",
            bridge = "vmbr0",
            firewall = true,
        )

        // Toggle firewall off
        val fwOff = GuestRepository.buildUpdatedNicValue(initialNet, toggleFirewall = true)
        assertTrue(fwOff.contains("firewall=0"))
        assertTrue(fwOff.contains("virtio=AA:BB:CC:DD:EE:FF"))
        assertTrue(fwOff.contains("bridge=vmbr0"))

        // Toggle link_down on
        val linkDownOn = GuestRepository.buildUpdatedNicValue(initialNet, toggleLinkDown = true)
        assertTrue(linkDownOn.contains("link_down=1"))
        assertTrue(linkDownOn.contains("firewall=1"))
        assertTrue(linkDownOn.contains("virtio=AA:BB:CC:DD:EE:FF"))

        // Toggle link_down back off when net already has link_down=1
        val netDown = ConfigNet(
            key = "net0",
            raw = "virtio=AA:BB:CC:DD:EE:FF,bridge=vmbr0,link_down=1",
            mac = "AA:BB:CC:DD:EE:FF",
            bridge = "vmbr0",
            extra = mapOf("link_down" to "1"),
        )
        val linkDownOff = GuestRepository.buildUpdatedNicValue(netDown, toggleLinkDown = true)
        assertTrue(linkDownOff.contains("link_down=0"))
        assertTrue(linkDownOff.contains("virtio=AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun parsedGuestConfig_ipconfigForNet_resolvesCorrectCloudInit() {
        val cfg = ParsedGuestConfig(
            raw = mapOf(
                "net0" to "virtio=AA:BB:CC:DD:EE:FF,bridge=vmbr0",
                "ipconfig0" to "ip=192.168.1.100/24,gw=192.168.1.1",
            )
        )
        assertEquals("ip=192.168.1.100/24,gw=192.168.1.1", cfg.ipconfigForNet("net0"))
        assertNull(cfg.ipconfigForNet("net1"))
    }
}
