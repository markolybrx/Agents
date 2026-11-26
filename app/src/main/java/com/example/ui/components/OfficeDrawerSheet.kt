package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.RunLogEntity
import com.example.data.local.TokenUsageEntity
import com.example.data.prompts.AgentRoster
import com.example.domain.AgentLiveState
import com.example.domain.AgentWorkStatus
import com.example.domain.KeySlotRuntimeState
import com.example.domain.OfficeLiveState
import com.example.ui.theme.LocalStudioColors

@Composable
fun LiveOfficeDrawerPill(
    officeState: OfficeLiveState,
    onOpenDrawer: () -> Unit,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val studio = LocalStudioColors.current
    val activeCount = officeState.agents.values.count {
        it.status == AgentWorkStatus.THINKING || it.status == AgentWorkStatus.WRITING || it.status == AgentWorkStatus.AUDITING
    }
    val blockedCount = officeState.agents.values.count { it.status == AgentWorkStatus.BLOCKED }
    val ledColor = when {
        blockedCount > 0 -> colors.error
        officeState.isRunning -> colors.primary
        else -> studio.success
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .clickable(onClick = onOpenDrawer)
            .padding(horizontal = 16.dp, vertical = 11.dp)
            .testTag("office_drawer_pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(ledColor)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (officeState.isRunning) {
                    "STUDIO FLOOR · $activeCount ACTIVE · K${officeState.activeKeySlot ?: "-"}"
                } else {
                    "STUDIO FLOOR · 13 WORKERS ON SITE"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (officeState.isRunning) colors.primary else colors.onSurfaceVariant
            )
            Text(
                text = officeState.statusHeadline,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (officeState.isRunning) {
            FilledTonalIconButton(
                onClick = onTogglePause,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("pill_pause_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = "Pause active agency run",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(studio.surfaceElevated)
                .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Office",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface
            )
            Icon(
                imageVector = Icons.Filled.ExpandLess,
                contentDescription = stringResource(R.string.content_desc_office_drawer),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeDrawerModalSheet(
    officeState: OfficeLiveState,
    keyStates: List<KeySlotRuntimeState>,
    allLogs: List<RunLogEntity>,
    allTokenUsage: List<TokenUsageEntity>,
    selectedAgentId: String,
    onSelectAgent: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    canStart: Boolean
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        OfficeStudioPanelContent(
            officeState = officeState,
            keyStates = keyStates,
            allLogs = allLogs,
            allTokenUsage = allTokenUsage,
            selectedAgentId = selectedAgentId,
            onSelectAgent = onSelectAgent,
            onShowMessage = onShowMessage,
            onClose = onDismiss,
            onStartOrResume = onStartOrResume,
            onPause = onPause,
            canStart = canStart,
            showCloseButton = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
fun OfficeStudioPanelContent(
    officeState: OfficeLiveState,
    keyStates: List<KeySlotRuntimeState>,
    allLogs: List<RunLogEntity>,
    allTokenUsage: List<TokenUsageEntity>,
    selectedAgentId: String,
    onSelectAgent: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onClose: () -> Unit,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    canStart: Boolean,
    showCloseButton: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val studio = LocalStudioColors.current
    val selectedProfile = AgentRoster.findById(selectedAgentId)
    val selectedLive = officeState.agents[selectedProfile.id] ?: AgentLiveState(selectedProfile.id)
    var selectedLocation by remember(selectedAgentId) { mutableStateOf("At workstation") }
    var selectedDialogLine by remember(selectedAgentId) { mutableStateOf("Standing by") }
    var openDialogAgentId by remember { mutableStateOf<String?>(null) }

    val activeCount = officeState.agents.values.count {
        it.status == AgentWorkStatus.THINKING || it.status == AgentWorkStatus.WRITING || it.status == AgentWorkStatus.AUDITING
    }
    val doneCount = officeState.agents.values.count { it.status == AgentWorkStatus.DONE }
    val blockedCount = officeState.agents.values.count { it.status == AgentWorkStatus.BLOCKED }
    val standbyCount = (AgentRoster.agents.size - activeCount - doneCount - blockedCount).coerceAtLeast(0)

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Architectural Drawer Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Agency Floorplan",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface
                )
                Text(
                    text = "${officeState.currentPhaseLabel}  ·  ${officeState.activeModelId}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (officeState.isRunning) {
                    FilledTonalIconButton(
                        onClick = onPause,
                        modifier = Modifier.testTag("office_pause_btn")
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause run")
                    }
                } else if (canStart) {
                    FilledTonalIconButton(
                        onClick = onStartOrResume,
                        modifier = Modifier.testTag("office_play_btn")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Start run")
                    }
                }
                if (showCloseButton) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("office_close_btn")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close office drawer")
                    }
                }
            }
        }

        // Live Agent Status Indicator Summary Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(studio.surfaceElevated)
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusCounterPill(colors.primary, "ACTIVE $activeCount")
            StatusCounterPill(studio.success, "DONE $doneCount")
            StatusCounterPill(colors.error, "BLOCKED $blockedCount")
            StatusCounterPill(colors.onSurfaceVariant, "STANDBY $standbyCount")
        }

        // Real-Office Architectural Floor Canvas
        IsometricOfficeCanvas(
            officeState = officeState,
            keyStates = keyStates,
            selectedAgentId = selectedAgentId,
            onSelectAgent = onSelectAgent,
            onOpenAgentDialog = { openDialogAgentId = it },
            onWorkerStatusUpdate = { id, loc, line ->
                if (id == selectedAgentId) {
                    if (loc != selectedLocation) selectedLocation = loc
                    if (line != selectedDialogLine) selectedDialogLine = line
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(395.dp)
        )

        // Worker Roster Quick Selector with Per-Agent Status Indicators
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(AgentRoster.agents, key = { it.id }) { agent ->
                val live = officeState.agents[agent.id]
                val status = live?.status ?: AgentWorkStatus.IDLE
                val isSelected = agent.id == selectedAgentId
                val statusDot = when (status) {
                    AgentWorkStatus.THINKING -> studio.warning
                    AgentWorkStatus.WRITING -> colors.primary
                    AgentWorkStatus.AUDITING -> studio.info
                    AgentWorkStatus.DONE -> studio.success
                    AgentWorkStatus.BLOCKED -> colors.error
                    AgentWorkStatus.IDLE -> studio.success.copy(alpha = 0.65f)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) studio.surfaceHigh else studio.surfaceElevated)
                        .border(
                            1.dp,
                            if (isSelected) colors.primary else colors.outlineVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            if (isSelected) openDialogAgentId = agent.id else onSelectAgent(agent.id)
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("office_roster_chip_${agent.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusDot)
                    )
                    Text(
                        text = "${agent.numberCode} ${agent.shortName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurface
                    )
                    Text(
                        text = status.name.take(4),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusDot
                    )
                }
            }
        }

        // Selected Worker Inspector Card + One-Tap Agent Dialog Button
        val selectedStatusColor = when (selectedLive.status) {
            AgentWorkStatus.THINKING -> studio.warning
            AgentWorkStatus.WRITING -> colors.primary
            AgentWorkStatus.AUDITING -> studio.info
            AgentWorkStatus.DONE -> studio.success
            AgentWorkStatus.BLOCKED -> colors.error
            AgentWorkStatus.IDLE -> studio.success
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { openDialogAgentId = selectedProfile.id }
                .testTag("selected_agent_inspector_card"),
            shape = RoundedCornerShape(12.dp),
            color = studio.surfaceElevated
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(selectedStatusColor)
                        )
                        Text(
                            text = "${selectedProfile.numberCode}. ${selectedProfile.displayName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = { openDialogAgentId = selectedProfile.id },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("open_agent_dialog_btn")
                    ) {
                        Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Inspect", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Text(
                    text = "\"${selectedLive.speechBubble.ifBlank { selectedDialogLine }}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = selectedLive.status.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = selectedStatusColor
                    )
                    Text(
                        text = "·  $selectedLocation",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = "·  ${selectedLive.tokensUsedSession} tok",
                        style = MaterialTheme.typography.labelSmall,
                        color = studio.warning
                    )
                }
            }
        }
    }

    // Full Interactive Agent Detail Dialog
    openDialogAgentId?.let { targetId ->
        val profile = AgentRoster.findById(targetId)
        val live = officeState.agents[profile.id] ?: AgentLiveState(profile.id)
        val agentLogs = remember(allLogs, profile.id) { allLogs.filter { it.agentId == profile.id } }
        val agentTokens = remember(allTokenUsage, profile.id) { allTokenUsage.filter { it.agentId == profile.id } }

        AgentDetailDialog(
            agent = profile,
            liveState = live,
            currentLocation = if (profile.id == selectedAgentId) selectedLocation else profile.department.zoneTitle,
            currentDialogLine = if (profile.id == selectedAgentId) selectedDialogLine else "Standing by",
            agentLogs = agentLogs,
            agentTokenEvents = agentTokens,
            onCopyPrompt = onShowMessage,
            onDismiss = { openDialogAgentId = null }
        )
    }
}

@Composable
private fun StatusCounterPill(dotColor: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
