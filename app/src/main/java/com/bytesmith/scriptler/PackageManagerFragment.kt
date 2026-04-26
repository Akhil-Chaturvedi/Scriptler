package com.bytesmith.scriptler

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Package Manager screen — lets users search, install, and uninstall Python packages.
 *
 * Displays two sections:
 * 1. **Pre-bundled** — packages built into the APK at build time (from build.gradle)
 * 2. **Runtime-installed** — pure-Python packages downloaded from PyPI at runtime
 *
 * Also provides a search bar to find and install new packages from PyPI.
 */
class PackageManagerFragment : Fragment() {

    private lateinit var storageText: TextView
    private lateinit var searchInput: TextInputEditText
    private lateinit var searchButton: View
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var prebundledContainer: LinearLayout
    private lateinit var runtimeContainer: LinearLayout
    private lateinit var runtimeEmptyText: TextView
    private lateinit var runtimeHeader: TextView
    private lateinit var searchResultsHeader: TextView
    private lateinit var searchResultsContainer: LinearLayout

    private data class PrebundledPackage(
        val name: String,
        val version: String,
        val isNative: Boolean
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_package_manager, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        storageText = view.findViewById(R.id.storage_text)
        searchInput = view.findViewById(R.id.search_input)
        searchButton = view.findViewById(R.id.search_button)
        progressBar = view.findViewById(R.id.progress_bar)
        statusText = view.findViewById(R.id.status_text)
        prebundledContainer = view.findViewById(R.id.prebundled_container)
        runtimeContainer = view.findViewById(R.id.runtime_container)
        runtimeEmptyText = view.findViewById(R.id.runtime_empty_text)
        runtimeHeader = view.findViewById(R.id.runtime_header)
        searchResultsHeader = view.findViewById(R.id.search_results_header)
        searchResultsContainer = view.findViewById(R.id.search_results_container)

        // Set up search
        searchButton.setOnClickListener { performSearch() }

        // Handle Enter key in search input
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        // Render the package lists
        renderPrebundledPackages()
        renderRuntimePackages()
        updateStorageText()
    }

    override fun onResume() {
        super.onResume()
        // Refresh runtime packages list in case packages were installed/uninstalled elsewhere
        renderRuntimePackages()
        updateStorageText()
    }

    /**
     * Render the pre-bundled packages section.
     * Reads the list of bundled packages from prebundled_packages.txt (included in the APK)
     * and verifies availability via PythonExecutor.isModuleAvailable().
     * Native detection is done by checking for .so files in the app's native library path.
     */
    private fun renderPrebundledPackages() {
        prebundledContainer.removeAllViews()

        // Check if fragment is still attached before starting async work
        if (!isAdded) return

        val context = getContext() ?: return
        val runtimePip = RuntimePipManager(context)
        val executor = PythonExecutor(context)

        viewLifecycleOwner.lifecycleScope.launch {
            val detectedPackages = withContext(Dispatchers.IO) {
                // Check again inside IO thread - fragment might have detached
                if (!isAdded) return@withContext emptyList<PrebundledPackage>()

                val packages = mutableListOf<PrebundledPackage>()
                val bundledPackages = getBundledPackages()

                // Check each package from prebundled_packages.txt
                for (pipName in bundledPackages) {
                    // Skip if it's a runtime-installed package
                    if (runtimePip.isInstalled(pipName)) continue
                    // Check if it's available as a build-time (pre-bundled) package
                    if (executor.isModuleAvailable(pipName)) {
                        // Determine if native by checking for .so files in native library paths
                        val isNative = isNativePackage(pipName)
                        packages.add(PrebundledPackage(pipName, "", isNative))
                    }
                }
                packages
            }

            // This runs on main thread - safe because lifecycleScope is bound to view lifecycle
            // If fragment is detached, this code won't execute
            if (!isAdded) return@launch

            for (pkg in detectedPackages) {
                val displayName = pkg.name
                val row = createPackageRow(
                    name = displayName,
                    version = pkg.version.ifEmpty { "bundled" },
                    badge = if (pkg.isNative) "Native" else "Pure",
                    badgeColor = if (pkg.isNative) R.color.error_color else R.color.success_color,
                    actionText = null, // No action for pre-bundled packages
                    actionCallback = null
                )
                prebundledContainer.addView(row)
            }
        }
    }

    /**
     * Read the list of bundled packages from prebundled_packages.txt.
     * This file is included in the APK and contains the same packages
     * that were installed via build.gradle's chaquopy pip block.
     * Format: Same as prebundled_packages.txt - ignores empty lines, # and // comments.
     */
    private fun getBundledPackages(): List<String> {
        return try {
            val inputStream = context?.resources?.openRawResource(R.raw.prebundled_packages)
            inputStream?.bufferedReader()?.use { reader ->
                reader.readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check if a package is a native package by looking for .so files in the app's
     * native library directories.
     */
    private fun isNativePackage(pipName: String): Boolean {
        return try {
            val nativeLibDir = context?.applicationInfo?.nativeLibraryDir ?: return false
            val soFiles = java.io.File(nativeLibDir).listFiles { _, name ->
                name.contains(pipName, ignoreCase = true) && name.endsWith(".so")
            }
            !soFiles.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Render the runtime-installed packages section.
     */
    private fun renderRuntimePackages() {
        runtimeContainer.removeAllViews()

        val runtimePip = RuntimePipManager(requireContext())
        val installed = runtimePip.getInstalledPackages()

        if (installed.isEmpty()) {
            runtimeEmptyText.visibility = View.VISIBLE
            return
        }

        runtimeEmptyText.visibility = View.GONE

        for (pkg in installed) {
            val row = createPackageRow(
                name = pkg.pipName,
                version = pkg.version,
                badge = "Runtime",
                badgeColor = R.color.primary_color,
                actionText = "Uninstall",
                actionCallback = {
                    uninstallPackage(pkg.pipName)
                }
            )
            runtimeContainer.addView(row)
        }
    }

    /**
     * Perform a package search on PyPI.
     */
    private fun performSearch() {
        val query = searchInput.text?.toString()?.trim() ?: ""
        if (query.isEmpty()) return

        // Hide keyboard
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)

        showProgress("Searching for '$query'...")
        searchResultsContainer.removeAllViews()
        searchResultsHeader.visibility = View.VISIBLE
        searchResultsContainer.visibility = View.VISIBLE

        // Use viewLifecycleOwner.lifecycleScope instead of raw Thread to avoid
        // "not attached to context" crashes when fragment is detached
        viewLifecycleOwner.lifecycleScope.launch {
            showProgress("Searching for '$query'...")
            searchResultsContainer.removeAllViews()
            searchResultsHeader.visibility = View.VISIBLE
            searchResultsContainer.visibility = View.VISIBLE
    
            val runtimePip = RuntimePipManager(requireContext())
    
            // Use analyzePackage for better pure-Python vs native detection
            val analysisResult = withContext(Dispatchers.IO) {
                runtimePip.analyzePackage(query)
            }
    
            hideProgress()
    
            if (analysisResult.isFailure) {
                showStatus("Search failed: ${analysisResult.exceptionOrNull()?.message}")
                return@launch
            }
    
            val analysis = analysisResult.getOrThrow()
            hideStatus()
            renderSearchResult(analysis)
        }
    }
    
    /**
     * Render a single search result using PackageAnalysis for detailed info.
     */
    private fun renderSearchResult(analysis: RuntimePipManager.PackageAnalysis) {
        searchResultsContainer.removeAllViews()
    
        val isInstalled = RuntimePipManager(requireContext()).isInstalled(analysis.name)
    
        // Determine badge text and color based on package type
        val (badge, badgeColor, actionText, actionCallback, summary) = when {
            isInstalled -> {
                PackageDisplayInfo(
                    badge = "Installed",
                    badgeColor = R.color.primary_color,
                    actionText = null,
                    actionCallback = null,
                    summary = analysis.summary
                )
            }
            analysis.isPurePython -> {
                val sizeStr = if (analysis.purePythonWheelSize > 0) {
                    " (${ModuleManager.formatSize(analysis.purePythonWheelSize)})"
                } else ""
                PackageDisplayInfo(
                    badge = "Pure Python",
                    badgeColor = R.color.success_color,
                    actionText = "Install$sizeStr",
                    actionCallback = { installFromAnalysis(analysis) },
                    summary = analysis.summary
                )
            }
            analysis.hasNativeWheels -> {
                PackageDisplayInfo(
                    badge = "Native",
                    badgeColor = R.color.error_color,
                    actionText = null,
                    actionCallback = null,
                    summary = analysis.message // Show why it's not installable
                )
            }
            analysis.onlySdist -> {
                PackageDisplayInfo(
                    badge = "Source Only",
                    badgeColor = R.color.error_color,
                    actionText = null,
                    actionCallback = null,
                    summary = analysis.message
                )
            }
            else -> {
                PackageDisplayInfo(
                    badge = "Unknown",
                    badgeColor = R.color.warning_color,
                    actionText = null,
                    actionCallback = null,
                    summary = analysis.message
                )
            }
        }
    
        val row = createPackageRow(
            name = analysis.name,
            version = analysis.version,
            badge = badge,
            badgeColor = badgeColor,
            actionText = actionText,
            actionCallback = actionCallback,
            summary = summary
        )
    
        // Disable button if already installed or not available
        if (isInstalled || actionCallback == null) {
            val actionBtn = row.findViewWithTag<Button>("action_button")
            actionBtn?.isEnabled = false
            actionBtn?.alpha = 0.5f
        }
    
        searchResultsContainer.addView(row)
    }
    
    /**
     * Data class for package display information.
     */
    private data class PackageDisplayInfo(
        val badge: String,
        val badgeColor: Int,
        val actionText: String?,
        val actionCallback: (() -> Unit)?,
        val summary: String
    )

    /**
     * Install a package from search results.
     * Shows a mobile data warning if the device is on a metered connection.
     */
    private fun installPackage(info: RuntimePipManager.PyPIPackageInfo) {
        // Warn if on metered (mobile data) connection
        val runtimePip = RuntimePipManager(requireContext())
        if (runtimePip.isMeteredConnection()) {
            val sizeStr = ModuleManager.formatSize(info.wheelSize)
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Mobile Data Warning")
                .setMessage("You are on a mobile data connection. " +
                    "Installing ${info.name} will download approximately $sizeStr. " +
                    "Continue anyway?")
                .setPositiveButton("Install") { _, _ -> doInstallPackage(info) }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        doInstallPackage(info)
    }

    /**
     * Install a package from PackageAnalysis (used by the new search flow).
     */
    private fun installFromAnalysis(analysis: RuntimePipManager.PackageAnalysis) {
        // Warn if on metered (mobile data) connection
        val runtimePip = RuntimePipManager(requireContext())
        if (runtimePip.isMeteredConnection()) {
            val sizeStr = ModuleManager.formatSize(analysis.purePythonWheelSize)
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Mobile Data Warning")
                .setMessage("You are on a mobile data connection. " +
                    "Installing ${analysis.name} will download approximately $sizeStr. " +
                    "Continue anyway?")
                .setPositiveButton("Install") { _, _ -> doInstallFromAnalysis(analysis) }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
    
        doInstallFromAnalysis(analysis)
    }
    
    /**
     * Actually perform the package installation from PackageAnalysis.
     */
    private fun doInstallFromAnalysis(analysis: RuntimePipManager.PackageAnalysis) {
        showProgress("Installing ${analysis.name}...")
    
        Thread {
            val runtimePip = RuntimePipManager(requireContext())
            val result = runtimePip.installPackageWithDependencies(
                packageName = analysis.name,
                onProgress = { message: String, percent: Int ->
                    requireActivity().runOnUiThread {
                        progressBar.progress = percent
                        statusText.text = message
                    }
                }
            )
    
            requireActivity().runOnUiThread {
                hideProgress()
    
                if (result.isSuccess) {
                    result.getOrThrow() // Verify result is valid
                    showStatus("${analysis.name} ${analysis.version} installed successfully!")
                    // Refresh the runtime packages list
                    renderRuntimePackages()
                    updateStorageText()
                    // Re-render search result to update button states
                    renderSearchResult(analysis)
                } else {
                    showStatus("Installation failed: ${result.exceptionOrNull()?.message}")
                }
            }
        }.start()
    }
    
    /**
     * Actually perform the package installation (called after optional mobile data warning).
     */
    private fun doInstallPackage(info: RuntimePipManager.PyPIPackageInfo) {
        showProgress("Installing ${info.name}...")
    
        Thread {
            val runtimePip = RuntimePipManager(requireContext())
            val result = runtimePip.installPackageWithDependencies(
                packageName = info.name,
                onProgress = { message: String, percent: Int ->
                    requireActivity().runOnUiThread {
                        progressBar.progress = percent
                        statusText.text = message
                    }
                }
            )
    
            requireActivity().runOnUiThread {
                hideProgress()
    
                if (result.isSuccess) {
                    result.getOrThrow() // Verify result is valid
                    showStatus("${info.name} ${info.version} installed successfully!")
                    // Refresh the runtime packages list
                    renderRuntimePackages()
                    updateStorageText()
                } else {
                    showStatus("Installation failed: ${result.exceptionOrNull()?.message}")
                }
            }
        }.start()
    }

    /**
     * Uninstall a runtime-installed package.
     */
    private fun uninstallPackage(pipName: String) {
        val runtimePip = RuntimePipManager(requireContext())
        val result = runtimePip.uninstallPackage(pipName)

        if (result.isSuccess) {
            renderRuntimePackages()
            updateStorageText()
        } else {
            showStatus("Uninstall failed: ${result.exceptionOrNull()?.message}")
        }
    }

    /**
     * Create a package row view.
     */
    private fun createPackageRow(
        name: String,
        version: String,
        badge: String,
        badgeColor: Int,
        actionText: String?,
        actionCallback: (() -> Unit)?,
        summary: String? = null
    ): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)

            // Top row: name + badge + action
            val topRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            // Package name
            val nameText = TextView(requireContext()).apply {
                text = name
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(requireContext().getColor(R.color.text_color))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            topRow.addView(nameText)

            // Version
            val versionText = TextView(requireContext()).apply {
                text = version
                textSize = 12f
                setTextColor(requireContext().getColor(R.color.text_secondary_color))
                setPadding(0, 0, 8, 0)
            }
            topRow.addView(versionText)

            // Badge (Native/Pure/Runtime)
            val badgeText = TextView(requireContext()).apply {
                text = badge
                textSize = 11f
                setTextColor(requireContext().getColor(badgeColor))
                setPadding(8, 2, 8, 2)
                background = requireContext().getDrawable(R.drawable.card_background)
            }
            topRow.addView(badgeText)

            // Action button
            if (actionText != null) {
                val actionBtn = Button(requireContext()).apply {
                    tag = "action_button"
                    text = actionText
                    textSize = 12f
                    setTextColor(requireContext().getColor(R.color.primary_color))
                    setPadding(16, 0, 16, 0)
                    minHeight = 36
                    if (actionCallback != null) {
                        setOnClickListener { actionCallback() }
                    }
                }
                topRow.addView(actionBtn)
            }

            addView(topRow)

            // Summary (optional)
            if (summary != null && summary.isNotEmpty()) {
                val summaryText = TextView(requireContext()).apply {
                    text = summary
                    textSize = 12f
                    setTextColor(requireContext().getColor(R.color.text_secondary_color))
                    setPadding(0, 4, 0, 0)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                addView(summaryText)
            }
        }
    }

    private fun updateStorageText() {
        val runtimePip = RuntimePipManager(requireContext())
        val size = runtimePip.getTotalInstalledSize()
        storageText.text = "Runtime packages: ${ModuleManager.formatSize(size)}"
    }

    private fun showProgress(message: String) {
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        statusText.visibility = View.VISIBLE
        statusText.text = message
    }

    private fun hideProgress() {
        progressBar.visibility = View.GONE
    }

    private fun showStatus(message: String) {
        statusText.visibility = View.VISIBLE
        statusText.text = message
    }

    private fun hideStatus() {
        statusText.visibility = View.GONE
    }
}
