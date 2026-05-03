package com.bytesmith.scriptler.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bytesmith.scriptler.ScriptRepository
import com.bytesmith.scriptler.ScriptRunner
import com.bytesmith.scriptler.models.Script
import com.bytesmith.scriptler.models.ScriptLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Script Details screen.
 * Manages loading script details, logs, and executing scripts.
 */
class ScriptDetailsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScriptRepository.getInstance(application)
    private val scriptRunner = ScriptRunner(application)

    private val _uiState = MutableStateFlow<ScriptDetailsUiState>(ScriptDetailsUiState())
    val uiState: StateFlow<ScriptDetailsUiState> = _uiState.asStateFlow()

    /**
     * Load script details by ID.
     */
    fun loadScript(scriptId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val script = repository.getScriptById(scriptId)
                if (script != null) {
                    _uiState.value = _uiState.value.copy(
                        script = script,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        script = null,
                        isLoading = false,
                        error = "Script not found"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    script = null,
                    isLoading = false,
                    error = "Failed to load script: ${e.message}"
                )
            }
        }
    }

    /**
     * Load logs for a script.
     */
    fun loadLogs(scriptId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingLogs = true, error = null)
            try {
                val logs = repository.getLogsForScript(scriptId)
                _uiState.value = _uiState.value.copy(
                    logs = logs,
                    isLoadingLogs = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    logs = emptyList(),
                    isLoadingLogs = false,
                    error = "Failed to load logs: ${e.message}"
                )
            }
        }
    }

    /**
     * Execute the script.
     */
    fun executeScript(scriptId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExecuting = true, error = null)
            try {
                val script = repository.getScriptById(scriptId)
                if (script != null) {
                    val result = scriptRunner.execute(script)
                    
                    // Create a log entry
                    val log = ScriptLog(
                        id = java.util.UUID.randomUUID().toString(),
                        scriptId = scriptId,
                        timestamp = System.currentTimeMillis(),
                        output = result.output,
                        status = if (result.isError) "error" else "success",
                        isError = result.isError
                    )
                    
                    // Add log to repository
                    repository.addLogForScript(scriptId, log)
                    
                    // Update script's last run time
                    val updatedScript = script.copy(lastRun = System.currentTimeMillis())
                    repository.saveOrUpdateScript(updatedScript)
                    
                    // Reload logs and script
                    loadLogs(scriptId)
                    loadScript(scriptId)
                    
                    _uiState.value = _uiState.value.copy(
                        isExecuting = false,
                        executionResult = result
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isExecuting = false,
                        error = "Script not found"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExecuting = false,
                    error = "Failed to execute script: ${e.message}"
                )
            }
        }
    }

    /**
     * Clear all logs for the script.
     */
    fun clearLogs(scriptId: String) {
        viewModelScope.launch {
            try {
                repository.clearLogsForScript(scriptId)
                _uiState.value = _uiState.value.copy(logs = emptyList())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to clear logs: ${e.message}"
                )
            }
        }
    }

    /**
     * Refresh script and logs data.
     */
    fun refresh(scriptId: String) {
        loadScript(scriptId)
        loadLogs(scriptId)
    }

    /**
     * Clear the error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Clear the execution result.
     */
    fun clearExecutionResult() {
        _uiState.value = _uiState.value.copy(executionResult = null)
    }
}

/**
 * UI state for the Script Details screen.
 */
data class ScriptDetailsUiState(
    val script: Script? = null,
    val logs: List<ScriptLog> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingLogs: Boolean = false,
    val isExecuting: Boolean = false,
    val error: String? = null,
    val executionResult: ScriptRunner.ExecutionResult? = null
)
