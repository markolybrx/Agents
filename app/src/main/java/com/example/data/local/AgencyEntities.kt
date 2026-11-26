package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val blueprintFileName: String,
    val blueprintMarkdown: String,
    val techStackJson: String = "{}",
    val currentPhaseId: Int = 1,
    val status: String = "IDLE", // IDLE, RUNNING, PAUSED_CRITICAL, PAUSED_USER, COMPLETED
    val activeModelId: String = "gemini-3.1-flash-lite-preview",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "phases")
data class PhaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val phaseNumber: Int,
    val name: String,
    val status: String = "PENDING", // PENDING, IN_PROGRESS, PASSED, BLOCKED
    val checklistJson: String = "[]"
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val phaseNumber: Int,
    val taskCode: String,
    val title: String,
    val assignedAgentId: String,
    val targetFilesJson: String = "[]",
    val status: String = "PENDING", // PENDING, RUNNING, CODED, REVIEWED, DONE, BLOCKED, FAILED
    val retryCount: Int = 0,
    val splitDepth: Int = 0,
    val handoffNotes: String = "",
    val lastError: String = "",
    val orderIndex: Int = 0
)

@Entity(tableName = "generated_files")
data class GeneratedFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val path: String,
    val content: String,
    val lastAgentId: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "token_usage_events")
data class TokenUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val keySlotIndex: Int, // 1..4
    val agentId: String,
    val modelId: String,
    val thinkingLevel: String,
    val promptTokens: Int,
    val outputTokens: Int,
    val thoughtTokens: Int,
    val totalTokens: Int,
    val latencyMs: Long,
    val httpStatus: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "run_logs")
data class RunLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val agentId: String,
    val keySlotIndex: Int,
    val level: String, // INFO, SUCCESS, WARN, CRITICAL
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
