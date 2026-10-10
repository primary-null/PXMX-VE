package com.pxmx.app.data

import com.pxmx.app.data.api.DemoApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class DemoApiTest {

    private lateinit var demoApi: DemoApi

    @Before
    fun setup() {
        demoApi = DemoApi()
    }

    @Test
    fun powerActions_mutateGuestState() = runBlocking {
        // 1. Initial state of nova (vmid 100) is running
        var status = demoApi.guestStatus("alpha", "qemu", 100L).data?.status
        assertEquals("running", status)

        // 2. Stop action transitions to stopped
        demoApi.guestAction("alpha", "qemu", 100L, "stop")
        status = demoApi.guestStatus("alpha", "qemu", 100L).data?.status
        assertEquals("stopped", status)

        // 3. Start action transitions to running
        demoApi.guestAction("alpha", "qemu", 100L, "start")
        status = demoApi.guestStatus("alpha", "qemu", 100L).data?.status
        assertEquals("running", status)

        // 4. Suspend action transitions to paused
        demoApi.guestAction("alpha", "qemu", 100L, "suspend")
        status = demoApi.guestStatus("alpha", "qemu", 100L).data?.status
        assertEquals("paused", status)

        // 5. Resume action transitions to running
        demoApi.guestAction("alpha", "qemu", 100L, "resume")
        status = demoApi.guestStatus("alpha", "qemu", 100L).data?.status
        assertEquals("running", status)

        // 6. Shutdown action transitions to stopped
        demoApi.guestAction("alpha", "qemu", 100L, "shutdown")
        status = demoApi.guestStatus("alpha", "qemu", 100L).data?.status
        assertEquals("stopped", status)

        // 7. Reboot action transitions to running
        demoApi.guestAction("alpha", "qemu", 100L, "reboot")
        status = demoApi.guestStatus("alpha", "qemu", 100L).data?.status
        assertEquals("running", status)
    }

    @Test
    fun cloneGuest_spawnsNewGuest_qemuAndLxc() = runBlocking {
        // Clone QEMU VM
        val cloneResult = demoApi.cloneGuest(
            node = "alpha",
            type = "qemu",
            vmid = 9000L,
            newid = 500L,
            name = "my-cloned-vm",
            hostname = null
        )
        assertNotNull(cloneResult.data)
        assertTrue(cloneResult.data?.contains("500") == true)

        val qemuList = demoApi.nodeQemu("alpha").data.orEmpty()
        val clonedQemu = qemuList.find { it.vmid == 500L }
        assertNotNull(clonedQemu)
        assertEquals("my-cloned-vm", clonedQemu?.name)

        val qemuStatus = demoApi.guestStatus("alpha", "qemu", 500L).data
        assertEquals("my-cloned-vm", qemuStatus?.name)

        // Clone LXC Container
        val lxcCloneResult = demoApi.cloneGuest(
            node = "beta",
            type = "lxc",
            vmid = 200L,
            newid = 600L,
            name = null,
            hostname = "my-cloned-container"
        )
        assertNotNull(lxcCloneResult.data)
        val lxcStatus = demoApi.guestStatus("beta", "lxc", 600L).data
        assertEquals("my-cloned-container", lxcStatus?.name)
    }

    @Test
    fun configOverride_persistsOnbootToggle() = runBlocking {
        // Default onboot for nova is 1
        var config = demoApi.guestConfig("alpha", "qemu", 100L).data.orEmpty()
        assertEquals(1, config["onboot"])

        // Toggle onboot to 0
        demoApi.updateGuestConfig("alpha", "qemu", 100L, mapOf("onboot" to "0"))
        config = demoApi.guestConfig("alpha", "qemu", 100L).data.orEmpty()
        assertEquals(0, config["onboot"])

        // Toggle onboot back to 1
        demoApi.updateGuestConfig("alpha", "qemu", 100L, mapOf("onboot" to "1"))
        config = demoApi.guestConfig("alpha", "qemu", 100L).data.orEmpty()
        assertEquals(1, config["onboot"])
    }

    @Test
    fun tfaFlow_createTicketRequiresTfa_andCreateTicketTfaVerifies() = runBlocking {
        // 1. Regular user gets ticket directly
        val normalTicket = demoApi.createTicket("root@pam", "password")
        assertEquals("demo-ticket", normalTicket.data?.ticket)

        // 2. tfa-user gets a 200 with NeedTFA and a partial ticket
        val partial = demoApi.createTicket("tfa-user@pam", "password")
        assertEquals(1, partial.data?.needTfa)
        assertEquals("PVE:tfa-user@pam!tfa!DEMO-PARTIAL", partial.data?.ticket)

        // 3. Valid TOTP completes TFA via the second access/ticket call
        val tfaSuccess = demoApi.createTicketTfa("tfa-user@pam", "totp:123456", "PVE:tfa-user@pam!tfa!DEMO-PARTIAL")
        assertEquals("demo-ticket-tfa-verified", tfaSuccess.data?.ticket)

        // 4. Invalid TOTP rejects with 401
        try {
            demoApi.createTicketTfa("tfa-user@pam", "totp:999999", "PVE:tfa-user@pam!tfa!DEMO-PARTIAL")
            fail("Expected HttpException 401 for wrong OTP")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
        }
    }

    @Test
    fun nodeMemoryThresholds_matchSpecs() = runBlocking {
        val alpha = demoApi.nodeStatus("alpha").data!!
        val beta = demoApi.nodeStatus("beta").data!!
        val gamma = demoApi.nodeStatus("gamma").data!!

        val frac1 = alpha.memory!!.used!!.toDouble() / alpha.memory!!.total!!.toDouble()
        val frac2 = beta.memory!!.used!!.toDouble() / beta.memory!!.total!!.toDouble()
        val frac3 = gamma.memory!!.used!!.toDouble() / gamma.memory!!.total!!.toDouble()

        // alpha ≈55% (< 60%, green)
        assertTrue("alpha memory fraction $frac1 should be < 0.60", frac1 < 0.60)

        // beta ≈80% (60%..85%, amber)
        assertTrue("beta memory fraction $frac2 should be in 0.60..0.85", frac2 in 0.60..0.85)

        // gamma ≈90% (> 85%, red)
        assertTrue("gamma memory fraction $frac3 should be > 0.85", frac3 > 0.85)
    }

    @Test
    fun createLxc_withoutOstemplate_fails() = runBlocking {
        try {
            demoApi.createLxc(
                node = "alpha",
                vmid = 700L,
                hostname = "no-template-ct",
                ostemplate = null,
                rootfs = "local-lvm:16",
            )
            fail("Expected HttpException for blank ostemplate")
        } catch (e: HttpException) {
            assertEquals(400, e.code())
        }

        try {
            demoApi.createLxc(
                node = "alpha",
                vmid = 701L,
                hostname = "blank-template-ct",
                ostemplate = "   ",
                rootfs = "local-lvm:16",
            )
            fail("Expected HttpException for blank ostemplate")
        } catch (e: HttpException) {
            assertEquals(400, e.code())
        }
    }

    @Test
    fun createLxc_withOstemplate_succeeds() = runBlocking {
        val res = demoApi.createLxc(
            node = "alpha",
            vmid = 702L,
            hostname = "valid-ct",
            ostemplate = "local:vztmpl/debian-12-standard_12.2-1_amd64.tar.zst",
            rootfs = "local-lvm:16",
        )
        assertNotNull(res.data)
        val config = demoApi.guestConfig("alpha", "lxc", 702L).data.orEmpty()
        assertEquals("local:vztmpl/debian-12-standard_12.2-1_amd64.tar.zst", config["ostemplate"])
        assertEquals("local-lvm:16", config["rootfs"])
    }

    @Test
    fun alphaNodeStorage_andStorageContent_includesVztmpl() = runBlocking {
        val storages = demoApi.nodeStorage("alpha").data.orEmpty()
        val localStorage = storages.find { it.storage == "local" }
        assertNotNull(localStorage)
        assertTrue(localStorage?.content?.contains("vztmpl") == true)

        val content = demoApi.storageContent("alpha", "local", content = "vztmpl").data.orEmpty()
        assertTrue(content.any { it.volid == "local:vztmpl/debian-12-standard_12.2-1_amd64.tar.zst" })
    }

    @Test
    fun updateGuestConfig_withDeleteTags_removesTags() = runBlocking {
        val initialConfig = demoApi.guestConfig("alpha", "qemu", 100L).data.orEmpty()
        assertEquals("prod;web", initialConfig["tags"])
        val initialResource = demoApi.nodeQemu("alpha").data.orEmpty().find { it.vmid == 100L }
        assertEquals("prod;web", initialResource?.tags)

        demoApi.updateGuestConfig("alpha", "qemu", 100L, mapOf("delete" to "tags"))
        val config = demoApi.guestConfig("alpha", "qemu", 100L).data.orEmpty()
        assertFalse(config.containsKey("tags"))

        val resource = demoApi.nodeQemu("alpha").data.orEmpty().find { it.vmid == 100L }
        assertEquals("", resource?.tags)
    }

    @Test
    fun consoleProxy_allEndpointsSetDistinctPasswordAndTicket() = runBlocking {
        val qemu = demoApi.qemuVncProxy("alpha", 100L, 1).data!!
        assertEquals("DEMO_VNC_TICKET", qemu.ticket)
        assertEquals("DEMO_VNC_PASSWORD", qemu.password)

        val lxcTerm = demoApi.lxcTermProxy("beta", 202L, 1).data!!
        assertEquals("DEMO_TERM_TICKET", lxcTerm.ticket)
        assertEquals("DEMO_TERM_PASSWORD", lxcTerm.password)

        val nodeTerm = demoApi.nodeTermProxy("alpha", null, null).data!!
        assertEquals("DEMO_NODE_TICKET", nodeTerm.ticket)
        assertEquals("DEMO_NODE_PASSWORD", nodeTerm.password)

        val lxcVnc = demoApi.lxcVncProxy("gamma", 204L, 1).data!!
        assertEquals("DEMO_VNC_TICKET", lxcVnc.ticket)
        assertEquals("DEMO_VNC_PASSWORD", lxcVnc.password)
    }

    @Test
    fun nodeRrdData_containsArcsizeAndPressureKeys() = runBlocking {
        val points = demoApi.nodeRrdData("alpha", "hour").data.orEmpty()
        assertTrue(points.isNotEmpty())
        for (point in points) {
            assertTrue(point.containsKey("arcsize"))
            assertTrue(point.containsKey("pressurecpusome"))
            assertTrue(point.containsKey("pressurememorysome"))
            val arc = (point["arcsize"] as Number).toLong()
            assertTrue(arc >= 4_000_000_000L)
            val cpuPsi = (point["pressurecpusome"] as Number).toDouble()
            assertTrue(cpuPsi in 0.0..1.0)
            val memPsi = (point["pressurememorysome"] as Number).toDouble()
            assertTrue(memPsi in 0.0..1.0)
        }
    }

    @Test
    fun guestRrdData_containsMemhostAndPressureKeysForAllGuests() = runBlocking {
        for (vmid in listOf(100L, 102L, 200L)) {
            val points = demoApi.guestRrdData("alpha", "qemu", vmid, "hour").data.orEmpty()
            assertTrue(points.isNotEmpty())
            for (point in points) {
                assertTrue(point.containsKey("memhost"))
                assertTrue(point.containsKey("pressurecpusome"))
                assertTrue(point.containsKey("pressurememorysome"))
                val mem = (point["mem"] as Number).toLong()
                val memhost = (point["memhost"] as Number).toLong()
                assertTrue(memhost >= mem)
                val cpuPsi = (point["pressurecpusome"] as Number).toDouble()
                assertTrue(cpuPsi in 0.0..1.0)
                val memPsi = (point["pressurememorysome"] as Number).toDouble()
                assertTrue(memPsi in 0.0..1.0)
            }
        }
    }

    @Test
    fun guestFirewall_mapsOptionsAndRules() = runBlocking {
        val qemuOpt = demoApi.qemuFirewallOptions("alpha", 100L).data
        assertEquals(1, qemuOpt?.get("enable"))
        val qemuRules = demoApi.qemuFirewallRules("alpha", 100L).data.orEmpty()
        assertEquals(2, qemuRules.size)
        assertEquals("192.0.2.0/24", qemuRules[0]["source"])
        assertEquals("198.51.100.10", qemuRules[1]["source"])

        val qemuOptOther = demoApi.qemuFirewallOptions("alpha", 101L).data
        assertEquals(0, qemuOptOther?.get("enable"))
        assertTrue(demoApi.qemuFirewallRules("alpha", 101L).data.orEmpty().isEmpty())

        val lxcOpt = demoApi.lxcFirewallOptions("alpha", 200L).data
        assertEquals(1, lxcOpt?.get("enable"))
        val lxcRules = demoApi.lxcFirewallRules("alpha", 200L).data.orEmpty()
        assertEquals(2, lxcRules.size)
        assertEquals("192.0.2.0/24", lxcRules[0]["source"])
        assertEquals("203.0.113.15", lxcRules[1]["source"])

        val lxcOptOther = demoApi.lxcFirewallOptions("alpha", 201L).data
        assertEquals(0, lxcOptOther?.get("enable"))
        assertTrue(demoApi.lxcFirewallRules("alpha", 201L).data.orEmpty().isEmpty())
    }
}
