package com.bytesmith.scriptler.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bytesmith.scriptler.ModuleManager
import com.bytesmith.scriptler.PythonExecutor
import com.bytesmith.scriptler.RuntimePipManager
import com.bytesmith.scriptler.ui.screens.PackageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Package Manager screen.
 * Manages loading, installing, and uninstalling Python packages.
 *
 * All backend calls (PyPI queries, package installation, etc.) run on [Dispatchers.IO]
 * to avoid NetworkOnMainThread crashes.
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
     * Reads the actual prebundled packages from prebundled_packages.txt and
     * checks their availability to classify as native or pure Python.
     */
    fun loadPackages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, searchResults = emptyList(), hasSearched = false)
            try {
                withContext(Dispatchers.IO) {
                    // Get actual prebundled packages from prebundled_packages.txt
                    val prebundledNames = ModuleManager.getPrebundledPackagesList(getApplication())
                    val executor = PythonExecutor(getApplication())
                    val prebundledPackages = prebundledNames.map { pkgName ->
                        // Check if the module is available to determine if it's native
                        // Prebundled packages are available by definition (bundled at build time).
                        // We check if the module can be imported to verify it's actually present.
                        val isAvailable = executor.isModuleAvailable(pkgName)
                        PackageInfo(
                            name = pkgName,
                            version = "bundled",
                            isNative = isAvailable, // Build-time packages may have native components
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

                    val packageCount = prebundledPackages.size + installedPackages.size
                    val packageSize = runtimePipManager.getTotalInstalledSize()

                    _uiState.value = _uiState.value.copy(
                        prebundledPackages = prebundledPackages,
                        runtimePackages = runtimePackages,
                        packageCount = packageCount,
                        packageSize = packageSize,
                        isLoading = false
                    )
                }
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
     * Uses RuntimePipManager to query the PyPI JSON API.
     */
    fun searchPackages(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, error = null, hasSearched = true)
            try {
                if (query.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        searchResults = emptyList(),
                        isSearching = false,
                        hasSearched = false
                    )
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    val searchResult = runtimePipManager.searchPackages(query)
                    if (searchResult.isSuccess) {
                        val pypiResults = searchResult.getOrThrow()
                        val results = pypiResults.map { info ->
                            PackageInfo(
                                name = info.name,
                                version = info.version,
                                isNative = !info.isPurePython,
                                isRuntime = false
                            )
                        }
                        _uiState.value = _uiState.value.copy(
                            searchResults = results,
                            isSearching = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            searchResults = emptyList(),
                            isSearching = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                    error = "Search failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Install a package from PyPI.
     * Runs on IO dispatcher to avoid NetworkOnMainThreadException.
     */
    fun installPackage(packageName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isInstalling = true, error = null)
            try {
                withContext(Dispatchers.IO) {
                    val result = runtimePipManager.installPackage(packageName)
                    if (result.isSuccess) {
                        // Reload packages to reflect the new installation
                        loadPackages()
                        _uiState.value = _uiState.value.copy(isInstalling = false)
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        _uiState.value = _uiState.value.copy(
                            isInstalling = false,
                            error = "Failed to install $packageName: $error"
                        )
                    }
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
     * Runs on IO dispatcher for consistency.
     */
    fun uninstallPackage(packageName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUninstalling = true, error = null)
            try {
                withContext(Dispatchers.IO) {
                    val result = runtimePipManager.uninstallPackage(packageName)
                    if (result.isSuccess) {
                        // Reload packages to reflect the uninstallation
                        loadPackages()
                        _uiState.value = _uiState.value.copy(isUninstalling = false)
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        _uiState.value = _uiState.value.copy(
                            isUninstalling = false,
                            error = "Failed to uninstall $packageName: $error"
                        )
                    }
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
        _uiState.value = _uiState.value.copy(searchResults = emptyList(), hasSearched = false)
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
    val error: String? = null,
    val hasSearched: Boolean = false
)
