package com.example.data.local

import kotlinx.coroutines.flow.Flow

class AgencyRepository(private val dao: AgencyDao) {

    val allProjects: Flow<List<ProjectEntity>> = dao.observeAllProjects()
    val recentTokenUsage: Flow<List<TokenUsageEntity>> = dao.observeRecentTokenUsage()
    val allLogs: Flow<List<RunLogEntity>> = dao.observeAllLogs()

    fun observePhases(projectId: Long): Flow<List<PhaseEntity>> = dao.observePhases(projectId)
    fun observeTasks(projectId: Long): Flow<List<TaskEntity>> = dao.observeTasks(projectId)
    fun observeFiles(projectId: Long): Flow<List<GeneratedFileEntity>> = dao.observeFiles(projectId)
    fun observeProjectLogs(projectId: Long): Flow<List<RunLogEntity>> = dao.observeProjectLogs(projectId)
    fun observeProjectTokenUsage(projectId: Long): Flow<List<TokenUsageEntity>> = dao.observeProjectTokenUsage(projectId)

    suspend fun getProject(projectId: Long): ProjectEntity? = dao.getProjectById(projectId)
    suspend fun getPhases(projectId: Long): List<PhaseEntity> = dao.getPhasesForProject(projectId)
    suspend fun getTasks(projectId: Long): List<TaskEntity> = dao.getTasksForProject(projectId)
    suspend fun getFiles(projectId: Long): List<GeneratedFileEntity> = dao.getFilesForProject(projectId)
    suspend fun getProjectTokenUsage(projectId: Long): List<TokenUsageEntity> = dao.getTokenUsageForProject(projectId)
    suspend fun getProjectLogs(projectId: Long): List<RunLogEntity> = dao.getLogsForProject(projectId)

    suspend fun createProject(
        name: String,
        fileName: String,
        markdown: String,
        modelId: String
    ): Long {
        return dao.insertProject(
            ProjectEntity(
                name = name,
                blueprintFileName = fileName,
                blueprintMarkdown = markdown,
                activeModelId = modelId
            )
        )
    }

    suspend fun updateProject(project: ProjectEntity) {
        dao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(projectId: Long) {
        dao.deletePhasesForProject(projectId)
        dao.deleteTasksForProject(projectId)
        dao.deleteFilesForProject(projectId)
        dao.deleteLogsForProject(projectId)
        dao.deleteProjectById(projectId)
    }

    suspend fun replacePhasesAndTasks(
        projectId: Long,
        phases: List<PhaseEntity>,
        tasks: List<TaskEntity>
    ) {
        dao.deletePhasesForProject(projectId)
        dao.deleteTasksForProject(projectId)
        dao.insertPhases(phases)
        dao.insertTasks(tasks)
    }

    suspend fun updatePhase(phase: PhaseEntity) = dao.updatePhase(phase)
    suspend fun updateTask(task: TaskEntity) = dao.updateTask(task)
    suspend fun insertTasks(tasks: List<TaskEntity>) = dao.insertTasks(tasks)

    suspend fun upsertGeneratedFile(
        projectId: Long,
        path: String,
        content: String,
        agentId: String
    ) {
        val existing = dao.getFileByPath(projectId, path)
        dao.insertFile(
            GeneratedFileEntity(
                id = existing?.id ?: 0,
                projectId = projectId,
                path = path,
                content = content,
                lastAgentId = agentId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteGeneratedFile(projectId: Long, path: String) {
        dao.deleteFileByPath(projectId, path)
    }

    suspend fun resetProjectExecution(projectId: Long) {
        val project = dao.getProjectById(projectId) ?: return
        dao.deletePhasesForProject(projectId)
        dao.deleteTasksForProject(projectId)
        dao.deleteFilesForProject(projectId)
        dao.deleteLogsForProject(projectId)
        dao.updateProject(
            project.copy(
                status = "IDLE",
                currentPhaseId = 1,
                techStackJson = "{}",
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordTokenUsage(event: TokenUsageEntity) = dao.insertTokenUsage(event)

    suspend fun log(
        projectId: Long,
        agentId: String,
        keySlotIndex: Int,
        level: String,
        message: String
    ) {
        dao.insertLog(
            RunLogEntity(
                projectId = projectId,
                agentId = agentId,
                keySlotIndex = keySlotIndex,
                level = level,
                message = message
            )
        )
    }
}
