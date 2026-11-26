package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AgencySettings
import com.example.data.local.ProjectEntity
import com.example.data.local.RunLogEntity
import com.example.data.local.TaskEntity
import com.example.data.local.TokenUsageEntity
import com.example.data.prompts.AgentRoster
import com.example.domain.KeyRuntimeStatus
import com.example.domain.KeySlotRuntimeState
import com.example.domain.OfficeLiveState
import com.example.ui.theme.LocalStudioColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    settings: AgencySettings,
    keyStates: List<KeySlotRuntimeState>,
    officeState: OfficeLiveState,
    selectedProject: ProjectEntity?,
    tasks: List<TaskEntity>,
    tokenUsage: List<TokenUsageEntity>,
    logs: List<RunLogEntity>,
    onOpenOfficeDrawer: () -> Unit,
    onNavigateToKeys: () -> Unit,
    onNavigateToPlans: () -> Unit,
    onStartProject: (Long) -> Unit,
    onPauseRun: () -> Unit,
    onExportProject: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val studio = LocalStudioColors.current
    val numFmt = remember { NumberFormat.getIntegerInstance() }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val totalPrompt = remember(tokenUsage) { tokenUsage.sumOf { it.promptTokens } }
    val totalOutput = remember(tokenUsage) { tokenUsage.sumOf { it.outputTokens } }
    val totalThought = remember(tokenUsage) { tokenUsage.sumOf { it.thoughtTokens } }
    val totalTokens = remember(tokenUsage) { tokenUsage.sumOf { it.totalTokens } }

    val perAgentTokens = remember(tokenUsage) {
        tokenUsage.groupBy { it.agentId }
            .mapValues { (_, list) -> list.sumOf { it.totalTokens } }
            .entries
            .sortedByDescending { it.value }
            .take(5)
    }

    val configuredKeys = keyStates.count { it.status != KeyRuntimeStatus.UNCONFIGURED && it.enabled }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Minimal Architectural Editorial Masthead
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AGENTDESK  /  STUDIO 01",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Operations",
                            style = MaterialTheme.typography.displaySmall,
                            color = colors.onSurface
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenOfficeDrawer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("dashboard_open_office_btn")
                    ) {
                        Icon(Icons.Filled.Apartment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Floorplan")
                    }
                }

                // Active Model & Thinking Readout Strip (No neon gradient box)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (officeState.isRunning) colors.primary else studio.success)
                        )
                        Text(
                            text = settings.activeModelId,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = if (settings.thinkingEnabled) officeState.activeThinkingLevel.apiValue.uppercase() else "OFF",
                        style = MaterialTheme.typography.labelSmall,
                        color = studio.warning,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(studio.warningContainer)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // 2. 4-API-Key Rack Readout
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "API KEY POOL  ·  $configuredKeys OF 4 ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = "Configure",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onNavigateToKeys)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("dashboard_manage_keys_link")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    keyStates.forEach { slot ->
                        HardwareKeyCell(
                            slot = slot,
                            onClick = onNavigateToKeys,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 3. Token Usage Instrument Panel
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "TOKEN CONSUMPTION",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = numFmt.format(totalTokens),
                                style = MaterialTheme.typography.displaySmall,
                                color = colors.onSurface
                            )
                        }
                        Text(
                            text = "${tokenUsage.size} API turns",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = colors.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricReadout("PROMPT", numFmt.format(totalPrompt), colors.onSurface)
                        MetricReadout("OUTPUT", numFmt.format(totalOutput), studio.success)
                        MetricReadout("THINKING", numFmt.format(totalThought), studio.warning)
                    }

                    TokenSparklineChart(
                        events = tokenUsage.take(24).reversed(),
                        lineColor = colors.primary,
                        fillColor = colors.primary.copy(alpha = 0.14f),
                        gridColor = colors.outlineVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp)
                    )

                    if (perAgentTokens.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "BY AGENT",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                            val maxVal = perAgentTokens.first().value.coerceAtLeast(1)
                            perAgentTokens.forEach { (agentId, count) ->
                                val profile = AgentRoster.findById(agentId)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${profile.numberCode}  ${profile.shortName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurface
                                        )
                                        Text(
                                            text = numFmt.format(count),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { (count.toFloat() / maxVal).coerceIn(0.03f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = colors.primary,
                                        trackColor = studio.surfaceElevated
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Real token metrics stream here automatically once an agency run starts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 4. Active Project Pipeline Deck
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
            ) {
                if (selectedProject == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "NO ACTIVE BLUEPRINT",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            text = "Upload a Markdown project plan to mobilize the 13-agent studio.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface
                        )
                        Button(
                            onClick = onNavigateToPlans,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("dashboard_upload_plan_cta")
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Plans")
                        }
                    }
                } else {
                    val doneTasks = tasks.count { it.status == "DONE" }
                    val progress = if (tasks.isEmpty()) 0f else doneTasks.toFloat() / tasks.size
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PIPELINE  ·  ${selectedProject.status}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (selectedProject.status) {
                                        "RUNNING" -> colors.primary
                                        "COMPLETED" -> studio.success
                                        "PAUSED_CRITICAL" -> colors.error
                                        else -> colors.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = selectedProject.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (officeState.isRunning) {
                                OutlinedButton(
                                    onClick = onPauseRun,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("dashboard_pause_btn")
                                ) {
                                    Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pause")
                                }
                            } else {
                                Button(
                                    onClick = { onStartProject(selectedProject.id) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("dashboard_start_btn")
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (tasks.isEmpty()) "Start" else "Resume")
                                }
                            }
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = studio.success,
                            trackColor = studio.surfaceElevated
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = officeState.currentPhaseLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = "$doneTasks / ${tasks.size} tasks",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurface
                            )
                        }

                        HorizontalDivider(color = colors.outlineVariant)

                        OutlinedButton(
                            onClick = { onExportProject(selectedProject.id) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dashboard_export_project_btn")
                        ) {
                            Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export Project Archive (.zip)")
                        }
                    }
                }
            }
        }

        // 5. Minimal Monospace Activity Log
        if (logs.isNotEmpty()) {
            item {
                Text(
                    text = "ACTIVITY LEDGER",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
            items(logs.take(10), key = { it.id }) { entry ->
                val dotColor = when (entry.level) {
                    "SUCCESS" -> studio.success
                    "WARN" -> studio.warning
                    "CRITICAL" -> colors.error
                    else -> studio.info
                }
                val agent = AgentRoster.findById(entry.agentId)
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${agent.numberCode} ${agent.shortName}" +
                                        if (entry.keySlotIndex in 1..4) "  ·  K${entry.keySlotIndex}" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = timeFmt.format(Date(entry.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            Text(
                                text = entry.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun HardwareKeyCell(
    slot: KeySlotRuntimeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val studio = LocalStudioColors.current
    val ledColor = when (slot.status) {
        KeyRuntimeStatus.ACTIVE -> colors.primary
        KeyRuntimeStatus.READY -> studio.success
        KeyRuntimeStatus.COOLDOWN -> studio.warning
        KeyRuntimeStatus.ERROR -> colors.error
        else -> colors.outline
    }
    val statusLabel = when (slot.status) {
        KeyRuntimeStatus.COOLDOWN -> "${slot.cooldownRemainingSec}s"
        KeyRuntimeStatus.UNCONFIGURED -> "UNSET"
        else -> slot.status.name
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(
                1.dp,
                if (slot.status == KeyRuntimeStatus.ACTIVE) colors.primary else colors.outlineVariant,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag("key_mini_card_${slot.slotIndex}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "K${slot.slotIndex}",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(ledColor)
            )
        }
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (slot.status == KeyRuntimeStatus.UNCONFIGURED) colors.onSurfaceVariant else ledColor,
            maxLines = 1
        )
        Text(
            text = "${slot.requestsThisSession} req",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun MetricReadout(label: String, value: String, valueColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor
        )
    }
}

@Composable
private fun TokenSparklineChart(
    events: List<TokenUsageEntity>,
    lineColor: Color,
    fillColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(LocalStudioColors.current.surfaceElevated)
    ) {
        val w = size.width
        val h = size.height
        val pad = 10f

        for (i in 1..2) {
            val y = h * (i / 3f)
            drawLine(
                color = gridColor.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        if (events.isEmpty()) return@Canvas

        val values = events.map { it.totalTokens.toFloat() }
        val maxVal = (values.maxOrNull() ?: 1f).coerceAtLeast(100f)
        val stepX = if (values.size <= 1) 0f else (w - pad * 2) / (values.size - 1)

        val linePath = Path()
        val fillPath = Path()
        values.forEachIndexed { idx, v ->
            val x = if (values.size == 1) w / 2f else pad + idx * stepX
            val y = (h - pad) - ((v / maxVal) * (h - pad * 2))
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h - pad)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            drawCircle(color = lineColor, radius = 3f, center = Offset(x, y))
        }
        val lastX = if (values.size == 1) w / 2f else pad + (values.size - 1) * stepX
        fillPath.lineTo(lastX, h - pad)
        fillPath.close()

        drawPath(fillPath, color = fillColor)
        drawPath(linePath, color = lineColor, style = Stroke(width = 2.2f, cap = StrokeCap.Round))
    }
}
