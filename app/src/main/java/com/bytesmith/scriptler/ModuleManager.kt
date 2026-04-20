package com.bytesmith.scriptler

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Manages Python packages — both build-time (Chaquopy bundled) and runtime (pure-Python downloader).
 *
 * Build-time packages are declared in build.gradle's chaquopy { defaultConfig { pip { install } } } block.
 * Runtime packages are installed via [RuntimePipManager] by downloading pure-Python wheels from PyPI.
 *
 * This class provides a unified interface for checking package availability,
 * tracking installed packages, and managing storage.
 */
object ModuleManager {

    private const val TAG = "ModuleManager"
    private const val PREFS_NAME = "scriptler_modules"
    private const val INSTALLED_PACKAGES_KEY = "installed_packages"

    data class PackageInfo(
        val name: String,
        val pipName: String,
        val version: String = "",
        val sizeBytes: Long = 0
    )

    /**
     * Get the import-name to pip-install-name mapping from the raw resource file.
     */
    fun getPackageNameMap(context: Context): Map<String, String> {
        return try {
            val inputStream = context.resources.openRawResource(R.raw.package_name_map)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val json = reader.readText()
            reader.close()

            val type = object : TypeToken<Map<String, String>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading package name map", e)
            emptyMap()
        }
    }

    /**
     * Get the list of installed packages from SharedPreferences.
     */
    fun getInstalledPackages(context: Context): List<PackageInfo> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(INSTALLED_PACKAGES_KEY, null) ?: return emptyList()

        return try {
            val type = object : TypeToken<List<PackageInfo>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading installed packages", e)
            emptyList()
        }
    }

    /**
     * Save the list of installed packages to SharedPreferences.
     */
    private fun saveInstalledPackages(context: Context, packages: List<PackageInfo>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(packages)
        prefs.edit().putString(INSTALLED_PACKAGES_KEY, json).apply()
    }

    /**
     * Record that a package was installed.
     */
    fun recordInstalledPackage(context: Context, packageInfo: PackageInfo) {
        val packages = getInstalledPackages(context).toMutableList()
        // Remove existing entry for the same package
        packages.removeAll { it.pipName == packageInfo.pipName }
        packages.add(packageInfo)
        saveInstalledPackages(context, packages)
    }

    /**
     * Record that a package was uninstalled.
     */
    fun recordUninstalledPackage(context: Context, pipName: String) {
        val packages = getInstalledPackages(context).toMutableList()
        packages.removeAll { it.pipName == pipName }
        saveInstalledPackages(context, packages)
    }

    /**
     * Check if a package is already installed.
     */
    fun isPackageInstalled(context: Context, pipName: String): Boolean {
        return getInstalledPackages(context).any { it.pipName == pipName }
    }

    /**
     * Get the total size of all installed packages.
     */
    fun getTotalInstalledSize(context: Context): Long {
        return getInstalledPackages(context).sumOf { it.sizeBytes }
    }

    /**
     * Format a size in bytes to a human-readable string.
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        }
    }

    /**
     * Check if a Python package is available (either build-time or runtime).
     */
    fun isPackageAvailable(context: Context, importName: String): Boolean {
        // Check runtime-installed packages first
        val runtimePip = RuntimePipManager(context)
        if (runtimePip.isInstalled(importName)) {
            return true
        }

        // Check build-time bundled packages
        return try {
            val executor = PythonExecutor(context)
            executor.isModuleAvailable(importName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check package availability: $importName", e)
            false
        }
    }

    /**
     * Uninstall a Python package.
     * For runtime-installed packages, deletes the extracted files.
     * For build-time packages, only removes from tracking (cannot truly uninstall).
     */
    fun uninstallPackage(context: Context, pipName: String) {
        // Try runtime uninstall first
        val runtimePip = RuntimePipManager(context)
        val runtimeResult = runtimePip.uninstallPackage(pipName)
        if (runtimeResult.isSuccess) {
            Log.d(TAG, "Runtime package uninstalled: $pipName")
            return
        }

        // Fall back to build-time tracking removal
        recordUninstalledPackage(context, pipName)
        Log.d(TAG, "Package uninstalled (removed from tracking): $pipName")
    }

    /**
     * Get all installed packages — both build-time and runtime.
     */
    fun getAllInstalledPackages(context: Context): List<PackageInfo> {
        val buildTimePackages = getInstalledPackages(context)
        val runtimePip = RuntimePipManager(context)
        val runtimePackages = runtimePip.getInstalledPackages().map {
            PackageInfo(
                name = it.name,
                pipName = it.pipName,
                version = it.version,
                sizeBytes = it.sizeBytes
            )
        }
        return buildTimePackages + runtimePackages
    }

    /**
     * Get the total size of all installed packages (build-time + runtime).
     */
    fun getTotalInstalledSizeAll(context: Context): Long {
        val buildTimeSize = getTotalInstalledSize(context)
        val runtimePip = RuntimePipManager(context)
        return buildTimeSize + runtimePip.getTotalInstalledSize()
    }
}
