package com.bytesmith.scriptler

import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment

/**
 * Dialog shown when a Python script has missing imports.
 *
 * Checks each missing package and categorizes it as:
 * - Already installed (build-time or runtime) → shows "Available"
 * - Pure-Python package on PyPI → shows "Install" button with size
 * - Native C extension package → shows "Not available at runtime" with explanation
 *
 * When the user taps "Install", the package is downloaded from PyPI and extracted
 * to the runtime packages directory via [RuntimePipManager].
 */
class ModuleInstallDialog : DialogFragment() {

    private var missingPackages: List<String> = emptyList()
    private var onAllInstalled: (() -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null

    private lateinit var packagesContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var buttonClose: Button
    private lateinit var buttonInstallAll: Button

    /** Track the status of each package: importName → status */
    private val packageStatuses = mutableMapOf<String, PackageStatus>()

    private sealed class PackageStatus {
        data object Checking : PackageStatus()
        data class Available(val source: String) : PackageStatus()
        data class Installable(val sizeBytes: Long, val wheelUrl: String) : PackageStatus()
        data class NativeOnly(val message: String) : PackageStatus()
        data class NotFound(val message: String) : PackageStatus()
        data class Installing(val progress: Int) : PackageStatus()
        data class Installed(val version: String) : PackageStatus()
        data class Failed(val error: String) : PackageStatus()
    }

    companion object {
        fun newInstance(
            packages: List<String>,
            onAllInstalled: (() -> Unit)? = null,
            onDismiss: () -> Unit
        ): ModuleInstallDialog {
            val dialog = ModuleInstallDialog()
            dialog.missingPackages = packages
            dialog.onAllInstalled = onAllInstalled
            dialog.onDismiss = onDismiss
            return dialog
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireActivity())

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }

        // Title
        val title = TextView(requireContext()).apply {
            text = "Missing Python Packages"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.text_color))
        }
        container.addView(title)

        // Description
        val desc = TextView(requireContext()).apply {
            text = "Your script requires the following packages. " +
                "Pure-Python packages can be installed now. " +
                "Native packages (C extensions) must be bundled in the app."
            textSize = 14f
            setTextColor(requireContext().getColor(R.color.text_secondary_color))
            setPadding(0, 16, 0, 16)
        }
        container.addView(desc)

        // Package list
        packagesContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(packagesContainer)

        // Progress bar
        progressBar = ProgressBar(requireContext()).apply {
            visibility = View.GONE
            isIndeterminate = false
            max = 100
        }
        container.addView(progressBar)

        // Status text
        statusText = TextView(requireContext()).apply {
            visibility = View.GONE
            textSize = 14f
            setTextColor(requireContext().getColor(R.color.text_secondary_color))
            setPadding(0, 8, 0, 8)
        }
        container.addView(statusText)

        // Button container
        val buttonContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 16, 0, 0)
        }

        buttonInstallAll = Button(requireContext()).apply {
            text = "Install All"
            setTextColor(requireContext().getColor(R.color.primary_color))
            setOnClickListener { installAllInstallable() }
            visibility = View.GONE
        }
        buttonContainer.addView(buttonInstallAll)

        buttonClose = Button(requireContext()).apply {
            text = "Close"
            setTextColor(requireContext().getColor(R.color.text_secondary_color))
            setOnClickListener {
                onDismiss?.invoke()
                dismiss()
            }
        }
        buttonContainer.addView(buttonClose)

        container.addView(buttonContainer)

        builder.setView(container)

        // Start checking packages
        checkAllPackages()

        return builder.create()
    }

    /**
     * Check the availability status of all missing packages.
     */
    private fun checkAllPackages() {
        showStatus("Checking package availability...")
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true

        // Initialize all as "Checking"
        for (pkg in missingPackages) {
            packageStatuses[pkg] = PackageStatus.Checking
        }
        renderPackageList()

        Thread {
            val runtimePip = RuntimePipManager(requireContext())
            var hasInstallable = false

            for (pkg in missingPackages) {
                val pipName = PythonExecutor(requireContext()).getPipName(pkg)

                // Check if already available (build-time or runtime)
                if (ModuleManager.isPackageAvailable(requireContext(), pkg)) {
                    val source = if (runtimePip.isInstalled(pkg)) "Runtime" else "Built-in"
                    packageStatuses[pkg] = PackageStatus.Available(source)
                } else {
                    // Query PyPI for package info
                    val infoResult = runtimePip.queryPackage(pipName)
                    if (infoResult.isSuccess) {
                        val info = infoResult.getOrThrow()
                        if (info.isPurePython && info.wheelUrl != null) {
                            packageStatuses[pkg] = PackageStatus.Installable(info.wheelSize, info.wheelUrl)
                            hasInstallable = true
                        } else {
                            packageStatuses[pkg] = PackageStatus.NativeOnly(
                                "Requires native C extensions — must be bundled in the app"
                            )
                        }
                    } else {
                        packageStatuses[pkg] = PackageStatus.NotFound(
                            "Package not found on PyPI: ${infoResult.exceptionOrNull()?.message}"
                        )
                    }
                }
            }

            requireActivity().runOnUiThread {
                progressBar.visibility = View.GONE
                hideStatus()
                renderPackageList()

                // Show "Install All" button if there are installable packages
                if (hasInstallable) {
                    buttonInstallAll.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    /**
     * Render the package list based on current statuses.
     */
    private fun renderPackageList() {
        packagesContainer.removeAllViews()

        for (pkg in missingPackages) {
            val status = packageStatuses[pkg] ?: continue
            val pipName = PythonExecutor(requireContext()).getPipName(pkg)

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }

            // Package name
            val nameText = TextView(requireContext()).apply {
                text = if (pkg != pipName) "$pkg ($pipName)" else pkg
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(requireContext().getColor(R.color.text_color))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(nameText)

            // Status / action
            when (status) {
                is PackageStatus.Checking -> {
                    row.addView(createStatusBadge("Checking...", R.color.text_secondary_color))
                }
                is PackageStatus.Available -> {
                    row.addView(createStatusBadge("✓ ${status.source}", R.color.success_color))
                }
                is PackageStatus.Installable -> {
                    val sizeStr = ModuleManager.formatSize(status.sizeBytes)
                    val installBtn = Button(requireContext()).apply {
                        text = "Install ($sizeStr)"
                        textSize = 12f
                        setTextColor(requireContext().getColor(R.color.primary_color))
                        setOnClickListener { installSinglePackage(pkg) }
                    }
                    row.addView(installBtn)
                }
                is PackageStatus.NativeOnly -> {
                    row.addView(createStatusBadge("Native only", R.color.error_color))
                }
                is PackageStatus.NotFound -> {
                    row.addView(createStatusBadge("Not found", R.color.error_color))
                }
                is PackageStatus.Installing -> {
                    row.addView(createStatusBadge("Installing ${status.progress}%", R.color.primary_color))
                }
                is PackageStatus.Installed -> {
                    row.addView(createStatusBadge("✓ Installed", R.color.success_color))
                }
                is PackageStatus.Failed -> {
                    row.addView(createStatusBadge("✗ Failed", R.color.error_color))
                }
            }

            packagesContainer.addView(row)

            // Add detail text for NativeOnly and Failed statuses
            when (status) {
                is PackageStatus.NativeOnly -> {
                    val detail = TextView(requireContext()).apply {
                        text = status.message
                        textSize = 12f
                        setTextColor(requireContext().getColor(R.color.text_secondary_color))
                        setPadding(16, 0, 0, 8)
                    }
                    packagesContainer.addView(detail)
                }
                is PackageStatus.Failed -> {
                    val detail = TextView(requireContext()).apply {
                        text = status.error
                        textSize = 12f
                        setTextColor(requireContext().getColor(R.color.text_secondary_color))
                        setPadding(16, 0, 0, 8)
                    }
                    packagesContainer.addView(detail)
                }
                else -> { /* no detail */ }
            }
        }
    }

    /**
     * Install a single package by name.
     */
    private fun installSinglePackage(importName: String) {
        val pipName = PythonExecutor(requireContext()).getPipName(importName)

        // Warn if on metered (mobile data) connection
        val runtimePip = RuntimePipManager(requireContext())
        if (runtimePip.isMeteredConnection()) {
            val status = packageStatuses[importName]
            val sizeBytes = (status as? PackageStatus.Installable)?.sizeBytes ?: 0L
            val sizeStr = ModuleManager.formatSize(sizeBytes)

            androidx.appcompat.app.AlertDialog.Builder(requireActivity())
                .setTitle("Mobile Data Warning")
                .setMessage("You are on a mobile data connection. " +
                    "Installing $pipName will download approximately $sizeStr. " +
                    "Continue anyway?")
                .setPositiveButton("Install") { _, _ -> doInstallSingle(importName, pipName) }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        doInstallSingle(importName, pipName)
    }

    /**
     * Actually perform a single package installation (called after optional mobile data warning).
     */
    private fun doInstallSingle(importName: String, pipName: String) {
        packageStatuses[importName] = PackageStatus.Installing(0)
        renderPackageList()

        showStatus("Installing $pipName...")
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = false
        progressBar.progress = 0

        Thread {
            val runtimePip = RuntimePipManager(requireContext())
            val result = runtimePip.installPackageWithDependencies(
                packageName = pipName,
                onProgress = { message: String, percent: Int ->
                    requireActivity().runOnUiThread {
                        statusText.text = message
                        progressBar.progress = percent
                        packageStatuses[importName] = PackageStatus.Installing(percent)
                        renderPackageList()
                    }
                }
            )

            requireActivity().runOnUiThread {
                progressBar.visibility = View.GONE
                hideStatus()

                if (result.isSuccess) {
                    val installed = result.getOrThrow()
                    packageStatuses[importName] = PackageStatus.Installed(
                        installed.firstOrNull()?.version ?: "unknown"
                    )
                    // Check if all packages are now available
                    checkIfAllResolved()
                } else {
                    packageStatuses[importName] = PackageStatus.Failed(
                        result.exceptionOrNull()?.message ?: "Installation failed"
                    )
                }
                renderPackageList()
            }
        }.start()
    }

    /**
     * Install all installable packages sequentially.
     */
    private fun installAllInstallable() {
        val installable = packageStatuses.entries
            .filter { it.value is PackageStatus.Installable }
            .map { it.key }

        if (installable.isEmpty()) return

        // Warn if on metered (mobile data) connection
        val runtimePip = RuntimePipManager(requireContext())
        if (runtimePip.isMeteredConnection()) {
            val totalSize = packageStatuses.entries
                .filter { it.value is PackageStatus.Installable }
                .sumOf { (it.value as? PackageStatus.Installable)?.sizeBytes ?: 0L }
            val sizeStr = ModuleManager.formatSize(totalSize)

            androidx.appcompat.app.AlertDialog.Builder(requireActivity())
                .setTitle("Mobile Data Warning")
                .setMessage("You are on a mobile data connection. " +
                    "Installing these packages will download approximately $sizeStr. " +
                    "Continue anyway?")
                .setPositiveButton("Install") { _, _ -> doInstallAll(installable) }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        doInstallAll(installable)
    }

    /**
     * Actually perform the bulk installation (called after optional mobile data warning).
     */
    private fun doInstallAll(installable: List<String>) {
        buttonInstallAll.visibility = View.GONE
        showStatus("Installing ${installable.size} package(s)...")
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true

        Thread {
            val runtimePip = RuntimePipManager(requireContext())
            var allSuccess = true

            for ((index, importName) in installable.withIndex()) {
                val pipName = PythonExecutor(requireContext()).getPipName(importName)

                requireActivity().runOnUiThread {
                    packageStatuses[importName] = PackageStatus.Installing(0)
                    renderPackageList()
                    statusText.text = "Installing $pipName (${index + 1}/${installable.size})..."
                }

                val result = runtimePip.installPackageWithDependencies(
                    packageName = pipName,
                    onProgress = { message: String, percent: Int ->
                        requireActivity().runOnUiThread {
                            statusText.text = message
                            packageStatuses[importName] = PackageStatus.Installing(percent)
                            renderPackageList()
                        }
                    }
                )

                if (result.isSuccess) {
                    val installed = result.getOrThrow()
                    requireActivity().runOnUiThread {
                        packageStatuses[importName] = PackageStatus.Installed(
                            installed.firstOrNull()?.version ?: "unknown"
                        )
                        renderPackageList()
                    }
                } else {
                    allSuccess = false
                    requireActivity().runOnUiThread {
                        packageStatuses[importName] = PackageStatus.Failed(
                            result.exceptionOrNull()?.message ?: "Installation failed"
                        )
                        renderPackageList()
                    }
                }
            }

            requireActivity().runOnUiThread {
                progressBar.visibility = View.GONE
                hideStatus()
                checkIfAllResolved()
            }
        }.start()
    }

    /**
     * Check if all missing packages are now resolved.
     * If so, offer to re-run the script.
     */
    private fun checkIfAllResolved() {
        val allResolved = missingPackages.all { pkg ->
            val status = packageStatuses[pkg]
            status is PackageStatus.Available || status is PackageStatus.Installed
        }

        if (allResolved) {
            statusText.visibility = View.VISIBLE
            statusText.text = "All packages installed! You can now run the script."
            statusText.setTextColor(requireContext().getColor(R.color.success_color))

            // Change close button to "Run Script"
            buttonClose.text = "Run Script"
            buttonClose.setOnClickListener {
                onAllInstalled?.invoke()
                dismiss()
            }
        }
    }

    private fun createStatusBadge(text: String, colorRes: Int): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 12f
            setTextColor(requireContext().getColor(colorRes))
            setPadding(16, 4, 16, 4)
        }
    }

    private fun showStatus(message: String) {
        statusText.visibility = View.VISIBLE
        statusText.text = message
        statusText.setTextColor(requireContext().getColor(R.color.text_secondary_color))
    }

    private fun hideStatus() {
        statusText.visibility = View.GONE
    }
}
