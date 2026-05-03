package com.bytesmith.scriptler.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.bytesmith.scriptler.RuntimePipManager
import com.bytesmith.scriptler.StoragePermissionManager
import com.bytesmith.scriptler.utils.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings screen.
 * Manages app settings and displays storage/package information.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val packageManager = RuntimePipManager(application)

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        loadPackageInfo()
    }

    /**
     * Load all settings from SharedPreferences.
     */
    fun loadSettings() {
        _uiState.value = _uiState.value.copy(
            darkTheme = preferences.getBoolean("dark_theme_enabled", true),
            editorFontSize = preferences.getInt("editor_font_size", 14),
            autoSave = preferences.getBoolean("auto_save_enabled", true),
            notificationsEnabled = preferences.getBoolean("notifications_enabled", false)
        )
    }

    /**
     * Load package and storage information.
     */
    fun loadPackageInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val installedPackages = packageManager.getInstalledPackages()
                val packageCount = installedPackages.size
                val packageSize = packageManager.getTotalInstalledSize()
                val storageLocation = FileUtils.getStorageLocationDisplay()
                val storageMode = StoragePermissionManager.getStorageMode(getApplication())
                val storageModeLabel = when (storageMode) {
                    StoragePermissionManager.StorageMode.DEFAULT_PATH -> "Default Path"
                    StoragePermissionManager.StorageMode.SAF_CUSTOM -> "Custom Location"
                    StoragePermissionManager.StorageMode.APP_ONLY -> "App-Only Storage"
                }
                
                _uiState.value = _uiState.value.copy(
                    packageCount = packageCount,
                    packageSize = packageSize,
                    storageLocation = storageLocation,
                    storageMode = storageModeLabel,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load package info: ${e.message}"
                )
            }
        }
    }

    /**
     * Update dark theme setting.
     */
    fun updateDarkTheme(enabled: Boolean) {
        preferences.edit().putBoolean("dark_theme_enabled", enabled).apply()
        _uiState.value = _uiState.value.copy(darkTheme = enabled)
    }

    /**
     * Update editor font size.
     */
    fun updateEditorFontSize(size: Int) {
        preferences.edit().putInt("editor_font_size", size).apply()
        _uiState.value = _uiState.value.copy(editorFontSize = size)
    }

    /**
     * Update auto-save setting.
     */
    fun updateAutoSave(enabled: Boolean) {
        preferences.edit().putBoolean("auto_save_enabled", enabled).apply()
        _uiState.value = _uiState.value.copy(autoSave = enabled)
    }

    /**
     * Update notifications setting.
     */
    fun updateNotificationsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("notifications_enabled", enabled).apply()
        _uiState.value = _uiState.value.copy(notificationsEnabled = enabled)
    }

    /**
     * Set storage mode to app-only and refresh the UI state.
     */
    fun setAppOnlyStorage() {
        StoragePermissionManager.setStorageMode(getApplication(), StoragePermissionManager.StorageMode.APP_ONLY)
        FileUtils.ensureScriptlerBaseDir()
        loadPackageInfo()
    }

    /**
     * Set storage mode to default path and refresh the UI state.
     */
    fun setDefaultPathStorage() {
        StoragePermissionManager.setStorageMode(getApplication(), StoragePermissionManager.StorageMode.DEFAULT_PATH)
        FileUtils.ensureScriptlerBaseDir()
        loadPackageInfo()
    }

    /**
     * Set storage mode to SAF custom and refresh the UI state.
     */
    fun setSafCustomStorage() {
        StoragePermissionManager.setStorageMode(getApplication(), StoragePermissionManager.StorageMode.SAF_CUSTOM)
        FileUtils.ensureScriptlerBaseDir()
        loadPackageInfo()
    }

    /**
     * Clear all app data.
     */
    fun clearAllData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isClearingData = true, error = null)
            try {
                // Clear all scripts
                val scriptlerDir = FileUtils.getScriptlerBaseDir()
                FileUtils.deleteDirectory(scriptlerDir)
                
                // Reload package info (should be empty now)
                loadPackageInfo()
                
                _uiState.value = _uiState.value.copy(isClearingData = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isClearingData = false,
                    error = "Failed to clear data: ${e.message}"
                )
            }
        }
    }

    /**
     * Clear the error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * UI state for the Settings screen.
 */
data class SettingsUiState(
    val darkTheme: Boolean = true,
    val editorFontSize: Int = 14,
    val autoSave: Boolean = true,
    val notificationsEnabled: Boolean = false,
    val packageCount: Int = 0,
    val packageSize: Long = 0L,
    val storageLocation: String = "",
    val storageMode: String = "Default Path",
    val isLoading: Boolean = false,
    val isClearingData: Boolean = false,
    val error: String? = null
)
