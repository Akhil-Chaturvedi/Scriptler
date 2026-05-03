package com.bytesmith.scriptler.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bytesmith.scriptler.ScriptRepository
import com.bytesmith.scriptler.models.Script
import com.bytesmith.scriptler.utils.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Script Editor screen.
 * Manages loading, editing, and saving scripts.
 */
class ScriptEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScriptRepository.getInstance(application)

    private val _uiState = MutableStateFlow<ScriptEditorUiState>(ScriptEditorUiState())
    val uiState: StateFlow<ScriptEditorUiState> = _uiState.asStateFlow()

    /**
     * Load script data for editing.
     */
    fun loadScript(scriptId: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                if (scriptId != null) {
                    val script = repository.getScriptById(scriptId)
                    if (script != null) {
                        val code = FileUtils.readScript(script.name, script.language)
                        _uiState.value = _uiState.value.copy(
                            scriptId = script.id,
                            scriptName = script.name,
                            language = script.language,
                            code = code,
                            initialCode = code,
                            scheduleType = script.scheduleType,
                            scheduleValue = script.scheduleValue,
                            isActive = script.isActive,
                            isLoading = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Script not found"
                        )
                    }
                } else {
                    // New script - start with template
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        code = "",
                        initialCode = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load script: ${e.message}"
                )
            }
        }
    }

    /**
     * Update the script name.
     */
    fun updateScriptName(name: String) {
        _uiState.value = _uiState.value.copy(scriptName = name)
    }

    /**
     * Update the script language.
     */
    fun updateLanguage(language: String) {
        _uiState.value = _uiState.value.copy(language = language)
    }

    /**
     * Update the script code.
     */
    fun updateCode(code: String) {
        _uiState.value = _uiState.value.copy(code = code)
    }

    /**
     * Update the schedule type.
     */
    fun updateScheduleType(scheduleType: String) {
        _uiState.value = _uiState.value.copy(scheduleType = scheduleType)
    }

    /**
     * Update the schedule value.
     */
    fun updateScheduleValue(scheduleValue: String) {
        _uiState.value = _uiState.value.copy(scheduleValue = scheduleValue)
    }

    /**
     * Update the script active state.
     */
    fun updateActiveState(isActive: Boolean) {
        _uiState.value = _uiState.value.copy(isActive = isActive)
    }

    /**
     * Save the script.
     */
    fun saveScript() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val scriptId = _uiState.value.scriptId
                
                val script = if (scriptId != null) {
                    // Update existing script
                    val existingScript = repository.getScriptById(scriptId)
                    if (existingScript != null) {
                        existingScript.copy(
                            name = _uiState.value.scriptName,
                            language = _uiState.value.language,
                            scheduleType = _uiState.value.scheduleType,
                            scheduleValue = _uiState.value.scheduleValue,
                            isActive = _uiState.value.isActive
                        )
                    } else {
                        throw Exception("Script not found")
                    }
                } else {
                    // Create new script
                    val newScript = repository.createNewScript(
                        _uiState.value.scriptName,
                        _uiState.value.language
                    )
                    newScript.copy(
                        scheduleType = _uiState.value.scheduleType,
                        scheduleValue = _uiState.value.scheduleValue,
                        isActive = _uiState.value.isActive
                    )
                }
                
                // Save code to file
                FileUtils.saveScript(
                    script.name,
                    _uiState.value.code,
                    script.language
                )
                
                // Save script metadata
                repository.saveOrUpdateScript(script)
                
                _uiState.value = _uiState.value.copy(
                    scriptId = script.id,
                    initialCode = _uiState.value.code,
                    isSaving = false,
                    saveSuccess = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Failed to save script: ${e.message}"
                )
            }
        }
    }

    /**
     * Check if there are unsaved changes.
     */
    fun hasUnsavedChanges(): Boolean {
        return _uiState.value.code != _uiState.value.initialCode ||
               _uiState.value.scriptName != _uiState.value.initialScriptName ||
               _uiState.value.language != _uiState.value.initialLanguage ||
               _uiState.value.scheduleType != _uiState.value.initialScheduleType ||
               _uiState.value.scheduleValue != _uiState.value.initialScheduleValue
    }

    /**
     * Reset the state to initial values.
     */
    fun resetToInitial() {
        _uiState.value = _uiState.value.copy(
            scriptName = _uiState.value.initialScriptName,
            language = _uiState.value.initialLanguage,
            code = _uiState.value.initialCode,
            scheduleType = _uiState.value.initialScheduleType,
            scheduleValue = _uiState.value.initialScheduleValue
        )
    }

    /**
     * Clear the error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Clear the save success flag.
     */
    fun clearSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }
}

/**
 * UI state for the Script Editor screen.
 */
data class ScriptEditorUiState(
    val scriptId: String? = null,
    val scriptName: String = "",
    val language: String = "javascript",
    val code: String = "",
    val scheduleType: String = "none",
    val scheduleValue: String = "",
    val isActive: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false,
    // Initial values for change detection
    val initialScriptName: String = "",
    val initialLanguage: String = "javascript",
    val initialCode: String = "",
    val initialScheduleType: String = "none",
    val initialScheduleValue: String = ""
)
