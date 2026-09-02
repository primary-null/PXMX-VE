package com.pxmx.app.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestConfigParserTest {

    @Test
    fun `parse QEMU config correctly`() {
        val raw = mapOf(
            "name" to "test-vm",
            "memory" to 4096.0,
            "cores" to 2.0,
            "ostype" to "l26",
            "scsi0" to "local-lvm:vm-100-disk-0,size=32G",
            "ide0" to "local:iso/ubuntu.iso,media=cdrom,size=2G",
            "net0" to "virtio=AA:BB:CC:DD:EE:FF,bridge=vmbr0,firewall=1"
        )

        val parsed = GuestConfigParser.parse(raw)

        assertEquals("test-vm", parsed.name)
        assertEquals("4096", parsed.memory)
        assertEquals("2", parsed.cores)
        assertEquals("l26", parsed.ostype)
        
        assertEquals(2, parsed.disks.size)
        val scsi0 = parsed.disks.find { it.key == "scsi0" }!!
        assertEquals("local-lvm", scsi0.storage)
        assertEquals("32G", scsi0.size)
        assertFalse(scsi0.isCdrom)

        val ide0 = parsed.disks.find { it.key == "ide0" }!!
        assertEquals("local", ide0.storage)
        assertTrue(ide0.isCdrom)

        assertEquals(1, parsed.nets.size)
        val net0 = parsed.nets.first()
        assertEquals("virtio", net0.model)
        assertEquals("AA:BB:CC:DD:EE:FF", net0.mac)
        assertEquals("vmbr0", net0.bridge)
        assertTrue(net0.firewall)
    }

    @Test
    fun `parse LXC config correctly`() {
        val raw = mapOf(
            "hostname" to "test-ct",
            "ostype" to "ubuntu",
            "rootfs" to "local-lvm:vm-101-disk-0,size=8G",
            "net0" to "name=eth0,bridge=vmbr0,hwaddr=11:22:33:44:55:66,type=veth"
        )

        val parsed = GuestConfigParser.parse(raw)

        assertEquals("ubuntu", parsed.ostype)
        assertEquals(1, parsed.disks.size)
        assertEquals("local-lvm", parsed.disks.first().storage)
        assertEquals("8G", parsed.disks.first().size)

        assertEquals(1, parsed.nets.size)
        val net0 = parsed.nets.first()
        assertEquals("veth", net0.model)
        assertEquals("11:22:33:44:55:66", net0.mac)
    }

    @Test
    fun `redact secret keys in raw map`() {
        val raw = mapOf(
            "name" to "secret-vm",
            "password" to "mypassword",
            "cipassword" to "cloudpass",
            "sshkeys" to "ssh-rsa AAA...",
            "token" to "mytoken"
        )

        val parsed = GuestConfigParser.parse(raw)

        assertEquals("secret-vm", parsed.raw["name"])
        assertEquals("••••••••", parsed.raw["password"])
        assertEquals("••••••••", parsed.raw["cipassword"])
        assertEquals("••••••••", parsed.raw["sshkeys"])
        assertEquals("••••••••", parsed.raw["token"])
    }

    @Test
    fun `handle missing or null values gracefully`() {
        val raw = mapOf(
            "name" to "empty-vm",
            "memory" to null,
            "cores" to null
        )

        val parsed = GuestConfigParser.parse(raw)

        assertEquals("empty-vm", parsed.name)
        assertEquals(null, parsed.memory)
        assertEquals(null, parsed.cores)
        assertTrue(parsed.disks.isEmpty())
        assertTrue(parsed.nets.isEmpty())
    }
}
