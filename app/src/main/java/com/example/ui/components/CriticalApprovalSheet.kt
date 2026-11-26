package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.prompts.AgentRoster
import com.example.domain.CriticalAction
import com.example.domain.CriticalApprovalRequest
import com.example.ui.theme.LocalStudioColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CriticalApprovalSheet(
    request: CriticalApprovalRequest,
    onResolve: (CriticalAction, String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val studio = LocalStudioColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var guidanceNote by remember(request.taskCode) { mutableStateOf("") }
    val agent = AgentRoster.findById(request.agentId)

    ModalBottomSheet(
        onDismissRequest = { onResolve(CriticalAction.KEEP_PAUSED, "") },
        sheetState = sheetState,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("critical_approval_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = "Critical issue alert",
                    tint = colors.error,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.critical_gate_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.error
                    )
                    Text(
                        text = "${request.taskCode} • ${agent.displayName} • retries: ${request.retryCount} • split: ${request.splitDepth}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.errorContainer.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.error.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = request.reasonTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface
                    )
                    Text(
                        text = request.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    request.blockers.take(4).forEach { blocker ->
                        Text(
                            text = "• $blocker",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onErrorContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.surface.copy(alpha = 0.45f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = guidanceNote,
                onValueChange = { guidanceNote = it },
                label = { Text("Optional operator guidance for retry") },
                placeholder = { Text("e.g., Keep storage purely in IndexedDB and simplify types") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("critical_guidance_input"),
                maxLines = 3
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onResolve(CriticalAction.APPROVE_CONTINUE, guidanceNote) },
                    colors = ButtonDefaults.buttonColors(containerColor = studio.success),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("critical_approve_button")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Approve", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = { onResolve(CriticalAction.RETRY_WITH_GUIDANCE, guidanceNote) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("critical_retry_button")
                ) {
                    Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Retry", style = MaterialTheme.typography.labelLarge)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onResolve(CriticalAction.FORCE_SPLIT_TASK, guidanceNote) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("critical_split_button")
                ) {
                    Icon(Icons.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Split Task")
                }

                OutlinedButton(
                    onClick = { onResolve(CriticalAction.KEEP_PAUSED, "") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("critical_pause_button")
                ) {
                    Icon(Icons.Filled.PauseCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Keep Paused")
                }
            }
        }
    }
}
