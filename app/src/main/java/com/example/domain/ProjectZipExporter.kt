package com.example.domain

import com.example.data.local.GeneratedFileEntity
import com.example.data.local.PhaseEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.RunLogEntity
import com.example.data.local.TaskEntity
import com.example.data.local.TokenUsageEntity
import com.example.data.prompts.AgentRoster
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportEntryCategory(val badge: String) {
    MARKDOWN("MD"),
    CODEBASE("CODE"),
    METADATA("META")
}

data class ProjectExportOptions(
    val includeMarkdownDocs: Boolean = true,
    val includeCodebaseFiles: Boolean = true,
    val includeMetadata: Boolean = true,
    val includeRunLogs: Boolean = true
) {
    val hasAnySelection: Boolean
        get() = includeMarkdownDocs || includeCodebaseFiles || includeMetadata || includeRunLogs
}

data class ProjectExportSource(
    val project: ProjectEntity,
    val phases: List<PhaseEntity>,
    val tasks: List<TaskEntity>,
    val generatedFiles: List<GeneratedFileEntity>,
    val tokenUsage: List<TokenUsageEntity>,
    val runLogs: List<RunLogEntity>,
    val activeModelId: String,
    val thinkingEnabled: Boolean
)

data class ExportArchiveEntry(
    val path: String,
    val category: ExportEntryCategory,
    val content: ByteArray,
    val lastAgentId: String = "agentdesk-exporter",
    val updatedAt: Long = System.currentTimeMillis()
) {
    val sizeBytes: Int get() = content.size
}

data class ProjectExportPreview(
    val suggestedFileName: String,
    val entries: List<ExportArchiveEntry>,
    val markdownCount: Int,
    val codebaseCount: Int,
    val metadataCount: Int,
    val totalUncompressedBytes: Long
)

data class ProjectExportResult(
    val fileName: String,
    val entryCount: Int,
    val uncompressedBytes: Long,
    val destinationLabel: String
)

object ProjectZipExporter {

    fun buildSuggestedFileName(projectName: String, timestampMs: Long = System.currentTimeMillis()): String {
        val slug = projectName
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "agency-project" }
            .take(40)
        val dateStamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(timestampMs))
        return "$slug-$dateStamp.zip"
    }

    fun buildPreview(
        source: ProjectExportSource,
        options: ProjectExportOptions,
        exportedAtMs: Long = System.currentTimeMillis()
    ): ProjectExportPreview {
        val entries = assembleEntries(source, options, exportedAtMs)
        return ProjectExportPreview(
            suggestedFileName = buildSuggestedFileName(source.project.name, exportedAtMs),
            entries = entries,
            markdownCount = entries.count { it.category == ExportEntryCategory.MARKDOWN },
            codebaseCount = entries.count { it.category == ExportEntryCategory.CODEBASE },
            metadataCount = entries.count { it.category == ExportEntryCategory.METADATA },
            totalUncompressedBytes = entries.sumOf { it.sizeBytes.toLong() }
        )
    }

    fun writeZip(
        outputStream: OutputStream,
        preview: ProjectExportPreview
    ): Int {
        var written = 0
        ZipOutputStream(outputStream.buffered()).use { zos ->
            zos.setLevel(6)
            for (entry in preview.entries) {
                val zipEntry = ZipEntry(entry.path).apply {
                    time = entry.updatedAt
                }
                zos.putNextEntry(zipEntry)
                zos.write(entry.content)
                zos.closeEntry()
                written++
            }
        }
        return written
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun assembleEntries(
        source: ProjectExportSource,
        options: ProjectExportOptions,
        exportedAtMs: Long
    ): List<ExportArchiveEntry> {
        val entries = mutableListOf<ExportArchiveEntry>()
        val usedPaths = mutableSetOf<String>()

        fun addUnique(
            rawPath: String,
            category: ExportEntryCategory,
            text: String,
            agentId: String = "agentdesk-exporter",
            updatedAt: Long = exportedAtMs
        ) {
            val cleanPath = deduplicatePath(sanitizeZipPath(rawPath), usedPaths)
            usedPaths += cleanPath
            entries += ExportArchiveEntry(
                path = cleanPath,
                category = category,
                content = text.toByteArray(Charsets.UTF_8),
                lastAgentId = agentId,
                updatedAt = updatedAt
            )
        }

        val generatedMarkdownFiles = source.generatedFiles.filter { isMarkdownPath(it.path) }
        val generatedCodeFiles = source.generatedFiles.filterNot { isMarkdownPath(it.path) }

        // 1. Generated & Source Markdown Documents
        if (options.includeMarkdownDocs) {
            val blueprintFileName = sanitizeFileName(source.project.blueprintFileName, defaultExt = ".md")
            addUnique(
                rawPath = "markdown/$blueprintFileName",
                category = ExportEntryCategory.MARKDOWN,
                text = source.project.blueprintMarkdown,
                agentId = "project-planner",
                updatedAt = source.project.updatedAt
            )

            addUnique(
                rawPath = "README.md",
                category = ExportEntryCategory.MARKDOWN,
                text = buildProjectReadmeMarkdown(source, exportedAtMs),
                agentId = "master-orchestrator",
                updatedAt = exportedAtMs
            )

            if (source.phases.isNotEmpty() || source.tasks.isNotEmpty()) {
                addUnique(
                    rawPath = "markdown/ROADMAP.md",
                    category = ExportEntryCategory.MARKDOWN,
                    text = buildRoadmapMarkdown(source, exportedAtMs),
                    agentId = "master-orchestrator",
                    updatedAt = exportedAtMs
                )
            }

            val tasksWithNotes = source.tasks.filter { it.handoffNotes.isNotBlank() }
            if (tasksWithNotes.isNotEmpty()) {
                addUnique(
                    rawPath = "markdown/AGENT_HANDOFFS.md",
                    category = ExportEntryCategory.MARKDOWN,
                    text = buildHandoffsMarkdown(source.project.name, tasksWithNotes),
                    agentId = "master-orchestrator",
                    updatedAt = exportedAtMs
                )
            }

            // Include any generated .md artifacts produced by the agents
            generatedMarkdownFiles.forEach { file ->
                addUnique(
                    rawPath = "markdown/generated/${file.path}",
                    category = ExportEntryCategory.MARKDOWN,
                    text = file.content,
                    agentId = file.lastAgentId,
                    updatedAt = file.updatedAt
                )
            }
        }

        // 2. Codebase Files & Snippets
        if (options.includeCodebaseFiles) {
            generatedCodeFiles.forEach { file ->
                addUnique(
                    rawPath = "codebase/${file.path}",
                    category = ExportEntryCategory.CODEBASE,
                    text = file.content,
                    agentId = file.lastAgentId,
                    updatedAt = file.updatedAt
                )
            }

            // If user unchecked Markdown Docs but checked Codebase, still keep generated .md inside codebase/
            if (!options.includeMarkdownDocs) {
                generatedMarkdownFiles.forEach { file ->
                    addUnique(
                        rawPath = "codebase/${file.path}",
                        category = ExportEntryCategory.CODEBASE,
                        text = file.content,
                        agentId = file.lastAgentId,
                        updatedAt = file.updatedAt
                    )
                }
            }
        }

        // 3. Agency Run Logs (Markdown + JSON)
        if (options.includeRunLogs) {
            addUnique(
                rawPath = "metadata/run-logs.md",
                category = ExportEntryCategory.METADATA,
                text = buildRunLogsMarkdown(source.project.name, source.runLogs),
                agentId = "agentdesk-exporter",
                updatedAt = exportedAtMs
            )
            addUnique(
                rawPath = "metadata/run-logs.json",
                category = ExportEntryCategory.METADATA,
                text = buildRunLogsJson(source.runLogs),
                agentId = "agentdesk-exporter",
                updatedAt = exportedAtMs
            )
        }

        // 4. Structured Metadata & Token Telemetry
        if (options.includeMetadata) {
            addUnique(
                rawPath = "metadata/token-telemetry.json",
                category = ExportEntryCategory.METADATA,
                text = buildTokenTelemetryJson(source.tokenUsage),
                agentId = "agentdesk-exporter",
                updatedAt = exportedAtMs
            )

            // Build project-metadata.json last so its manifest covers all bundled entries
            val manifestSnapshot = entries.toList()
            addUnique(
                rawPath = "metadata/project-metadata.json",
                category = ExportEntryCategory.METADATA,
                text = buildProjectMetadataJson(source, options, manifestSnapshot, exportedAtMs),
                agentId = "agentdesk-exporter",
                updatedAt = exportedAtMs
            )
        }

        return entries
    }

    private fun buildProjectReadmeMarkdown(source: ProjectExportSource, exportedAtMs: Long): String {
        val project = source.project
        val doneTasks = source.tasks.count { it.status == "DONE" }
        val totalTokens = source.tokenUsage.sumOf { it.totalTokens }
        return buildString {
            appendLine("# ${project.name}")
            appendLine()
            appendLine("> Exported locally from **AgentDesk** on `${formatIsoUtc(exportedAtMs)}`")
            appendLine()
            appendLine("## Project Overview")
            appendLine("- **Status:** `${project.status}`")
            appendLine("- **Active Model:** `${project.activeModelId.ifBlank { source.activeModelId }}`")
            appendLine("- **Thinking Mode:** `${if (source.thinkingEnabled) "Enabled" else "Off"}`")
            appendLine("- **Blueprint File:** `${project.blueprintFileName}`")
            appendLine("- **Phases:** ${source.phases.size} | **Tasks Completed:** $doneTasks / ${source.tasks.size}")
            appendLine("- **Generated Codebase Files:** ${source.generatedFiles.size}")
            appendLine("- **Recorded Token Usage:** $totalTokens tokens across ${source.tokenUsage.size} API turns")
            appendLine()
            appendLine("## Archive Structure")
            appendLine("- `README.md` — Project summary and archive guide")
            appendLine("- `markdown/` — Project blueprint, generated execution roadmap, handoffs, and generated `.md` files")
            appendLine("- `codebase/` — Generated application source code and snippets")
            appendLine("- `metadata/` — Structured `project-metadata.json`, `token-telemetry.json`, and run logs")
            if (source.generatedFiles.isNotEmpty()) {
                appendLine()
                appendLine("## Generated Files")
                source.generatedFiles.forEach { file ->
                    val agent = AgentRoster.findById(file.lastAgentId)
                    appendLine("- `${file.path}` — authored by ${agent.numberCode} ${agent.shortName}")
                }
            }
        }
    }

    private fun buildRoadmapMarkdown(source: ProjectExportSource, exportedAtMs: Long): String {
        return buildString {
            appendLine("# Execution Roadmap — ${source.project.name}")
            appendLine()
            appendLine("_Generated ${formatIsoUtc(exportedAtMs)}_")
            appendLine()
            source.phases.forEach { phase ->
                appendLine("## Phase ${phase.phaseNumber}: ${phase.name} (`${phase.status}`)")
                appendLine()
                val phaseTasks = source.tasks.filter { it.phaseNumber == phase.phaseNumber }
                if (phaseTasks.isEmpty()) {
                    appendLine("_No tasks assigned in this phase._")
                } else {
                    phaseTasks.forEach { task ->
                        val agent = AgentRoster.findById(task.assignedAgentId)
                        val check = if (task.status == "DONE") "x" else " "
                        appendLine("- [$check] **${task.taskCode}** ${task.title} — `${agent.numberCode} ${agent.shortName}` (`${task.status}`)")
                        if (task.handoffNotes.isNotBlank()) {
                            appendLine("  - Handoff: ${task.handoffNotes.replace("\n", " ")}")
                        }
                        if (task.lastError.isNotBlank()) {
                            appendLine("  - Last issue: ${task.lastError.replace("\n", " ")}")
                        }
                    }
                }
                val checklist = parseChecklistLines(phase.checklistJson)
                if (checklist.isNotEmpty()) {
                    appendLine()
                    appendLine("### Phase-Gate Checklist")
                    checklist.forEach { (desc, verified) ->
                        val mark = if (verified) "x" else " "
                        appendLine("- [$mark] $desc")
                    }
                }
                appendLine()
            }
        }
    }

    private fun buildHandoffsMarkdown(projectName: String, tasks: List<TaskEntity>): String {
        return buildString {
            appendLine("# Agent Handoff Notes — $projectName")
            appendLine()
            tasks.forEach { task ->
                val agent = AgentRoster.findById(task.assignedAgentId)
                appendLine("## ${task.taskCode}: ${task.title}")
                appendLine("- **Agent:** ${agent.numberCode} ${agent.displayName} (`${agent.id}`)")
                appendLine("- **Status:** `${task.status}`")
                appendLine()
                appendLine(task.handoffNotes.trim())
                appendLine()
            }
        }
    }

    private fun buildRunLogsMarkdown(projectName: String, logs: List<RunLogEntity>): String {
        return buildString {
            appendLine("# Agency Activity Ledger — $projectName")
            appendLine()
            if (logs.isEmpty()) {
                appendLine("_No activity logs recorded for this project yet._")
            } else {
                logs.forEach { log ->
                    val agent = AgentRoster.findById(log.agentId)
                    val keyTag = if (log.keySlotIndex in 1..4) " [K${log.keySlotIndex}]" else ""
                    appendLine("- `${formatIsoUtc(log.timestamp)}` **[${log.level}]** ${agent.numberCode} ${agent.shortName}$keyTag — ${log.message}")
                }
            }
        }
    }

    private fun buildRunLogsJson(logs: List<RunLogEntity>): String {
        val arr = JSONArray()
        logs.forEach { log ->
            arr.put(
                JSONObject().apply {
                    put("id", log.id)
                    put("timestampIso", formatIsoUtc(log.timestamp))
                    put("timestampMs", log.timestamp)
                    put("agentId", log.agentId)
                    put("keySlotIndex", log.keySlotIndex)
                    put("level", log.level)
                    put("message", log.message)
                }
            )
        }
        return JSONObject().apply {
            put("count", logs.size)
            put("logs", arr)
        }.toString(2)
    }

    private fun buildTokenTelemetryJson(events: List<TokenUsageEntity>): String {
        val arr = JSONArray()
        events.forEach { ev ->
            arr.put(
                JSONObject().apply {
                    put("id", ev.id)
                    put("timestampIso", formatIsoUtc(ev.timestamp))
                    put("agentId", ev.agentId)
                    put("keySlotIndex", ev.keySlotIndex)
                    put("modelId", ev.modelId)
                    put("thinkingLevel", ev.thinkingLevel)
                    put("promptTokens", ev.promptTokens)
                    put("outputTokens", ev.outputTokens)
                    put("thoughtTokens", ev.thoughtTokens)
                    put("totalTokens", ev.totalTokens)
                    put("latencyMs", ev.latencyMs)
                    put("httpStatus", ev.httpStatus)
                }
            )
        }
        return JSONObject().apply {
            put("turns", events.size)
            put("totalPromptTokens", events.sumOf { it.promptTokens })
            put("totalOutputTokens", events.sumOf { it.outputTokens })
            put("totalThoughtTokens", events.sumOf { it.thoughtTokens })
            put("totalTokens", events.sumOf { it.totalTokens })
            put("events", arr)
        }.toString(2)
    }

    private fun buildProjectMetadataJson(
        source: ProjectExportSource,
        options: ProjectExportOptions,
        currentEntries: List<ExportArchiveEntry>,
        exportedAtMs: Long
    ): String {
        val project = source.project
        val techStack = try {
            JSONObject(project.techStackJson.ifBlank { "{}" })
        } catch (_: Exception) {
            JSONObject()
        }

        val byAgentObj = JSONObject()
        source.tokenUsage.groupBy { it.agentId }.forEach { (agentId, list) ->
            byAgentObj.put(
                agentId,
                JSONObject().apply {
                    put("turns", list.size)
                    put("promptTokens", list.sumOf { it.promptTokens })
                    put("outputTokens", list.sumOf { it.outputTokens })
                    put("thoughtTokens", list.sumOf { it.thoughtTokens })
                    put("totalTokens", list.sumOf { it.totalTokens })
                }
            )
        }

        val byKeySlotObj = JSONObject()
        source.tokenUsage.groupBy { it.keySlotIndex }.forEach { (slot, list) ->
            byKeySlotObj.put(
                "K$slot",
                JSONObject().apply {
                    put("requests", list.size)
                    put("totalTokens", list.sumOf { it.totalTokens })
                }
            )
        }

        val manifestArr = JSONArray()
        currentEntries.forEach { item ->
            manifestArr.put(
                JSONObject().apply {
                    put("path", item.path)
                    put("category", item.category.name)
                    put("bytes", item.sizeBytes)
                    put("lastAgentId", item.lastAgentId)
                    put("updatedAtIso", formatIsoUtc(item.updatedAt))
                }
            )
        }

        val avgLatency = if (source.tokenUsage.isEmpty()) 0L
        else source.tokenUsage.sumOf { it.latencyMs } / source.tokenUsage.size

        return JSONObject().apply {
            put("schemaVersion", "1.0")
            put("exportedBy", "AgentDesk")
            put("exportedAtIso", formatIsoUtc(exportedAtMs))
            put("exportOptions", JSONObject().apply {
                put("includeMarkdownDocs", options.includeMarkdownDocs)
                put("includeCodebaseFiles", options.includeCodebaseFiles)
                put("includeMetadata", options.includeMetadata)
                put("includeRunLogs", options.includeRunLogs)
            })
            put("project", JSONObject().apply {
                put("id", project.id)
                put("name", project.name)
                put("status", project.status)
                put("blueprintFileName", project.blueprintFileName)
                put("activeModelId", project.activeModelId.ifBlank { source.activeModelId })
                put("thinkingEnabled", source.thinkingEnabled)
                put("currentPhaseId", project.currentPhaseId)
                put("techStack", techStack)
                put("createdAtIso", formatIsoUtc(project.createdAt))
                put("updatedAtIso", formatIsoUtc(project.updatedAt))
            })
            put("counts", JSONObject().apply {
                put("phases", source.phases.size)
                put("tasksTotal", source.tasks.size)
                put("tasksCompleted", source.tasks.count { it.status == "DONE" })
                put("generatedFiles", source.generatedFiles.size)
                put("runLogs", source.runLogs.size)
                put("archiveEntries", currentEntries.size + 1)
            })
            put("tokenTelemetry", JSONObject().apply {
                put("apiTurns", source.tokenUsage.size)
                put("promptTokens", source.tokenUsage.sumOf { it.promptTokens })
                put("outputTokens", source.tokenUsage.sumOf { it.outputTokens })
                put("thoughtTokens", source.tokenUsage.sumOf { it.thoughtTokens })
                put("totalTokens", source.tokenUsage.sumOf { it.totalTokens })
                put("avgLatencyMs", avgLatency)
                put("byAgent", byAgentObj)
                put("byKeySlot", byKeySlotObj)
            })
            put("manifest", manifestArr)
        }.toString(2)
    }

    private fun parseChecklistLines(json: String): List<Pair<String, Boolean>> {
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { idx ->
                val obj = arr.optJSONObject(idx)
                val desc = obj?.optString("description", "")?.trim().orEmpty()
                val verified = obj?.optBoolean("verified", false) ?: false
                desc to verified
            }.filter { it.first.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isMarkdownPath(path: String): Boolean {
        val lower = path.lowercase(Locale.US)
        return lower.endsWith(".md") || lower.endsWith(".markdown")
    }

    private fun sanitizeFileName(raw: String, defaultExt: String): String {
        val base = raw
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[^a-zA-Z0-9._-]+"), "-")
            .trim('-', '.')
            .ifBlank { "project-blueprint$defaultExt" }
        return if (base.endsWith(defaultExt, ignoreCase = true)) base else "$base$defaultExt"
    }

    private fun sanitizeZipPath(rawPath: String): String {
        val segments = rawPath
            .replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .map { seg -> seg.replace(Regex("[<>:\"|?*\\u0000-\\u001F]"), "_") }
        return if (segments.isEmpty()) "artifact.txt" else segments.joinToString("/")
    }

    private fun deduplicatePath(path: String, usedPaths: Set<String>): String {
        if (path !in usedPaths) return path
        val slashIdx = path.lastIndexOf('/')
        val dir = if (slashIdx >= 0) path.substring(0, slashIdx + 1) else ""
        val fileName = if (slashIdx >= 0) path.substring(slashIdx + 1) else path
        val dotIdx = fileName.lastIndexOf('.')
        val stem = if (dotIdx > 0) fileName.substring(0, dotIdx) else fileName
        val ext = if (dotIdx > 0) fileName.substring(dotIdx) else ""
        var counter = 2
        while (true) {
            val candidate = "$dir${stem}_$counter$ext"
            if (candidate !in usedPaths) return candidate
            counter++
        }
    }

    private fun formatIsoUtc(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(epochMs))
    }
}
