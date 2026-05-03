package com.bytesmith.scriptler.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bytesmith.scriptler.ScriptRepository
import com.bytesmith.scriptler.models.Script
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Scripts List screen.
 * Manages loading scripts from the repository and handling script actions.
 */
class ScriptsListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScriptRepository.getInstance(application)

    private val _uiState = MutableStateFlow<ScriptsListUiState>(ScriptsListUiState())
    val uiState: StateFlow<ScriptsListUiState> = _uiState.asStateFlow()

    init {
        loadScripts()
    }

    /**
     * Load all scripts from the repository.
     */
    fun loadScripts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val scripts = repository.getAllScripts()
                _uiState.value = ScriptsListUiState(
                    scripts = scripts,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = ScriptsListUiState(
                    scripts = emptyList(),
                    isLoading = false,
                    error = "Failed to load scripts: ${e.message}"
                )
            }
        }
    }

    /**
     * Create a new script.
     */
    fun createScript(name: String, language: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            try {
                val newScript = repository.createNewScript(name, language)
                _uiState.value = _uiState.value.copy(
                    scripts = _uiState.value.scripts + newScript,
                    isCreating = false,
                    newlyCreatedScriptId = newScript.id
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = "Failed to create script: ${e.message}"
                )
            }
        }
    }

    /**
     * Delete a script.
     */
    fun deleteScript(script: Script) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, error = null)
            try {
                repository.deleteScript(script.id)
                _uiState.value = _uiState.value.copy(
                    scripts = _uiState.value.scripts.filter { it.id != script.id },
                    isDeleting = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    error = "Failed to delete script: ${e.message}"
                )
            }
        }
    }

    /**
     * Rename a script.
     */
    fun renameScript(script: Script, newName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRenaming = true, error = null)
            try {
                val success = repository.renameScript(script.id, newName)
                if (success) {
                    val updatedScripts = _uiState.value.scripts.map {
                        if (it.id == script.id) it.copy(name = newName) else it
                    }
                    _uiState.value = _uiState.value.copy(
                        scripts = updatedScripts,
                        isRenaming = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isRenaming = false,
                        error = "Failed to rename script"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRenaming = false,
                    error = "Failed to rename script: ${e.message}"
                )
            }
        }
    }

    /**
     * Toggle script active state.
     */
    fun toggleScriptActive(script: Script) {
        viewModelScope.launch {
            try {
                val updatedScript = script.copy(isActive = !script.isActive)
                repository.saveOrUpdateScript(updatedScript)
                val updatedScripts = _uiState.value.scripts.map {
                    if (it.id == script.id) updatedScript else it
                }
                _uiState.value = _uiState.value.copy(scripts = updatedScripts)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to toggle script: ${e.message}"
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

    /**
     * Clear the newly created script ID after navigation.
     */
    fun clearNewlyCreatedScriptId() {
        _uiState.value = _uiState.value.copy(newlyCreatedScriptId = null)
    }
}

/**
 * UI state for the Scripts List screen.
 */
data class ScriptsListUiState(
    val scripts: List<Script> = emptyList(),
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val isDeleting: Boolean = false,
    val isRenaming: Boolean = false,
    val error: String? = null,
    val newlyCreatedScriptId: String? = null
)
