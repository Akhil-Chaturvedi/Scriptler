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
import com.google.android.material.textfield.TextInputEditText

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

    /**
     * Known pre-bundled packages declared in build.gradle.
     * The isNative flag indicates whether the package contains C extensions (.so files).
     * This list is used as a fallback; the actual availability is verified dynamically
     * via PythonExecutor.isModuleAvailable() in renderPrebundledPackages().
     */
    private val knownPrebundledPackages = listOf(
        PrebundledPackage("lxml", "", true),
        PrebundledPackage("requests", "", false),
        PrebundledPackage("beautifulsoup4", "", false)
    )

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
     * Dynamically verifies which packages are actually available via PythonExecutor,
     * so the list stays in sync with build.gradle changes.
     */
    private fun renderPrebundledPackages() {
        prebundledContainer.removeAllViews()

        // Detect pre-bundled packages dynamically by checking which known packages
        // are available via the Python interpreter (but NOT installed at runtime)
        val runtimePip = RuntimePipManager(requireContext())
        val executor = PythonExecutor(requireContext())

        Thread {
            val detectedPackages = mutableListOf<PrebundledPackage>()

            for (pkg in knownPrebundledPackages) {
                // A package is "pre-bundled" if it's available via PythonExecutor
                // but NOT installed at runtime via RuntimePipManager
                if (executor.isModuleAvailable(pkg.name) && !runtimePip.isInstalled(pkg.name)) {
                    detectedPackages.add(pkg)
                }
            }

            // Also check import-name variants (e.g., "bs4" for beautifulsoup4)
            val packageNameMap = ModuleManager.getPackageNameMap(requireContext())
            for ((importName, pipName) in packageNameMap) {
                // Skip if already in detected list
                if (detectedPackages.any { it.name == pipName || it.name == importName }) continue
                // Skip if it's a runtime-installed package
                if (runtimePip.isInstalled(importName) || runtimePip.isInstalled(pipName)) continue
                // Check if it's available as a build-time package
                if (executor.isModuleAvailable(importName)) {
                    // Determine if native by checking if it's in the known list
                    val isNative = knownPrebundledPackages.any { it.name == pipName && it.isNative }
                    detectedPackages.add(PrebundledPackage(pipName, "", isNative))
                }
            }

            requireActivity().runOnUiThread {
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
        }.start()
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

        Thread {
            val runtimePip = RuntimePipManager(requireContext())
            val searchResult = runtimePip.searchPackages(query)

            requireActivity().runOnUiThread {
                hideProgress()

                if (searchResult.isFailure) {
                    showStatus("Search failed: ${searchResult.exceptionOrNull()?.message}")
                    return@runOnUiThread
                }

                val results = searchResult.getOrThrow()
                if (results.isEmpty()) {
                    showStatus("No packages found for '$query'")
                    return@runOnUiThread
                }

                hideStatus()
                renderSearchResults(results)
            }
        }.start()
    }

    /**
     * Render search results.
     */
    private fun renderSearchResults(results: List<RuntimePipManager.PyPIPackageInfo>) {
        searchResultsContainer.removeAllViews()

        for (info in results) {
            val isInstalled = RuntimePipManager(requireContext()).isInstalled(info.name)

            val row = createPackageRow(
                name = info.name,
                version = info.version,
                badge = if (info.isPurePython) "Pure Python" else "Native",
                badgeColor = if (info.isPurePython) R.color.success_color else R.color.error_color,
                actionText = when {
                    isInstalled -> "Installed"
                    info.isPurePython -> "Install (${ModuleManager.formatSize(info.wheelSize)})"
                    else -> "Not available"
                },
                actionCallback = when {
                    isInstalled -> null
                    info.isPurePython -> ({ installPackage(info) })
                    else -> null
                },
                summary = info.summary
            )

            // Disable button if already installed or not available
            if (isInstalled || !info.isPurePython) {
                val actionBtn = row.findViewWithTag<Button>("action_button")
                actionBtn?.isEnabled = false
                actionBtn?.alpha = 0.5f
            }

            searchResultsContainer.addView(row)
        }
    }

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
                    val installed = result.getOrThrow()
                    showStatus("${info.name} ${info.version} installed successfully!")
                    // Refresh the runtime packages list
                    renderRuntimePackages()
                    updateStorageText()
                    // Re-render search results to update button states
                    renderSearchResults(listOf(info))
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
