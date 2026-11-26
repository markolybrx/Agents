package com.example.domain

import com.example.data.prompts.AgentRoster
import com.example.data.prompts.ThinkingLevel

enum class AgentWorkStatus {
    IDLE,
    THINKING,
    WRITING,
    AUDITING,
    BLOCKED,
    DONE
}

data class AgentLiveState(
    val agentId: String,
    val status: AgentWorkStatus = AgentWorkStatus.IDLE,
    val speechBubble: String = "",
    val activeTaskTitle: String = "",
    val tokensUsedSession: Int = 0,
    val lastHandoffSummary: String = ""
)

data class OfficeLiveState(
    val isRunning: Boolean = false,
    val activeProjectId: Long? = null,
    val activeAgentId: String? = null,
    val activeKeySlot: Int? = null, // 1..4
    val activeModelId: String = "gemini-3.1-flash-lite-preview",
    val activeThinkingLevel: ThinkingLevel = ThinkingLevel.MEDIUM,
    val currentPhaseLabel: String = "Standby",
    val statusHeadline: String = "Studio ready — upload a .md plan to launch",
    val agents: Map<String, AgentLiveState> = AgentRoster.agents.associate {
        it.id to AgentLiveState(agentId = it.id)
    }
)

enum class CriticalAction {
    APPROVE_CONTINUE,
    RETRY_WITH_GUIDANCE,
    FORCE_SPLIT_TASK,
    KEEP_PAUSED
}

data class CriticalApprovalRequest(
    val projectId: Long,
    val taskId: Long?,
    val taskCode: String,
    val taskTitle: String,
    val agentId: String,
    val reasonTitle: String,
    val details: String,
    val blockers: List<String>,
    val retryCount: Int,
    val splitDepth: Int
)

data class ChecklistItemUi(
    val id: String,
    val description: String,
    val verified: Boolean
)
