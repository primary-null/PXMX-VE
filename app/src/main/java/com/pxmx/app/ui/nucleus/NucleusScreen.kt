@file:Suppress("TooManyFunctions")

package com.pxmx.app.ui.nucleus

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.pxmx.app.ui.theme.themePageColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.rememberReduceMotion
import com.pxmx.app.ui.components.techTopAppBarColors
import com.pxmx.app.ui.icons.InstrumentGlyphs
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private const val NANOS_PER_MILLI = 1_000_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NucleusScreen(
    viewModel: NucleusViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val reduceMotion = rememberReduceMotion()

    BackHandler(onBack = onBack)

    val hullColor = TechColors.Hull
    val deckColor = TechColors.Deck
    val edgeColor = TechColors.Edge
    val amberColor = TechColors.Amber
    val coolBlueColor = TechColors.CoolBlue
    val linkGreenColor = TechColors.LinkGreen
    val muteColor = TechColors.Mute
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    var yawRad by remember { mutableFloatStateOf(0.55f) }
    var pitchRad by remember { mutableFloatStateOf(0.46f) }
    var zoom by remember { mutableFloatStateOf(1.0f) }
    var isDragging by remember { mutableStateOf(false) }

    var animTimeMillis by remember { mutableStateOf(0L) }
    LaunchedEffect(reduceMotion, state.sessionLost) {
        if (reduceMotion || state.sessionLost) {
            animTimeMillis = 0L
            return@LaunchedEffect
        }
        var startNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (startNanos == 0L) startNanos = nanos
                animTimeMillis = (nanos - startNanos) / NANOS_PER_MILLI
            }
        }
    }

    // Slow idle yaw only when the user is not dragging and reduce-motion is off.
    LaunchedEffect(reduceMotion, isDragging, state.sessionLost) {
        if (reduceMotion || isDragging || state.sessionLost) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    yawRad = (yawRad + dt * 0.18f) % (2f * PI.toFloat())
                }
                lastNanos = nanos
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = themePageColor(hullColor),
        topBar = {
            TopAppBar(
                colors = techTopAppBarColors(),
                title = {
                    Column {
                        Text(
                            text = "Nucleus · ${state.siteTitle}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !state.loading && !state.refreshing,
                    ) {
                        Icon(
                            imageVector = InstrumentGlyphs.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.Unspecified,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.sessionLost) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(hullColor)
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                TechPlate(railColor = muteColor) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "SESSION INACTIVE",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = muteColor,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "No active server session.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            return@Scaffold
        }

        if (state.loading && state.layout.guests.isEmpty() && state.layout.nodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(hullColor)
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = amberColor)
            }
            return@Scaffold
        }

        var hitTargets by remember { mutableStateOf<List<ProjectedGuestHitTarget>>(emptyList()) }
        val currentTargets by rememberUpdatedState(hitTargets)
        val onGuestTapUpdated by rememberUpdatedState<(NucleusGuest) -> Unit>({ guest ->
            viewModel.onGuestTapped(guest)
        })

        val labelPaint = remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.MONOSPACE
                textAlign = Paint.Align.CENTER
            }
        }
        val spokePaint = remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.MONOSPACE
                textAlign = Paint.Align.LEFT
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(hullColor)
                .padding(padding)
                .pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downPos = down.position
                        var movedPastSlop = false

                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                movedPastSlop = true
                                isDragging = true
                                val zoomChange = event.calculateZoom()
                                if (zoomChange != 1f && zoomChange > 0f) {
                                    zoom = clampCameraZoom(zoom * zoomChange)
                                }
                                event.changes.forEach { it.consume() }
                            } else if (pressed.size == 1) {
                                val change = pressed[0]
                                if (!movedPastSlop) {
                                    if ((change.position - downPos).getDistance() > slop) {
                                        movedPastSlop = true
                                        isDragging = true
                                    }
                                }
                                if (movedPastSlop) {
                                    val delta = change.positionChange()
                                    if (delta != Offset.Zero) {
                                        yawRad += delta.x * 0.0075f
                                        pitchRad = clampCameraPitch(pitchRad + delta.y * 0.0075f)
                                        change.consume()
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        isDragging = false
                        if (!movedPastSlop) {
                            val hit = hitTestGuestInScreenSpace(
                                tapX = downPos.x,
                                tapY = downPos.y,
                                targets = currentTargets,
                            )
                            if (hit != null) {
                                onGuestTapUpdated(hit)
                            }
                        }
                    }
                },
        ) {
            val vw = size.width
            val vh = size.height
            if (vw <= 0f || vh <= 0f) return@Canvas

            val layout = state.layout

            // 1. Draw perspective orbit rings
            val primaryRingRadius = if (layout.core.isCluster) {
                CLUSTER_NODE_ORBIT_RADIUS
            } else {
                STANDALONE_GUEST_ORBIT_RADIUS
            }
            if (layout.nodes.isNotEmpty() || layout.guests.isNotEmpty()) {
                drawOrbitRing3D(
                    center = layout.core.position,
                    radius = primaryRingRadius,
                    yawRad = yawRad,
                    pitchRad = pitchRad,
                    zoom = zoom,
                    viewportWidth = vw,
                    viewportHeight = vh,
                    color = edgeColor.copy(alpha = 0.55f),
                    strokeWidthPx = 1.2.dp.toPx(),
                )
            }

            for (node in layout.nodes) {
                drawOrbitRing3D(
                    center = node.position,
                    radius = CLUSTER_GUEST_ORBIT_RADIUS,
                    yawRad = yawRad,
                    pitchRad = pitchRad,
                    zoom = zoom,
                    viewportWidth = vw,
                    viewportHeight = vh,
                    color = edgeColor.copy(alpha = 0.38f * node.brightness),
                    strokeWidthPx = 1.dp.toPx(),
                )
            }

            // 2. Project all bodies and build screen-space hit targets
            val newHitTargets = ArrayList<ProjectedGuestHitTarget>(layout.guests.size)
            val drawableBodies = ArrayList<ProjectedBody>(1 + layout.nodes.size + layout.guests.size)

            val coreProjected = projectPoint3D(layout.core.position, yawRad, pitchRad, zoom, vw, vh)
            drawableBodies += ProjectedBody.CoreBody(layout.core, coreProjected)

            for (node in layout.nodes) {
                val proj = projectPoint3D(node.position, yawRad, pitchRad, zoom, vw, vh)
                drawableBodies += ProjectedBody.NodeBody(node, proj)
            }

            for (guest in layout.guests) {
                val proj = projectPoint3D(guest.position, yawRad, pitchRad, zoom, vw, vh)
                val hitRadius = (28.dp.toPx() * layout.guestMarkScale).coerceAtLeast(20.dp.toPx())
                newHitTargets += ProjectedGuestHitTarget(
                    guest = guest,
                    screenX = proj.screenX,
                    screenY = proj.screenY,
                    depth = proj.depth,
                    hitRadiusPx = hitRadius,
                )
                drawableBodies += ProjectedBody.GuestBody(guest, proj)
            }
            hitTargets = newHitTargets

            // 3. Sort back-to-front by depth and draw plates
            drawableBodies.sortByDescending { it.projected.depth }

            var selectedGuestProjected: Pair<NucleusGuest, ProjectedPoint>? = null

            for (body in drawableBodies) {
                when (body) {
                    is ProjectedBody.CoreBody -> {
                        drawCorePlate(
                            core = body.core,
                            proj = body.projected,
                            amberColor = amberColor,
                            deckColor = deckColor,
                            hullColor = hullColor,
                            textColor = onSurfaceColor,
                            labelPaint = labelPaint,
                        )
                    }
                    is ProjectedBody.NodeBody -> {
                        drawNodePlate(
                            node = body.node,
                            proj = body.projected,
                            coolBlueColor = coolBlueColor,
                            muteColor = muteColor,
                            deckColor = deckColor,
                            textColor = onSurfaceColor,
                            labelPaint = labelPaint,
                        )
                    }
                    is ProjectedBody.GuestBody -> {
                        val isSelected = body.guest.key == state.selectedGuestKey
                        if (isSelected) {
                            selectedGuestProjected = body.guest to body.projected
                        }
                        drawGuestPlate(
                            guest = body.guest,
                            proj = body.projected,
                            isSelected = isSelected,
                            drawLabel = layout.shouldDrawGuestLabel(body.guest.key, state.selectedGuestKey),
                            markScale = layout.guestMarkScale,
                            amberColor = amberColor,
                            deckColor = deckColor,
                            hullColor = hullColor,
                            textColor = onSurfaceColor,
                            labelPaint = labelPaint,
                        )
                    }
                }
            }

            // 4. Draw spokes on top of the selected guest so labels are always legible
            selectedGuestProjected?.let { (_, proj) ->
                val spokesToDraw = if (state.loadingSpokes) {
                    listOf(NucleusSpoke(SpokeFamily.STORAGE, "READING…", isMuted = true))
                } else {
                    state.selectedSpokes
                }
                drawGuestSpokesOverlay(
                    guestProj = proj,
                    spokes = spokesToDraw,
                    amberColor = amberColor,
                    coolBlueColor = coolBlueColor,
                    linkGreenColor = linkGreenColor,
                    muteColor = muteColor,
                    hullColor = hullColor,
                    edgeColor = edgeColor,
                    spokePaint = spokePaint,
                )
            }

            drawNucleusDressing(
                layout = layout,
                yawRad = yawRad,
                pitchRad = pitchRad,
                zoom = zoom,
                viewportWidth = vw,
                viewportHeight = vh,
                animMillis = animTimeMillis,
                selectedGuestKey = state.selectedGuestKey,
                amberColor = amberColor,
                coolBlueColor = coolBlueColor,
                labelPaint = labelPaint,
            )
        }
    }
}

private sealed interface ProjectedBody {
    val projected: ProjectedPoint

    data class CoreBody(
        val core: NucleusCore,
        override val projected: ProjectedPoint,
    ) : ProjectedBody

    data class NodeBody(
        val node: NucleusNode,
        override val projected: ProjectedPoint,
    ) : ProjectedBody

    data class GuestBody(
        val guest: NucleusGuest,
        override val projected: ProjectedPoint,
    ) : ProjectedBody
}

private fun DrawScope.drawOrbitRing3D(
    center: Vec3,
    radius: Float,
    yawRad: Float,
    pitchRad: Float,
    zoom: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    color: Color,
    strokeWidthPx: Float,
) {
    val segments = 48
    val path = Path()
    for (i in 0..segments) {
        val angle = (2.0 * PI * i / segments).toFloat()
        val worldPt = Vec3(
            x = center.x + radius * cos(angle),
            y = center.y,
            z = center.z + radius * sin(angle),
        )
        val proj = projectPoint3D(worldPt, yawRad, pitchRad, zoom, viewportWidth, viewportHeight)
        if (i == 0) {
            path.moveTo(proj.screenX, proj.screenY)
        } else {
            path.lineTo(proj.screenX, proj.screenY)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidthPx),
    )
}

private fun DrawScope.drawCorePlate(
    core: NucleusCore,
    proj: ProjectedPoint,
    amberColor: Color,
    deckColor: Color,
    hullColor: Color,
    textColor: Color,
    labelPaint: Paint,
) {
    val cx = proj.screenX
    val cy = proj.screenY
    val text = core.label.uppercase()

    labelPaint.textSize = 13.sp.toPx()
    labelPaint.isFakeBoldText = true
    val textWidth = labelPaint.measureText(text)
    val plateW = (textWidth + 32.dp.toPx()).coerceAtLeast(88.dp.toPx())
    val plateH = 36.dp.toPx()
    val cut = 8.dp.toPx()

    // Subtle amber outer pulse ring
    drawCircle(
        color = amberColor.copy(alpha = 0.16f),
        radius = plateW * 0.62f,
        center = Offset(cx, cy),
    )
    drawCircle(
        color = amberColor.copy(alpha = 0.42f),
        radius = plateW * 0.52f,
        center = Offset(cx, cy),
        style = Stroke(width = 1.dp.toPx()),
    )

    val path = createCutPlatePath(cx, cy, plateW, plateH, cut)
    drawPath(path = path, color = hullColor)
    drawPath(path = path, color = deckColor.copy(alpha = 0.92f))
    drawPath(path = path, color = amberColor, style = Stroke(width = 2.dp.toPx()))

    // Left amber rail
    val railW = 4.dp.toPx()
    drawRect(
        color = amberColor,
        topLeft = Offset(cx - plateW * 0.5f, cy - plateH * 0.5f),
        size = androidx.compose.ui.geometry.Size(railW, plateH),
    )

    labelPaint.color = textColor.toArgb()
    val textY = cy - (labelPaint.descent() + labelPaint.ascent()) * 0.5f
    drawContext.canvas.nativeCanvas.drawText(text, cx + railW * 0.3f, textY, labelPaint)
}

private fun DrawScope.drawNodePlate(
    node: NucleusNode,
    proj: ProjectedPoint,
    coolBlueColor: Color,
    muteColor: Color,
    deckColor: Color,
    textColor: Color,
    labelPaint: Paint,
) {
    val cx = proj.screenX
    val cy = proj.screenY
    val alpha = node.brightness
    val accent = if (node.isOnline) coolBlueColor else muteColor.copy(alpha = alpha)
    val text = node.name.uppercase()

    labelPaint.textSize = 11.sp.toPx()
    labelPaint.isFakeBoldText = true
    val textWidth = labelPaint.measureText(text)
    val plateW = (textWidth + 24.dp.toPx()).coerceAtLeast(64.dp.toPx())
    val plateH = 28.dp.toPx()
    val cut = 6.dp.toPx()

    val path = createCutPlatePath(cx, cy, plateW, plateH, cut)
    drawPath(path = path, color = deckColor.copy(alpha = 0.88f * alpha))
    drawPath(path = path, color = accent, style = Stroke(width = 1.4.dp.toPx()))

    val railW = 3.5.dp.toPx()
    drawRect(
        color = accent,
        topLeft = Offset(cx - plateW * 0.5f, cy - plateH * 0.5f),
        size = androidx.compose.ui.geometry.Size(railW, plateH),
    )

    labelPaint.color = textColor.copy(alpha = alpha).toArgb()
    val textY = cy - (labelPaint.descent() + labelPaint.ascent()) * 0.5f
    drawContext.canvas.nativeCanvas.drawText(text, cx + railW * 0.3f, textY, labelPaint)
}

private fun DrawScope.drawGuestPlate(
    guest: NucleusGuest,
    proj: ProjectedPoint,
    isSelected: Boolean,
    drawLabel: Boolean,
    markScale: Float,
    amberColor: Color,
    deckColor: Color,
    hullColor: Color,
    textColor: Color,
    labelPaint: Paint,
) {
    val cx = proj.screenX
    val cy = proj.screenY
    val text = guest.name.uppercase()

    labelPaint.textSize = (10.sp.toPx() * markScale).coerceAtLeast(8.sp.toPx())
    labelPaint.isFakeBoldText = isSelected

    val plateW: Float
    val plateH: Float
    if (drawLabel) {
        val textWidth = labelPaint.measureText(text)
        plateW = ((textWidth + 18.dp.toPx()) * markScale).coerceAtLeast(46.dp.toPx() * markScale)
        plateH = 22.dp.toPx() * markScale
    } else {
        plateW = 16.dp.toPx() * markScale
        plateH = 12.dp.toPx() * markScale
    }
    val cut = 4.dp.toPx() * markScale

    if (isSelected) {
        drawCircle(
            color = amberColor.copy(alpha = 0.22f),
            radius = plateW * 0.65f,
            center = Offset(cx, cy),
        )
    }

    val path = createCutPlatePath(cx, cy, plateW, plateH, cut)
    drawPath(path = path, color = if (isSelected) deckColor else hullColor)
    drawPath(
        path = path,
        color = if (isSelected) amberColor else amberColor.copy(alpha = 0.82f),
        style = Stroke(width = if (isSelected) 2.dp.toPx() else 1.2.dp.toPx()),
    )

    if (drawLabel) {
        labelPaint.color = (if (isSelected) amberColor else textColor).toArgb()
        val textY = cy - (labelPaint.descent() + labelPaint.ascent()) * 0.5f
        drawContext.canvas.nativeCanvas.drawText(text, cx, textY, labelPaint)
    }
}

private fun DrawScope.drawGuestSpokesOverlay(
    guestProj: ProjectedPoint,
    spokes: List<NucleusSpoke>,
    amberColor: Color,
    coolBlueColor: Color,
    linkGreenColor: Color,
    muteColor: Color,
    hullColor: Color,
    edgeColor: Color,
    spokePaint: Paint,
) {
    if (spokes.isEmpty()) return

    val cx = guestProj.screenX
    val cy = guestProj.screenY
    val count = spokes.size
    val startRadius = 22.dp.toPx()
    val endRadius = 52.dp.toPx()

    // Fan spokes upward/outward around the guest; bias away from screen edges
    val baseAngle = if (cy > size.height * 0.55f) -PI.toFloat() * 0.5f else PI.toFloat() * 0.5f
    val spread = (count * 0.62f).coerceAtMost(PI.toFloat() * 1.35f)

    spokePaint.textSize = 10.sp.toPx()
    spokePaint.isFakeBoldText = false

    spokes.forEachIndexed { idx, spoke ->
        val offsetFrac = if (count == 1) 0f else (idx.toFloat() / (count - 1).toFloat()) - 0.5f
        val angle = baseAngle + offsetFrac * spread
        val cosA = cos(angle)
        val sinA = sin(angle)

        val sx = cx + cosA * startRadius
        val sy = cy + sinA * startRadius
        val ex = cx + cosA * endRadius
        val ey = cy + sinA * endRadius

        val color = when {
            spoke.isMuted -> muteColor
            spoke.family == SpokeFamily.STORAGE -> amberColor
            spoke.family == SpokeFamily.NETWORK -> coolBlueColor
            spoke.family == SpokeFamily.USB -> linkGreenColor
            else -> muteColor
        }

        // Radial tick line
        drawLine(
            color = color,
            start = Offset(sx, sy),
            end = Offset(ex, ey),
            strokeWidth = 1.5.dp.toPx(),
        )
        // Terminal tick node
        drawCircle(
            color = color,
            radius = 2.8.dp.toPx(),
            center = Offset(ex, ey),
        )

        val text = spoke.label
        val textW = spokePaint.measureText(text)
        val padH = 6.dp.toPx()
        val boxW = textW + padH * 2f
        val boxH = 18.dp.toPx()

        val rawLeft = if (cosA >= 0f) ex + 5.dp.toPx() else ex - 5.dp.toPx() - boxW
        val boxLeft = rawLeft.coerceIn(6.dp.toPx(), (size.width - boxW - 6.dp.toPx()).coerceAtLeast(6.dp.toPx()))
        val boxTop = (ey - boxH * 0.5f).coerceIn(6.dp.toPx(), (size.height - boxH - 6.dp.toPx()).coerceAtLeast(6.dp.toPx()))

        drawRect(
            color = hullColor.copy(alpha = 0.94f),
            topLeft = Offset(boxLeft, boxTop),
            size = androidx.compose.ui.geometry.Size(boxW, boxH),
        )
        drawRect(
            color = if (spoke.isMuted) edgeColor else color.copy(alpha = 0.85f),
            topLeft = Offset(boxLeft, boxTop),
            size = androidx.compose.ui.geometry.Size(boxW, boxH),
            style = Stroke(width = 1.dp.toPx()),
        )

        spokePaint.color = color.toArgb()
        val baseline = boxTop + boxH * 0.5f - (spokePaint.descent() + spokePaint.ascent()) * 0.5f
        drawContext.canvas.nativeCanvas.drawText(text, boxLeft + padH, baseline, spokePaint)
    }
}

private fun createCutPlatePath(
    cx: Float,
    cy: Float,
    width: Float,
    height: Float,
    cut: Float,
): Path {
    val left = cx - width * 0.5f
    val right = cx + width * 0.5f
    val top = cy - height * 0.5f
    val bottom = cy + height * 0.5f
    return Path().apply {
        moveTo(left, top)
        lineTo(right, top)
        lineTo(right, bottom - cut)
        lineTo(right - cut, bottom)
        lineTo(left, bottom)
        close()
    }
}

private data class PlateHalf(val x: Float, val y: Float)

private fun plateEdge(center: Offset, toward: Offset, half: PlateHalf): Offset {
    val dx = toward.x - center.x
    val dy = toward.y - center.y
    if (dx == 0f && dy == 0f) return center
    val tx = if (dx == 0f) Float.MAX_VALUE else half.x / abs(dx)
    val ty = if (dy == 0f) Float.MAX_VALUE else half.y / abs(dy)
    val t = min(tx, ty)
    return Offset(center.x + dx * t, center.y + dy * t)
}

@Suppress("MagicNumber")
private fun stablePhase(id: String): Float {
    var hash = 0
    for (ch in id) {
        hash = hash * 31 + ch.code
    }
    val positive = if (hash < 0) -hash else hash
    return (positive % 1000) / 1000f
}

@Suppress("MagicNumber")
private fun DrawScope.coreHalf(label: String, paint: Paint): PlateHalf {
    paint.textSize = 13.sp.toPx()
    paint.isFakeBoldText = true
    val width = (paint.measureText(label.uppercase()) + 32.dp.toPx()).coerceAtLeast(88.dp.toPx())
    return PlateHalf(width * 0.5f, 18.dp.toPx())
}

@Suppress("MagicNumber")
private fun DrawScope.nodeHalf(name: String, paint: Paint): PlateHalf {
    paint.textSize = 11.sp.toPx()
    paint.isFakeBoldText = true
    val width = (paint.measureText(name.uppercase()) + 24.dp.toPx()).coerceAtLeast(64.dp.toPx())
    return PlateHalf(width * 0.5f, 14.dp.toPx())
}

@Suppress("MagicNumber")
private fun DrawScope.guestHalf(
    guest: NucleusGuest,
    drawLabel: Boolean,
    markScale: Float,
    paint: Paint,
): PlateHalf {
    paint.textSize = (10.sp.toPx() * markScale).coerceAtLeast(8.sp.toPx())
    val width: Float
    val height: Float
    if (drawLabel) {
        val textWidth = paint.measureText(guest.name.uppercase())
        width = ((textWidth + 18.dp.toPx()) * markScale).coerceAtLeast(46.dp.toPx() * markScale)
        height = 22.dp.toPx() * markScale
    } else {
        width = 16.dp.toPx() * markScale
        height = 12.dp.toPx() * markScale
    }
    return PlateHalf(width * 0.5f, height * 0.5f)
}

@Suppress("LongParameterList")
private fun DrawScope.drawEdgeLink(
    start: Offset,
    startHalf: PlateHalf,
    end: Offset,
    endHalf: PlateHalf,
    color: Color,
    stroke: Float,
) {
    drawLine(
        color = color,
        start = plateEdge(start, end, startHalf),
        end = plateEdge(end, start, endHalf),
        strokeWidth = stroke,
    )
}

@Suppress("MagicNumber")
private fun DrawScope.drawBrokenRing(
    center: Offset,
    radius: Float,
    rotation: Float,
    color: Color,
    stroke: Float,
) {
    val topLeft = Offset(center.x - radius, center.y - radius)
    val oval = Size(radius * 2f, radius * 2f)
    val style = Stroke(width = stroke)
    drawArc(color, rotation, 128f, false, topLeft, oval, style = style)
    drawArc(color, rotation + 180f, 128f, false, topLeft, oval, style = style)
}

@Suppress("MagicNumber")
private fun DrawScope.drawReactor(center: Offset, half: PlateHalf, animMillis: Long, amber: Color) {
    val base = hypot(half.x, half.y)
    val outerTurn = (animMillis % 14_000L).toFloat() / 14_000f * 360f
    val innerTurn = (animMillis % 10_000L).toFloat() / 10_000f * -360f
    val breath = 0.03f * (0.65f + 0.35f * sin((animMillis % 14_000L) / 14_000f * (2f * PI.toFloat())))
    drawCircle(
        color = amber.copy(alpha = breath),
        radius = base + 20.dp.toPx(),
        center = center,
        style = Stroke(width = 8.dp.toPx()),
    )
    drawBrokenRing(center, base + 8.dp.toPx(), innerTurn, amber.copy(alpha = 0.55f), 1.2.dp.toPx())
    drawBrokenRing(center, base + 16.dp.toPx(), outerTurn, amber.copy(alpha = 0.4f), 1.dp.toPx())
}

@Suppress("MagicNumber")
private fun DrawScope.drawNodeCollar(
    center: Offset,
    half: PlateHalf,
    animMillis: Long,
    online: Boolean,
    blue: Color,
) {
    val radius = hypot(half.x, half.y) + 5.dp.toPx()
    drawCircle(
        color = blue.copy(alpha = if (online) 0.7f else 0.35f),
        radius = radius,
        center = center,
        style = Stroke(width = 1.dp.toPx()),
    )
    val socket = 1.5.dp.toPx()
    for (deg in intArrayOf(45, 135, 225, 315)) {
        val rad = deg * PI.toFloat() / 180f
        drawCircle(
            color = blue,
            radius = socket,
            center = Offset(center.x + cos(rad) * radius, center.y + sin(rad) * radius),
        )
    }
    if (!online) return
    val travel = (animMillis % 2_400L).toFloat() / 2_400f * (2f * PI.toFloat())
    drawCircle(
        color = Color.White,
        radius = 2.dp.toPx(),
        center = Offset(center.x + cos(travel) * radius, center.y + sin(travel) * radius),
    )
}

@Suppress("MagicNumber")
private fun DrawScope.drawGuestOrnament(
    center: Offset,
    half: PlateHalf,
    selected: Boolean,
    labeled: Boolean,
    amber: Color,
) {
    if (labeled && half.x > 10.dp.toPx()) {
        drawRect(
            color = amber,
            topLeft = Offset(center.x - half.x + 4.dp.toPx(), center.y - 1.5.dp.toPx()),
            size = Size(3.dp.toPx(), 3.dp.toPx()),
        )
    } else {
        drawCircle(color = amber, radius = 1.5.dp.toPx(), center = center)
    }
    if (selected) {
        drawBrackets(center, half, amber)
    }
}

@Suppress("MagicNumber")
private fun DrawScope.drawBrackets(center: Offset, half: PlateHalf, color: Color) {
    val outset = 4.dp.toPx()
    val arm = 6.dp.toPx()
    val stroke = 1.2.dp.toPx()
    val left = center.x - half.x - outset
    val right = center.x + half.x + outset
    val top = center.y - half.y - outset
    val bottom = center.y + half.y + outset
    drawLine(color, Offset(left, top + arm), Offset(left, top), stroke)
    drawLine(color, Offset(left, top), Offset(left + arm, top), stroke)
    drawLine(color, Offset(right - arm, top), Offset(right, top), stroke)
    drawLine(color, Offset(right, top), Offset(right, top + arm), stroke)
    drawLine(color, Offset(right, bottom - arm), Offset(right, bottom), stroke)
    drawLine(color, Offset(right, bottom), Offset(right - arm, bottom), stroke)
    drawLine(color, Offset(left + arm, bottom), Offset(left, bottom), stroke)
    drawLine(color, Offset(left, bottom), Offset(left, bottom - arm), stroke)
}

@Suppress("MagicNumber", "LongParameterList", "CyclomaticComplexMethod")
private fun DrawScope.drawLightPacket(
    core: Offset,
    coreHalf: PlateHalf,
    node: Offset?,
    nodeHalf: PlateHalf?,
    guest: Offset,
    guestHalf: PlateHalf,
    animMillis: Long,
    phase: Float,
    alpha: Float,
    color: Color,
) {
    val loop = ((animMillis.toFloat() / 2_500f) + phase) % 1f
    val point = if (node == null || nodeHalf == null) {
        pointAlong(plateEdge(core, guest, coreHalf), plateEdge(guest, core, guestHalf), loop)
    } else if (loop < 0.5f) {
        pointAlong(plateEdge(core, node, coreHalf), plateEdge(node, core, nodeHalf), loop / 0.5f)
    } else {
        pointAlong(plateEdge(node, guest, nodeHalf), plateEdge(guest, node, guestHalf), (loop - 0.5f) / 0.5f)
    }
    drawCircle(color = color.copy(alpha = alpha), radius = 2.5.dp.toPx(), center = point)
    drawCircle(color = Color.White.copy(alpha = alpha), radius = 1.dp.toPx(), center = point)
}

private fun pointAlong(start: Offset, end: Offset, t: Float): Offset =
    Offset(start.x + (end.x - start.x) * t, start.y + (end.y - start.y) * t)

@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod", "MagicNumber")
private fun DrawScope.drawNucleusDressing(
    layout: NucleusSceneLayout,
    yawRad: Float,
    pitchRad: Float,
    zoom: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    animMillis: Long,
    selectedGuestKey: String?,
    amberColor: Color,
    coolBlueColor: Color,
    labelPaint: Paint,
) {
    val hotNode = layout.guests.firstOrNull { it.key == selectedGuestKey }?.node
    val coreProj = projectPoint3D(layout.core.position, yawRad, pitchRad, zoom, viewportWidth, viewportHeight)
    val coreCenter = Offset(coreProj.screenX, coreProj.screenY)
    val coreHalf = coreHalf(layout.core.label, labelPaint)
    drawReactor(coreCenter, coreHalf, animMillis, amberColor)

    val nodeCenters = HashMap<String, Offset>(layout.nodes.size)
    val nodeHalves = HashMap<String, PlateHalf>(layout.nodes.size)
    for (node in layout.nodes) {
        val proj = projectPoint3D(node.position, yawRad, pitchRad, zoom, viewportWidth, viewportHeight)
        val center = Offset(proj.screenX, proj.screenY)
        val half = nodeHalf(node.name, labelPaint)
        nodeCenters[node.name] = center
        nodeHalves[node.name] = half
        val hot = node.name == hotNode
        drawEdgeLink(
            start = coreCenter,
            startHalf = coreHalf,
            end = center,
            endHalf = half,
            color = coolBlueColor.copy(alpha = if (hot) 0.9f else 0.28f * node.brightness),
            stroke = if (hot) 1.8.dp.toPx() else 1.dp.toPx(),
        )
        drawNodeCollar(center, half, animMillis, node.isOnline, coolBlueColor)
    }

    for (guest in layout.guests) {
        val proj = projectPoint3D(guest.position, yawRad, pitchRad, zoom, viewportWidth, viewportHeight)
        val guestCenter = Offset(proj.screenX, proj.screenY)
        val labeled = layout.shouldDrawGuestLabel(guest.key, selectedGuestKey)
        val half = guestHalf(guest, labeled, layout.guestMarkScale, labelPaint)
        val parentCenter = nodeCenters[guest.node] ?: coreCenter
        val parentHalf = nodeHalves[guest.node] ?: coreHalf
        val selected = guest.key == selectedGuestKey
        drawEdgeLink(
            start = parentCenter,
            startHalf = parentHalf,
            end = guestCenter,
            endHalf = half,
            color = amberColor.copy(alpha = if (selected) 0.9f else 0.22f),
            stroke = if (selected) 1.8.dp.toPx() else 1.dp.toPx(),
        )
        drawGuestOrnament(guestCenter, half, selected, labeled, amberColor)
        val nodeCenter = nodeCenters[guest.node]
        drawLightPacket(
            core = coreCenter,
            coreHalf = coreHalf,
            node = nodeCenter,
            nodeHalf = if (nodeCenter == null) null else parentHalf,
            guest = guestCenter,
            guestHalf = half,
            animMillis = animMillis,
            phase = stablePhase(guest.key),
            alpha = if (selected) 1f else 0.55f,
            color = amberColor,
        )
    }
}
