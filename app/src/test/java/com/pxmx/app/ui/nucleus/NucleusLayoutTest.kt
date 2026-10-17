package com.pxmx.app.ui.nucleus

import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.config.GuestConfigParser
import com.pxmx.app.data.model.ClusterMember
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.SiteInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NucleusLayoutTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun standalone_placesOneCore_andOnlyRunningGuests_stoppedGuestAbsent() {
        val site = SiteInfo(
            isCluster = false,
            clusterName = null,
            nodeName = "pve-solo",
            nodeCount = 1,
        )
        val resources = listOf(
            ClusterResource(id = "node/pve-solo", type = "node", node = "pve-solo", name = "pve-solo", status = "online"),
            ClusterResource(id = "qemu/100", type = "qemu", node = "pve-solo", vmid = 100L, name = "web-vm", status = "running"),
            ClusterResource(id = "lxc/200", type = "lxc", node = "pve-solo", vmid = 200L, name = "dns-ct", status = "running"),
            ClusterResource(id = "qemu/101", type = "qemu", node = "pve-solo", vmid = 101L, name = "stopped-vm", status = "stopped"),
            ClusterResource(id = "lxc/201", type = "lxc", node = "pve-solo", vmid = 201L, name = "frozen-ct", status = "frozen"),
            ClusterResource(id = "qemu/9000", type = "qemu", node = "pve-solo", vmid = 9000L, name = "tmpl", status = "running", template = 1),
            ClusterResource(id = "storage/local", type = "storage", node = "pve-solo", storage = "local", status = "available"),
            ClusterResource(id = "pool/prod", type = "pool", name = "prod"),
            ClusterResource(id = "sdn/zone1", type = "sdn", name = "zone1"),
        )

        val scene = buildNucleusLayout(site, resources)

        assertFalse(scene.core.isCluster)
        assertEquals("pve-solo", scene.core.label)
        assertEquals(Vec3(0f, 0f, 0f), scene.core.position)
        assertTrue("Standalone layout should not have separate inner node ring", scene.nodes.isEmpty())

        assertEquals(2, scene.guests.size)
        val guestNames = scene.guests.map { it.name }
        assertTrue(guestNames.contains("web-vm"))
        assertTrue(guestNames.contains("dns-ct"))
        assertFalse("Stopped guest must be absent", guestNames.contains("stopped-vm"))
        assertFalse("Frozen guest must be absent", guestNames.contains("frozen-ct"))
        assertFalse("Template must be absent", guestNames.contains("tmpl"))
        assertEquals("STANDALONE · 2 RUNNING GUESTS", scene.subtitle)

        scene.guests.forEach { guest ->
            assertEquals(Vec3(0f, 0f, 0f), guest.orbitCenter)
        }
    }

    @Test
    fun cluster_placesOneCore_plusEachNode_andAttachesGuestToItsOwnNode() {
        val site = SiteInfo(
            isCluster = true,
            clusterName = "lab-cluster",
            nodeName = "alpha",
            nodeCount = 3,
            members = listOf(
                ClusterMember(host = "192.0.2.10", nodeName = "alpha"),
                ClusterMember(host = "192.0.2.11", nodeName = "beta"),
                ClusterMember(host = "192.0.2.12", nodeName = "gamma"),
            ),
        )
        val resources = listOf(
            ClusterResource(id = "node/alpha", type = "node", node = "alpha", name = "alpha", status = "online"),
            ClusterResource(id = "node/beta", type = "node", node = "beta", name = "beta", status = "online"),
            ClusterResource(id = "node/gamma", type = "node", node = "gamma", name = "gamma", status = "offline"),
            ClusterResource(id = "qemu/100", type = "qemu", node = "alpha", vmid = 100L, name = "nova", status = "running"),
            ClusterResource(id = "lxc/202", type = "lxc", node = "beta", vmid = 202L, name = "pulsar", status = "running"),
            ClusterResource(id = "qemu/101", type = "qemu", node = "beta", vmid = 101L, name = "meteor", status = "stopped"),
        )

        val scene = buildNucleusLayout(site, resources)

        assertTrue(scene.core.isCluster)
        assertEquals("lab-cluster", scene.core.label)
        assertEquals(3, scene.nodes.size)
        assertEquals(listOf("alpha", "beta", "gamma"), scene.nodes.map { it.name })

        // Verify member host IPs are never leaked into node or core labels
        scene.nodes.forEach { node ->
            assertFalse(node.name.contains("192.0.2"))
        }

        // Offline node still appears, dimmer than online nodes
        val alphaNode = scene.nodes.first { it.name == "alpha" }
        val betaNode = scene.nodes.first { it.name == "beta" }
        val gammaNode = scene.nodes.first { it.name == "gamma" }
        assertTrue(alphaNode.isOnline)
        assertTrue(betaNode.isOnline)
        assertFalse(gammaNode.isOnline)
        assertTrue(gammaNode.brightness < alphaNode.brightness)

        // Running guests attached to their own node
        assertEquals(2, scene.guests.size)
        val nova = scene.guests.first { it.name == "nova" }
        val pulsar = scene.guests.first { it.name == "pulsar" }

        assertEquals("alpha", nova.node)
        assertEquals(alphaNode.position, nova.orbitCenter)
        assertTrue(nova.position.distanceTo(alphaNode.position) < nova.position.distanceTo(betaNode.position))

        assertEquals("beta", pulsar.node)
        assertEquals(betaNode.position, pulsar.orbitCenter)
        assertTrue(pulsar.position.distanceTo(betaNode.position) < pulsar.position.distanceTo(alphaNode.position))
    }

    @Test
    fun cluster_withEmptyMembers_fallsBackToNodeRowsFromResources() {
        val site = SiteInfo(
            isCluster = true,
            clusterName = null,
            nodeName = "node-a",
            nodeCount = 2,
            members = emptyList(),
        )
        val resources = listOf(
            ClusterResource(id = "node/node-a", type = "node", node = "node-a", status = "online"),
            ClusterResource(id = "node/node-b", type = "node", node = "node-b", status = "online"),
        )

        val scene = buildNucleusLayout(site, resources)
        assertEquals("Cluster", scene.core.label)
        assertEquals(listOf("node-a", "node-b"), scene.nodes.map { it.name })
    }

    @Test
    fun spokeLabels_fromSampleConfig_includeDiskStorageBridgeAndUsbDisplay_andExcludeCipassword() {
        val rawConfig = mapOf<String, Any?>(
            "name" to "nova",
            "scsi0" to "local-lvm:vm-100-disk-0,size=32G",
            "net0" to "virtio=AA:BB:CC:DD:EE:FF,bridge=vmbr0",
            "usb0" to "host=1234:5678,usb3=1",
            "cipassword" to "hunter2-secret-pass",
            "password" to "root-secret",
            "sshkeys" to "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5",
            "digest" to "0123456789abcdef0123456789abcdef01234567",
            "description" to "confidential guest notes",
        )

        val parsed = GuestConfigParser.parse(rawConfig)
        val spokes = buildGuestSpokes(parsed)
        val labels = spokes.map { it.label }
        val joined = labels.joinToString(" | ")

        // Storage spoke includes disk label, storage name, and size
        val storageSpokes = spokes.filter { it.family == SpokeFamily.STORAGE }
        assertEquals(1, storageSpokes.size)
        assertFalse(storageSpokes.first().isMuted)
        assertTrue(storageSpokes.first().label.contains("local-lvm"))
        assertTrue(storageSpokes.first().label.contains("32G"))

        // Network spoke includes nic key and bridge
        val netSpokes = spokes.filter { it.family == SpokeFamily.NETWORK }
        assertEquals(1, netSpokes.size)
        assertFalse(netSpokes.first().isMuted)
        assertTrue(netSpokes.first().label.contains("net0"))
        assertTrue(netSpokes.first().label.contains("vmbr0"))

        // USB spoke includes USB display
        val usbSpokes = spokes.filter { it.family == SpokeFamily.USB }
        assertEquals(1, usbSpokes.size)
        assertFalse(usbSpokes.first().isMuted)
        assertTrue(usbSpokes.first().label.contains("1234:5678"))

        // Never includes cipassword, secrets, digest, or description
        assertFalse(joined.contains("cipassword", ignoreCase = true))
        assertFalse(joined.contains("hunter2", ignoreCase = true))
        assertFalse(joined.contains("root-secret", ignoreCase = true))
        assertFalse(joined.contains("ssh-ed25519", ignoreCase = true))
        assertFalse(joined.contains("0123456789abcdef", ignoreCase = true))
        assertFalse(joined.contains("confidential", ignoreCase = true))
    }

    @Test
    fun spokeLabels_emptyFamilies_returnShortMutedFallbacks() {
        val spokes = buildGuestSpokes(emptyMap())
        assertEquals(3, spokes.size)
        assertEquals(NucleusSpoke(SpokeFamily.STORAGE, "No disks", isMuted = true), spokes[0])
        assertEquals(NucleusSpoke(SpokeFamily.NETWORK, "No NICs", isMuted = true), spokes[1])
        assertEquals(NucleusSpoke(SpokeFamily.USB, "No USB", isMuted = true), spokes[2])
    }

    @Test
    fun demoApi_vmid100_includesUsb0_andBuildsThreeActiveSpokeFamilies() = runBlocking {
        val demoApi = DemoApi()
        val cfg = demoApi.guestConfig("alpha", "qemu", 100L, null).data.orEmpty()
        assertEquals("host=1234:5678,usb3=1", cfg["usb0"])

        val spokes = buildGuestSpokes(cfg)
        assertTrue(spokes.any { it.family == SpokeFamily.STORAGE && !it.isMuted && it.label.contains("local-lvm") })
        assertTrue(spokes.any { it.family == SpokeFamily.NETWORK && !it.isMuted && it.label.contains("vmbr0") })
        assertTrue(spokes.any { it.family == SpokeFamily.USB && !it.isMuted && it.label.contains("1234:5678") })
    }

    @Test
    fun moreThan48RunningGuests_shrinksMarks_andAlwaysDrawsSelectedGuestName() {
        val site = SiteInfo(isCluster = false, nodeName = "pve-big")
        val resources = (1..52).map { i ->
            ClusterResource(
                id = "qemu/$i",
                type = "qemu",
                node = "pve-big",
                vmid = i.toLong(),
                name = "guest-$i",
                status = "running",
            )
        }

        val scene = buildNucleusLayout(site, resources)
        assertEquals(52, scene.guests.size)
        assertTrue(scene.compactGuestMarks)
        assertTrue(scene.guestMarkScale < 1.0f)

        val selectedKey = scene.guests[10].key
        val unselectedKey = scene.guests[0].key
        assertTrue(scene.shouldDrawGuestLabel(selectedKey, selectedKey))
        assertFalse(scene.shouldDrawGuestLabel(unselectedKey, selectedKey))
    }

    @Test
    fun viewModel_fetchesGuestConfigOnce_collapsesOnSecondTap_andRetriesAfterFailure() {
        val site = SiteInfo(isCluster = false, nodeName = "pve1")
        val guestRes = ClusterResource(id = "qemu/100", type = "qemu", node = "pve1", vmid = 100L, name = "nova", status = "running")
        var fetchCalls = 0
        var shouldFail = true

        val vm = NucleusViewModel(
            siteInfoProvider = { Result.success(site) },
            resourcesProvider = { Result.success(listOf(guestRes)) },
            rawConfigFetcher = { _, _, _ ->
                fetchCalls++
                if (shouldFail) {
                    Result.failure(RuntimeException("temporary network error"))
                } else {
                    Result.success(
                        mapOf(
                            "scsi0" to "local-lvm:vm-100-disk-0,size=32G",
                            "net0" to "virtio=AA:BB:CC:DD:EE:FF,bridge=vmbr0",
                            "usb0" to "host=1234:5678,usb3=1",
                        ),
                    )
                }
            },
            hasActiveSession = { true },
        )

        val guest = vm.ui.value.layout.guests.first()

        // First tap fails -> shows "Config unavailable"
        vm.onGuestTapped(guest)
        assertEquals(1, fetchCalls)
        assertEquals(guest.key, vm.ui.value.selectedGuestKey)
        assertEquals(listOf("Config unavailable"), vm.ui.value.selectedSpokes.map { it.label })

        // Tap same guest again -> collapses
        vm.onGuestTapped(guest)
        assertNull(vm.ui.value.selectedGuestKey)
        assertTrue(vm.ui.value.selectedSpokes.isEmpty())

        // Tap again after recovery -> retries and succeeds
        shouldFail = false
        vm.onGuestTapped(guest)
        assertEquals(2, fetchCalls)
        assertEquals(guest.key, vm.ui.value.selectedGuestKey)
        assertTrue(vm.ui.value.selectedSpokes.any { it.label.contains("local-lvm") })

        // Collapse and tap again -> uses cached config without re-fetching
        vm.onGuestTapped(guest)
        vm.onGuestTapped(guest)
        assertEquals(2, fetchCalls)
    }

    @Test
    fun hitTestGuestInScreenSpace_selectsNearestGuestWithinRadius() {
        val g1 = NucleusGuest("pve1/qemu/100", 100L, "nova", "qemu", "pve1", Vec3(1f, 0f, 0f), Vec3(0f, 0f, 0f), 1.65f, 0f)
        val g2 = NucleusGuest("pve1/qemu/101", 101L, "quasar", "qemu", "pve1", Vec3(1f, 0f, -1f), Vec3(0f, 0f, 0f), 1.65f, 0f)
        val targets = listOf(
            ProjectedGuestHitTarget(g1, screenX = 200f, screenY = 300f, depth = 5.0f, hitRadiusPx = 40f),
            ProjectedGuestHitTarget(g2, screenX = 205f, screenY = 302f, depth = 3.5f, hitRadiusPx = 40f),
        )

        val hit = hitTestGuestInScreenSpace(202f, 301f, targets)
        assertNotNull(hit)
        assertEquals("quasar", hit?.name)
        assertNull(hitTestGuestInScreenSpace(500f, 500f, targets))
    }
}
