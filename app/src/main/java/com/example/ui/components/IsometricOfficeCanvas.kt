package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.prompts.AgentProfile
import com.example.data.prompts.AgentRoster
import com.example.domain.AgentLiveState
import com.example.domain.AgentWorkStatus
import com.example.domain.KeyRuntimeStatus
import com.example.domain.KeySlotRuntimeState
import com.example.domain.OfficeLiveState
import com.example.ui.theme.LocalStudioColors
import com.example.ui.theme.StudioExtendedColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private data class OfficeWaypoint(
    val x: Float,
    val y: Float,
    val activity: String,
    val dialogQuip: String
)

private class WorkerSimState(
    val agentId: String,
    var x: Float,
    var y: Float,
    var targetX: Float,
    var targetY: Float,
    var facingRight: Boolean = true,
    var isWalking: Boolean = false,
    var isSeated: Boolean = true,
    var walkPhase: Float = 0f,
    var pauseSeconds: Float = 2f,
    var currentLocationLabel: String = "At workstation",
    var currentDialogLine: String = "Ready for dispatch",
    var dialogVisibleSeconds: Float = 0f
)

private val OFFICE_HOTSPOTS = listOf(
    OfficeWaypoint(0.84f, 0.48f, "At espresso bar", "Pulling a double shot"),
    OfficeWaypoint(0.79f, 0.44f, "Chatting in lounge", "Syncing on sprint scope"),
    OfficeWaypoint(0.34f, 0.16f, "Checking whiteboard", "Reviewing phase roadmap"),
    OfficeWaypoint(0.13f, 0.17f, "Inspecting 4-key rack", "All 4 key blades nominal"),
    OfficeWaypoint(0.49f, 0.36f, "Hallway standup", "Ready for next handoff"),
    OfficeWaypoint(0.66f, 0.36f, "Design sync", "Aligning layout tokens"),
    OfficeWaypoint(0.28f, 0.60f, "Reviewing sprint board", "Checking phase gates"),
    OfficeWaypoint(0.64f, 0.60f, "QA corridor check", "Zero regressions so far")
)

private fun deskIdleQuipFor(agentId: String): String = when (agentId) {
    "master-orchestrator" -> "Awaiting .md blueprint"
    "project-planner" -> "Ready to draft specs"
    "ui-designer" -> "Polishing token grid"
    "api-designer" -> "Schemas standing by"
    "frontend-developer" -> "PWA shell ready"
    "dynamic-stack-specialist" -> "Build configs primed"
    "backend-developer" -> "IndexedDB layer ready"
    "code-reviewer" -> "Waiting on code diffs"
    "qa-tester" -> "Test harness armed"
    "debugger" -> "Zero errors in queue"
    "accessibility-tester" -> "WCAG AA checks ready"
    "performance-engineer" -> "Monitoring Web Vitals"
    "deployment-engineer" -> "Release headers set"
    else -> "Standing by at desk"
}

/**
 * Architectural Real-Office Studio Canvas with 13 distinct moving human office workers,
 * crisp overhead status indicators, and real-time conversational dialog bubbles.
 */
@Composable
fun IsometricOfficeCanvas(
    officeState: OfficeLiveState,
    keyStates: List<KeySlotRuntimeState>,
    selectedAgentId: String,
    onSelectAgent: (String) -> Unit,
    onOpenAgentDialog: (String) -> Unit = {},
    onWorkerStatusUpdate: (String, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val studio = LocalStudioColors.current

    val workers = remember {
        val rng = Random(42)
        AgentRoster.agents.mapIndexed { idx, agent ->
            val startStrolling = idx % 3 == 1
            val spot = if (startStrolling) OFFICE_HOTSPOTS[idx % OFFICE_HOTSPOTS.size] else null
            WorkerSimState(
                agentId = agent.id,
                x = if (spot != null) spot.x else agent.studioX,
                y = if (spot != null) spot.y else agent.studioY + 0.025f,
                targetX = agent.studioX,
                targetY = agent.studioY + 0.025f,
                isWalking = startStrolling,
                isSeated = !startStrolling,
                walkPhase = rng.nextFloat() * 6.28f,
                pauseSeconds = 1.5f + rng.nextFloat() * 4.5f,
                currentLocationLabel = spot?.activity ?: "At workstation",
                currentDialogLine = spot?.dialogQuip ?: deskIdleQuipFor(agent.id),
                dialogVisibleSeconds = if (idx == 0 || idx == 4) 4.2f else 0f
            )
        }.associateBy { it.agentId }
    }

    var frameTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        val rng = Random(System.currentTimeMillis())
        var lastNanos = 0L
        var ambientChatterCooldown = 1.2f

        while (true) {
            withFrameNanos { nowNanos ->
                val dt = if (lastNanos == 0L) 0.016f else ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.004f, 0.05f)
                lastNanos = nowNanos
                ambientChatterCooldown -= dt

                var activeChatterCount = 0
                for (agent in AgentRoster.agents) {
                    val sim = workers[agent.id] ?: continue
                    if (sim.dialogVisibleSeconds > 0f) {
                        sim.dialogVisibleSeconds = (sim.dialogVisibleSeconds - dt).coerceAtLeast(0f)
                        if (sim.dialogVisibleSeconds > 0f) activeChatterCount++
                    }

                    val live = officeState.agents[agent.id]
                    val status = live?.status ?: AgentWorkStatus.IDLE
                    val isActivelyWorking = status == AgentWorkStatus.THINKING ||
                        status == AgentWorkStatus.WRITING ||
                        status == AgentWorkStatus.AUDITING

                    val seatX = agent.studioX
                    val seatY = agent.studioY + 0.022f

                    if (isActivelyWorking) {
                        sim.targetX = seatX
                        sim.targetY = seatY
                        sim.currentLocationLabel = when (status) {
                            AgentWorkStatus.THINKING -> "Planning at desk"
                            AgentWorkStatus.AUDITING -> "Auditing at desk"
                            else -> "Coding at desk"
                        }
                        if (!live?.speechBubble.isNullOrBlank()) {
                            sim.currentDialogLine = live!!.speechBubble
                        }
                    } else if (status == AgentWorkStatus.BLOCKED) {
                        sim.targetX = (seatX + 0.03f).coerceAtMost(0.92f)
                        sim.targetY = (seatY + 0.02f).coerceAtMost(0.93f)
                        sim.currentLocationLabel = "Waiting for approval"
                        sim.currentDialogLine = live?.speechBubble?.ifBlank { "Need manual approval!" } ?: "Need manual approval!"
                    }

                    val dx = sim.targetX - sim.x
                    val dy = sim.targetY - sim.y
                    val dist = hypot(dx, dy)

                    if (dist > 0.004f) {
                        val speed = if (isActivelyWorking) 0.145f else 0.068f
                        val step = (speed * dt).coerceAtMost(dist)
                        sim.x += (dx / dist) * step
                        sim.y += (dy / dist) * step
                        sim.facingRight = dx >= -0.001f
                        sim.isWalking = true
                        sim.isSeated = false
                        sim.walkPhase = (sim.walkPhase + dt * (if (isActivelyWorking) 14f else 9.5f)) % (2f * PI.toFloat())
                    } else {
                        sim.x = sim.targetX
                        sim.y = sim.targetY
                        sim.isWalking = false
                        val atOwnDesk = hypot(sim.x - seatX, sim.y - seatY) < 0.015f
                        sim.isSeated = atOwnDesk && status != AgentWorkStatus.BLOCKED
                        if (isActivelyWorking) {
                            sim.walkPhase = (sim.walkPhase + dt * 11f) % (2f * PI.toFloat())
                        } else if (status != AgentWorkStatus.BLOCKED) {
                            sim.pauseSeconds -= dt
                            if (sim.pauseSeconds <= 0f) {
                                val goBackToDesk = !atOwnDesk || rng.nextFloat() < 0.42f
                                if (goBackToDesk && !atOwnDesk) {
                                    sim.targetX = seatX
                                    sim.targetY = seatY
                                    sim.currentLocationLabel = "At workstation"
                                    sim.currentDialogLine = deskIdleQuipFor(agent.id)
                                    sim.pauseSeconds = 5f + rng.nextFloat() * 7f
                                } else {
                                    val spot = OFFICE_HOTSPOTS[rng.nextInt(OFFICE_HOTSPOTS.size)]
                                    sim.targetX = (spot.x + (rng.nextFloat() - 0.5f) * 0.05f).coerceIn(0.08f, 0.92f)
                                    sim.targetY = (spot.y + (rng.nextFloat() - 0.5f) * 0.035f).coerceIn(0.14f, 0.91f)
                                    sim.currentLocationLabel = spot.activity
                                    sim.currentDialogLine = spot.dialogQuip
                                    sim.pauseSeconds = 3.5f + rng.nextFloat() * 5.5f
                                }
                            }
                        }
                    }

                    if (agent.id == selectedAgentId) {
                        onWorkerStatusUpdate(agent.id, sim.currentLocationLabel, sim.currentDialogLine)
                    }
                }

                // Periodically pop a crisp ambient dialog bubble above 1-2 office workers so the studio feels conversational
                if (ambientChatterCooldown <= 0f && activeChatterCount < 2) {
                    val candidate = AgentRoster.agents[rng.nextInt(AgentRoster.agents.size)]
                    val sim = workers[candidate.id]
                    if (sim != null && sim.dialogVisibleSeconds <= 0f) {
                        sim.dialogVisibleSeconds = 4.2f
                        ambientChatterCooldown = 2.6f
                    }
                }

                frameTick = nowNanos
            }
        }
    }

    val ambientTransition = rememberInfiniteTransition(label = "office_ambient")
    val steamPhase by ambientTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "steam"
    )
    val ledBlink by ambientTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1050, easing = LinearEasing), RepeatMode.Reverse),
        label = "led_blink"
    )

    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }
    val monoPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(studio.oakFloor)
            .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
            .testTag("isometric_office_canvas")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(selectedAgentId) {
                    detectTapGestures { tap ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        var bestAgentId: String? = null
                        var bestDist = Float.MAX_VALUE
                        for (agent in AgentRoster.agents) {
                            val sim = workers[agent.id]
                            val wx = (sim?.x ?: agent.studioX) * w
                            val wy = (sim?.y ?: agent.studioY) * h
                            val dWorker = hypot(tap.x - wx, tap.y - wy)
                            val dDesk = hypot(tap.x - agent.studioX * w, tap.y - agent.studioY * h)
                            val d = minOf(dWorker, dDesk)
                            if (d < bestDist) {
                                bestDist = d
                                bestAgentId = agent.id
                            }
                        }
                        if (bestAgentId != null && bestDist <= (minOf(w, h) * 0.14f).coerceAtLeast(48f)) {
                            if (bestAgentId == selectedAgentId) {
                                onOpenAgentDialog(bestAgentId)
                            } else {
                                onSelectAgent(bestAgentId)
                            }
                        }
                    }
                }
        ) {
            @Suppress("UNUSED_VARIABLE")
            val tick = frameTick
            val w = size.width
            val h = size.height
            val unit = (minOf(w, h) * 0.048f).coerceIn(14f, 26f)

            // 1. Natural Oak Wood Flooring & Corridors
            drawOakWoodFloor(w, h, studio)
            drawRoundRect(
                color = studio.concreteHall,
                topLeft = Offset(w * 0.04f, h * 0.32f),
                size = Size(w * 0.92f, h * 0.078f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawRoundRect(
                color = studio.concreteHall,
                topLeft = Offset(w * 0.72f, h * 0.37f),
                size = Size(w * 0.24f, h * 0.19f),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // 2. Five Studio Room Rugs
            drawRoomRug(w * 0.06f, h * 0.11f, w * 0.48f, h * 0.19f, studio.rugControl, "WAR ROOM", monoPaint)
            drawRoomRug(w * 0.58f, h * 0.11f, w * 0.36f, h * 0.21f, studio.rugDesign, "DESIGN STUDIO", monoPaint)
            drawRoomRug(w * 0.06f, h * 0.41f, w * 0.61f, h * 0.17f, studio.rugBuild, "ENGINEERING BAY", monoPaint)
            drawRoomRug(w * 0.06f, h * 0.63f, w * 0.88f, h * 0.21f, studio.rugAudit, "QA & DEBUG LAB", monoPaint)
            drawRoomRug(w * 0.27f, h * 0.84f, w * 0.36f, h * 0.12f, studio.rugShip, "RELEASE DOCK", monoPaint)

            // 3. Walls, Partitions & Office Props
            drawStudioWallsAndPartitions(w, h, studio)
            drawWarRoomWhiteboard(Offset(w * 0.34f, h * 0.068f), unit, studio)
            drawApiServerRackCabinet(Offset(w * 0.095f, h * 0.092f), unit, keyStates, officeState.activeKeySlot, ledBlink, monoPaint, colors.primary, studio)
            drawEspressoCoffeeBar(Offset(w * 0.855f, h * 0.455f), unit, steamPhase, studio)

            drawPottedPlant(Offset(w * 0.55f, h * 0.13f), unit * 0.82f, studio)
            drawPottedPlant(Offset(w * 0.93f, h * 0.14f), unit * 0.85f, studio)
            drawPottedPlant(Offset(w * 0.065f, h * 0.57f), unit * 0.8f, studio)
            drawPottedPlant(Offset(w * 0.92f, h * 0.56f), unit * 0.78f, studio)
            drawPottedPlant(Offset(w * 0.23f, h * 0.90f), unit * 0.8f, studio)

            // 4. Underfloor Signal Cable to Active Working Desk
            val activeAgentId = officeState.activeAgentId
            if (activeAgentId != null && officeState.activeKeySlot != null) {
                val targetAgent = AgentRoster.findById(activeAgentId)
                val rackExit = Offset(w * 0.11f, h * 0.135f)
                val deskPt = Offset(targetAgent.studioX * w, targetAgent.studioY * h)
                val elbow = Offset(rackExit.x, h * 0.355f)
                val elbow2 = Offset(deskPt.x, h * 0.355f)
                val wireColor = colors.primary.copy(alpha = 0.52f)
                drawLine(wireColor, rackExit, elbow, strokeWidth = 2.6f, cap = StrokeCap.Round)
                drawLine(wireColor, elbow, elbow2, strokeWidth = 2.6f, cap = StrokeCap.Round)
                drawLine(wireColor, elbow2, deskPt, strokeWidth = 2.6f, cap = StrokeCap.Round)
                val pulsePt = when {
                    steamPhase < 0.33f -> lerpOffset(rackExit, elbow, steamPhase / 0.33f)
                    steamPhase < 0.66f -> lerpOffset(elbow, elbow2, (steamPhase - 0.33f) / 0.33f)
                    else -> lerpOffset(elbow2, deskPt, (steamPhase - 0.66f) / 0.34f)
                }
                drawCircle(colors.primary.copy(alpha = 0.35f), radius = 8f, center = pulsePt)
                drawCircle(colors.primary, radius = 3.8f, center = pulsePt)
            }

            // 5. Depth-Sorted Desks + Moving Workers
            val drawQueue = mutableListOf<Pair<Float, () -> Unit>>()
            val bubbleOverlayQueue = mutableListOf<() -> Unit>()

            for (agent in AgentRoster.agents) {
                val deskCenter = Offset(agent.studioX * w, agent.studioY * h)
                val live = officeState.agents[agent.id] ?: AgentLiveState(agent.id)
                val sim = workers[agent.id]
                val isActive = live.status == AgentWorkStatus.THINKING ||
                    live.status == AgentWorkStatus.WRITING ||
                    live.status == AgentWorkStatus.AUDITING
                val isOccupiedAndActive = isActive && (sim?.isSeated == true)
                val isSelected = agent.id == selectedAgentId

                drawQueue.add(deskCenter.y to {
                    drawArchitecturalDesk(
                        center = deskCenter,
                        unit = unit,
                        isActive = isOccupiedAndActive,
                        isSelected = isSelected,
                        accent = colors.primary,
                        studio = studio,
                        ledBlink = ledBlink
                    )
                })

                val workerPos = Offset((sim?.x ?: agent.studioX) * w, (sim?.y ?: agent.studioY) * h)
                drawQueue.add((workerPos.y + 4f) to {
                    drawOfficeWorkerWithStatus(
                        pos = workerPos,
                        unit = unit,
                        agent = agent,
                        live = live,
                        sim = sim,
                        isSelected = isSelected,
                        accent = colors.primary,
                        warning = studio.warning,
                        info = studio.info,
                        success = studio.success,
                        error = colors.error,
                        surfaceDark = colors.surface,
                        ledBlink = ledBlink,
                        labelPaint = labelPaint
                    )
                })

                // Queue speech dialog bubbles on top of everything so they never get clipped by desks/walls
                val shouldShowDialog = isActive ||
                    live.status == AgentWorkStatus.BLOCKED ||
                    isSelected ||
                    ((sim?.dialogVisibleSeconds ?: 0f) > 0f)

                if (shouldShowDialog) {
                    val dialogText = live.speechBubble.ifBlank { sim?.currentDialogLine.orEmpty() }
                    if (dialogText.isNotBlank()) {
                        val bubbleAccent = when (live.status) {
                            AgentWorkStatus.THINKING -> studio.warning
                            AgentWorkStatus.WRITING -> colors.primary
                            AgentWorkStatus.AUDITING -> studio.info
                            AgentWorkStatus.BLOCKED -> colors.error
                            AgentWorkStatus.DONE -> studio.success
                            AgentWorkStatus.IDLE -> if (isSelected) colors.primary else Color(0xFF8C867C)
                        }
                        bubbleOverlayQueue.add {
                            drawSpeechDialogBubble(
                                anchorPos = Offset(workerPos.x, workerPos.y - unit * 1.15f),
                                unit = unit,
                                text = dialogText,
                                accent = bubbleAccent,
                                isHighPriority = isActive || live.status == AgentWorkStatus.BLOCKED || isSelected,
                                labelPaint = labelPaint
                            )
                        }
                    }
                }
            }

            drawQueue.sortedBy { it.first }.forEach { it.second() }

            // 6. Soft Morning Architectural Sunlight Gradient
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x20FFF4D9), Color.Transparent, Color(0x18141210)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )

            // 7. Render crisp Agent Speech Dialog Bubbles cleanly above sunlight layer
            bubbleOverlayQueue.forEach { it() }
        }
    }
}

private fun DrawScope.drawSpeechDialogBubble(
    anchorPos: Offset,
    unit: Float,
    text: String,
    accent: Color,
    isHighPriority: Boolean,
    labelPaint: Paint
) {
    val clipped = text.take(26)
    labelPaint.textSize = (unit * 0.43f).coerceIn(13f, 17f)
    val measuredW = labelPaint.measureText(clipped)
    val bw = (measuredW + 20f).coerceIn(unit * 2.6f, size.width * 0.50f)
    val bh = unit * 0.88f
    val bx = (anchorPos.x - bw / 2f).coerceIn(6f, size.width - bw - 6f)
    val by = (anchorPos.y - bh - 7f).coerceAtLeast(6f)

    // Subtle drop shadow
    drawRoundRect(
        color = Color(0x45000000),
        topLeft = Offset(bx + 1.5f, by + 2.5f),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(8f, 8f)
    )

    // Bubble body
    val bgAlpha = if (isHighPriority) 0.96f else 0.88f
    drawRoundRect(
        color = Color(0xFF181816).copy(alpha = bgAlpha),
        topLeft = Offset(bx, by),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawRoundRect(
        color = accent,
        topLeft = Offset(bx, by),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = if (isHighPriority) 1.6f else 1.1f)
    )

    // Speech pointer tail aimed at worker head
    val tailX = anchorPos.x.coerceIn(bx + 10f, bx + bw - 10f)
    val tailPath = Path().apply {
        moveTo(tailX - 5f, by + bh - 0.5f)
        lineTo(tailX + 5f, by + bh - 0.5f)
        lineTo(tailX, by + bh + 6f)
        close()
    }
    drawPath(tailPath, color = Color(0xFF181816).copy(alpha = bgAlpha))
    drawLine(accent, Offset(tailX - 5f, by + bh), Offset(tailX, by + bh + 6f), strokeWidth = 1.3f)
    drawLine(accent, Offset(tailX + 5f, by + bh), Offset(tailX, by + bh + 6f), strokeWidth = 1.3f)

    labelPaint.color = Color(0xFFF5F2EB).toArgb()
    drawContext.canvas.nativeCanvas.drawText(clipped, bx + bw / 2f, by + bh * 0.68f, labelPaint)
}

private fun DrawScope.drawOfficeWorkerWithStatus(
    pos: Offset,
    unit: Float,
    agent: AgentProfile,
    live: AgentLiveState,
    sim: WorkerSimState?,
    isSelected: Boolean,
    accent: Color,
    warning: Color,
    info: Color,
    success: Color,
    error: Color,
    surfaceDark: Color,
    ledBlink: Float,
    labelPaint: Paint
) {
    val skin = Color(agent.appearance.skinToneHex)
    val hair = Color(agent.appearance.hairHex)
    val shirt = Color(agent.appearance.shirtHex)
    val trousers = Color(agent.appearance.trousersHex)

    val isWalking = sim?.isWalking == true
    val isSeated = sim?.isSeated == true
    val phase = sim?.walkPhase ?: 0f
    val isActive = live.status == AgentWorkStatus.THINKING ||
        live.status == AgentWorkStatus.WRITING ||
        live.status == AgentWorkStatus.AUDITING

    val statusColor = when (live.status) {
        AgentWorkStatus.THINKING -> warning
        AgentWorkStatus.WRITING -> accent
        AgentWorkStatus.AUDITING -> info
        AgentWorkStatus.DONE -> success
        AgentWorkStatus.BLOCKED -> error
        AgentWorkStatus.IDLE -> if (isWalking) Color(0xFFB5A996) else Color(0xFF53A376)
    }

    val bounceY = if (isWalking) kotlin.math.abs(sin(phase)) * (unit * 0.14f) else 0f
    val baseY = pos.y - bounceY

    // Ground Contact Shadow
    drawOval(
        color = Color(0x3A000000),
        topLeft = Offset(pos.x - unit * 0.34f, pos.y + unit * 0.16f),
        size = Size(unit * 0.68f, unit * 0.24f)
    )

    if (isSelected) {
        drawOval(
            color = accent,
            topLeft = Offset(pos.x - unit * 0.46f, pos.y + unit * 0.10f),
            size = Size(unit * 0.92f, unit * 0.36f),
            style = Stroke(width = 2.2f)
        )
    }

    // Articulated Legs
    val stride = if (isWalking) sin(phase) * (unit * 0.22f) else 0f
    if (!isSeated) {
        drawLine(
            color = trousers,
            start = Offset(pos.x - unit * 0.11f, baseY - unit * 0.04f),
            end = Offset(pos.x - unit * 0.13f + stride, baseY + unit * 0.26f),
            strokeWidth = unit * 0.18f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = trousers,
            start = Offset(pos.x + unit * 0.11f, baseY - unit * 0.04f),
            end = Offset(pos.x + unit * 0.13f - stride, baseY + unit * 0.26f),
            strokeWidth = unit * 0.18f,
            cap = StrokeCap.Round
        )
    }

    // Torso
    val torsoW = unit * 0.56f
    val torsoH = unit * 0.52f
    drawRoundRect(
        color = shirt,
        topLeft = Offset(pos.x - torsoW / 2f, baseY - torsoH),
        size = Size(torsoW, torsoH),
        cornerRadius = CornerRadius(unit * 0.22f, unit * 0.22f)
    )

    // Arms
    val armSwing = if (isWalking) -stride * 0.8f else if (isActive && isSeated) sin(phase * 1.4f) * (unit * 0.1f) else 0f
    val armTargetY = if (isActive && isSeated) baseY - torsoH * 0.95f else baseY - torsoH * 0.18f
    drawLine(
        color = shirt,
        start = Offset(pos.x - torsoW * 0.44f, baseY - torsoH * 0.72f),
        end = Offset(pos.x - torsoW * 0.62f + armSwing, armTargetY),
        strokeWidth = unit * 0.14f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = shirt,
        start = Offset(pos.x + torsoW * 0.44f, baseY - torsoH * 0.72f),
        end = Offset(pos.x + torsoW * 0.62f - armSwing, armTargetY),
        strokeWidth = unit * 0.14f,
        cap = StrokeCap.Round
    )

    // Head & Hair
    val headCenter = Offset(pos.x, baseY - torsoH - unit * 0.24f)
    val headR = unit * 0.27f
    drawCircle(color = skin, radius = headR, center = headCenter)
    drawArc(
        color = hair,
        startAngle = 185f,
        sweepAngle = 170f,
        useCenter = true,
        topLeft = Offset(headCenter.x - headR * 1.04f, headCenter.y - headR * 1.06f),
        size = Size(headR * 2.08f, headR * 1.8f)
    )
    if (agent.appearance.hasGlasses) {
        drawLine(
            color = Color(0xFF1F1E1C),
            start = Offset(headCenter.x - headR * 0.65f, headCenter.y + 1f),
            end = Offset(headCenter.x + headR * 0.65f, headCenter.y + 1f),
            strokeWidth = 1.8f
        )
    }

    // Overhead Hardware Agent Status Indicator Badge (always visible per agent!)
    val badgeCenter = Offset(headCenter.x + headR * 1.15f, headCenter.y - headR * 0.55f)
    if (isActive || live.status == AgentWorkStatus.BLOCKED) {
        drawCircle(
            color = statusColor.copy(alpha = 0.34f * ledBlink),
            radius = unit * 0.28f,
            center = badgeCenter
        )
    }
    drawCircle(color = Color(0xFF181816), radius = unit * 0.18f, center = badgeCenter)
    drawCircle(
        color = statusColor.copy(alpha = if (isActive || live.status == AgentWorkStatus.BLOCKED) ledBlink else 0.95f),
        radius = unit * 0.13f,
        center = badgeCenter
    )

    // Compact Name + Status Indicator Pill beneath worker
    val tagText = "${agent.numberCode} ${agent.shortName}"
    labelPaint.textSize = (unit * 0.45f).coerceIn(13f, 17f)
    val tagW = (tagText.length * unit * 0.26f + 22f).coerceAtLeast(unit * 2.3f)
    val tagH = unit * 0.68f
    val tagY = pos.y + unit * 0.34f
    val pillBg = when {
        isActive -> statusColor
        live.status == AgentWorkStatus.BLOCKED -> error
        isSelected -> surfaceDark
        else -> Color(0xD6181816)
    }
    drawRoundRect(
        color = pillBg,
        topLeft = Offset(pos.x - tagW / 2f, tagY),
        size = Size(tagW, tagH),
        cornerRadius = CornerRadius(6f, 6f)
    )
    // Status LED dot inside the left side of the name pill
    drawCircle(
        color = if (isActive || live.status == AgentWorkStatus.BLOCKED) Color(0xFF14110F) else statusColor,
        radius = 3.0f,
        center = Offset(pos.x - tagW / 2f + 7f, tagY + tagH * 0.5f)
    )
    labelPaint.color = if (isActive) Color(0xFF14110F).toArgb() else Color(0xFFF3F0E8).toArgb()
    drawContext.canvas.nativeCanvas.drawText(tagText, pos.x + 4f, tagY + tagH * 0.72f, labelPaint)
}

private fun DrawScope.drawOakWoodFloor(w: Float, h: Float, studio: StudioExtendedColors) {
    drawRect(color = studio.oakFloor)
    val plankHeight = (h / 26f).coerceAtLeast(14f)
    var row = 0
    var y = 0f
    while (y < h) {
        if (row % 2 == 0) {
            drawRect(
                color = studio.oakFloorDark.copy(alpha = 0.35f),
                topLeft = Offset(0f, y),
                size = Size(w, plankHeight)
            )
        }
        drawLine(studio.oakPlankLine.copy(alpha = 0.45f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        val seamStep = w / 5f
        val offset = if (row % 2 == 0) 0f else seamStep * 0.5f
        var x = offset
        while (x < w) {
            drawLine(studio.oakPlankLine.copy(alpha = 0.32f), Offset(x, y), Offset(x, y + plankHeight), strokeWidth = 1f)
            x += seamStep
        }
        y += plankHeight
        row++
    }
}

private fun DrawScope.drawRoomRug(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    rugColor: Color,
    label: String,
    monoPaint: Paint
) {
    drawRoundRect(
        color = Color(0x26000000),
        topLeft = Offset(left + 3f, top + 4f),
        size = Size(width, height),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = rugColor.copy(alpha = 0.82f),
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(9f, 9f)
    )
    drawRoundRect(
        color = Color(0x33FFFFFF),
        topLeft = Offset(left + 4f, top + 4f),
        size = Size(width - 8f, height - 8f),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = 1.2f)
    )
    monoPaint.textSize = (size.width * 0.020f).coerceIn(13f, 19f)
    monoPaint.color = Color(0xDDF5F2EB).toArgb()
    drawContext.canvas.nativeCanvas.drawText(label, left + 10f, top + 18f, monoPaint)
}

private fun DrawScope.drawStudioWallsAndPartitions(w: Float, h: Float, studio: StudioExtendedColors) {
    val wallH = h * 0.088f
    drawRect(color = studio.wallPlaster, topLeft = Offset.Zero, size = Size(w, wallH))
    drawLine(studio.wallTrim, Offset(0f, wallH), Offset(w, wallH), strokeWidth = 3f)

    val windowPositions = listOf(0.56f, 0.72f, 0.86f)
    for (wx in windowPositions) {
        drawRoundRect(
            color = Color(0xFFD5E6EE),
            topLeft = Offset(w * wx, wallH * 0.18f),
            size = Size(w * 0.11f, wallH * 0.56f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = studio.wallTrim,
            topLeft = Offset(w * wx, wallH * 0.18f),
            size = Size(w * 0.11f, wallH * 0.56f),
            cornerRadius = CornerRadius(2f, 2f),
            style = Stroke(width = 1.8f)
        )
    }

    val dividerX = w * 0.56f
    drawRect(color = studio.glassTint, topLeft = Offset(dividerX - 3f, wallH), size = Size(6f, h * 0.22f))
    drawLine(studio.wallTrim, Offset(dividerX, wallH), Offset(dividerX, h * 0.308f), strokeWidth = 2.4f)

    val slatX = w * 0.70f
    for (i in 0..5) {
        val sy = h * 0.405f + i * (h * 0.028f)
        drawRoundRect(
            color = studio.deskWoodSide,
            topLeft = Offset(slatX, sy),
            size = Size(5f, h * 0.018f),
            cornerRadius = CornerRadius(2f, 2f)
        )
    }
}

private fun DrawScope.drawWarRoomWhiteboard(center: Offset, unit: Float, studio: StudioExtendedColors) {
    val bw = unit * 4.4f
    val bh = unit * 1.15f
    drawRoundRect(
        color = Color(0xFFFAF8F3),
        topLeft = Offset(center.x - bw / 2f, center.y - bh / 2f),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(3f, 3f)
    )
    drawRoundRect(
        color = studio.wallTrim,
        topLeft = Offset(center.x - bw / 2f, center.y - bh / 2f),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(3f, 3f),
        style = Stroke(width = 1.8f)
    )
    val noteColors = listOf(Color(0xFFE56B38), Color(0xFFD9A043), Color(0xFF53A376), Color(0xFF6A99B8))
    noteColors.forEachIndexed { idx, nc ->
        val nx = center.x - bw * 0.40f + idx * (bw * 0.21f)
        drawRect(nc, Offset(nx, center.y - bh * 0.24f), Size(unit * 0.55f, unit * 0.45f))
    }
}

private fun DrawScope.drawApiServerRackCabinet(
    pos: Offset,
    unit: Float,
    keyStates: List<KeySlotRuntimeState>,
    activeKeySlot: Int?,
    ledBlink: Float,
    monoPaint: Paint,
    primaryAccent: Color,
    studio: StudioExtendedColors
) {
    val rw = unit * 3.0f
    val rh = unit * 2.15f
    drawRoundRect(
        color = Color(0x44000000),
        topLeft = Offset(pos.x - rw / 2f + 4f, pos.y - rh / 2f + 5f),
        size = Size(rw, rh),
        cornerRadius = CornerRadius(6f, 6f)
    )
    drawRoundRect(
        color = Color(0xFF222325),
        topLeft = Offset(pos.x - rw / 2f, pos.y - rh / 2f),
        size = Size(rw, rh),
        cornerRadius = CornerRadius(5f, 5f)
    )
    drawRoundRect(
        color = Color(0xFF47494D),
        topLeft = Offset(pos.x - rw / 2f, pos.y - rh / 2f),
        size = Size(rw, rh),
        cornerRadius = CornerRadius(5f, 5f),
        style = Stroke(width = 1.5f)
    )

    val bladeH = (rh - 12f) / 4f
    for (idx in 0 until 4) {
        val slotNum = idx + 1
        val kState = keyStates.firstOrNull { it.slotIndex == slotNum }
        val by = pos.y - rh / 2f + 5f + idx * bladeH
        drawRoundRect(
            color = Color(0xFF2E3033),
            topLeft = Offset(pos.x - rw / 2f + 4f, by),
            size = Size(rw - 8f, bladeH - 2.5f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        val ledColor = when (kState?.status) {
            KeyRuntimeStatus.ACTIVE -> primaryAccent
            KeyRuntimeStatus.READY -> studio.success
            KeyRuntimeStatus.COOLDOWN -> studio.warning
            KeyRuntimeStatus.ERROR -> Color(0xFFD9534F)
            else -> Color(0xFF5C5E62)
        }
        val isFiring = activeKeySlot == slotNum
        drawCircle(
            color = ledColor.copy(alpha = if (isFiring) ledBlink else 0.9f),
            radius = if (isFiring) 3.8f else 2.7f,
            center = Offset(pos.x - rw / 2f + 11f, by + bladeH * 0.42f)
        )
        monoPaint.textSize = (unit * 0.42f).coerceIn(11f, 15f)
        monoPaint.color = Color(0xFFDAD6CE).toArgb()
        drawContext.canvas.nativeCanvas.drawText("K$slotNum", pos.x - rw / 2f + 18f, by + bladeH * 0.68f, monoPaint)
    }
}

private fun DrawScope.drawEspressoCoffeeBar(
    center: Offset,
    unit: Float,
    steamPhase: Float,
    studio: StudioExtendedColors
) {
    val cw = unit * 3.1f
    val ch = unit * 1.45f
    drawRoundRect(
        color = Color(0x33000000),
        topLeft = Offset(center.x - cw / 2f + 3f, center.y - ch / 2f + 5f),
        size = Size(cw, ch),
        cornerRadius = CornerRadius(6f, 6f)
    )
    drawRoundRect(
        color = studio.deskWoodSide,
        topLeft = Offset(center.x - cw / 2f, center.y - ch / 2f + 5f),
        size = Size(cw, ch),
        cornerRadius = CornerRadius(5f, 5f)
    )
    drawRoundRect(
        color = studio.deskWoodTop,
        topLeft = Offset(center.x - cw / 2f, center.y - ch / 2f),
        size = Size(cw, ch * 0.78f),
        cornerRadius = CornerRadius(5f, 5f)
    )
    val mx = center.x - cw * 0.28f
    val my = center.y - ch * 0.42f
    drawRoundRect(Color(0xFF282726), Offset(mx, my), Size(unit * 0.95f, unit * 0.72f), CornerRadius(3f, 3f))
    drawRect(Color(0xFFD5D2CC), Offset(mx + 3f, my + unit * 0.32f), Size(unit * 0.85f - 6f, 3f))
    for (s in 0..1) {
        val st = (steamPhase + s * 0.5f) % 1f
        drawCircle(
            color = Color.White.copy(alpha = (1f - st) * 0.55f),
            radius = 2.5f + st * 3.5f,
            center = Offset(mx + unit * 0.45f + sin(st * 6f) * 3f, my - st * unit * 0.8f)
        )
    }
    drawCircle(Color(0xFFF3EFE6), radius = unit * 0.16f, center = Offset(center.x + unit * 0.65f, center.y - ch * 0.1f))
    drawCircle(Color(0xFFE56B38), radius = unit * 0.15f, center = Offset(center.x + unit * 1.05f, center.y - ch * 0.02f))
    drawCircle(studio.chairFabric, radius = unit * 0.28f, center = Offset(center.x - unit * 0.7f, center.y + ch * 0.78f))
    drawCircle(studio.chairFabric, radius = unit * 0.28f, center = Offset(center.x + unit * 0.6f, center.y + ch * 0.78f))
}

private fun DrawScope.drawPottedPlant(center: Offset, r: Float, studio: StudioExtendedColors) {
    drawCircle(Color(0x33000000), radius = r * 0.65f, center = Offset(center.x + 2f, center.y + 4f))
    drawCircle(studio.plantPot, radius = r * 0.46f, center = center)
    val angles = listOf(0f, 1.1f, 2.3f, 3.6f, 4.9f)
    angles.forEachIndexed { idx, a ->
        val lx = center.x + cos(a) * r * 0.34f
        val ly = center.y + sin(a) * r * 0.34f - r * 0.12f
        drawCircle(
            color = if (idx % 2 == 0) studio.plantLeaf else Color(0xFF4D8F60),
            radius = r * 0.34f,
            center = Offset(lx, ly)
        )
    }
}

private fun DrawScope.drawArchitecturalDesk(
    center: Offset,
    unit: Float,
    isActive: Boolean,
    isSelected: Boolean,
    accent: Color,
    studio: StudioExtendedColors,
    ledBlink: Float
) {
    val dw = unit * 2.55f
    val dh = unit * 1.15f
    val left = center.x - dw / 2f
    val top = center.y - dh / 2f

    if (isSelected) {
        drawRoundRect(
            color = accent.copy(alpha = 0.22f),
            topLeft = Offset(left - 5f, top - 6f),
            size = Size(dw + 10f, dh + 18f),
            cornerRadius = CornerRadius(10f, 10f)
        )
        drawRoundRect(
            color = accent,
            topLeft = Offset(left - 5f, top - 6f),
            size = Size(dw + 10f, dh + 18f),
            cornerRadius = CornerRadius(10f, 10f),
            style = Stroke(width = 2f)
        )
    }

    drawRoundRect(Color(0x30000000), Offset(left + 3f, top + 8f), Size(dw, dh), CornerRadius(5f, 5f))
    drawRoundRect(studio.chairFabric, Offset(center.x - unit * 0.44f, center.y + dh * 0.35f), Size(unit * 0.88f, unit * 0.56f), CornerRadius(6f, 6f))
    drawRect(Color(0xFF2B2A28), Offset(left + 4f, top + dh - 2f), Size(4f, 9f))
    drawRect(Color(0xFF2B2A28), Offset(left + dw - 8f, top + dh - 2f), Size(4f, 9f))

    drawRoundRect(studio.deskWoodSide, Offset(left, top + 5f), Size(dw, dh), CornerRadius(4f, 4f))
    drawRoundRect(studio.deskWoodTop, Offset(left, top), Size(dw, dh * 0.86f), CornerRadius(4f, 4f))
    drawRoundRect(Color(0x28FFFFFF), Offset(left + 1f, top + 1f), Size(dw - 2f, dh * 0.82f), CornerRadius(3f, 3f), style = Stroke(width = 1f))

    val lampPos = Offset(left + dw - unit * 0.36f, top + unit * 0.25f)
    if (isActive) {
        drawCircle(studio.warning.copy(alpha = 0.26f * ledBlink), radius = unit * 0.65f, center = Offset(lampPos.x - 4f, lampPos.y + 4f))
    }
    drawCircle(Color(0xFFC99B42), radius = unit * 0.14f, center = lampPos)

    val mw = unit * 1.18f
    val mh = unit * 0.62f
    val mx = center.x - mw / 2f
    val my = top - mh * 0.42f
    drawRect(Color(0xFF9E9B94), Offset(center.x - 3f, top + 2f), Size(6f, 7f))
    drawRoundRect(Color(0xFF1F2124), Offset(mx, my), Size(mw, mh), CornerRadius(3f, 3f))
    drawRoundRect(if (isActive) Color(0xFF283C46) else studio.monitorScreen, Offset(mx + 2f, my + 2f), Size(mw - 4f, mh - 4f), CornerRadius(2f, 2f))
    if (isActive) {
        drawLine(studio.warning, Offset(mx + 5f, my + 6f), Offset(mx + mw * 0.58f, my + 6f), strokeWidth = 2f)
        drawLine(Color(0xFFD5E6EE), Offset(mx + 5f, my + 11f), Offset(mx + mw * 0.78f, my + 11f), strokeWidth = 1.8f)
        drawLine(studio.success, Offset(mx + 5f, my + 16f), Offset(mx + mw * 0.48f, my + 16f), strokeWidth = 1.8f)
    }

    drawRoundRect(Color(0xFF2E2D2B), Offset(center.x - unit * 0.42f, top + dh * 0.46f), Size(unit * 0.84f, unit * 0.22f), CornerRadius(2f, 2f))
    drawCircle(Color(0xFFF3EFE6), radius = unit * 0.11f, center = Offset(left + unit * 0.34f, top + dh * 0.55f))
}

private fun lerpOffset(a: Offset, b: Offset, t: Float): Offset =
    Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
