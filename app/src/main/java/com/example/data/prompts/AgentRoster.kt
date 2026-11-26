package com.example.data.prompts

enum class StudioDepartment(
    val label: String,
    val zoneTitle: String,
    val folderKey: String
) {
    CONTROL("Control", "Executive War Room", "00-control"),
    DESIGN("Design", "Design Studio", "01-design"),
    BUILD("Build", "Engineering Bay", "02-build"),
    AUDIT_QA("Audit & QA", "QA & Debug Lab", "03-audit-test-repair"),
    SHIP("Ship", "Release Dock", "04-ship")
}

enum class ThinkingLevel(val apiValue: String, val label: String) {
    LOW("low", "thinking: low"),
    MEDIUM("medium", "thinking: medium"),
    HIGH("high", "thinking: high")
}

data class WorkerAppearance(
    val skinToneHex: Long,
    val hairHex: Long,
    val shirtHex: Long,
    val trousersHex: Long,
    val hasGlasses: Boolean = false
)

data class AgentProfile(
    val id: String,
    val numberCode: String,
    val displayName: String,
    val shortName: String,
    val department: StudioDepartment,
    val thinkingLevel: ThinkingLevel,
    val promptFileId: String,
    val roleSummary: String,
    // Normalized architectural floorplan desk coordinates (0f..1f)
    val studioX: Float,
    val studioY: Float,
    val appearance: WorkerAppearance
)

data class PromptDocument(
    val id: String,
    val folder: String,
    val folderBadge: String,
    val filename: String,
    val title: String,
    val skill: String,
    val thinkingLevel: ThinkingLevel,
    val description: String,
    val content: String
) {
    val fullPath: String
        get() = if (folder == "root") filename else "$folder/$filename"

    val approxTokens: Int
        get() = (content.length + 3) / 4

    val lineCount: Int
        get() = content.count { it == '\n' } + 1
}

object AgentRoster {
    val agents: List<AgentProfile> = listOf(
        // Executive War Room (Top-Left Wing)
        AgentProfile(
            id = "master-orchestrator",
            numberCode = "01",
            displayName = "Master Orchestrator",
            shortName = "Orchestrator",
            department = StudioDepartment.CONTROL,
            thinkingLevel = ThinkingLevel.HIGH,
            promptFileId = "01-master-orchestrator",
            roleSummary = "Agency head: intake, task routing, phase gates, autonomous recovery.",
            studioX = 0.24f,
            studioY = 0.24f,
            appearance = WorkerAppearance(0xFFE0B494, 0xFF2B2420, 0xFF2B3440, 0xFF1F2226, hasGlasses = true)
        ),
        AgentProfile(
            id = "project-planner",
            numberCode = "00",
            displayName = "Project Planner",
            shortName = "Planner",
            department = StudioDepartment.CONTROL,
            thinkingLevel = ThinkingLevel.HIGH,
            promptFileId = "00-project-planner-skill",
            roleSummary = "Pre-agency skill: turns rough ideas into Orchestrator-ready .md blueprints.",
            studioX = 0.43f,
            studioY = 0.23f,
            appearance = WorkerAppearance(0xFFC68E68, 0xFF1E1A18, 0xFF8C4A32, 0xFF2B2B2B)
        ),

        // Design Studio (Top-Right Wing)
        AgentProfile(
            id = "ui-designer",
            numberCode = "03",
            displayName = "UI Designer",
            shortName = "UI Design",
            department = StudioDepartment.DESIGN,
            thinkingLevel = ThinkingLevel.MEDIUM,
            promptFileId = "03-ui-designer",
            roleSummary = "Design tokens, responsive mobile-first layouts, PWA visual specs.",
            studioX = 0.68f,
            studioY = 0.24f,
            appearance = WorkerAppearance(0xFFF1C7A8, 0xFF5A3724, 0xFFD96B43, 0xFF33312E)
        ),
        AgentProfile(
            id = "api-designer",
            numberCode = "10",
            displayName = "API Designer",
            shortName = "API Spec",
            department = StudioDepartment.DESIGN,
            thinkingLevel = ThinkingLevel.MEDIUM,
            promptFileId = "10-api-designer",
            roleSummary = "REST/GraphQL/IndexedDB schemas and offline sync contracts.",
            studioX = 0.85f,
            studioY = 0.29f,
            appearance = WorkerAppearance(0xFF9C6B4B, 0xFF1C1917, 0xFF7D5C7E, 0xFF252629, hasGlasses = true)
        ),

        // Engineering Bay (Center-Left Open Bullpen — 3 desks)
        AgentProfile(
            id = "frontend-developer",
            numberCode = "04",
            displayName = "Frontend Developer",
            shortName = "Frontend",
            department = StudioDepartment.BUILD,
            thinkingLevel = ThinkingLevel.MEDIUM,
            promptFileId = "04-frontend-developer",
            roleSummary = "UI views, manifest.webmanifest, Service Worker offline caching.",
            studioX = 0.16f,
            studioY = 0.48f,
            appearance = WorkerAppearance(0xFFE7BA96, 0xFF3E2B1E, 0xFF3D6B82, 0xFF262A30)
        ),
        AgentProfile(
            id = "dynamic-stack-specialist",
            numberCode = "05",
            displayName = "Dynamic Stack Specialist",
            shortName = "Stack Spec",
            department = StudioDepartment.BUILD,
            thinkingLevel = ThinkingLevel.MEDIUM,
            promptFileId = "05-dynamic-stack-specialist",
            roleSummary = "Adapts to locked tech_stack (Vanilla, React+Vite, Next.js, SvelteKit).",
            studioX = 0.36f,
            studioY = 0.49f,
            appearance = WorkerAppearance(0xFFB57C58, 0xFF1B1918, 0xFF476A5B, 0xFF222424, hasGlasses = true)
        ),
        AgentProfile(
            id = "backend-developer",
            numberCode = "06",
            displayName = "Backend & Data Dev",
            shortName = "Backend",
            department = StudioDepartment.BUILD,
            thinkingLevel = ThinkingLevel.MEDIUM,
            promptFileId = "06-backend-developer",
            roleSummary = "API routes, IndexedDB/storage repositories, auth & sync handlers.",
            studioX = 0.56f,
            studioY = 0.48f,
            appearance = WorkerAppearance(0xFFD8A47F, 0xFF2D2520, 0xFF4F5D6B, 0xFF232528)
        ),

        // QA & Debug Lab (Lower Architectural Wing — 5 desks)
        AgentProfile(
            id = "code-reviewer",
            numberCode = "07",
            displayName = "Code Reviewer",
            shortName = "Reviewer",
            department = StudioDepartment.AUDIT_QA,
            thinkingLevel = ThinkingLevel.HIGH,
            promptFileId = "07-code-reviewer",
            roleSummary = "Diff auditor for logic bugs, security flaws, and PWA compliance.",
            studioX = 0.17f,
            studioY = 0.72f,
            appearance = WorkerAppearance(0xFFF0C6A6, 0xFF4B3D32, 0xFFE5DFD3, 0xFF2F2D2A, hasGlasses = true)
        ),
        AgentProfile(
            id = "qa-tester",
            numberCode = "08",
            displayName = "QA / Tester",
            shortName = "QA Tester",
            department = StudioDepartment.AUDIT_QA,
            thinkingLevel = ThinkingLevel.MEDIUM,
            promptFileId = "08-qa-tester",
            roleSummary = "Writes automated unit/integration/PWA tests and audits results.",
            studioX = 0.36f,
            studioY = 0.73f,
            appearance = WorkerAppearance(0xFFC9906A, 0xFF231D1A, 0xFFC7933B, 0xFF2B2825)
        ),
        AgentProfile(
            id = "debugger",
            numberCode = "09",
            displayName = "Debugger Agent",
            shortName = "Debugger",
            department = StudioDepartment.AUDIT_QA,
            thinkingLevel = ThinkingLevel.HIGH,
            promptFileId = "09-debugger",
            roleSummary = "Diagnoses build failures, runtime crashes, and reviewer blockers.",
            studioX = 0.56f,
            studioY = 0.72f,
            appearance = WorkerAppearance(0xFF8D5E41, 0xFF171514, 0xFFA84A44, 0xFF242424)
        ),
        AgentProfile(
            id = "accessibility-tester",
            numberCode = "11",
            displayName = "Accessibility Tester",
            shortName = "A11y Audit",
            department = StudioDepartment.AUDIT_QA,
            thinkingLevel = ThinkingLevel.LOW,
            promptFileId = "11-accessibility-tester",
            roleSummary = "WCAG 2.1 AA semantics, keyboard navigation, contrast & focus.",
            studioX = 0.76f,
            studioY = 0.65f,
            appearance = WorkerAppearance(0xFFE4B792, 0xFF6B4226, 0xFF5A806B, 0xFF2A2B2D)
        ),
        AgentProfile(
            id = "performance-engineer",
            numberCode = "12",
            displayName = "Performance Engineer",
            shortName = "Perf Eng",
            department = StudioDepartment.AUDIT_QA,
            thinkingLevel = ThinkingLevel.MEDIUM,
            promptFileId = "12-performance-engineer",
            roleSummary = "Optimizes Core Web Vitals, SW caching, and bundle splitting.",
            studioX = 0.85f,
            studioY = 0.81f,
            appearance = WorkerAppearance(0xFFD09C77, 0xFF221F1D, 0xFF6A7D8C, 0xFF262626, hasGlasses = true)
        ),

        // Release Dock (Bottom Center-Left)
        AgentProfile(
            id = "deployment-engineer",
            numberCode = "13",
            displayName = "Deployment Engineer",
            shortName = "Deploy Eng",
            department = StudioDepartment.SHIP,
            thinkingLevel = ThinkingLevel.LOW,
            promptFileId = "13-deployment-engineer",
            roleSummary = "Hosting configs, SW no-cache headers, HTTPS & release docs.",
            studioX = 0.44f,
            studioY = 0.89f,
            appearance = WorkerAppearance(0xFFB07855, 0xFF1F1C1A, 0xFF3E7857, 0xFF232524)
        )
    )

    private val byId = agents.associateBy { it.id }

    fun findById(agentId: String): AgentProfile {
        val normalized = agentId.trim().lowercase()
        return byId[normalized]
            ?: agents.firstOrNull { normalized.contains(it.id) || it.id.contains(normalized) }
            ?: agents.first()
    }
}
