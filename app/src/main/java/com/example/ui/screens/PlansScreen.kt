package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PhaseEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.TaskEntity
import com.example.data.prompts.AgentRoster
import com.example.domain.ChecklistItemUi
import com.example.domain.OfficeLiveState
import com.example.ui.theme.LocalStudioColors
import org.json.JSONArray

@Composable
fun PlansScreen(
    projects: List<ProjectEntity>,
    selectedProjectId: Long?,
    phases: List<PhaseEntity>,
    tasks: List<TaskEntity>,
    officeState: OfficeLiveState,
    isGeneratingBlueprint: Boolean,
    onImportMarkdownUris: (List<android.net.Uri>) -> Unit,
    onCreateMarkdownProject: (String, String, String) -> Unit,
    onLoadTemplate: () -> Unit,
    onGenerateBlueprint: (String, (String) -> Unit) -> Unit,
    onSelectProject: (Long) -> Unit,
    onStartProject: (Long) -> Unit,
    onPauseRun: () -> Unit,
    onResetProject: (Long) -> Unit,
    onDeleteProject: (Long) -> Unit,
    onExportProject: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val studio = LocalStudioColors.current

    var showPasteDialog by remember { mutableStateOf(false) }
    var showPlannerDialog by remember { mutableStateOf(false) }
    var showPreviewMarkdown by remember { mutableStateOf<ProjectEntity?>(null) }

    val mdPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onImportMarkdownUris(uris)
    }

    val selectedProject = projects.firstOrNull { it.id == selectedProjectId }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("plans_screen"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Editorial Masthead
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "PROJECT INTAKE  /  MARKDOWN",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = "Blueprints",
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.onSurface
                )
            }
        }

        // Intake Action Deck
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
                        text = "Import .md specifications or synthesize an Orchestrator-ready roadmap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { mdPickerLauncher.launch(arrayOf("text/markdown", "text/plain", "*/*")) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("upload_md_button")
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.upload_markdown_plan))
                        }

                        OutlinedButton(
                            onClick = { showPlannerDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("planner_skill_button")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Planner Skill")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showPasteDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("paste_md_button")
                        ) {
                            Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Paste .md")
                        }
                        OutlinedButton(
                            onClick = onLoadTemplate,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("load_template_button")
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Template")
                        }
                    }
                }
            }
        }

        // Saved Blueprints List
        item {
            Text(
                text = "QUEUED PROJECTS  ·  ${projects.size}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }

        if (projects.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "No Markdown plans uploaded",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface
                        )
                        Text(
                            text = "Upload a .md roadmap or load the Blueprint Template above to start.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(projects, key = { it.id }) { proj ->
                val isSelected = proj.id == selectedProjectId
                val isRunningThis = officeState.isRunning && officeState.activeProjectId == proj.id
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) studio.surfaceElevated else colors.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isSelected) colors.primary else colors.outlineVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectProject(proj.id) }
                        .testTag("project_card_${proj.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = proj.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${proj.blueprintFileName}  ·  ${proj.status}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (proj.status) {
                                        "RUNNING" -> colors.primary
                                        "COMPLETED" -> studio.success
                                        "PAUSED_CRITICAL" -> colors.error
                                        else -> colors.onSurfaceVariant
                                    }
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { showPreviewMarkdown = proj },
                                    modifier = Modifier.testTag("preview_md_${proj.id}")
                                ) {
                                    Icon(Icons.Filled.Description, contentDescription = "View Markdown blueprint")
                                }
                                IconButton(
                                    onClick = { onExportProject(proj.id) },
                                    modifier = Modifier.testTag("export_project_${proj.id}")
                                ) {
                                    Icon(
                                        Icons.Filled.Archive,
                                        contentDescription = stringResource(R.string.content_desc_export_project)
                                    )
                                }
                                IconButton(
                                    onClick = { onResetProject(proj.id) },
                                    modifier = Modifier.testTag("reset_project_${proj.id}")
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Reset run state")
                                }
                                IconButton(
                                    onClick = { onDeleteProject(proj.id) },
                                    modifier = Modifier.testTag("delete_project_${proj.id}")
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete project", tint = colors.error)
                                }
                            }
                        }

                        if (isRunningThis) {
                            OutlinedButton(
                                onClick = onPauseRun,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("pause_project_${proj.id}")
                            ) {
                                Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pause Autonomous Loop")
                            }
                        } else {
                            Button(
                                onClick = { onStartProject(proj.id) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("run_project_${proj.id}")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (proj.status == "IDLE") "Launch 13-Agent Run" else "Resume Run")
                            }
                        }
                    }
                }
            }
        }

        // Phased Roadmap & Checklists
        if (selectedProject != null && phases.isNotEmpty()) {
            item {
                Text(
                    text = "EXECUTION ROADMAP  ·  ${selectedProject.name.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
            items(phases, key = { it.id }) { phase ->
                val phaseTasks = tasks.filter { it.phaseNumber == phase.phaseNumber }
                val checklist = remember(phase.checklistJson) { parseChecklist(phase.checklistJson) }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Phase ${phase.phaseNumber}  ·  ${phase.name}",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = phase.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = when (phase.status) {
                                    "PASSED" -> studio.success
                                    "IN_PROGRESS" -> colors.primary
                                    "BLOCKED" -> colors.error
                                    else -> colors.onSurfaceVariant
                                }
                            )
                        }

                        HorizontalDivider(color = colors.outlineVariant)

                        phaseTasks.forEach { t ->
                            val agent = AgentRoster.findById(t.assignedAgentId)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(studio.surfaceElevated)
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (t.status == "DONE") Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = when (t.status) {
                                        "DONE" -> studio.success
                                        "RUNNING" -> colors.primary
                                        "BLOCKED", "FAILED" -> colors.error
                                        else -> colors.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${t.taskCode}  ${t.title}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = "${agent.numberCode} ${agent.shortName}  ·  ${t.status}" +
                                            if (t.retryCount > 0 || t.splitDepth > 0) "  ·  retry:${t.retryCount} split:${t.splitDepth}" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (checklist.isNotEmpty()) {
                            Text(
                                text = "PHASE-GATE CHECKLIST",
                                style = MaterialTheme.typography.labelSmall,
                                color = studio.warning
                            )
                            checklist.forEach { item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (item.verified) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (item.verified) studio.success else colors.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPasteDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text("Paste Markdown Plan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Project Title (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Markdown Blueprint (.md)") },
                        minLines = 6,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateMarkdownProject(title, "pasted-plan.md", body)
                        showPasteDialog = false
                    }
                ) { Text("Save Plan") }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPlannerDialog) {
        var idea by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!isGeneratingBlueprint) showPlannerDialog = false },
            title = { Text("00. Project Planner Skill") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Describe your PWA idea. The planner agent will use your 4-key pool to draft an Orchestrator-ready .md blueprint.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = idea,
                        onValueChange = { idea = it },
                        label = { Text("Your PWA Concept") },
                        placeholder = { Text("e.g., Offline-first studio invoice tracker PWA") },
                        minLines = 4,
                        enabled = !isGeneratingBlueprint,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isGeneratingBlueprint) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Planner drafting blueprint...", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isGeneratingBlueprint && idea.isNotBlank(),
                    onClick = { onGenerateBlueprint(idea) { showPlannerDialog = false } }
                ) { Text("Synthesize .md") }
            },
            dismissButton = {
                TextButton(
                    enabled = !isGeneratingBlueprint,
                    onClick = { showPlannerDialog = false }
                ) { Text("Cancel") }
            }
        )
    }

    showPreviewMarkdown?.let { proj ->
        AlertDialog(
            onDismissRequest = { showPreviewMarkdown = null },
            title = { Text(proj.blueprintFileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    item {
                        Text(
                            text = proj.blueprintMarkdown,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPreviewMarkdown = null }) { Text("Close") }
            }
        )
    }
}

private fun parseChecklist(json: String): List<ChecklistItemUi> {
    return try {
        val arr = JSONArray(json)
        List(arr.length()) { idx ->
            val obj = arr.optJSONObject(idx)
            ChecklistItemUi(
                id = obj?.optString("id", "C$idx") ?: "C$idx",
                description = obj?.optString("description", "") ?: "",
                verified = obj?.optBoolean("verified", false) ?: false
            )
        }.filter { it.description.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }
}
