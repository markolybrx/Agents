package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AgencySettings
import com.example.data.local.KeySource
import com.example.data.local.StoredKeySlot
import com.example.domain.KeyRuntimeStatus
import com.example.domain.KeySlotRuntimeState
import com.example.ui.KeyTestStatus
import com.example.ui.theme.LocalStudioColors

@Composable
fun KeysScreen(
    settings: AgencySettings,
    keyStates: List<KeySlotRuntimeState>,
    keyTestStatuses: Map<Int, KeyTestStatus>,
    onSaveKeySlot: (Int, String, String, Boolean) -> Unit,
    onToggleSlotEnabled: (Int, Boolean) -> Unit,
    onTestKeySlot: (Int) -> Unit,
    onClearCooldown: (Int) -> Unit,
    onSelectModel: (String) -> Unit,
    onSetThinkingEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val studio = LocalStudioColors.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("keys_screen"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "INFRASTRUCTURE  /  4-PROJECT POOL",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = "Keys & Models",
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.onSurface
                )
            }
        }

        // Active Model & Thinking Selector
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "MODEL ENDPOINT",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AgencySettings.AVAILABLE_MODELS.forEach { (modelId, display) ->
                            val selected = settings.activeModelId == modelId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) studio.surfaceElevated else colors.surface)
                                    .border(
                                        1.dp,
                                        if (selected) colors.primary else colors.outlineVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectModel(modelId) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("model_chip_$modelId"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(display, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                                    Text(modelId, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                                }
                                if (selected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = colors.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Per-Agent Thinking Levels", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                            Text(
                                text = "Applies low / medium / high thinkingConfig on Gemini 3.x endpoints.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.thinkingEnabled,
                            onCheckedChange = onSetThinkingEnabled,
                            modifier = Modifier.testTag("thinking_switch")
                        )
                    }
                }
            }
        }

        // Concise Architecture Note
        item {
            Text(
                text = "Assign each slot a Gemini API key from a separate Google Cloud project for independent RPM/TPM quotas. In-app keys remain strictly on-device and override AI Studio Secrets fallbacks.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }

        // 4 API Key Slots
        items(settings.keySlots, key = { it.slotIndex }) { storedSlot ->
            val runtime = keyStates.firstOrNull { it.slotIndex == storedSlot.slotIndex }
            val testState = keyTestStatuses[storedSlot.slotIndex]
            ApiKeySlotEditorCard(
                stored = storedSlot,
                runtime = runtime,
                testStatus = testState,
                onSave = { label, key, enabled ->
                    onSaveKeySlot(storedSlot.slotIndex, label, key, enabled)
                },
                onToggleEnabled = { enabled ->
                    onToggleSlotEnabled(storedSlot.slotIndex, enabled)
                },
                onTest = { onTestKeySlot(storedSlot.slotIndex) },
                onClearCooldown = { onClearCooldown(storedSlot.slotIndex) }
            )
        }
    }
}

@Composable
private fun ApiKeySlotEditorCard(
    stored: StoredKeySlot,
    runtime: KeySlotRuntimeState?,
    testStatus: KeyTestStatus?,
    onSave: (String, String, Boolean) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTest: () -> Unit,
    onClearCooldown: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val studio = LocalStudioColors.current

    var labelText by remember(stored.slotIndex, stored.label) { mutableStateOf(stored.label) }
    var keyText by remember(stored.slotIndex, stored.customKey) { mutableStateOf(stored.customKey) }
    var revealKey by remember(stored.slotIndex) { mutableStateOf(false) }

    val ledColor = when (runtime?.status) {
        KeyRuntimeStatus.ACTIVE -> colors.primary
        KeyRuntimeStatus.READY -> studio.success
        KeyRuntimeStatus.COOLDOWN -> studio.warning
        KeyRuntimeStatus.ERROR -> colors.error
        else -> colors.outline
    }
    val sourceLabel = when (stored.source) {
        KeySource.IN_APP -> "LOCAL KEY"
        KeySource.SECRETS_FALLBACK -> "SECRETS FALLBACK"
        KeySource.UNCONFIGURED -> "UNCONFIGURED"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
            .testTag("key_slot_card_${stored.slotIndex}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(ledColor)
                    )
                    Column {
                        Text(
                            text = "Key Slot 0${stored.slotIndex}",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface
                        )
                        Text(
                            text = "$sourceLabel  ·  ${runtime?.status?.name ?: "IDLE"}" +
                                if ((runtime?.cooldownRemainingSec ?: 0) > 0) " (${runtime?.cooldownRemainingSec}s)" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (stored.source == KeySource.UNCONFIGURED) colors.onSurfaceVariant else ledColor
                        )
                    }
                }
                Switch(
                    checked = stored.enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("key_enable_switch_${stored.slotIndex}")
                )
            }

            OutlinedTextField(
                value = labelText,
                onValueChange = { labelText = it },
                label = { Text("Project Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = keyText,
                onValueChange = { keyText = it },
                label = { Text("Gemini API Key") },
                placeholder = {
                    Text(
                        if (stored.source == KeySource.SECRETS_FALLBACK)
                            "Using GEMINI_API_KEY_${stored.slotIndex} from Secrets"
                        else
                            "Paste key (AIza...)"
                    )
                },
                singleLine = true,
                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { revealKey = !revealKey }) {
                        Icon(
                            imageVector = if (revealKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(R.string.content_desc_toggle_key_visibility)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("key_input_${stored.slotIndex}")
            )

            if (runtime != null && (runtime.requestsThisSession > 0 || runtime.lastError != null)) {
                Text(
                    text = "${runtime.requestsThisSession} req  ·  ${runtime.tokensThisSession} tok" +
                        (runtime.lastError?.let { "  ·  $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (runtime.lastError != null) studio.warning else colors.onSurfaceVariant
                )
            }

            if (testStatus != null && testStatus.message.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(studio.surfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    if (testStatus.testing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                    Text(
                        text = testStatus.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (testStatus.success) {
                            true -> studio.success
                            false -> colors.error
                            null -> colors.onSurface
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onSave(labelText, keyText, stored.enabled) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("key_save_btn_${stored.slotIndex}")
                ) {
                    Text("Save")
                }

                OutlinedButton(
                    onClick = onTest,
                    enabled = stored.isConfigured && testStatus?.testing != true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("key_test_btn_${stored.slotIndex}")
                ) {
                    Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ping")
                }

                if (runtime?.status == KeyRuntimeStatus.COOLDOWN) {
                    IconButton(onClick = onClearCooldown) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reset cooldown")
                    }
                }
            }
        }
    }
}
