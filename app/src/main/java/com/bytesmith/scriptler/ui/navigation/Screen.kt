package com.bytesmith.scriptler.ui.navigation

sealed class Screen(val route: String) {
    object Scripts : Screen("scripts")
    object Packages : Screen("packages")
    object Settings : Screen("settings")
    
    data class ScriptDetails(val scriptId: String) : Screen("script_details/$scriptId")
    data class ScriptEditor(val scriptId: String?) : Screen("script_editor/$scriptId")
}
