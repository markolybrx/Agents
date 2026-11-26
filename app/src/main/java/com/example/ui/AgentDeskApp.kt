package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.components.CriticalApprovalSheet
import com.example.ui.components.ExportProjectDialog
import com.example.ui.components.LiveOfficeDrawerPill
import com.example.ui.components.OfficeDrawerModalSheet
import com.example.ui.components.OfficeStudioPanelContent
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.KeysScreen
import com.example.ui.screens.PlansScreen
import com.example.ui.screens.WorkspaceScreen

private object AgencyRoutes {
    const val DASHBOARD = "dashboard"
    const val PLANS = "plans"
    const val WORKSPACE = "workspace"
    const val KEYS = "keys"
}

private data class NavTabItem(
    val route: String,
    val labelRes: Int,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
    val testTag: String
)

private val agencyNavItems = listOf(
    NavTabItem(AgencyRoutes.DASHBOARD, R.string.nav_dashboard, Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_dashboard"),
    NavTabItem(AgencyRoutes.PLANS, R.string.nav_plans, Icons.Filled.Description, Icons.Outlined.Description, "nav_plans"),
    NavTabItem(AgencyRoutes.WORKSPACE, R.string.nav_workspace, Icons.Filled.Code, Icons.Outlined.Code, "nav_workspace"),
    NavTabItem(AgencyRoutes.KEYS, R.string.nav_keys, Icons.Filled.Key, Icons.Outlined.Key, "nav_keys")
)

@Composable
fun AgentDeskApp(
    viewModel: AgencyViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val keyStates by viewModel.keyRuntimeStates.collectAsStateWithLifecycle()
    val officeState by viewModel.officeState.collectAsStateWithLifecycle()
    val criticalApproval by viewModel.criticalApproval.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val selectedProjectId by viewModel.selectedProjectId.collectAsStateWithLifecycle()
    val phases by viewModel.selectedProjectPhases.collectAsStateWithLifecycle()
    val tasks by viewModel.selectedProjectTasks.collectAsStateWithLifecycle()
    val files by viewModel.selectedProjectFiles.collectAsStateWithLifecycle()
    val tokenUsage by viewModel.recentTokenUsage.collectAsStateWithLifecycle()
    val logs by viewModel.allLogs.collectAsStateWithLifecycle()
    val isOfficeOpen by viewModel.isOfficeDrawerOpen.collectAsStateWithLifecycle()
    val selectedOfficeAgent by viewModel.selectedOfficeAgentId.collectAsStateWithLifecycle()
    val keyTestStatuses by viewModel.keyTestStatuses.collectAsStateWithLifecycle()
    val isGeneratingBlueprint by viewModel.isGeneratingBlueprint.collectAsStateWithLifecycle()
    val exportUiState by viewModel.exportUiState.collectAsStateWithLifecycle()

    val selectedProject = projects.firstOrNull { it.id == selectedProjectId }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AgencyRoutes.DASHBOARD

    LaunchedEffect(Unit) {
        viewModel.toastMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isExpanded = maxWidth >= 840.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        LiveOfficeDrawerPill(
                            officeState = officeState,
                            onOpenDrawer = { viewModel.setOfficeDrawerOpen(true) },
                            onTogglePause = { viewModel.pauseActiveRun() }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.navigationBarsPadding()
                        ) {
                            agencyNavItems.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.filledIcon else item.outlinedIcon,
                                            contentDescription = stringResource(item.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(item.labelRes)) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpanded) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        agencyNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.filledIcon else item.outlinedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = AgencyRoutes.DASHBOARD,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(AgencyRoutes.DASHBOARD) {
                            DashboardScreen(
                                settings = settings,
                                keyStates = keyStates,
                                officeState = officeState,
                                selectedProject = selectedProject,
                                tasks = tasks,
                                tokenUsage = tokenUsage,
                                logs = logs,
                                onOpenOfficeDrawer = { viewModel.setOfficeDrawerOpen(true) },
                                onNavigateToKeys = { navigateTo(AgencyRoutes.KEYS) },
                                onNavigateToPlans = { navigateTo(AgencyRoutes.PLANS) },
                                onStartProject = { viewModel.startProjectRun(it) },
                                onPauseRun = { viewModel.pauseActiveRun() },
                                onExportProject = { viewModel.openExportProjectDialog(it) }
                            )
                        }
                        composable(AgencyRoutes.PLANS) {
                            PlansScreen(
                                projects = projects,
                                selectedProjectId = selectedProjectId,
                                phases = phases,
                                tasks = tasks,
                                officeState = officeState,
                                isGeneratingBlueprint = isGeneratingBlueprint,
                                onImportMarkdownUris = { uris -> viewModel.importMarkdownUris(context, uris) },
                                onCreateMarkdownProject = { t, f, md -> viewModel.createProjectFromMarkdown(t, f, md) },
                                onLoadTemplate = { viewModel.loadDefaultTemplateProject() },
                                onGenerateBlueprint = { idea, onDone -> viewModel.generateBlueprintWithPlanner(idea) { onDone(it) } },
                                onSelectProject = { viewModel.selectProject(it) },
                                onStartProject = { viewModel.startProjectRun(it) },
                                onPauseRun = { viewModel.pauseActiveRun() },
                                onResetProject = { viewModel.resetProjectRun(it) },
                                onDeleteProject = { viewModel.deleteProject(it) },
                                onExportProject = { viewModel.openExportProjectDialog(it) }
                            )
                        }
                        composable(AgencyRoutes.WORKSPACE) {
                            WorkspaceScreen(
                                selectedProject = selectedProject,
                                generatedFiles = files,
                                onOpenExportProject = { viewModel.openExportProjectDialog() },
                                onShowMessage = { msg -> viewModel.showMessage(msg) }
                            )
                        }
                        composable(AgencyRoutes.KEYS) {
                            KeysScreen(
                                settings = settings,
                                keyStates = keyStates,
                                keyTestStatuses = keyTestStatuses,
                                onSaveKeySlot = { idx, label, key, enabled ->
                                    viewModel.saveApiKeySlot(idx, label, key, enabled)
                                },
                                onToggleSlotEnabled = { idx, enabled ->
                                    viewModel.toggleKeySlotEnabled(idx, enabled)
                                },
                                onTestKeySlot = { viewModel.testKeySlot(it) },
                                onClearCooldown = { viewModel.clearKeyCooldown(it) },
                                onSelectModel = { viewModel.selectActiveModel(it) },
                                onSetThinkingEnabled = { viewModel.setThinkingEnabled(it) }
                            )
                        }
                    }
                }

                // Expanded Tablet / Foldable Supporting Pane: Persistent Isometric Office Studio
                if (isExpanded) {
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(
                        modifier = Modifier
                            .width(420.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        OfficeStudioPanelContent(
                            officeState = officeState,
                            keyStates = keyStates,
                            allLogs = logs,
                            allTokenUsage = tokenUsage,
                            selectedAgentId = selectedOfficeAgent,
                            onSelectAgent = { viewModel.selectOfficeAgent(it) },
                            onShowMessage = { viewModel.showMessage(it) },
                            onClose = {},
                            onStartOrResume = { selectedProject?.let { viewModel.startProjectRun(it.id) } },
                            onPause = { viewModel.pauseActiveRun() },
                            canStart = selectedProject != null,
                            showCloseButton = false
                        )
                    }
                }
            }
        }

        // Animated Isometric Office Drawer Modal Sheet on Compact / Medium Screens
        if (isOfficeOpen && !isExpanded) {
            OfficeDrawerModalSheet(
                officeState = officeState,
                keyStates = keyStates,
                allLogs = logs,
                allTokenUsage = tokenUsage,
                selectedAgentId = selectedOfficeAgent,
                onSelectAgent = { viewModel.selectOfficeAgent(it) },
                onShowMessage = { viewModel.showMessage(it) },
                onDismiss = { viewModel.setOfficeDrawerOpen(false) },
                onStartOrResume = { selectedProject?.let { viewModel.startProjectRun(it.id) } },
                onPause = { viewModel.pauseActiveRun() },
                canStart = selectedProject != null
            )
        }

        // Global Manual Approval Gate on Critical Issues
        criticalApproval?.let { req ->
            CriticalApprovalSheet(
                request = req,
                onResolve = { action, guidance -> viewModel.resolveCriticalGate(action, guidance) }
            )
        }

        // Global Export Project Local ZIP Archive Dialog
        if (exportUiState.isOpen) {
            ExportProjectDialog(
                uiState = exportUiState,
                onUpdateOptions = { viewModel.updateExportOptions(it) },
                onSaveToUri = { uri -> viewModel.exportProjectToUri(context, uri) },
                onQuickDownload = { viewModel.quickDownloadProjectZip(context) },
                onDismiss = { viewModel.dismissExportProjectDialog() }
            )
        }
    }
}
