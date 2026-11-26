package com.example.ui.components

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.domain.ExportEntryCategory
import com.example.domain.ProjectExportOptions
import com.example.domain.ProjectExportPreview
import com.example.domain.ProjectExportResult
import com.example.domain.ProjectZipExporter
import com.example.ui.ExportProjectUiState
import com.example.ui.theme.LocalStudioColors

@Composable
fun ExportProjectDialog(
    uiState: ExportProjectUiState,
    onUpdateOptions: (ProjectExportOptions) -> Unit,
    onSaveToUri: (Uri) -> Unit,
    onQuickDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val project = uiState.project ?: return
    val colors = MaterialTheme.colorScheme
    val studio = LocalStudioColors.current

    val zipCreatorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) onSaveToUri(uri)
    }

    val preview = uiState.preview
    val suggestedName = preview?.suggestedFileName
        ?: ProjectZipExporter.buildSuggestedFileName(project.name)
    val canExport = !uiState.isExporting &&
        !uiState.isPreparing &&
        uiState.options.hasAnySelection &&
        (preview?.entries?.isNotEmpty() == true)

    Dialog(
        onDismissRequest = { if (!uiState.isExporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .widthIn(max = 540.dp)
                .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
                .testTag("export_project_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = stringResource(R.string.export_dialog_eyebrow),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary
                        )
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Archive,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = suggestedName,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !uiState.isExporting,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("export_dialog_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close export dialog",
                            tint = colors.onSurfaceVariant
                        )
                    }
                }

                // Summary Metric Strip
                if (uiState.isPreparing || preview == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(studio.surfaceElevated)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Scanning Markdown plans, codebase files, and telemetry metadata...",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                } else {
                    ArchiveMetricsStrip(preview = preview)

                    // Bundle Content Toggles
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "ARCHIVE CONTENTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                        ExportOptionRow(
                            title = "Markdown Documents",
                            subtitle = "Blueprint .md, generated README.md, ROADMAP.md & handoffs",
                            checked = uiState.options.includeMarkdownDocs,
                            onCheckedChange = {
                                onUpdateOptions(uiState.options.copy(includeMarkdownDocs = it))
                            },
                            tag = "export_toggle_markdown"
                        )
                        ExportOptionRow(
                            title = "Codebase Files & Snippets",
                            subtitle = "${uiState.codebaseFileCount} generated source file(s) from workspace",
                            checked = uiState.options.includeCodebaseFiles,
                            onCheckedChange = {
                                onUpdateOptions(uiState.options.copy(includeCodebaseFiles = it))
                            },
                            tag = "export_toggle_codebase"
                        )
                        ExportOptionRow(
                            title = "Project & Token Metadata",
                            subtitle = "project-metadata.json manifest & token-telemetry.json",
                            checked = uiState.options.includeMetadata,
                            onCheckedChange = {
                                onUpdateOptions(uiState.options.copy(includeMetadata = it))
                            },
                            tag = "export_toggle_metadata"
                        )
                        ExportOptionRow(
                            title = "Agency Run Logs",
                            subtitle = "run-logs.md & run-logs.json activity ledger",
                            checked = uiState.options.includeRunLogs,
                            onCheckedChange = {
                                onUpdateOptions(uiState.options.copy(includeRunLogs = it))
                            },
                            tag = "export_toggle_logs"
                        )
                    }

                    // Manifest Preview List
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ZIP MANIFEST PREVIEW  ·  ${preview.entries.size} FILE(S)",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = ProjectZipExporter.formatBytes(preview.totalUncompressedBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurface
                            )
                        }

                        if (preview.entries.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(studio.surfaceElevated)
                                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "Select at least one bundle section above to build a ZIP archive.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 96.dp, max = 180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(studio.surfaceElevated)
                                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("export_manifest_list"),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(preview.entries, key = { it.path }) { entry ->
                                    val pillColor = when (entry.category) {
                                        ExportEntryCategory.MARKDOWN -> colors.primary
                                        ExportEntryCategory.CODEBASE -> studio.success
                                        ExportEntryCategory.METADATA -> studio.info
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = entry.category.badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = pillColor,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(pillColor.copy(alpha = 0.14f))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                        Text(
                                            text = entry.path,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = ProjectZipExporter.formatBytes(entry.sizeBytes.toLong()),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Export Confirmation Banner
                uiState.lastExportResult?.let { result ->
                    ExportSuccessBanner(result = result)
                }

                HorizontalDivider(color = colors.outlineVariant)

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        OutlinedButton(
                            enabled = canExport,
                            onClick = onQuickDownload,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_quick_download_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.export_dialog_quick_download))
                        }
                    }

                    Button(
                        enabled = canExport,
                        onClick = { zipCreatorLauncher.launch(suggestedName) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_save_as_zip_button")
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colors.onPrimary
                            )
                        } else {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (uiState.isExporting) "Bundling…" else stringResource(R.string.export_dialog_save_as))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveMetricsStrip(preview: ProjectExportPreview) {
    val colors = MaterialTheme.colorScheme
    val studio = LocalStudioColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ArchiveMetricCell(
            label = "MARKDOWN",
            value = preview.markdownCount.toString(),
            accent = colors.primary,
            modifier = Modifier.weight(1f)
        )
        ArchiveMetricCell(
            label = "CODEBASE",
            value = preview.codebaseCount.toString(),
            accent = studio.success,
            modifier = Modifier.weight(1f)
        )
        ArchiveMetricCell(
            label = "METADATA",
            value = preview.metadataCount.toString(),
            accent = studio.info,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ArchiveMetricCell(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val studio = LocalStudioColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(studio.surfaceElevated)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = accent
        )
    }
}

@Composable
private fun ExportOptionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    val colors = MaterialTheme.colorScheme
    val studio = LocalStudioColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) studio.surfaceElevated else colors.surface)
            .border(
                1.dp,
                if (checked) colors.outline else colors.outlineVariant,
                RoundedCornerShape(8.dp)
            )
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExportSuccessBanner(result: ProjectExportResult) {
    val studio = LocalStudioColors.current
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(studio.successContainer)
            .border(1.dp, studio.success.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("export_success_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = studio.success,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = "Archive saved (${result.entryCount} files · ${ProjectZipExporter.formatBytes(result.uncompressedBytes)})",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface
            )
            Text(
                text = result.destinationLabel,
                style = MaterialTheme.typography.labelSmall,
                color = studio.success,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
