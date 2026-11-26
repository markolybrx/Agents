package com.example.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AgencyDatabase
import com.example.data.local.AgencyRepository
import com.example.data.local.AgencySettings
import com.example.data.local.GeneratedFileEntity
import com.example.data.local.KeyPoolPreferences
import com.example.data.local.PhaseEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.RunLogEntity
import com.example.data.local.TaskEntity
import com.example.data.local.TokenUsageEntity
import com.example.data.network.GeminiCallResult
import com.example.data.network.GeminiRestClient
import com.example.data.prompts.PromptCatalogData
import com.example.domain.AgencyRunnerEngine
import com.example.domain.CriticalAction
import com.example.domain.CriticalApprovalRequest
import com.example.domain.FourKeyPoolManager
import com.example.domain.KeySlotRuntimeState
import com.example.domain.OfficeLiveState
import com.example.domain.ProjectExportOptions
import com.example.domain.ProjectExportPreview
import com.example.domain.ProjectExportResult
import com.example.domain.ProjectExportSource
import com.example.domain.ProjectZipExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class KeyTestStatus(
    val testing: Boolean = false,
    val success: Boolean? = null,
    val message: String = ""
)

data class ExportProjectUiState(
    val isOpen: Boolean = false,
    val isPreparing: Boolean = false,
    val isExporting: Boolean = false,
    val project: ProjectEntity? = null,
    val codebaseFileCount: Int = 0,
    val options: ProjectExportOptions = ProjectExportOptions(),
    val preview: ProjectExportPreview? = null,
    val lastExportResult: ProjectExportResult? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class AgencyViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AgencyDatabase.getInstance(application)
    private val repository = AgencyRepository(db.agencyDao())
    private val preferences = KeyPoolPreferences(application)
    private val geminiClient = GeminiRestClient()
    private val keyPool = FourKeyPoolManager(viewModelScope)
    private val runner = AgencyRunnerEngine(repository, keyPool, geminiClient, viewModelScope)

    val settings: StateFlow<AgencySettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AgencySettings())

    val keyRuntimeStates: StateFlow<List<KeySlotRuntimeState>> = keyPool.runtimeStates
    val officeState: StateFlow<OfficeLiveState> = runner.officeState
    val criticalApproval: StateFlow<CriticalApprovalRequest?> = runner.criticalApproval

    val projects: StateFlow<List<ProjectEntity>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTokenUsage: StateFlow<List<TokenUsageEntity>> = repository.recentTokenUsage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLogs: StateFlow<List<RunLogEntity>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProjectId = MutableStateFlow<Long?>(null)
    val selectedProjectId: StateFlow<Long?> = _selectedProjectId.asStateFlow()

    val selectedProjectPhases: StateFlow<List<PhaseEntity>> = _selectedProjectId
        .flatMapLatest { id -> if (id != null) repository.observePhases(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedProjectTasks: StateFlow<List<TaskEntity>> = _selectedProjectId
        .flatMapLatest { id -> if (id != null) repository.observeTasks(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedProjectFiles: StateFlow<List<GeneratedFileEntity>> = _selectedProjectId
        .flatMapLatest { id -> if (id != null) repository.observeFiles(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isOfficeDrawerOpen = MutableStateFlow(false)
    val isOfficeDrawerOpen: StateFlow<Boolean> = _isOfficeDrawerOpen.asStateFlow()

    private val _selectedOfficeAgentId = MutableStateFlow("master-orchestrator")
    val selectedOfficeAgentId: StateFlow<String> = _selectedOfficeAgentId.asStateFlow()

    private val _keyTestStatuses = MutableStateFlow<Map<Int, KeyTestStatus>>(emptyMap())
    val keyTestStatuses: StateFlow<Map<Int, KeyTestStatus>> = _keyTestStatuses.asStateFlow()

    private val _isGeneratingBlueprint = MutableStateFlow(false)
    val isGeneratingBlueprint: StateFlow<Boolean> = _isGeneratingBlueprint.asStateFlow()

    private val _exportUiState = MutableStateFlow(ExportProjectUiState())
    val exportUiState: StateFlow<ExportProjectUiState> = _exportUiState.asStateFlow()
    private var cachedExportSource: ProjectExportSource? = null

    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessages: SharedFlow<String> = _toastMessages.asSharedFlow()

    init {
        viewModelScope.launch {
            settings.collect { current ->
                keyPool.updateConfiguredSlots(current.keySlots)
                runner.updateSettings(current)
            }
        }
        viewModelScope.launch {
            projects.collect { list ->
                if (_selectedProjectId.value == null && list.isNotEmpty()) {
                    _selectedProjectId.value = list.first().id
                } else if (_selectedProjectId.value != null && list.none { it.id == _selectedProjectId.value }) {
                    _selectedProjectId.value = list.firstOrNull()?.id
                }
            }
        }
    }

    fun selectProject(projectId: Long) {
        _selectedProjectId.value = projectId
    }

    fun setOfficeDrawerOpen(open: Boolean) {
        _isOfficeDrawerOpen.value = open
    }

    fun selectOfficeAgent(agentId: String) {
        _selectedOfficeAgentId.value = agentId
    }

    fun showMessage(message: String) {
        _toastMessages.tryEmit(message)
    }

    // ---- Markdown Blueprint Import & Creation ----

    fun importMarkdownUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            try {
                val sections = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        val name = resolveFileName(context, uri)
                        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                            BufferedReader(InputStreamReader(stream)).readText()
                        }.orEmpty()
                        if (text.isBlank()) null else (name to text)
                    }
                }
                if (sections.isEmpty()) {
                    _toastMessages.tryEmit("Selected file was empty or unreadable.")
                    return@launch
                }
                val primaryName = sections.first().first
                val combinedMarkdown = if (sections.size == 1) {
                    sections.first().second
                } else {
                    sections.joinToString("\n\n---\n\n") { (name, body) ->
                        "<!-- Source: $name -->\n$body"
                    }
                }
                val title = extractProjectTitle(combinedMarkdown, primaryName)
                val newId = repository.createProject(
                    name = title,
                    fileName = if (sections.size == 1) primaryName else "${sections.size} .md plans ($primaryName)",
                    markdown = combinedMarkdown,
                    modelId = settings.value.activeModelId
                )
                _selectedProjectId.value = newId
                _toastMessages.tryEmit("Imported \"$title\" from Markdown")
            } catch (e: Exception) {
                _toastMessages.tryEmit("Markdown import failed: ${e.message}")
            }
        }
    }

    fun createProjectFromMarkdown(titleInput: String, fileName: String, markdown: String) {
        if (markdown.isBlank()) {
            _toastMessages.tryEmit("Markdown blueprint cannot be empty.")
            return
        }
        viewModelScope.launch {
            val title = titleInput.trim().ifBlank { extractProjectTitle(markdown, fileName) }
            val newId = repository.createProject(
                name = title,
                fileName = fileName.ifBlank { "project-blueprint.md" },
                markdown = markdown,
                modelId = settings.value.activeModelId
            )
            _selectedProjectId.value = newId
            _toastMessages.tryEmit("Created plan \"$title\"")
        }
    }

    fun loadDefaultTemplateProject() {
        createProjectFromMarkdown(
            titleInput = "Offline Habit Pulse PWA",
            fileName = "project-blueprint-template.md",
            markdown = PromptCatalogData.defaultBlueprintTemplate
        )
    }

    fun generateBlueprintWithPlanner(idea: String, onSuccess: (String) -> Unit) {
        if (idea.isBlank()) {
            _toastMessages.tryEmit("Describe your PWA idea first.")
            return
        }
        viewModelScope.launch {
            _isGeneratingBlueprint.value = true
            val result = runner.generateBlueprintFromIdea(idea)
            _isGeneratingBlueprint.value = false
            result.onSuccess { markdown ->
                val title = extractProjectTitle(markdown, "planner-blueprint.md")
                val id = repository.createProject(
                    name = title,
                    fileName = "ai-planner-blueprint.md",
                    markdown = markdown,
                    modelId = settings.value.activeModelId
                )
                _selectedProjectId.value = id
                _toastMessages.tryEmit("Planner synthesized \"$title\"")
                onSuccess(markdown)
            }.onFailure { err ->
                _toastMessages.tryEmit("Planner failed: ${err.message}")
            }
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            if (officeState.value.activeProjectId == projectId && officeState.value.isRunning) {
                runner.pauseRun()
            }
            repository.deleteProject(projectId)
            _toastMessages.tryEmit("Project deleted")
        }
    }

    // ---- Pipeline Control ----

    fun startProjectRun(projectId: Long) {
        _selectedProjectId.value = projectId
        runner.startOrResumeProject(projectId)
    }

    fun pauseActiveRun() {
        runner.pauseRun()
        _toastMessages.tryEmit("Agency loop paused")
    }

    fun resetProjectRun(projectId: Long) {
        viewModelScope.launch {
            if (officeState.value.activeProjectId == projectId && officeState.value.isRunning) {
                runner.pauseRun()
            }
            repository.resetProjectExecution(projectId)
            _toastMessages.tryEmit("Project execution reset to IDLE")
        }
    }

    fun resolveCriticalGate(action: CriticalAction, guidanceNote: String = "") {
        runner.resolveCriticalApproval(action, guidanceNote)
    }

    // ---- 4-Key Pool & Model Configuration ----

    fun saveApiKeySlot(slotIndex: Int, label: String, apiKey: String, enabled: Boolean) {
        viewModelScope.launch {
            preferences.saveKeySlot(slotIndex, label, apiKey, enabled)
            keyPool.clearCooldown(slotIndex)
            _toastMessages.tryEmit("Saved Key Slot #$slotIndex")
        }
    }

    fun toggleKeySlotEnabled(slotIndex: Int, enabled: Boolean) {
        viewModelScope.launch {
            preferences.setSlotEnabled(slotIndex, enabled)
        }
    }

    fun clearKeyCooldown(slotIndex: Int) {
        viewModelScope.launch {
            keyPool.clearCooldown(slotIndex)
            _toastMessages.tryEmit("Cooldown cleared for Key #$slotIndex")
        }
    }

    fun testKeySlot(slotIndex: Int) {
        val slot = settings.value.keySlots.firstOrNull { it.slotIndex == slotIndex }
        if (slot == null || !slot.isConfigured) {
            _toastMessages.tryEmit("Enter an API key in Slot #$slotIndex first.")
            return
        }
        viewModelScope.launch {
            _keyTestStatuses.value = _keyTestStatuses.value + (slotIndex to KeyTestStatus(testing = true, message = "Testing..."))
            val res = geminiClient.pingKey(slot.effectiveKey, settings.value.activeModelId)
            val status = when (res) {
                is GeminiCallResult.Success -> KeyTestStatus(
                    testing = false,
                    success = true,
                    message = "OK (${res.latencyMs}ms • ${res.usage.totalTokens} tok)"
                )
                is GeminiCallResult.Failure -> KeyTestStatus(
                    testing = false,
                    success = false,
                    message = "HTTP ${res.httpStatus}: ${res.message.take(70)}"
                )
            }
            _keyTestStatuses.value = _keyTestStatuses.value + (slotIndex to status)
        }
    }

    fun selectActiveModel(modelId: String) {
        viewModelScope.launch {
            preferences.setActiveModel(modelId)
            _toastMessages.tryEmit("Active model switched to $modelId")
        }
    }

    fun setThinkingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setThinkingEnabled(enabled)
        }
    }

    // ---- Project ZIP Archive Export (Markdown + Codebase + Metadata) ----

    fun openExportProjectDialog(projectId: Long? = null) {
        val targetId = projectId ?: _selectedProjectId.value
        if (targetId == null) {
            _toastMessages.tryEmit("Select or create a project before exporting.")
            return
        }
        viewModelScope.launch {
            val initialProject = projects.value.firstOrNull { it.id == targetId }
            val defaultOptions = ProjectExportOptions()
            _exportUiState.value = ExportProjectUiState(
                isOpen = true,
                isPreparing = true,
                project = initialProject,
                options = defaultOptions
            )
            try {
                val source = withContext(Dispatchers.IO) {
                    loadExportSource(targetId)
                }
                if (source == null) {
                    _exportUiState.value = ExportProjectUiState()
                    _toastMessages.tryEmit("Project not found for export.")
                    return@launch
                }
                cachedExportSource = source
                val preview = withContext(Dispatchers.Default) {
                    ProjectZipExporter.buildPreview(source, defaultOptions)
                }
                _exportUiState.value = ExportProjectUiState(
                    isOpen = true,
                    isPreparing = false,
                    project = source.project,
                    codebaseFileCount = source.generatedFiles.size,
                    options = defaultOptions,
                    preview = preview
                )
            } catch (e: Exception) {
                _exportUiState.value = ExportProjectUiState()
                _toastMessages.tryEmit("Unable to prepare export bundle: ${e.message}")
            }
        }
    }

    fun dismissExportProjectDialog() {
        cachedExportSource = null
        _exportUiState.value = ExportProjectUiState()
    }

    fun updateExportOptions(options: ProjectExportOptions) {
        val source = cachedExportSource ?: return
        viewModelScope.launch {
            val preview = withContext(Dispatchers.Default) {
                ProjectZipExporter.buildPreview(source, options)
            }
            _exportUiState.value = _exportUiState.value.copy(
                options = options,
                preview = preview,
                lastExportResult = null
            )
        }
    }

    fun exportProjectToUri(context: Context, destinationUri: Uri) {
        val currentState = _exportUiState.value
        val source = cachedExportSource
        val options = currentState.options
        viewModelScope.launch {
            _exportUiState.value = _exportUiState.value.copy(isExporting = true)
            try {
                val resolvedSource = source ?: withContext(Dispatchers.IO) {
                    _selectedProjectId.value?.let { loadExportSource(it) }
                } ?: return@launch
                val preview = withContext(Dispatchers.Default) {
                    ProjectZipExporter.buildPreview(resolvedSource, options)
                }
                val writtenCount = withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                        ProjectZipExporter.writeZip(out, preview)
                    } ?: throw IllegalStateException("Could not open destination file for writing.")
                }
                val savedName = resolveFileName(context, destinationUri)
                val result = ProjectExportResult(
                    fileName = savedName,
                    entryCount = writtenCount,
                    uncompressedBytes = preview.totalUncompressedBytes,
                    destinationLabel = "Saved to device: $savedName"
                )
                _exportUiState.value = _exportUiState.value.copy(
                    isExporting = false,
                    preview = preview,
                    lastExportResult = result
                )
                _toastMessages.tryEmit("Exported $writtenCount file(s) to $savedName")
            } catch (e: Exception) {
                _exportUiState.value = _exportUiState.value.copy(isExporting = false)
                _toastMessages.tryEmit("ZIP export failed: ${e.message}")
            }
        }
    }

    fun quickDownloadProjectZip(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            _toastMessages.tryEmit("Use Save ZIP As to choose a download folder.")
            return
        }
        val source = cachedExportSource ?: return
        val options = _exportUiState.value.options
        viewModelScope.launch {
            _exportUiState.value = _exportUiState.value.copy(isExporting = true)
            try {
                val preview = withContext(Dispatchers.Default) {
                    ProjectZipExporter.buildPreview(source, options)
                }
                val fileName = preview.suggestedFileName
                val writtenCount = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AgentDesk")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("Unable to create archive in Downloads.")
                    try {
                        val count = resolver.openOutputStream(itemUri)?.use { out ->
                            ProjectZipExporter.writeZip(out, preview)
                        } ?: throw IllegalStateException("Unable to open Downloads output stream.")
                        val readyValues = ContentValues().apply {
                            put(MediaStore.Downloads.IS_PENDING, 0)
                        }
                        resolver.update(itemUri, readyValues, null, null)
                        count
                    } catch (writeErr: Exception) {
                        resolver.delete(itemUri, null, null)
                        throw writeErr
                    }
                }
                val result = ProjectExportResult(
                    fileName = fileName,
                    entryCount = writtenCount,
                    uncompressedBytes = preview.totalUncompressedBytes,
                    destinationLabel = "Downloads/AgentDesk/$fileName"
                )
                _exportUiState.value = _exportUiState.value.copy(
                    isExporting = false,
                    preview = preview,
                    lastExportResult = result
                )
                _toastMessages.tryEmit("Downloaded $fileName ($writtenCount files) to Downloads/AgentDesk")
            } catch (e: Exception) {
                _exportUiState.value = _exportUiState.value.copy(isExporting = false)
                _toastMessages.tryEmit("Download failed: ${e.message}")
            }
        }
    }

    private suspend fun loadExportSource(projectId: Long): ProjectExportSource? {
        val project = repository.getProject(projectId) ?: return null
        val phases = repository.getPhases(projectId)
        val tasks = repository.getTasks(projectId)
        val files = repository.getFiles(projectId)
        val tokens = repository.getProjectTokenUsage(projectId)
        val logs = repository.getProjectLogs(projectId)
        val currentSettings = settings.value
        return ProjectExportSource(
            project = project,
            phases = phases,
            tasks = tasks,
            generatedFiles = files,
            tokenUsage = tokens,
            runLogs = logs,
            activeModelId = currentSettings.activeModelId,
            thinkingEnabled = currentSettings.thinkingEnabled
        )
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && cursor.moveToFirst()) {
                val found = cursor.getString(nameIdx)
                if (!found.isNullOrBlank()) return found
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "project-plan.md"
    }

    private fun extractProjectTitle(markdown: String, fallbackFileName: String): String {
        val heading = markdown.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.replace("Project Blueprint:", "", ignoreCase = true)
            ?.trim()
        if (!heading.isNullOrBlank()) return heading.take(48)
        return fallbackFileName.substringBeforeLast('.').ifBlank { "Untitled Agency Plan" }.take(48)
    }
}
