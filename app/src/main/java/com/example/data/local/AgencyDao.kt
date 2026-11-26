package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AgencyDao {

    // Projects
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Long)

    // Phases
    @Query("SELECT * FROM phases WHERE projectId = :projectId ORDER BY phaseNumber ASC")
    fun observePhases(projectId: Long): Flow<List<PhaseEntity>>

    @Query("SELECT * FROM phases WHERE projectId = :projectId ORDER BY phaseNumber ASC")
    suspend fun getPhasesForProject(projectId: Long): List<PhaseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhases(phases: List<PhaseEntity>)

    @Update
    suspend fun updatePhase(phase: PhaseEntity)

    @Query("DELETE FROM phases WHERE projectId = :projectId")
    suspend fun deletePhasesForProject(projectId: Long)

    // Tasks
    @Query("SELECT * FROM tasks WHERE projectId = :projectId ORDER BY phaseNumber ASC, orderIndex ASC, id ASC")
    fun observeTasks(projectId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE projectId = :projectId ORDER BY phaseNumber ASC, orderIndex ASC, id ASC")
    suspend fun getTasksForProject(projectId: Long): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE projectId = :projectId")
    suspend fun deleteTasksForProject(projectId: Long)

    // Generated Files
    @Query("SELECT * FROM generated_files WHERE projectId = :projectId ORDER BY path ASC")
    fun observeFiles(projectId: Long): Flow<List<GeneratedFileEntity>>

    @Query("SELECT * FROM generated_files WHERE projectId = :projectId ORDER BY path ASC")
    suspend fun getFilesForProject(projectId: Long): List<GeneratedFileEntity>

    @Query("SELECT * FROM generated_files WHERE projectId = :projectId AND path = :path LIMIT 1")
    suspend fun getFileByPath(projectId: Long, path: String): GeneratedFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: GeneratedFileEntity): Long

    @Query("DELETE FROM generated_files WHERE projectId = :projectId AND path = :path")
    suspend fun deleteFileByPath(projectId: Long, path: String)

    @Query("DELETE FROM generated_files WHERE projectId = :projectId")
    suspend fun deleteFilesForProject(projectId: Long)

    // Token Usage Telemetry
    @Query("SELECT * FROM token_usage_events ORDER BY timestamp DESC LIMIT 200")
    fun observeRecentTokenUsage(): Flow<List<TokenUsageEntity>>

    @Query("SELECT * FROM token_usage_events WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun observeProjectTokenUsage(projectId: Long): Flow<List<TokenUsageEntity>>

    @Query("SELECT * FROM token_usage_events WHERE projectId = :projectId ORDER BY timestamp ASC")
    suspend fun getTokenUsageForProject(projectId: Long): List<TokenUsageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTokenUsage(event: TokenUsageEntity)

    // Run Logs
    @Query("SELECT * FROM run_logs WHERE projectId = :projectId ORDER BY timestamp DESC LIMIT 120")
    fun observeProjectLogs(projectId: Long): Flow<List<RunLogEntity>>

    @Query("SELECT * FROM run_logs WHERE projectId = :projectId ORDER BY timestamp ASC")
    suspend fun getLogsForProject(projectId: Long): List<RunLogEntity>

    @Query("SELECT * FROM run_logs ORDER BY timestamp DESC LIMIT 120")
    fun observeAllLogs(): Flow<List<RunLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: RunLogEntity)

    @Query("DELETE FROM run_logs WHERE projectId = :projectId")
    suspend fun deleteLogsForProject(projectId: Long)
}
