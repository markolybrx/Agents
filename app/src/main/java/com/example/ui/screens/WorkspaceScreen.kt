package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.GeneratedFileEntity
import com.example.data.local.ProjectEntity
import com.example.data.prompts.PromptCatalogData
import com.example.data.prompts.ThinkingLevel
import com.example.ui.theme.LocalStudioColors

@Composable
fun WorkspaceScreen(
    selectedProject: ProjectEntity?,
    generatedFiles: List<GeneratedFileEntity>,
    onOpenExportProject: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val studio = LocalStudioColors.current
    val context = LocalContext.current

    var activeTab by remember { mutableIntStateOf(0) }
    var selectedFileId by remember(generatedFiles) { mutableStateOf(generatedFiles.firstOrNull()?.id) }
    var promptSearch by remember { mutableStateOf("") }
    var selectedPromptId by remember { mutableStateOf(PromptCatalogData.documents.first().id) }

    val filteredDocs = remember(promptSearch) {
        val q = promptSearch.trim().lowercase()
        if (q.isEmpty()) PromptCatalogData.documents
        else PromptCatalogData.documents.filter {
            it.title.lowercase().contains(q) ||
                it.filename.lowercase().contains(q) ||
                it.folder.lowercase().contains(q) ||
                it.skill.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("workspace_screen"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "ARTIFACTS  /  PROMPT CATALOG",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = "Workspace",
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.onSurface
                )
            }
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.testTag("workspace_tab_code")
                ) {
                    Text("Code (${generatedFiles.size})")
                }
                SegmentedButton(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("workspace_tab_prompts")
                ) {
                    Text("Prompt Directory (17)")
                }
            }
        }

        if (activeTab == 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedProject?.name ?: "No project selected",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${generatedFiles.size} file(s) persisted locally",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Button(
                        enabled = selectedProject != null,
                        shape = RoundedCornerShape(8.dp),
                        onClick = onOpenExportProject,
                        modifier = Modifier.testTag("export_zip_button")
                    ) {
                        Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.export_project_zip))
                    }
                }
            }

            if (generatedFiles.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("No generated artifacts yet", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                            Text(
                                text = "Launch a Markdown blueprint from the Plans tab to watch generated PWA files populate here in real time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(generatedFiles, key = { it.id }) { file ->
                            val selected = file.id == selectedFileId
                            Text(
                                text = file.path,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) studio.surfaceHigh else colors.surface)
                                    .border(
                                        1.dp,
                                        if (selected) colors.primary else colors.outlineVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedFileId = file.id }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                val activeFile = generatedFiles.firstOrNull { it.id == selectedFileId } ?: generatedFiles.first()
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = activeFile.path,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.primary
                                    )
                                    Text(
                                        text = "Author: ${activeFile.lastAgentId}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        copyToClipboard(context, activeFile.path, activeFile.content)
                                        onShowMessage("Copied ${activeFile.path}")
                                    }
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy file content")
                                }
                            }
                            HorizontalDivider(color = colors.outlineVariant)
                            Text(
                                text = activeFile.content,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(studio.surfaceElevated)
                                    .padding(12.dp)
                            )
                        }
                    }
                }
            }
        } else {
            item {
                OutlinedTextField(
                    value = promptSearch,
                    onValueChange = { promptSearch = it },
                    placeholder = { Text("Search 17 agency prompt files...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("prompt_directory_search")
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filteredDocs, key = { it.id }) { doc ->
                        val selected = doc.id == selectedPromptId
                        Text(
                            text = doc.filename,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) studio.surfaceHigh else colors.surface)
                                .border(
                                    1.dp,
                                    if (selected) colors.primary else colors.outlineVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedPromptId = doc.id }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            val activeDoc = filteredDocs.firstOrNull { it.id == selectedPromptId }
                ?: filteredDocs.firstOrNull()
                ?: PromptCatalogData.documents.first()

            item {
                val thinkColor = when (activeDoc.thinkingLevel) {
                    ThinkingLevel.HIGH -> studio.warning
                    ThinkingLevel.MEDIUM -> studio.info
                    ThinkingLevel.LOW -> studio.success
                }
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
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = activeDoc.fullPath,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.primary
                                )
                                Text(
                                    text = activeDoc.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = activeDoc.thinkingLevel.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = thinkColor
                                    )
                                    Text(
                                        text = "·  ${activeDoc.skill}  ·  ~${activeDoc.approxTokens} tok",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    copyToClipboard(context, activeDoc.filename, activeDoc.content)
                                    onShowMessage("Copied ${activeDoc.filename}")
                                },
                                modifier = Modifier.testTag("copy_prompt_button")
                            ) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = stringResource(R.string.content_desc_copy_prompt)
                                )
                            }
                        }

                        Text(
                            text = activeDoc.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )

                        HorizontalDivider(color = colors.outlineVariant)

                        Text(
                            text = activeDoc.content,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(studio.surfaceElevated)
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}
