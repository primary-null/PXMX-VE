package com.pxmx.app.ui.icons

import com.pxmx.app.data.model.ClusterResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GuestIconsTest {

    private fun resource(
        type: String,
        name: String? = null,
        ostype: String? = null,
        tags: String? = null,
        template: Int? = null,
    ) = ClusterResource(
        id = "$type/100",
        type = type,
        name = name,
        ostype = ostype,
        tags = tags,
        template = template,
    )

    @Test
    fun styleFor_node_returnsNodeKindAndIcon() {
        val res = resource(type = "node", name = "pve1")
        val style = GuestIcons.styleFor(res)
        assertEquals(GuestKind.NODE, style.kind)
        assertEquals("Node", style.label)
        assertSame(GuestGlyphs.Node, style.icon)
    }

    @Test
    fun styleFor_storage_returnsStorageKindAndIcon() {
        val res = resource(type = "storage", name = "local-zfs")
        val style = GuestIcons.styleFor(res)
        assertEquals(GuestKind.STORAGE, style.kind)
        assertEquals("Storage", style.label)
        assertSame(GuestGlyphs.Storage, style.icon)
    }

    @Test
    fun styleFor_lxc_container_returnsContainerKind() {
        val res = resource(type = "lxc", name = "dns-pihole")
        val style = GuestIcons.styleFor(res)
        assertEquals(GuestKind.CONTAINER, style.kind)
        assertEquals("Container", style.label)
        assertSame(GuestGlyphs.Container, style.icon)
    }

    @Test
    fun styleFor_lxc_template_returnsTemplateKind() {
        val res = resource(type = "lxc", name = "debian-base", template = 1)
        val style = GuestIcons.styleFor(res)
        assertEquals(GuestKind.TEMPLATE, style.kind)
        assertEquals("CT template", style.label)
        assertSame(GuestGlyphs.Template, style.icon)
    }

    @Test
    fun styleFor_qemu_template_returnsTemplateKind() {
        val res = resource(type = "qemu", name = "ubuntu-cloud-init", template = 1)
        val style = GuestIcons.styleFor(res)
        assertEquals(GuestKind.TEMPLATE, style.kind)
        assertEquals("VM template", style.label)
        assertSame(GuestGlyphs.Template, style.icon)
    }

    @Test
    fun styleFor_homeAssistant_matchesHaosNameOrTags() {
        val res1 = resource(type = "qemu", name = "haos-prod", ostype = "l26")
        val style1 = GuestIcons.styleFor(res1)
        assertEquals(GuestKind.HOME_ASSISTANT, style1.kind)
        assertEquals("Home Assistant", style1.label)
        assertSame(GuestGlyphs.HomeAssistant, style1.icon)

        val res2 = resource(type = "qemu", name = "vm-smarthome", tags = "hass,iot")
        val style2 = GuestIcons.styleFor(res2)
        assertEquals(GuestKind.HOME_ASSISTANT, style2.kind)
        assertSame(GuestGlyphs.HomeAssistant, style2.icon)
    }

    @Test
    fun styleFor_windows_matchesOstypeOrName() {
        // By ostype
        val res1 = resource(type = "qemu", name = "desktop", ostype = "win11")
        val style1 = GuestIcons.styleFor(res1)
        assertEquals(GuestKind.WINDOWS, style1.kind)
        assertEquals("Windows", style1.label)
        assertSame(GuestGlyphs.Windows, style1.icon)

        val res2 = resource(type = "qemu", name = "legacy", ostype = "wxp")
        val style2 = GuestIcons.styleFor(res2)
        assertEquals(GuestKind.WINDOWS, style2.kind)
        assertSame(GuestGlyphs.Windows, style2.icon)

        // By name or tag heuristic
        val res3 = resource(type = "qemu", name = "win-ad-server", ostype = "l26")
        val style3 = GuestIcons.styleFor(res3)
        assertEquals(GuestKind.WINDOWS, style3.kind)
        assertSame(GuestGlyphs.Windows, style3.icon)

        val res4 = resource(type = "qemu", name = "sql-box", tags = "mssql,prod")
        val style4 = GuestIcons.styleFor(res4)
        assertEquals(GuestKind.WINDOWS, style4.kind)
        assertSame(GuestGlyphs.Windows, style4.icon)
    }

    @Test
    fun styleFor_linux_matchesOstypeOrKeywords() {
        // Standard ostype
        val res1 = resource(type = "qemu", name = "srv01", ostype = "l26")
        val style1 = GuestIcons.styleFor(res1)
        assertEquals(GuestKind.LINUX, style1.kind)
        assertEquals("Linux", style1.label)
        assertSame(GuestGlyphs.Linux, style1.icon)

        // Linux by name keywords
        val res2 = resource(type = "qemu", name = "docker-runner", ostype = "other")
        val style2 = GuestIcons.styleFor(res2)
        assertEquals(GuestKind.LINUX, style2.kind)
        assertSame(GuestGlyphs.Linux, style2.icon)

        val res3 = resource(type = "qemu", name = "k8s-worker", ostype = null)
        val style3 = GuestIcons.styleFor(res3)
        assertEquals(GuestKind.LINUX, style3.kind)
        assertSame(GuestGlyphs.Linux, style3.icon)

        val res4 = resource(type = "qemu", name = "solaris-box", ostype = "solaris10")
        val style4 = GuestIcons.styleFor(res4)
        assertEquals(GuestKind.LINUX, style4.kind)
        assertEquals("Solaris", style4.label)
        assertSame(GuestGlyphs.Linux, style4.icon)
    }

    @Test
    fun styleFor_genericVm_fallback() {
        val res = resource(type = "qemu", name = "custom-app", ostype = "unknown-os")
        val style = GuestIcons.styleFor(res)
        assertEquals(GuestKind.GENERIC_VM, style.kind)
        assertEquals("VM", style.label)
        assertSame(GuestGlyphs.GenericVm, style.icon)
    }

    @Test
    fun styleFor_firewall_matchesPfsenseAndOpnsense() {
        val res1 = resource(type = "qemu", name = "pfsense-gateway", ostype = "other")
        val style1 = GuestIcons.styleFor(res1)
        assertEquals(GuestKind.FIREWALL, style1.kind)
        assertEquals("Firewall", style1.label)
        assertSame(GuestGlyphs.Firewall, style1.icon)

        val res2 = resource(type = "qemu", name = "edge-router", tags = "opnsense,firewall")
        val style2 = GuestIcons.styleFor(res2)
        assertEquals(GuestKind.FIREWALL, style2.kind)
        assertSame(GuestGlyphs.Firewall, style2.icon)
    }

    @Test
    fun styleFor_media_matchesPlexAndJellyfin() {
        val res1 = resource(type = "qemu", name = "plex-media-server", ostype = "l26")
        val style1 = GuestIcons.styleFor(res1)
        assertEquals(GuestKind.MEDIA, style1.kind)
        assertEquals("Media", style1.label)
        assertSame(GuestGlyphs.Media, style1.icon)

        val res2 = resource(type = "qemu", name = "streamer", tags = "jellyfin")
        val style2 = GuestIcons.styleFor(res2)
        assertEquals(GuestKind.MEDIA, style2.kind)
        assertSame(GuestGlyphs.Media, style2.icon)
    }

    @Test
    fun styleFor_database_matchesPostgresAndRedis() {
        val res1 = resource(type = "qemu", name = "postgres-primary", ostype = "l26")
        val style1 = GuestIcons.styleFor(res1)
        assertEquals(GuestKind.DATABASE, style1.kind)
        assertEquals("Database", style1.label)
        assertSame(GuestGlyphs.Database, style1.icon)

        val res2 = resource(type = "qemu", name = "cache-tier", tags = "redis")
        val style2 = GuestIcons.styleFor(res2)
        assertEquals(GuestKind.DATABASE, style2.kind)
        assertSame(GuestGlyphs.Database, style2.icon)
    }
}
