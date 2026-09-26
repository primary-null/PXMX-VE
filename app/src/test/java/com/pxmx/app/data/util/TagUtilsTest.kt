package com.pxmx.app.data.util

import com.pxmx.app.data.model.ClusterResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagUtilsTest {

    @Test
    fun testSplitTags() {
        assertEquals(emptyList<String>(), TagUtils.splitTags(null))
        assertEquals(emptyList<String>(), TagUtils.splitTags(""))
        assertEquals(emptyList<String>(), TagUtils.splitTags("   "))
        assertEquals(emptyList<String>(), TagUtils.splitTags(";;;"))
        assertEquals(listOf("prod"), TagUtils.splitTags("prod"))
        assertEquals(listOf("prod", "db", "linux"), TagUtils.splitTags("prod;db;linux"))
        assertEquals(listOf("prod", "db", "linux"), TagUtils.splitTags(" prod ; db ; linux "))
        assertEquals(listOf("prod", "db"), TagUtils.splitTags(";;prod;;db;;"))
    }

    @Test
    fun testJoinTags() {
        assertEquals("", TagUtils.joinTags(emptyList()))
        assertEquals("prod", TagUtils.joinTags(listOf("prod")))
        assertEquals("prod;db;linux", TagUtils.joinTags(listOf("prod", "db", "linux")))
        assertEquals("prod;db", TagUtils.joinTags(listOf(" prod ", "db ")))
    }

    @Test
    fun testIsValidTag() {
        assertTrue(TagUtils.isValidTag("prod"))
        assertTrue(TagUtils.isValidTag("db-1"))
        assertTrue(TagUtils.isValidTag("web_server"))
        assertFalse(TagUtils.isValidTag(""))
        assertFalse(TagUtils.isValidTag("   "))
        assertFalse(TagUtils.isValidTag("prod;db"))
        assertFalse(TagUtils.isValidTag(";"))
    }

    @Test
    fun testAddTag() {
        assertEquals("prod", TagUtils.addTag(null, "prod"))
        assertEquals("prod", TagUtils.addTag("", "prod"))
        assertEquals("prod;db", TagUtils.addTag("prod", "db"))
        // Avoid duplicate
        assertEquals("prod", TagUtils.addTag("prod", "prod"))
        assertEquals("prod", TagUtils.addTag("prod", "PROD"))
        // Reject invalid tag (contains semicolon or empty)
        assertEquals("prod", TagUtils.addTag("prod", "invalid;tag"))
        assertEquals("prod", TagUtils.addTag("prod", "  "))
    }

    @Test
    fun testRemoveTag() {
        assertEquals("", TagUtils.removeTag(null, "prod"))
        assertEquals("", TagUtils.removeTag("prod", "prod"))
        assertEquals("db;linux", TagUtils.removeTag("prod;db;linux", "prod"))
        assertEquals("prod;linux", TagUtils.removeTag("prod;db;linux", "db"))
        assertEquals("prod;db", TagUtils.removeTag("prod;db;linux", "linux"))
        // Case-insensitive removal
        assertEquals("db", TagUtils.removeTag("PROD;db", "prod"))
        // Non-existent tag leaves list unchanged
        assertEquals("prod;db", TagUtils.removeTag("prod;db", "missing"))
    }

    @Test
    fun testTagFilteringOnResources() {
        val vm1 = ClusterResource(id = "qemu/100", name = "gateway", tags = "prod;network;edge")
        val vm2 = ClusterResource(id = "qemu/101", name = "database", tags = "prod;db")
        val vm3 = ClusterResource(id = "lxc/200", name = "dev-box", tags = "dev;sandbox")
        val all = listOf(vm1, vm2, vm3)

        val prodFiltered = all.filter { TagUtils.splitTags(it.tags).any { t -> t.equals("prod", ignoreCase = true) } }
        assertEquals(2, prodFiltered.size)
        assertTrue(prodFiltered.contains(vm1))
        assertTrue(prodFiltered.contains(vm2))

        val devFiltered = all.filter { TagUtils.splitTags(it.tags).any { t -> t.equals("dev", ignoreCase = true) } }
        assertEquals(1, devFiltered.size)
        assertTrue(devFiltered.contains(vm3))

        val noneFiltered = all.filter { TagUtils.splitTags(it.tags).any { t -> t.equals("storage", ignoreCase = true) } }
        assertTrue(noneFiltered.isEmpty())
    }
}
