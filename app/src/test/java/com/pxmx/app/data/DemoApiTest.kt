package com.pxmx.app.data

import com.pxmx.app.data.api.DemoApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    fun tfaFlow_createTicketRequiresTfa_andAccessTfaVerifies() = runBlocking {
        // 1. Regular user gets ticket directly
        val normalTicket = demoApi.createTicket("root@pam", "password")
        assertEquals("demo-ticket", normalTicket.data?.ticket)

        // 2. tfa-user triggers 401 with NeedTFA challenge
        try {
            demoApi.createTicket("tfa-user@pam", "password")
            fail("Expected HttpException 401 for tfa-user")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
            val errorBody = e.response()?.errorBody()?.string() ?: ""
            assertTrue(errorBody.contains("NeedTFA") && errorBody.contains("TFA-PARTIAL-DEMO-TICKET"))
        }

        // 3. Valid OTP completes TFA
        val tfaSuccess = demoApi.accessTfa("TFA-PARTIAL-DEMO-TICKET", "123456")
        assertEquals("demo-ticket-tfa-verified", tfaSuccess.data?.ticket)

        // 4. Invalid OTP rejects with 401
        try {
            demoApi.accessTfa("TFA-PARTIAL-DEMO-TICKET", "999999")
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
}
