package com.bytesmith.scriptler.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bytesmith.scriptler.models.Script
import com.bytesmith.scriptler.ui.components.*
import com.bytesmith.scriptler.ui.screens.*
import com.bytesmith.scriptler.ui.theme.*
import com.bytesmith.scriptler.ui.viewmodel.*

@Composable
fun ScriptlerNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Scripts.route
) {
    @Suppress("UNUSED_VARIABLE")
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    // Initialize ViewModels
    val scriptsListViewModel: ScriptsListViewModel = viewModel()
    val scriptDetailsViewModel: ScriptDetailsViewModel = viewModel()
    val scriptEditorViewModel: ScriptEditorViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val packageManagerViewModel: PackageManagerViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
        }
    ) {
        composable(Screen.Scripts.route) {
            val uiState by scriptsListViewModel.uiState.collectAsState()

            ScriptsListScreenCompose(
                scripts = uiState.scripts,
                isLoading = uiState.isLoading,
                isCreating = uiState.isCreating,
                isDeleting = uiState.isDeleting,
                isRenaming = uiState.isRenaming,
                error = uiState.error,
                onCreateScript = { name, language ->
                    scriptsListViewModel.createScript(name, language)
                },
                onScriptClick = { script ->
                    navController.navigate("script_details/${script.id}")
                },
                onScriptToggle = { script ->
                    scriptsListViewModel.toggleScriptActive(script)
                },
                onScriptEdit = { script ->
                    navController.navigate("script_editor/${script.id}")
                },
                onScriptDelete = { script ->
                    scriptsListViewModel.deleteScript(script)
                },
                onScriptRename = { script, newName ->
                    scriptsListViewModel.renameScript(script, newName)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToPackages = {
                    navController.navigate(Screen.Packages.route)
                },
                onClearError = {
                    scriptsListViewModel.clearError()
                }
            )
        }

        composable(Screen.Packages.route) {
            val uiState by packageManagerViewModel.uiState.collectAsState()

            PackageManagerScreenCompose(
                prebundledPackages = uiState.prebundledPackages,
                runtimePackages = uiState.runtimePackages,
                searchResults = uiState.searchResults,
                packageCount = uiState.packageCount,
                packageSize = uiState.packageSize,
                isLoading = uiState.isLoading,
                isSearching = uiState.isSearching,
                isInstalling = uiState.isInstalling,
                isUninstalling = uiState.isUninstalling,
                error = uiState.error,
                onSearch = { query ->
                    packageManagerViewModel.searchPackages(query)
                },
                onInstallPackage = { packageName ->
                    packageManagerViewModel.installPackage(packageName)
                },
                onUninstallPackage = { packageName ->
                    packageManagerViewModel.uninstallPackage(packageName)
                },
                onNavigateToScripts = {
                    navController.popBackStack()
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onRefresh = {
                    packageManagerViewModel.loadPackages()
                },
                onClearError = {
                    packageManagerViewModel.clearError()
                },
                onClearSearchResults = {
                    packageManagerViewModel.clearSearchResults()
                }
            )
        }

        composable(Screen.Settings.route) {
            val uiState by settingsViewModel.uiState.collectAsState()

            LaunchedEffect(Unit) {
                settingsViewModel.loadPackageInfo()
            }

            SettingsScreenCompose(
                darkTheme = uiState.darkTheme,
                editorFontSize = uiState.editorFontSize,
                autoSave = uiState.autoSave,
                notificationsEnabled = uiState.notificationsEnabled,
                packageCount = uiState.packageCount,
                packageSize = uiState.packageSize,
                storageLocation = uiState.storageLocation,
                storageMode = uiState.storageMode,
                isLoading = uiState.isLoading,
                isClearingData = uiState.isClearingData,
                error = uiState.error,
                onNavigateToScripts = {
                    navController.popBackStack()
                },
                onUpdateDarkTheme = { enabled ->
                    settingsViewModel.updateDarkTheme(enabled)
                },
                onUpdateEditorFontSize = { size ->
                    settingsViewModel.updateEditorFontSize(size)
                },
                onUpdateAutoSave = { enabled ->
                    settingsViewModel.updateAutoSave(enabled)
                },
                onUpdateNotifications = { enabled ->
                    settingsViewModel.updateNotificationsEnabled(enabled)
                },
                onClearAllData = {
                    settingsViewModel.clearAllData()
                },
                onRefresh = {
                    settingsViewModel.loadPackageInfo()
                },
                onClearError = {
                    settingsViewModel.clearError()
                },
                onStorageModeChanged = {
                    settingsViewModel.loadPackageInfo()
                }
            )
        }

        composable(
            route = "script_details/{scriptId}",
            arguments = listOf(navArgument("scriptId") { type = NavType.StringType })
        ) { backStackEntry ->
            val scriptId = backStackEntry.arguments?.getString("scriptId") ?: ""
            val uiState by scriptDetailsViewModel.uiState.collectAsState()

            LaunchedEffect(scriptId) {
                scriptDetailsViewModel.loadScript(scriptId)
                scriptDetailsViewModel.loadLogs(scriptId)
            }

            ScriptDetailsScreenCompose(
                scriptId = scriptId,
                script = uiState.script,
                logs = uiState.logs,
                isLoading = uiState.isLoading,
                isLoadingLogs = uiState.isLoadingLogs,
                isExecuting = uiState.isExecuting,
                error = uiState.error,
                executionResult = uiState.executionResult,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onRunScript = {
                    scriptDetailsViewModel.executeScript(scriptId)
                },
                onClearLogs = {
                    scriptDetailsViewModel.clearLogs(scriptId)
                },
                onEditScript = {
                    navController.navigate("script_editor/$scriptId")
                },
                onRefresh = {
                    scriptDetailsViewModel.refresh(scriptId)
                },
                onClearError = {
                    scriptDetailsViewModel.clearError()
                },
                onClearExecutionResult = {
                    scriptDetailsViewModel.clearExecutionResult()
                }
            )
        }

        composable(
            route = "script_editor/{scriptId}",
            arguments = listOf(navArgument("scriptId") { type = NavType.StringType; nullable = true })
        ) { backStackEntry ->
            val scriptId = backStackEntry.arguments?.getString("scriptId")
            val uiState by scriptEditorViewModel.uiState.collectAsState()

            LaunchedEffect(scriptId) {
                scriptEditorViewModel.loadScript(scriptId)
            }

            ScriptEditorScreenCompose(
                scriptName = uiState.scriptName,
                language = uiState.language,
                code = uiState.code,
                scheduleType = uiState.scheduleType,
                scheduleValue = uiState.scheduleValue,
                isLoading = uiState.isLoading,
                isSaving = uiState.isSaving,
                error = uiState.error,
                saveSuccess = uiState.saveSuccess,
                onUpdateScriptName = { name ->
                    scriptEditorViewModel.updateScriptName(name)
                },
                onUpdateLanguage = { language ->
                    scriptEditorViewModel.updateLanguage(language)
                },
                onUpdateCode = { code ->
                    scriptEditorViewModel.updateCode(code)
                },
                onUpdateScheduleType = { type ->
                    scriptEditorViewModel.updateScheduleType(type)
                },
                onUpdateScheduleValue = { value ->
                    scriptEditorViewModel.updateScheduleValue(value)
                },
                onSave = {
                    scriptEditorViewModel.saveScript()
                },
                onCancel = {
                    navController.popBackStack()
                },
                onClearError = {
                    scriptEditorViewModel.clearError()
                },
                onClearSaveSuccess = {
                    scriptEditorViewModel.clearSaveSuccess()
                }
            )
        }
    }
}
