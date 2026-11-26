package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.RunLogEntity
import com.example.data.local.TokenUsageEntity
import com.example.data.prompts.AgentProfile
import com.example.data.prompts.PromptCatalogData
import com.example.domain.AgentLiveState
import com.example.domain.AgentWorkStatus
import com.example.ui.theme.LocalStudioColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AgentDetailDialog(
    agent: AgentProfile,
    liveState: AgentLiveState,
    currentLocation: String,
    currentDialogLine: String,
    agentLogs: List<RunLogEntity>,
    agentTokenEvents: List<TokenUsageEntity>,
    onCopyPrompt: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val studio = LocalStudioColors.current
    val context = LocalContext.current
    val numFmt = remember { NumberFormat.getIntegerInstance() }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val promptDoc = remember(agent.promptFileId) { PromptCatalogData.getById(agent.promptFileId) }
    val totalTokensAllTime = remember(agentTokenEvents) { agentTokenEvents.sumOf { it.totalTokens } }

    val statusColor = when (liveState.status) {
        AgentWorkStatus.THINKING -> studio.warning
        AgentWorkStatus.WRITING -> colors.primary
        AgentWorkStatus.AUDITING -> studio.info
        AgentWorkStatus.DONE -> studio.success
        AgentWorkStatus.BLOCKED -> colors.error
        AgentWorkStatus.IDLE -> colors.onSurfaceVariant
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                .testTag("agent_detail_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header: Avatar badge + Agent identity + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(agent.appearance.shirtHex))
                                .border(1.dp, colors.onSurface.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = agent.numberCode,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFF5F2EB)
                            )
                        }
                        Column {
                            Text(
                                text = agent.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.onSurface
                            )
                            Text(
                                text = "${agent.department.zoneTitle}  ·  $currentLocation",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("agent_dialog_close_btn")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close agent dialog")
                    }
                }

                // Live Status Indicator Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(studio.surfaceElevated)
                        .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "STATUS  ·  ${liveState.status.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                        Text(
                            text = "\"${liveState.speechBubble.ifBlank { currentDialogLine }}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurface
                        )
                    }
                }

                // Scrollable Body: Metrics, Handoff, Logs, System Prompt
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = agent.roleSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )

                    // Key Metrics Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(studio.surfaceElevated)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DialogMetric("THINKING", agent.thinkingLevel.apiValue.uppercase(), studio.warning)
                        DialogMetric("TURNS", agentTokenEvents.size.toString(), colors.onSurface)
                        DialogMetric("TOKENS", numFmt.format(maxOf(totalTokensAllTime, liveState.tokensUsedSession)), studio.success)
                    }

                    if (liveState.activeTaskTitle.isNotBlank() || liveState.lastHandoffSummary.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "CURRENT TASK / HANDOFF",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = liveState.activeTaskTitle.ifBlank { liveState.lastHandoffSummary },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(studio.surfaceElevated)
                                    .padding(10.dp)
                            )
                        }
                    }

                    // Agent Activity Log History
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "RECENT AGENT LOGS (${agentLogs.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                        if (agentLogs.isEmpty()) {
                            Text(
                                text = "No dispatch events recorded for ${agent.shortName} yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        } else {
                            agentLogs.take(5).forEach { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(studio.surfaceElevated)
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = timeFmt.format(Date(log.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                    Text(
                                        text = log.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = colors.outlineVariant)

                    // System Prompt Contract Snippet
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "SYSTEM PROMPT  ·  ${promptDoc.fullPath}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary
                        )
                        Text(
                            text = promptDoc.content,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(studio.surfaceElevated)
                                .padding(10.dp)
                        )
                    }
                }

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText(promptDoc.filename, promptDoc.content))
                            onCopyPrompt("Copied ${promptDoc.filename}")
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("agent_dialog_copy_prompt_btn")
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Prompt")
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(0.75f)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogMetric(label: String, value: String, valueColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = valueColor
        )
    }
}
