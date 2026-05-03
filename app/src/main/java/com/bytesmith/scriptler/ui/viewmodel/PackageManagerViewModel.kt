package com.bytesmith.scriptler.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bytesmith.scriptler.ModuleManager
import com.bytesmith.scriptler.RuntimePipManager
import com.bytesmith.scriptler.ui.screens.PackageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Package Manager screen.
 * Manages loading, installing, and uninstalling Python packages.
 */
class PackageManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val runtimePipManager = RuntimePipManager(application)

    private val _uiState = MutableStateFlow<PackageManagerUiState>(PackageManagerUiState())
    val uiState: StateFlow<PackageManagerUiState> = _uiState.asStateFlow()

    init {
        loadPackages()
    }

    /**
     * Load all packages (prebundled and runtime).
     */
    fun loadPackages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Get prebundled packages from ModuleManager
                val packageNameMap = ModuleManager.getPackageNameMap(getApplication())
                val prebundledPackages = packageNameMap.entries.map { (importName, @Suppress("UNUSED_DESTRUCTURED_PARAMETER_ENTRY") pipName) ->
                    PackageInfo(
                        name = importName,
                        version = "bundled",
                        isNative = false,
                        isRuntime = false
                    )
                }

                // Get runtime packages
                val installedPackages = runtimePipManager.getInstalledPackages()
                val runtimePackages = installedPackages.map { packageInfo ->
                    PackageInfo(
                        name = packageInfo.pipName,
                        version = packageInfo.version,
                        isNative = false,
                        isRuntime = true
                    )
                }

                val packageCount = installedPackages.size
                val packageSize = runtimePipManager.getTotalInstalledSize()

                _uiState.value = _uiState.value.copy(
                    prebundledPackages = prebundledPackages,
                    runtimePackages = runtimePackages,
                    packageCount = packageCount,
                    packageSize = packageSize,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    prebundledPackages = emptyList(),
                    runtimePackages = emptyList(),
                    isLoading = false,
                    error = "Failed to load packages: ${e.message}"
                )
            }
        }
    }

    /**
     * Search for packages on PyPI.
     */
    fun searchPackages(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, error = null)
            try {
                if (query.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        searchResults = emptyList(),
                        isSearching = false
                    )
                    return@launch
                }

                // For now, just filter the prebundled packages
                // In a real implementation, you'd query PyPI here
                val packageNameMap = ModuleManager.getPackageNameMap(getApplication())
                val results = packageNameMap.entries
                    .filter { (importName, _) ->
                        importName.contains(query, ignoreCase = true)
                    }
                    .map { (_, pipName) ->
                        PackageInfo(
                            name = pipName,
                            version = "",
                            isNative = false,
                            isRuntime = false
                        )
                    }

                _uiState.value = _uiState.value.copy(
                    searchResults = results,
                    isSearching = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                    error = "Failed to search packages: ${e.message}"
                )
            }
        }
    }

    /**
     * Install a package.
     */
    fun installPackage(packageName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isInstalling = true, error = null)
            try {
                val result = runtimePipManager.installPackage(packageName)
                if (result.isSuccess) {
                    // Reload packages
                    loadPackages()
                    _uiState.value = _uiState.value.copy(isInstalling = false)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    _uiState.value = _uiState.value.copy(
                        isInstalling = false,
                        error = "Failed to install $packageName: $error"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isInstalling = false,
                    error = "Failed to install package: ${e.message}"
                )
            }
        }
    }

    /**
     * Uninstall a runtime package.
     */
    fun uninstallPackage(packageName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUninstalling = true, error = null)
            try {
                val result = runtimePipManager.uninstallPackage(packageName)
                if (result.isSuccess) {
                    // Reload packages
                    loadPackages()
                    _uiState.value = _uiState.value.copy(isUninstalling = false)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    _uiState.value = _uiState.value.copy(
                        isUninstalling = false,
                        error = "Failed to uninstall $packageName: $error"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUninstalling = false,
                    error = "Failed to uninstall package: ${e.message}"
                )
            }
        }
    }

    /**
     * Check if a package is installed.
     */
    fun isPackageInstalled(packageName: String): Boolean {
        return ModuleManager.isPackageInstalled(getApplication(), packageName)
    }

    /**
     * Clear the error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Clear search results.
     */
    fun clearSearchResults() {
        _uiState.value = _uiState.value.copy(searchResults = emptyList())
    }
}

/**
 * UI state for the Package Manager screen.
 */
data class PackageManagerUiState(
    val prebundledPackages: List<PackageInfo> = emptyList(),
    val runtimePackages: List<PackageInfo> = emptyList(),
    val searchResults: List<PackageInfo> = emptyList(),
    val packageCount: Int = 0,
    val packageSize: Long = 0L,
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val isInstalling: Boolean = false,
    val isUninstalling: Boolean = false,
    val error: String? = null
)
