package com.pxmx.app.ui.nucleus

import com.pxmx.app.data.config.GuestConfigParser
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.ParsedGuestConfig
import com.pxmx.app.data.model.SiteInfo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

const val NUCLEUS_COMPACT_GUEST_THRESHOLD = 48
const val STANDALONE_GUEST_ORBIT_RADIUS = 1.65f
const val CLUSTER_NODE_ORBIT_RADIUS = 1.55f
const val CLUSTER_GUEST_ORBIT_RADIUS = 0.68f

const val MIN_CAMERA_PITCH_RAD = -1.15f
const val MAX_CAMERA_PITCH_RAD = 1.15f
const val MIN_CAMERA_ZOOM = 0.55f
const val MAX_CAMERA_ZOOM = 2.25f

data class Vec3(
    val x: Float,
    val y: Float,
    val z: Float,
) {
    fun distanceTo(other: Vec3): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }
}

data class ProjectedPoint(
    val screenX: Float,
    val screenY: Float,
    val depth: Float,
    val scale: Float,
)

data class NucleusCore(
    val label: String,
    val isCluster: Boolean,
    val position: Vec3 = Vec3(0f, 0f, 0f),
)

data class NucleusNode(
    val name: String,
    val isOnline: Boolean,
    val position: Vec3,
    val orbitRadius: Float,
    val angleRad: Float,
    val brightness: Float = if (isOnline) 1.0f else 0.45f,
)

data class NucleusGuest(
    val key: String,
    val vmid: Long,
    val name: String,
    val type: String,
    val node: String,
    val position: Vec3,
    val orbitCenter: Vec3,
    val orbitRadius: Float,
    val angleRad: Float,
)

enum class SpokeFamily {
    STORAGE,
    NETWORK,
    USB,
}

data class NucleusSpoke(
    val family: SpokeFamily,
    val label: String,
    val isMuted: Boolean = false,
)

data class NucleusSceneLayout(
    val core: NucleusCore,
    val nodes: List<NucleusNode>,
    val guests: List<NucleusGuest>,
    val subtitle: String,
    val compactGuestMarks: Boolean,
    val guestMarkScale: Float,
) {
    val runningGuestCount: Int get() = guests.size

    fun shouldDrawGuestLabel(guestKey: String, selectedGuestKey: String?): Boolean {
        if (guestKey == selectedGuestKey) return true
        return !compactGuestMarks
    }
}

data class ProjectedGuestHitTarget(
    val guest: NucleusGuest,
    val screenX: Float,
    val screenY: Float,
    val depth: Float,
    val hitRadiusPx: Float,
)

/**
 * Pure layout function for the Nucleus 3D topology scene.
 *
 * - Standalone ([SiteInfo.isCluster] == false): one core at origin labeled with node name
 *   (fallback "Proxmox"); running qemu/lxc guests orbit that core directly.
 * - Cluster ([SiteInfo.isCluster] == true): one core at origin labeled with cluster name
 *   (fallback "Cluster"); member nodes sit on an inner ring; each node's running guests
 *   sit on a ring around that node.
 * - Stopped guests, paused/suspended/frozen guests, templates, storage, pools, and SDN
 *   are excluded from orbiting bodies.
 * - Never reads or exposes [com.pxmx.app.data.model.ClusterMember.host] or IPs.
 */
fun buildNucleusLayout(
    siteInfo: SiteInfo?,
    resources: List<ClusterResource>,
): NucleusSceneLayout {
    val isCluster = siteInfo?.isCluster == true

    val runningGuests = resources.filter { res ->
        res.isGuest && res.template != 1 && res.isRunning
    }

    val compactMarks = runningGuests.size > NUCLEUS_COMPACT_GUEST_THRESHOLD
    val guestScale = if (compactMarks) 0.65f else 1.0f
    val subtitle = formatNucleusSubtitle(isCluster = isCluster, runningGuestCount = runningGuests.size)

    if (!isCluster) {
        val nodeFromResource = resources
            .firstOrNull { it.type == "node" }
            ?.let { it.node?.trim()?.takeIf(String::isNotEmpty) ?: it.name?.trim()?.takeIf(String::isNotEmpty) }
        val coreLabel = siteInfo?.nodeName?.trim()?.takeIf(String::isNotEmpty)
            ?: nodeFromResource
            ?: "Proxmox"
        val core = NucleusCore(
            label = coreLabel,
            isCluster = false,
            position = Vec3(0f, 0f, 0f),
        )

        val total = runningGuests.size.coerceAtLeast(1)
        val placedGuests = runningGuests.mapIndexed { index, res ->
            val angle = (2.0 * PI * index / total).toFloat()
            val vmid = res.vmid ?: res.id?.substringAfter('/')?.toLongOrNull() ?: index.toLong()
            val guestType = res.type?.lowercase() ?: "qemu"
            val guestNode = res.node?.trim()?.takeIf(String::isNotEmpty) ?: coreLabel
            val guestName = sanitizeNodeOrGuestLabel(res.displayName)
            val pos = Vec3(
                x = STANDALONE_GUEST_ORBIT_RADIUS * cos(angle),
                y = 0.14f * sin(angle * 2f),
                z = STANDALONE_GUEST_ORBIT_RADIUS * sin(angle),
            )
            NucleusGuest(
                key = "$guestNode/$guestType/$vmid",
                vmid = vmid,
                name = guestName,
                type = guestType,
                node = guestNode,
                position = pos,
                orbitCenter = core.position,
                orbitRadius = STANDALONE_GUEST_ORBIT_RADIUS,
                angleRad = angle,
            )
        }

        return NucleusSceneLayout(
            core = core,
            nodes = emptyList(),
            guests = placedGuests,
            subtitle = subtitle,
            compactGuestMarks = compactMarks,
            guestMarkScale = guestScale,
        )
    }

    val coreLabel = siteInfo?.clusterName?.trim()?.takeIf(String::isNotEmpty) ?: "Cluster"
    val core = NucleusCore(
        label = coreLabel,
        isCluster = true,
        position = Vec3(0f, 0f, 0f),
    )

    val orderedNodeNames = mutableListOf<String>()
    val seenLower = mutableSetOf<String>()
    fun addNodeName(raw: String?) {
        val clean = raw?.trim()?.takeIf(String::isNotEmpty) ?: return
        if (seenLower.add(clean.lowercase())) {
            orderedNodeNames += clean
        }
    }

    // Use member nodeNames only (never member.host) + node resource rows + site nodeName + running guest nodes.
    siteInfo?.members.orEmpty().forEach { member -> addNodeName(member.nodeName) }
    resources.filter { it.type == "node" }.forEach { res -> addNodeName(res.node ?: res.name) }
    addNodeName(siteInfo?.nodeName)
    runningGuests.forEach { res -> addNodeName(res.node) }

    val nodeCount = orderedNodeNames.size.coerceAtLeast(1)
    val placedNodes = orderedNodeNames.mapIndexed { index, nodeName ->
        val nodeRes = resources.firstOrNull { res ->
            res.type == "node" && (
                res.node.equals(nodeName, ignoreCase = true) ||
                    res.name.equals(nodeName, ignoreCase = true)
                )
        }
        val status = nodeRes?.status
        val isOnline = status == null ||
            status.equals("online", ignoreCase = true) ||
            status.equals("running", ignoreCase = true)
        val angle = (2.0 * PI * index / nodeCount).toFloat()
        val pos = Vec3(
            x = CLUSTER_NODE_ORBIT_RADIUS * cos(angle),
            y = 0f,
            z = CLUSTER_NODE_ORBIT_RADIUS * sin(angle),
        )
        NucleusNode(
            name = sanitizeNodeOrGuestLabel(nodeName),
            isOnline = isOnline,
            position = pos,
            orbitRadius = CLUSTER_NODE_ORBIT_RADIUS,
            angleRad = angle,
        )
    }

    val placedGuests = mutableListOf<NucleusGuest>()
    for (node in placedNodes) {
        val guestsForNode = runningGuests.filter { res ->
            val gNode = res.node?.trim()?.takeIf(String::isNotEmpty)
                ?: siteInfo?.nodeName?.trim()?.takeIf(String::isNotEmpty)
                ?: placedNodes.firstOrNull()?.name
            gNode.equals(node.name, ignoreCase = true)
        }
        val countOnNode = guestsForNode.size.coerceAtLeast(1)
        guestsForNode.forEachIndexed { j, res ->
            val angle = node.angleRad + (2.0 * PI * j / countOnNode).toFloat() + 0.35f
            val vmid = res.vmid ?: res.id?.substringAfter('/')?.toLongOrNull() ?: j.toLong()
            val guestType = res.type?.lowercase() ?: "qemu"
            val guestName = sanitizeNodeOrGuestLabel(res.displayName)
            val pos = Vec3(
                x = node.position.x + CLUSTER_GUEST_ORBIT_RADIUS * cos(angle),
                y = node.position.y + 0.12f * sin(angle),
                z = node.position.z + CLUSTER_GUEST_ORBIT_RADIUS * sin(angle),
            )
            placedGuests += NucleusGuest(
                key = "${node.name}/$guestType/$vmid",
                vmid = vmid,
                name = guestName,
                type = guestType,
                node = node.name,
                position = pos,
                orbitCenter = node.position,
                orbitRadius = CLUSTER_GUEST_ORBIT_RADIUS,
                angleRad = angle,
            )
        }
    }

    return NucleusSceneLayout(
        core = core,
        nodes = placedNodes,
        guests = placedGuests,
        subtitle = subtitle,
        compactGuestMarks = compactMarks,
        guestMarkScale = guestScale,
    )
}

fun formatNucleusSubtitle(isCluster: Boolean, runningGuestCount: Int): String {
    val mode = if (isCluster) "CLUSTER" else "STANDALONE"
    val noun = if (runningGuestCount == 1) "RUNNING GUEST" else "RUNNING GUESTS"
    return "$mode · $runningGuestCount $noun"
}

/**
 * Build the three families of spokes (storage, network, USB) from a parsed guest config.
 * Empty families receive a single muted spoke ("No disks", "No NICs", "No USB").
 * Never includes digest, description, or secret keys.
 */
fun buildGuestSpokes(config: ParsedGuestConfig): List<NucleusSpoke> {
    val spokes = mutableListOf<NucleusSpoke>()

    if (config.disks.isEmpty()) {
        spokes += NucleusSpoke(
            family = SpokeFamily.STORAGE,
            label = "No disks",
            isMuted = true,
        )
    } else {
        for (disk in config.disks) {
            val parts = listOfNotNull(
                disk.label.takeIf { it.isNotBlank() },
                disk.storage?.takeIf { it.isNotBlank() } ?: disk.key.takeIf { it.isNotBlank() },
                disk.size?.takeIf { it.isNotBlank() },
            )
            spokes += NucleusSpoke(
                family = SpokeFamily.STORAGE,
                label = sanitizeSpokeLabel(parts.joinToString(" · ")),
                isMuted = false,
            )
        }
    }

    if (config.nets.isEmpty()) {
        spokes += NucleusSpoke(
            family = SpokeFamily.NETWORK,
            label = "No NICs",
            isMuted = true,
        )
    } else {
        for (net in config.nets) {
            val bridgeOrModel = net.bridge?.takeIf { it.isNotBlank() }
                ?: net.model?.takeIf { it.isNotBlank() }
            val parts = listOfNotNull(
                net.key.takeIf { it.isNotBlank() },
                bridgeOrModel,
            )
            spokes += NucleusSpoke(
                family = SpokeFamily.NETWORK,
                label = sanitizeSpokeLabel(parts.joinToString(" · ")),
                isMuted = false,
            )
        }
    }

    if (config.usbs.isEmpty()) {
        spokes += NucleusSpoke(
            family = SpokeFamily.USB,
            label = "No USB",
            isMuted = true,
        )
    } else {
        for (usb in config.usbs) {
            spokes += NucleusSpoke(
                family = SpokeFamily.USB,
                label = sanitizeSpokeLabel(usb.display),
                isMuted = false,
            )
        }
    }

    return spokes
}

fun buildGuestSpokes(rawConfig: Map<String, Any?>): List<NucleusSpoke> {
    return buildGuestSpokes(GuestConfigParser.parse(rawConfig))
}

fun buildUnavailableSpokes(): List<NucleusSpoke> = listOf(
    NucleusSpoke(
        family = SpokeFamily.STORAGE,
        label = "Config unavailable",
        isMuted = true,
    ),
)

fun clampCameraPitch(pitchRad: Float): Float =
    pitchRad.coerceIn(MIN_CAMERA_PITCH_RAD, MAX_CAMERA_PITCH_RAD)

fun clampCameraZoom(zoom: Float): Float =
    zoom.coerceIn(MIN_CAMERA_ZOOM, MAX_CAMERA_ZOOM)

/**
 * Perspective projection of a 3D world point onto a 2D viewport.
 * The camera orbits the origin (0, 0, 0) so the core always remains centered in frame.
 */
fun projectPoint3D(
    point: Vec3,
    yawRad: Float,
    pitchRad: Float,
    zoom: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): ProjectedPoint {
    val clampedPitch = clampCameraPitch(pitchRad)
    val clampedZoom = clampCameraZoom(zoom)

    val cosY = cos(yawRad)
    val sinY = sin(yawRad)
    val x1 = point.x * cosY - point.z * sinY
    val y1 = point.y
    val z1 = point.x * sinY + point.z * cosY

    val cosP = cos(clampedPitch)
    val sinP = sin(clampedPitch)
    val x2 = x1
    val y2 = y1 * cosP - z1 * sinP
    val z2 = y1 * sinP + z1 * cosP

    val cameraDistance = 4.8f / clampedZoom
    val depth = (cameraDistance - z2).coerceAtLeast(0.8f)
    val focalLength = min(viewportWidth, viewportHeight).coerceAtLeast(1f) * 1.15f
    val scale = focalLength / depth

    val screenX = viewportWidth * 0.5f + x2 * scale
    val screenY = viewportHeight * 0.5f - y2 * scale

    return ProjectedPoint(
        screenX = screenX,
        screenY = screenY,
        depth = depth,
        scale = scale,
    )
}

/**
 * Screen-space hit-test for orbiting guests. Prefers the front-most guest when targets overlap.
 */
fun hitTestGuestInScreenSpace(
    tapX: Float,
    tapY: Float,
    targets: List<ProjectedGuestHitTarget>,
): NucleusGuest? {
    return targets
        .filter { target ->
            hypot(tapX - target.screenX, tapY - target.screenY) <= target.hitRadiusPx
        }
        .minWithOrNull(
            compareBy<ProjectedGuestHitTarget> { it.depth }
                .thenBy { hypot(tapX - it.screenX, tapY - it.screenY) },
        )
        ?.guest
}

private val ipv4Regex = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b""")
private val forbiddenTokens = listOf("cipassword", "password", "sshkeys", "digest", "csrf", "ticket")

private fun sanitizeNodeOrGuestLabel(raw: String): String {
    val stripped = ipv4Regex.replace(raw.trim(), "").trim()
    return stripped.ifEmpty { "guest" }
}

private fun sanitizeSpokeLabel(raw: String): String {
    var result = raw.trim()
    for (token in forbiddenTokens) {
        if (result.contains(token, ignoreCase = true)) {
            result = result.replace(Regex(token, RegexOption.IGNORE_CASE), "")
        }
    }
    return result.trim().ifEmpty { "—" }
}
