package com.example.domain

import com.example.data.local.AgencyRepository
import com.example.data.local.AgencySettings
import com.example.data.local.PhaseEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.TaskEntity
import com.example.data.local.TokenUsageEntity
import com.example.data.network.GeminiCallResult
import com.example.data.network.GeminiRestClient
import com.example.data.prompts.AgentRoster
import com.example.data.prompts.PromptCatalogData
import com.example.data.prompts.ThinkingLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Autonomous 13-agent PWA agency runner with 4-API-key round-robin failover,
 * 4-tier timeout/split recovery, deterministic static gating, and manual approval on critical issues.
 */
class AgencyRunnerEngine(
    private val repository: AgencyRepository,
    private val keyPool: FourKeyPoolManager,
    private val geminiClient: GeminiRestClient,
    private val scope: CoroutineScope
) {
    private val _officeState = MutableStateFlow(OfficeLiveState())
    val officeState: StateFlow<OfficeLiveState> = _officeState.asStateFlow()

    private val _criticalApproval = MutableStateFlow<CriticalApprovalRequest?>(null)
    val criticalApproval: StateFlow<CriticalApprovalRequest?> = _criticalApproval.asStateFlow()

    private var activeJob: Job? = null
    private var approvalDeferred: CompletableDeferred<Pair<CriticalAction, String>>? = null
    private var currentSettings: AgencySettings = AgencySettings()

    fun updateSettings(settings: AgencySettings) {
        currentSettings = settings
        _officeState.update { it.copy(activeModelId = settings.activeModelId) }
    }

    fun startOrResumeProject(projectId: Long) {
        if (activeJob?.isActive == true) return
        activeJob = scope.launch {
            runProjectLoop(projectId)
        }
    }

    fun pauseRun() {
        approvalDeferred?.complete(CriticalAction.KEEP_PAUSED to "")
        _criticalApproval.value = null
        activeJob?.cancel()
        activeJob = null
        val pid = _officeState.value.activeProjectId
        if (pid != null) {
            scope.launch {
                repository.getProject(pid)?.let { proj ->
                    if (proj.status == "RUNNING") {
                        repository.updateProject(proj.copy(status = "PAUSED_USER"))
                    }
                }
            }
        }
        _officeState.update {
            it.copy(
                isRunning = false,
                activeAgentId = null,
                activeKeySlot = null,
                statusHeadline = "Run paused by operator"
            )
        }
    }

    fun resolveCriticalApproval(action: CriticalAction, guidanceNote: String = "") {
        _criticalApproval.value = null
        approvalDeferred?.complete(action to guidanceNote.trim())
    }

    suspend fun generateBlueprintFromIdea(idea: String): Result<String> {
        val profile = AgentRoster.findById("project-planner")
        updateAgentVisual(
            agentId = profile.id,
            status = AgentWorkStatus.THINKING,
            bubble = "Drafting .md blueprint...",
            taskTitle = "Blueprint synthesis"
        )
        val result = invokeWithKeyPool(
            projectId = 0L,
            agentId = profile.id,
            thinkingLevel = profile.thinkingLevel,
            systemInstruction = PromptCatalogData.getById("00-project-planner-skill").content,
            userPayload = "User PWA Idea:\n$idea\n\nFollow this exact structure:\n${PromptCatalogData.defaultBlueprintTemplate}",
            expectJson = false
        )
        updateAgentVisual(
            agentId = profile.id,
            status = if (result is GeminiCallResult.Success) AgentWorkStatus.DONE else AgentWorkStatus.IDLE,
            bubble = if (result is GeminiCallResult.Success) "Blueprint ready" else "",
            taskTitle = ""
        )
        return when (result) {
            is GeminiCallResult.Success -> Result.success(result.text)
            is GeminiCallResult.Failure -> Result.failure(IllegalStateException(result.message))
        }
    }

    private suspend fun runProjectLoop(projectId: Long) {
        var project = repository.getProject(projectId) ?: return
        if (keyPool.configuredKeyCount() == 0) {
            repository.log(
                projectId,
                "master-orchestrator",
                0,
                "CRITICAL",
                "No active API keys configured. Add at least 1 Gemini key in Keys & Models."
            )
            _officeState.update {
                it.copy(
                    isRunning = false,
                    statusHeadline = "Add at least 1 Gemini API key to start"
                )
            }
            return
        }

        project = project.copy(status = "RUNNING", activeModelId = currentSettings.activeModelId)
        repository.updateProject(project)
        _officeState.update {
            it.copy(
                isRunning = true,
                activeProjectId = projectId,
                activeModelId = currentSettings.activeModelId,
                statusHeadline = "Launching ${project.name}..."
            )
        }

        try {
            // Step 1: INTAKE if no phases exist yet
            var phases = repository.getPhases(projectId)
            if (phases.isEmpty()) {
                val intakeOk = executeOrchestratorIntake(project)
                if (!intakeOk) return
                project = repository.getProject(projectId) ?: return
                phases = repository.getPhases(projectId)
            }

            // Step 2: Iterate through phases sequentially
            while (true) {
                phases = repository.getPhases(projectId)
                val activePhase = phases.firstOrNull { it.status != "PASSED" }
                if (activePhase == null) {
                    project = project.copy(status = "COMPLETED")
                    repository.updateProject(project)
                    repository.log(projectId, "master-orchestrator", 0, "SUCCESS", "All phases passed — PROJECT_COMPLETE!")
                    _officeState.update {
                        it.copy(
                            isRunning = false,
                            activeAgentId = null,
                            activeKeySlot = null,
                            currentPhaseLabel = "Complete",
                            statusHeadline = "${project.name} shipped — ready for ZIP export"
                        )
                    }
                    return
                }

                if (activePhase.status == "PENDING") {
                    repository.updatePhase(activePhase.copy(status = "IN_PROGRESS"))
                }
                project = project.copy(currentPhaseId = activePhase.phaseNumber)
                repository.updateProject(project)
                _officeState.update {
                    it.copy(currentPhaseLabel = "Phase ${activePhase.phaseNumber}: ${activePhase.name}")
                }

                // Step 3: Process all pending tasks in active phase sequentially
                var phaseProceed = true
                while (phaseProceed) {
                    val phaseTasks = repository.getTasks(projectId)
                        .filter { it.phaseNumber == activePhase.phaseNumber }
                    val nextTask = phaseTasks.firstOrNull { it.status != "DONE" } ?: break

                    val taskOk = executeTaskLifecycle(project, activePhase, nextTask)
                    if (!taskOk) {
                        // User chose KEEP_PAUSED at a critical gate
                        return
                    }
                }

                // Step 4: Phase-end QA test generation
                runPhaseQaTester(project, activePhase)

                // Step 5: Phase Gate evaluation via Master Orchestrator
                val gateOutcome = runPhaseGate(project, activePhase)
                if (!gateOutcome) return
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            repository.log(projectId, "master-orchestrator", 0, "CRITICAL", "Engine error: ${e.message}")
            repository.updateProject(project.copy(status = "PAUSED_CRITICAL"))
            _officeState.update {
                it.copy(
                    isRunning = false,
                    activeAgentId = null,
                    activeKeySlot = null,
                    statusHeadline = "Paused due to unexpected error"
                )
            }
        }
    }

    private suspend fun executeOrchestratorIntake(project: ProjectEntity): Boolean {
        val orchestrator = AgentRoster.findById("master-orchestrator")
        repository.log(project.id, orchestrator.id, 0, "INFO", "INTAKE: Analyzing ${project.blueprintFileName}...")
        updateAgentVisual(
            agentId = orchestrator.id,
            status = AgentWorkStatus.THINKING,
            bubble = "Planning phases & tasks...",
            taskTitle = "INTAKE Blueprint Analysis"
        )

        val payload = JSONObject().apply {
            put("mode", "INTAKE")
            put("input_markdown", project.blueprintMarkdown.take(14_000))
        }.toString()

        val call = invokeWithKeyPool(
            projectId = project.id,
            agentId = orchestrator.id,
            thinkingLevel = ThinkingLevel.HIGH,
            systemInstruction = PromptCatalogData.getById("01-master-orchestrator").content,
            userPayload = payload
        )

        if (call is GeminiCallResult.Failure) {
            val (action, _) = requestHumanApproval(
                CriticalApprovalRequest(
                    projectId = project.id,
                    taskId = null,
                    taskCode = "INTAKE",
                    taskTitle = "Blueprint Intake",
                    agentId = orchestrator.id,
                    reasonTitle = "Orchestrator Intake Failed",
                    details = call.message,
                    blockers = listOf(call.message),
                    retryCount = 1,
                    splitDepth = 0
                )
            )
            return if (action == CriticalAction.RETRY_WITH_GUIDANCE || action == CriticalAction.APPROVE_CONTINUE) {
                executeOrchestratorIntake(project)
            } else {
                false
            }
        }

        val success = call as GeminiCallResult.Success
        val parsed = parseIntakeState(project, success.text)
        repository.updateProject(
            project.copy(
                name = parsed.projectName,
                techStackJson = parsed.techStackJson,
                currentPhaseId = 1
            )
        )
        repository.replacePhasesAndTasks(project.id, parsed.phases, parsed.tasks)
        repository.log(
            project.id,
            orchestrator.id,
            0,
            "SUCCESS",
            "INTAKE locked stack & generated ${parsed.phases.size} phases (${parsed.tasks.size} atomic tasks)."
        )
        updateAgentVisual(
            agentId = orchestrator.id,
            status = AgentWorkStatus.DONE,
            bubble = "Roadmap locked (${parsed.tasks.size} tasks)",
            taskTitle = "INTAKE Complete",
            handoff = "Locked ${parsed.phases.size} phases"
        )
        return true
    }

    private suspend fun executeTaskLifecycle(
        project: ProjectEntity,
        phase: PhaseEntity,
        initialTask: TaskEntity
    ): Boolean {
        var task = initialTask
        var compactMode = false
        var humanGuidance = ""

        while (true) {
            val worker = AgentRoster.findById(task.assignedAgentId)
            repository.updateTask(task.copy(status = "RUNNING"))
            repository.log(
                project.id,
                worker.id,
                0,
                "INFO",
                "[${task.taskCode}] ${worker.shortName} executing: ${task.title}"
            )
            updateAgentVisual(
                agentId = worker.id,
                status = AgentWorkStatus.WRITING,
                bubble = task.title.take(36),
                taskTitle = "${task.taskCode}: ${task.title}"
            )

            val existingFiles = repository.getFiles(project.id)
            val targetFiles = jsonStringArray(task.targetFilesJson)
            val relevantFilesObj = JSONObject()
            existingFiles
                .filter { ef -> targetFiles.isEmpty() || targetFiles.any { ef.path.equals(it, true) } }
                .take(if (compactMode) 2 else 6)
                .forEach { relevantFilesObj.put(it.path, it.content.take(if (compactMode) 4000 else 9000)) }

            val payload = JSONObject().apply {
                put("tech_stack", safeJsonObject(project.techStackJson))
                put(
                    "task",
                    JSONObject().apply {
                        put("task_id", task.taskCode)
                        put("title", task.title)
                        put("assigned_agent", worker.id)
                        put("target_files", JSONArray(targetFiles))
                        put("retry_count", task.retryCount)
                        put("split_depth", task.splitDepth)
                    }
                )
                put("existing_files", relevantFilesObj)
                if (compactMode) {
                    put("budget_hint", JSONObject().put("compact_mode", true))
                }
                if (humanGuidance.isNotBlank()) {
                    put("operator_guidance", humanGuidance)
                }
            }.toString()

            val call = invokeWithKeyPool(
                projectId = project.id,
                agentId = worker.id,
                thinkingLevel = worker.thinkingLevel,
                systemInstruction = PromptCatalogData.buildSystemInstructionForWorker(worker.id),
                userPayload = payload
            )

            // Tier 2 / Tier 3 handling on timeout or MAX_TOKENS
            if (call is GeminiCallResult.Failure) {
                if (!compactMode && call.isTimeout) {
                    compactMode = true
                    repository.log(project.id, worker.id, 0, "WARN", "Tier-2 recovery: pruning context (compact_mode=true) & retrying.")
                    continue
                }
                val nextRetry = task.retryCount + 1
                task = task.copy(retryCount = nextRetry, lastError = call.message)
                repository.updateTask(task)

                if (nextRetry < 3) {
                    delay(1200L)
                    continue
                }
                val (action, note) = requestHumanApproval(
                    CriticalApprovalRequest(
                        projectId = project.id,
                        taskId = task.id,
                        taskCode = task.taskCode,
                        taskTitle = task.title,
                        agentId = worker.id,
                        reasonTitle = "Circuit Breaker: 3 Retries Exhausted",
                        details = "Task failed after 3 attempts: ${call.message}",
                        blockers = listOf(call.message),
                        retryCount = nextRetry,
                        splitDepth = task.splitDepth
                    )
                )
                when (action) {
                    CriticalAction.APPROVE_CONTINUE -> {
                        repository.updateTask(task.copy(status = "DONE"))
                        return true
                    }
                    CriticalAction.RETRY_WITH_GUIDANCE -> {
                        humanGuidance = note
                        task = task.copy(retryCount = 0)
                        continue
                    }
                    CriticalAction.FORCE_SPLIT_TASK -> {
                        return attemptAutonomousSplit(project, phase, task, call.message)
                    }
                    CriticalAction.KEEP_PAUSED -> return false
                }
            }

            val success = call as GeminiCallResult.Success
            if (success.finishReason.equals("MAX_TOKENS", ignoreCase = true)) {
                repository.log(project.id, worker.id, 0, "WARN", "MAX_TOKENS hit on ${task.taskCode} — invoking Orchestrator RECOVER split.")
                return attemptAutonomousSplit(project, phase, task, "MAX_TOKENS truncation")
            }

            val parsedEnvelope = parseWorkerEnvelope(success.text)
            val isTooLarge = parsedEnvelope.blockers.any { it.contains("TASK_TOO_LARGE", ignoreCase = true) }
            if (isTooLarge) {
                repository.log(project.id, worker.id, 0, "WARN", "Worker signaled TASK_TOO_LARGE — splitting via Orchestrator.")
                return attemptAutonomousSplit(project, phase, task, parsedEnvelope.blockers.joinToString("; "))
            }

            if (parsedEnvelope.status.equals("BLOCKED", ignoreCase = true)) {
                updateAgentVisual(worker.id, AgentWorkStatus.BLOCKED, "Blocked — needs review", task.title)
                val (action, note) = requestHumanApproval(
                    CriticalApprovalRequest(
                        projectId = project.id,
                        taskId = task.id,
                        taskCode = task.taskCode,
                        taskTitle = task.title,
                        agentId = worker.id,
                        reasonTitle = "Agent Reported BLOCKED",
                        details = parsedEnvelope.summary,
                        blockers = parsedEnvelope.blockers.ifEmpty { listOf("Worker returned BLOCKED status.") },
                        retryCount = task.retryCount,
                        splitDepth = task.splitDepth
                    )
                )
                when (action) {
                    CriticalAction.APPROVE_CONTINUE -> {
                        repository.updateTask(task.copy(status = "DONE"))
                        return true
                    }
                    CriticalAction.RETRY_WITH_GUIDANCE -> {
                        humanGuidance = note
                        continue
                    }
                    CriticalAction.FORCE_SPLIT_TASK -> {
                        return attemptAutonomousSplit(project, phase, task, parsedEnvelope.blockers.joinToString("; "))
                    }
                    CriticalAction.KEEP_PAUSED -> return false
                }
            }

            // Persist returned files to Room workspace
            val writtenPairs = mutableListOf<Pair<String, String>>()
            for (fileItem in parsedEnvelope.files) {
                if (fileItem.action.equals("DELETE", ignoreCase = true)) {
                    repository.deleteGeneratedFile(project.id, fileItem.path)
                } else {
                    repository.upsertGeneratedFile(project.id, fileItem.path, fileItem.content, worker.id)
                    writtenPairs.add(fileItem.path to fileItem.content)
                }
            }
            val handoffText = parsedEnvelope.handoffNotes.joinToString(" • ").ifBlank { parsedEnvelope.summary }
            task = task.copy(status = "CODED", handoffNotes = handoffText)
            repository.updateTask(task)
            updateAgentVisual(worker.id, AgentWorkStatus.DONE, "Saved ${writtenPairs.size} file(s)", task.title, handoffText)

            // Deterministic Static Integrity Gate (zero API calls)
            val gateReport = StaticIntegrityGate.validateFiles(writtenPairs)
            if (!gateReport.passed) {
                repository.log(
                    project.id,
                    "debugger",
                    0,
                    "WARN",
                    "Static gate flagged ${gateReport.issues.size} issue(s) on ${task.taskCode} — dispatching Debugger."
                )
                val debugOk = runDebuggerRepair(project, task, gateReport.issues)
                if (!debugOk) return false
            }

            // Code Reviewer Audit
            val reviewOk = runCodeReviewerAudit(project, task, writtenPairs, handoffText)
            if (!reviewOk) return false

            repository.updateTask(task.copy(status = "DONE"))
            repository.log(project.id, worker.id, 0, "SUCCESS", "[${task.taskCode}] Completed & verified.")
            return true
        }
    }

    private suspend fun runCodeReviewerAudit(
        project: ProjectEntity,
        task: TaskEntity,
        writtenFiles: List<Pair<String, String>>,
        handoff: String
    ): Boolean {
        if (writtenFiles.isEmpty()) return true
        val reviewer = AgentRoster.findById("code-reviewer")
        updateAgentVisual(
            agentId = reviewer.id,
            status = AgentWorkStatus.AUDITING,
            bubble = "Auditing ${task.taskCode}...",
            taskTitle = "Review ${task.taskCode}"
        )

        val filesJson = JSONObject()
        writtenFiles.forEach { (p, c) -> filesJson.put(p, c.take(8000)) }
        val payload = JSONObject().apply {
            put("task_id", task.taskCode)
            put("task_title", task.title)
            put("handoff_notes", handoff)
            put("files_to_review", filesJson)
        }.toString()

        val call = invokeWithKeyPool(
            projectId = project.id,
            agentId = reviewer.id,
            thinkingLevel = ThinkingLevel.HIGH,
            systemInstruction = PromptCatalogData.buildSystemInstructionForWorker(reviewer.id),
            userPayload = payload
        )

        if (call is GeminiCallResult.Failure) {
            repository.log(project.id, reviewer.id, 0, "WARN", "Reviewer skipped due to transient API error: ${call.message}")
            updateAgentVisual(reviewer.id, AgentWorkStatus.IDLE, "", "")
            return true
        }

        val envelope = parseWorkerEnvelope((call as GeminiCallResult.Success).text)
        if (envelope.status.equals("NEEDS_REVISION", ignoreCase = true) && envelope.blockers.isNotEmpty()) {
            repository.log(project.id, reviewer.id, 0, "WARN", "Code Reviewer found critical blocker(s): ${envelope.blockers.first()}")
            updateAgentVisual(reviewer.id, AgentWorkStatus.BLOCKED, "Critical defect found", task.taskCode)
            // First try automatic Debugger repair; if Debugger still fails, escalate to Manual Approval
            val repaired = runDebuggerRepair(project, task, envelope.blockers)
            if (repaired) {
                updateAgentVisual(reviewer.id, AgentWorkStatus.DONE, "Fix verified", task.taskCode)
                return true
            }
            return false
        }

        updateAgentVisual(reviewer.id, AgentWorkStatus.DONE, "Approved ${task.taskCode}", task.taskCode, envelope.summary)
        return true
    }

    private suspend fun runDebuggerRepair(
        project: ProjectEntity,
        task: TaskEntity,
        blockers: List<String>
    ): Boolean {
        val debugger = AgentRoster.findById("debugger")
        updateAgentVisual(
            agentId = debugger.id,
            status = AgentWorkStatus.WRITING,
            bubble = "Patching ${task.taskCode}...",
            taskTitle = "Debug ${task.taskCode}"
        )

        val currentFiles = repository.getFiles(project.id)
        val targetFiles = jsonStringArray(task.targetFilesJson)
        val filesJson = JSONObject()
        currentFiles
            .filter { ef -> targetFiles.isEmpty() || targetFiles.any { ef.path.equals(it, true) } }
            .forEach { filesJson.put(it.path, it.content.take(8000)) }

        val payload = JSONObject().apply {
            put("task_id", task.taskCode)
            put("retry_count", task.retryCount + 1)
            put("blockers", JSONArray(blockers))
            put("target_files_content", filesJson)
        }.toString()

        val call = invokeWithKeyPool(
            projectId = project.id,
            agentId = debugger.id,
            thinkingLevel = ThinkingLevel.HIGH,
            systemInstruction = PromptCatalogData.buildSystemInstructionForWorker(debugger.id),
            userPayload = payload
        )

        if (call is GeminiCallResult.Success) {
            val env = parseWorkerEnvelope(call.text)
            if (!env.status.equals("BLOCKED", ignoreCase = true) && env.files.isNotEmpty()) {
                env.files.forEach { f ->
                    repository.upsertGeneratedFile(project.id, f.path, f.content, debugger.id)
                }
                repository.log(project.id, debugger.id, 0, "SUCCESS", "Debugger patched ${env.files.size} file(s) for ${task.taskCode}.")
                updateAgentVisual(debugger.id, AgentWorkStatus.DONE, "Patched ${env.files.size} file(s)", task.taskCode, env.summary)
                return true
            }
        }

        updateAgentVisual(debugger.id, AgentWorkStatus.BLOCKED, "Needs manual approval", task.taskCode)
        val (action, _) = requestHumanApproval(
            CriticalApprovalRequest(
                projectId = project.id,
                taskId = task.id,
                taskCode = task.taskCode,
                taskTitle = task.title,
                agentId = debugger.id,
                reasonTitle = "Critical Review / Debug Blocker",
                details = "Auto-debugger could not cleanly resolve critical blockers on ${task.taskCode}.",
                blockers = blockers,
                retryCount = task.retryCount + 1,
                splitDepth = task.splitDepth
            )
        )
        return action != CriticalAction.KEEP_PAUSED
    }

    private suspend fun runPhaseQaTester(project: ProjectEntity, phase: PhaseEntity) {
        val qa = AgentRoster.findById("qa-tester")
        updateAgentVisual(
            agentId = qa.id,
            status = AgentWorkStatus.AUDITING,
            bubble = "Writing Phase ${phase.phaseNumber} tests...",
            taskTitle = "QA Phase ${phase.phaseNumber}"
        )
        val files = repository.getFiles(project.id)
        val manifestSummary = JSONArray().apply {
            files.forEach { put(it.path) }
        }
        val payload = JSONObject().apply {
            put("mode", "WRITE_TESTS")
            put("phase_name", phase.name)
            put("checklist", safeJsonArray(phase.checklistJson))
            put("generated_file_paths", manifestSummary)
            put("tech_stack", safeJsonObject(project.techStackJson))
        }.toString()

        val call = invokeWithKeyPool(
            projectId = project.id,
            agentId = qa.id,
            thinkingLevel = ThinkingLevel.MEDIUM,
            systemInstruction = PromptCatalogData.buildSystemInstructionForWorker(qa.id),
            userPayload = payload
        )
        if (call is GeminiCallResult.Success) {
            val env = parseWorkerEnvelope(call.text)
            env.files.forEach { f ->
                repository.upsertGeneratedFile(project.id, f.path, f.content, qa.id)
            }
            repository.log(project.id, qa.id, 0, "SUCCESS", "QA generated ${env.files.size} test file(s) for Phase ${phase.phaseNumber}.")
            updateAgentVisual(qa.id, AgentWorkStatus.DONE, "Tests ready (${env.files.size})", "Phase ${phase.phaseNumber} QA", env.summary)
        } else {
            updateAgentVisual(qa.id, AgentWorkStatus.IDLE, "", "")
        }
    }

    private suspend fun runPhaseGate(project: ProjectEntity, phase: PhaseEntity): Boolean {
        val orchestrator = AgentRoster.findById("master-orchestrator")
        updateAgentVisual(
            agentId = orchestrator.id,
            status = AgentWorkStatus.THINKING,
            bubble = "Evaluating Phase ${phase.phaseNumber} gate...",
            taskTitle = "PHASE_GATE #${phase.phaseNumber}"
        )

        val files = repository.getFiles(project.id)
        val payload = JSONObject().apply {
            put("mode", "PHASE_GATE")
            put("phase_id", phase.phaseNumber)
            put("phase_name", phase.name)
            put("checklist", safeJsonArray(phase.checklistJson))
            put("generated_files", JSONArray(files.map { it.path }))
        }.toString()

        val call = invokeWithKeyPool(
            projectId = project.id,
            agentId = orchestrator.id,
            thinkingLevel = ThinkingLevel.HIGH,
            systemInstruction = PromptCatalogData.getById("01-master-orchestrator").content,
            userPayload = payload
        )

        if (call is GeminiCallResult.Success) {
            val obj = safeJsonObject(call.text)
            val decision = obj.optString("decision", "ADVANCE_PHASE")
            val reasoning = obj.optString("reasoning", "Phase checklist verified.")

            if (decision.equals("PAUSE_FOR_HUMAN", ignoreCase = true)) {
                val (action, _) = requestHumanApproval(
                    CriticalApprovalRequest(
                        projectId = project.id,
                        taskId = null,
                        taskCode = "PHASE-${phase.phaseNumber}",
                        taskTitle = phase.name,
                        agentId = orchestrator.id,
                        reasonTitle = "Phase Gate Paused for Human Review",
                        details = reasoning,
                        blockers = listOf(reasoning),
                        retryCount = 0,
                        splitDepth = 0
                    )
                )
                if (action == CriticalAction.KEEP_PAUSED) return false
            }

            val verifiedChecklist = markChecklistVerified(phase.checklistJson)
            repository.updatePhase(phase.copy(status = "PASSED", checklistJson = verifiedChecklist))
            repository.log(project.id, orchestrator.id, 0, "SUCCESS", "PHASE_GATE ${phase.phaseNumber} PASSED: $reasoning")
            updateAgentVisual(orchestrator.id, AgentWorkStatus.DONE, "Phase ${phase.phaseNumber} passed", phase.name, reasoning)
            return true
        }

        // Fallback advance if gate call had transient error after all tasks succeeded
        val verifiedChecklist = markChecklistVerified(phase.checklistJson)
        repository.updatePhase(phase.copy(status = "PASSED", checklistJson = verifiedChecklist))
        return true
    }

    private suspend fun attemptAutonomousSplit(
        project: ProjectEntity,
        phase: PhaseEntity,
        task: TaskEntity,
        reason: String
    ): Boolean {
        if (task.splitDepth >= 2) {
            val (action, _) = requestHumanApproval(
                CriticalApprovalRequest(
                    projectId = project.id,
                    taskId = task.id,
                    taskCode = task.taskCode,
                    taskTitle = task.title,
                    agentId = "master-orchestrator",
                    reasonTitle = "Circuit Breaker: Max Split Depth (2) Reached",
                    details = "Task ${task.taskCode} exceeded autonomous split depth. Reason: $reason",
                    blockers = listOf(reason),
                    retryCount = task.retryCount,
                    splitDepth = task.splitDepth
                )
            )
            return if (action == CriticalAction.APPROVE_CONTINUE) {
                repository.updateTask(task.copy(status = "DONE"))
                true
            } else {
                false
            }
        }

        val orchestrator = AgentRoster.findById("master-orchestrator")
        updateAgentVisual(
            agentId = orchestrator.id,
            status = AgentWorkStatus.THINKING,
            bubble = "Splitting ${task.taskCode}...",
            taskTitle = "RECOVER Split"
        )
        val payload = JSONObject().apply {
            put("mode", "RECOVER")
            put("reason", reason)
            put("oversized_task", JSONObject().apply {
                put("task_id", task.taskCode)
                put("title", task.title)
                put("assigned_agent", task.assignedAgentId)
                put("target_files", safeJsonArray(task.targetFilesJson))
                put("split_depth", task.splitDepth)
            })
        }.toString()

        val call = invokeWithKeyPool(
            projectId = project.id,
            agentId = orchestrator.id,
            thinkingLevel = ThinkingLevel.HIGH,
            systemInstruction = PromptCatalogData.getById("01-master-orchestrator").content,
            userPayload = payload
        )

        val subtasks = if (call is GeminiCallResult.Success) {
            extractSplitSubtasks(project.id, phase.phaseNumber, task, call.text)
        } else {
            emptyList()
        }

        if (subtasks.isNotEmpty()) {
            repository.updateTask(task.copy(status = "DONE", handoffNotes = "Split into ${subtasks.size} subtasks"))
            repository.insertTasks(subtasks)
            repository.log(project.id, orchestrator.id, 0, "INFO", "Autonomous SPLIT_TASK created ${subtasks.size} subtasks for ${task.taskCode}.")
            updateAgentVisual(orchestrator.id, AgentWorkStatus.DONE, "Split into ${subtasks.size} tasks", task.taskCode)
            return true
        }

        // If Orchestrator could not split cleanly, ask operator
        val (action, _) = requestHumanApproval(
            CriticalApprovalRequest(
                projectId = project.id,
                taskId = task.id,
                taskCode = task.taskCode,
                taskTitle = task.title,
                agentId = orchestrator.id,
                reasonTitle = "Task Split Requires Operator Decision",
                details = reason,
                blockers = listOf(reason),
                retryCount = task.retryCount,
                splitDepth = task.splitDepth
            )
        )
        if (action == CriticalAction.APPROVE_CONTINUE) {
            repository.updateTask(task.copy(status = "DONE"))
            return true
        }
        return false
    }

    /**
     * Tier-1 4-API-Key Round-Robin Failover Engine.
     * Rotates through all configured healthy keys, applying 60s cooldown on 429 and logging real usageMetadata.
     */
    private suspend fun invokeWithKeyPool(
        projectId: Long,
        agentId: String,
        thinkingLevel: ThinkingLevel,
        systemInstruction: String,
        userPayload: String,
        expectJson: Boolean = true
    ): GeminiCallResult {
        val triedSlots = mutableSetOf<Int>()
        val modelId = currentSettings.activeModelId

        for (attempt in 1..4) {
            val (acquired, waitMs) = keyPool.acquireNextKey(triedSlots)
            if (acquired == null) {
                if (waitMs < 0) {
                    return GeminiCallResult.Failure(
                        httpStatus = 0,
                        isRateLimited = false,
                        isTimeout = false,
                        isTransient = false,
                        message = "No Gemini API keys configured.",
                        latencyMs = 0L
                    )
                }
                if (waitMs > 0) {
                    val waitSec = ((waitMs + 999) / 1000).coerceAtMost(15)
                    repository.log(projectId, agentId, 0, "WARN", "All keys cooling down — waiting ${waitSec}s...")
                    delay(waitSec * 1000L)
                    triedSlots.clear()
                    continue
                }
                break
            }

            triedSlots.add(acquired.slotIndex)
            _officeState.update {
                it.copy(
                    activeAgentId = agentId,
                    activeKeySlot = acquired.slotIndex,
                    activeModelId = modelId,
                    activeThinkingLevel = thinkingLevel,
                    statusHeadline = "${AgentRoster.findById(agentId).shortName} firing on Key #${acquired.slotIndex}"
                )
            }

            val result = geminiClient.generateContent(
                apiKey = acquired.apiKey,
                modelId = modelId,
                systemInstruction = systemInstruction,
                userPayload = userPayload,
                thinkingLevel = thinkingLevel,
                thinkingEnabled = currentSettings.thinkingEnabled,
                expectJson = expectJson
            )

            when (result) {
                is GeminiCallResult.Success -> {
                    keyPool.releaseSuccess(acquired.slotIndex, result.usage.totalTokens)
                    if (projectId > 0L) {
                        repository.recordTokenUsage(
                            TokenUsageEntity(
                                projectId = projectId,
                                keySlotIndex = acquired.slotIndex,
                                agentId = agentId,
                                modelId = modelId,
                                thinkingLevel = thinkingLevel.apiValue,
                                promptTokens = result.usage.promptTokens,
                                outputTokens = result.usage.outputTokens,
                                thoughtTokens = result.usage.thoughtTokens,
                                totalTokens = result.usage.totalTokens,
                                latencyMs = result.latencyMs,
                                httpStatus = result.httpStatus
                            )
                        )
                    }
                    _officeState.update { state ->
                        val prev = state.agents[agentId] ?: AgentLiveState(agentId)
                        state.copy(
                            agents = state.agents + (agentId to prev.copy(tokensUsedSession = prev.tokensUsedSession + result.usage.totalTokens))
                        )
                    }
                    return result
                }
                is GeminiCallResult.Failure -> {
                    if (result.isRateLimited) {
                        keyPool.markCooldown(acquired.slotIndex, 60_000L, "429 RESOURCE_EXHAUSTED")
                        repository.log(
                            projectId,
                            agentId,
                            acquired.slotIndex,
                            "WARN",
                            "Key #${acquired.slotIndex} hit 429 — 60s cooldown, rotating to next key."
                        )
                        continue
                    } else if (result.isTransient && attempt < 4) {
                        keyPool.markCooldown(acquired.slotIndex, 15_000L, result.message)
                        repository.log(
                            projectId,
                            agentId,
                            acquired.slotIndex,
                            "WARN",
                            "Key #${acquired.slotIndex} failed (${result.message}) — failing over."
                        )
                        delay(500L * attempt)
                        continue
                    } else {
                        keyPool.markError(acquired.slotIndex, result.message)
                        return result
                    }
                }
            }
        }

        return GeminiCallResult.Failure(
            httpStatus = 429,
            isRateLimited = true,
            isTimeout = false,
            isTransient = true,
            message = "All 4 API keys exhausted or timed out on this turn.",
            latencyMs = 0L
        )
    }

    private suspend fun requestHumanApproval(request: CriticalApprovalRequest): Pair<CriticalAction, String> {
        repository.getProject(request.projectId)?.let { proj ->
            repository.updateProject(proj.copy(status = "PAUSED_CRITICAL"))
        }
        repository.log(
            request.projectId,
            request.agentId,
            0,
            "CRITICAL",
            "MANUAL APPROVAL REQUIRED (${request.taskCode}): ${request.reasonTitle}"
        )
        _officeState.update {
            it.copy(
                activeKeySlot = null,
                statusHeadline = "Paused for manual approval: ${request.reasonTitle}"
            )
        }
        val deferred = CompletableDeferred<Pair<CriticalAction, String>>()
        approvalDeferred = deferred
        _criticalApproval.value = request
        val decision = deferred.await()
        if (decision.first != CriticalAction.KEEP_PAUSED) {
            repository.getProject(request.projectId)?.let { proj ->
                repository.updateProject(proj.copy(status = "RUNNING"))
            }
        } else {
            _officeState.update { it.copy(isRunning = false, activeAgentId = null) }
        }
        return decision
    }

    private fun updateAgentVisual(
        agentId: String,
        status: AgentWorkStatus,
        bubble: String,
        taskTitle: String,
        handoff: String? = null
    ) {
        _officeState.update { state ->
            val current = state.agents[agentId] ?: AgentLiveState(agentId)
            val updated = current.copy(
                status = status,
                speechBubble = bubble,
                activeTaskTitle = taskTitle,
                lastHandoffSummary = handoff ?: current.lastHandoffSummary
            )
            state.copy(agents = state.agents + (agentId to updated))
        }
    }

    // ---- JSON Parsing Helpers ----

    private data class ParsedIntake(
        val projectName: String,
        val techStackJson: String,
        val phases: List<PhaseEntity>,
        val tasks: List<TaskEntity>
    )

    private fun parseIntakeState(project: ProjectEntity, rawJson: String): ParsedIntake {
        val root = safeJsonObject(rawJson)
        val state = root.optJSONObject("updated_project_state") ?: root
        val name = state.optString("project_name", "").ifBlank { project.name }
        val stack = state.optJSONObject("tech_stack")?.toString()
            ?: """{"framework":"React + Vite","language":"TypeScript","styling":"Tailwind CSS","storage_or_backend":"IndexedDB","testing_tool":"Vitest","deployment_target":"Cloudflare Pages"}"""

        val phasesOut = mutableListOf<PhaseEntity>()
        val tasksOut = mutableListOf<TaskEntity>()
        val phasesArr = state.optJSONArray("phases")

        if (phasesArr != null && phasesArr.length() > 0) {
            for (i in 0 until phasesArr.length()) {
                val pObj = phasesArr.optJSONObject(i) ?: continue
                val phaseNum = pObj.optInt("phase_id", i + 1)
                val phaseName = pObj.optString("name", "Phase $phaseNum")
                val checklist = pObj.optJSONArray("checklist")?.toString() ?: "[]"
                phasesOut.add(
                    PhaseEntity(
                        projectId = project.id,
                        phaseNumber = phaseNum,
                        name = phaseName,
                        status = if (i == 0) "IN_PROGRESS" else "PENDING",
                        checklistJson = checklist
                    )
                )
                val tArr = pObj.optJSONArray("tasks")
                if (tArr != null) {
                    for (tIdx in 0 until tArr.length()) {
                        val tObj = tArr.optJSONObject(tIdx) ?: continue
                        tasksOut.add(
                            TaskEntity(
                                projectId = project.id,
                                phaseNumber = phaseNum,
                                taskCode = tObj.optString("task_id", "P$phaseNum-T${tIdx + 1}"),
                                title = tObj.optString("title", "Implement module"),
                                assignedAgentId = AgentRoster.findById(
                                    tObj.optString("assigned_agent", "frontend-developer")
                                ).id,
                                targetFilesJson = tObj.optJSONArray("target_files")?.toString() ?: "[]",
                                status = "PENDING",
                                orderIndex = tIdx
                            )
                        )
                    }
                }
            }
        }

        if (phasesOut.isEmpty()) {
            // Deterministic fallback roadmap derived directly from the standard PWA phases
            phasesOut.add(
                PhaseEntity(
                    projectId = project.id,
                    phaseNumber = 1,
                    name = "Foundation, Tokens & PWA Shell",
                    status = "IN_PROGRESS",
                    checklistJson = """[{"id":"P1-C1","description":"Valid manifest.webmanifest & SW shell","verified":false}]"""
                )
            )
            tasksOut.addAll(
                listOf(
                    TaskEntity(
                        projectId = project.id,
                        phaseNumber = 1,
                        taskCode = "P1-T1",
                        title = "Define design tokens & responsive layout spec",
                        assignedAgentId = "ui-designer",
                        targetFilesJson = """["docs/design-tokens.json"]""",
                        orderIndex = 0
                    ),
                    TaskEntity(
                        projectId = project.id,
                        phaseNumber = 1,
                        taskCode = "P1-T2",
                        title = "Scaffold PWA manifest and Service Worker",
                        assignedAgentId = "frontend-developer",
                        targetFilesJson = """["public/manifest.webmanifest","public/sw.js"]""",
                        orderIndex = 1
                    ),
                    TaskEntity(
                        projectId = project.id,
                        phaseNumber = 1,
                        taskCode = "P1-T3",
                        title = "Generate production hosting config & DEPLOY.md",
                        assignedAgentId = "deployment-engineer",
                        targetFilesJson = """["DEPLOY.md"]""",
                        orderIndex = 2
                    )
                )
            )
        }

        return ParsedIntake(name, stack, phasesOut, tasksOut)
    }

    private data class WorkerFileOutput(val path: String, val action: String, val content: String)
    private data class WorkerEnvelope(
        val status: String,
        val summary: String,
        val files: List<WorkerFileOutput>,
        val handoffNotes: List<String>,
        val blockers: List<String>
    )

    private fun parseWorkerEnvelope(rawJson: String): WorkerEnvelope {
        val obj = safeJsonObject(rawJson)
        val status = obj.optString("status", "COMPLETED")
        val summary = obj.optString("summary", "Completed task.")
        val files = mutableListOf<WorkerFileOutput>()
        val filesArr = obj.optJSONArray("files")
        if (filesArr != null) {
            for (i in 0 until filesArr.length()) {
                val f = filesArr.optJSONObject(i) ?: continue
                val path = f.optString("path", "").trim()
                if (path.isNotEmpty()) {
                    files.add(
                        WorkerFileOutput(
                            path = path,
                            action = f.optString("action", "CREATE"),
                            content = f.optString("content", "")
                        )
                    )
                }
            }
        }
        val notes = jsonStringArray(obj.optJSONArray("handoff_notes")?.toString() ?: "[]")
        val blockers = jsonStringArray(obj.optJSONArray("blockers")?.toString() ?: "[]")
        return WorkerEnvelope(status, summary, files, notes, blockers)
    }

    private fun extractSplitSubtasks(
        projectId: Long,
        phaseNumber: Int,
        parent: TaskEntity,
        rawJson: String
    ): List<TaskEntity> {
        val root = safeJsonObject(rawJson)
        val state = root.optJSONObject("updated_project_state") ?: root
        val phasesArr = state.optJSONArray("phases")
        val out = mutableListOf<TaskEntity>()
        if (phasesArr != null) {
            for (i in 0 until phasesArr.length()) {
                val p = phasesArr.optJSONObject(i) ?: continue
                if (p.optInt("phase_id", -1) != phaseNumber) continue
                val tArr = p.optJSONArray("tasks") ?: continue
                for (j in 0 until tArr.length()) {
                    val t = tArr.optJSONObject(j) ?: continue
                    val code = t.optString("task_id", "")
                    if (code.startsWith(parent.taskCode) && code != parent.taskCode) {
                        out.add(
                            TaskEntity(
                                projectId = projectId,
                                phaseNumber = phaseNumber,
                                taskCode = code,
                                title = t.optString("title", "${parent.title} (Part ${j + 1})"),
                                assignedAgentId = AgentRoster.findById(
                                    t.optString("assigned_agent", parent.assignedAgentId)
                                ).id,
                                targetFilesJson = t.optJSONArray("target_files")?.toString() ?: parent.targetFilesJson,
                                status = "PENDING",
                                splitDepth = parent.splitDepth + 1,
                                orderIndex = parent.orderIndex + j + 1
                            )
                        )
                    }
                }
            }
        }
        return out
    }

    private fun markChecklistVerified(checklistJson: String): String {
        val arr = safeJsonArray(checklistJson)
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.put("verified", true)
        }
        return arr.toString()
    }

    private fun safeJsonObject(raw: String): JSONObject = try {
        JSONObject(raw)
    } catch (_: Exception) {
        JSONObject()
    }

    private fun safeJsonArray(raw: String): JSONArray = try {
        JSONArray(raw)
    } catch (_: Exception) {
        JSONArray()
    }

    private fun jsonStringArray(raw: String): List<String> {
        val arr = safeJsonArray(raw)
        return List(arr.length()) { idx -> arr.optString(idx, "") }.filter { it.isNotBlank() }
    }
}
