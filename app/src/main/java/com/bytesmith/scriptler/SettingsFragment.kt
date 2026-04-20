package com.bytesmith.scriptler

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.bytesmith.scriptler.utils.FileUtils

class SettingsFragment : Fragment() {

    private lateinit var switchDarkTheme: Switch
    private lateinit var iconTheme: ImageView
    private lateinit var textFontSizeValue: TextView
    private lateinit var buttonFontSizeDecrease: ImageButton
    private lateinit var buttonFontSizeIncrease: ImageButton
    private lateinit var switchAutoSave: Switch
    private lateinit var switchNotificationsEnabled: Switch
    private lateinit var textVersion: TextView
    private lateinit var aboutItemGithub: LinearLayout

    // Cache management UI
    private lateinit var textPackageCacheSize: TextView
    private lateinit var settingItemClearCache: LinearLayout
    private lateinit var textStorageLocation: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        // Get references to UI elements
        switchDarkTheme = view.findViewById(R.id.switch_dark_theme)
        iconTheme = view.findViewById(R.id.icon_theme)
        textFontSizeValue = view.findViewById(R.id.text_font_size_value)
        buttonFontSizeDecrease = view.findViewById(R.id.button_font_size_decrease)
        buttonFontSizeIncrease = view.findViewById(R.id.button_font_size_increase)
        switchAutoSave = view.findViewById(R.id.switch_auto_save)
        switchNotificationsEnabled = view.findViewById(R.id.switch_notifications_enabled)
        textVersion = view.findViewById(R.id.text_version)
        aboutItemGithub = view.findViewById(R.id.about_item_github)

        // Cache management references
        textPackageCacheSize = view.findViewById(R.id.text_package_cache_size)
        settingItemClearCache = view.findViewById(R.id.setting_item_clear_cache)
        textStorageLocation = view.findViewById(R.id.text_storage_location)

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Load and display current settings
        loadSettings(preferences)

        // Set up listeners
        switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            saveBooleanSetting(preferences, "dark_theme_enabled", isChecked)
            updateThemeIcon(isChecked)
            // Apply theme change
            val activity = activity as? MainActivity
            activity?.recreate()
        }

        buttonFontSizeDecrease.setOnClickListener {
            val currentSize = textFontSizeValue.text.toString().toInt()
            val newSize = maxOf(10, currentSize - 1)
            textFontSizeValue.text = newSize.toString()
            saveIntSetting(preferences, "editor_font_size", newSize)
        }

        buttonFontSizeIncrease.setOnClickListener {
            val currentSize = textFontSizeValue.text.toString().toInt()
            val newSize = minOf(24, currentSize + 1)
            textFontSizeValue.text = newSize.toString()
            saveIntSetting(preferences, "editor_font_size", newSize)
        }

        switchAutoSave.setOnCheckedChangeListener { _, isChecked ->
            saveBooleanSetting(preferences, "auto_save_enabled", isChecked)
        }

        switchNotificationsEnabled.setOnCheckedChangeListener { _, isChecked ->
            saveBooleanSetting(preferences, "notifications_enabled", isChecked)
        }

        aboutItemGithub.setOnClickListener {
            openLink("https://github.com/bytesmith/scriptler")
        }

        // Clear cache button
        settingItemClearCache.setOnClickListener { showClearCacheConfirmation() }

        // Set version text
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            textVersion.text = "v${packageInfo.versionName}"
        } catch (e: Exception) {
            textVersion.text = "v1.0.0"
        }

        // Load cache size and storage location
        updateCacheSizeDisplay()
        updateStorageLocationDisplay()

        return view
    }

    override fun onResume() {
        super.onResume()
        // Refresh cache size when returning to settings (e.g., after uninstalling packages)
        updateCacheSizeDisplay()
        updateStorageLocationDisplay()
    }

    private fun loadSettings(preferences: android.content.SharedPreferences) {
        val isDarkThemeEnabled = preferences.getBoolean("dark_theme_enabled", true)
        switchDarkTheme.isChecked = isDarkThemeEnabled
        updateThemeIcon(isDarkThemeEnabled)

        val editorFontSize = preferences.getInt("editor_font_size", 14)
        textFontSizeValue.text = editorFontSize.toString()

        val isAutoSaveEnabled = preferences.getBoolean("auto_save_enabled", true)
        switchAutoSave.isChecked = isAutoSaveEnabled

        val areNotificationsEnabled = preferences.getBoolean("notifications_enabled", false)
        switchNotificationsEnabled.isChecked = areNotificationsEnabled
    }

    private fun saveBooleanSetting(preferences: android.content.SharedPreferences, key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    private fun saveIntSetting(preferences: android.content.SharedPreferences, key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
    }

    private fun updateThemeIcon(isDarkTheme: Boolean) {
        if (isDarkTheme) {
            iconTheme.setImageResource(R.drawable.ic_moon)
        } else {
            iconTheme.setImageResource(R.drawable.ic_sun)
        }
    }

    // --- Cache Management ---

    private fun updateCacheSizeDisplay() {
        try {
            val runtimePip = RuntimePipManager(requireContext())
            val storageUsed = runtimePip.getTotalInstalledSize()
            val packageCount = runtimePip.getInstalledPackages().size
            val formattedSize = formatFileSize(storageUsed)
            textPackageCacheSize.text = if (packageCount > 0) {
                "$packageCount package(s), $formattedSize"
            } else {
                "No runtime packages installed"
            }
        } catch (e: Exception) {
            textPackageCacheSize.text = "Runtime packages: unknown"
        }
    }

    private fun updateStorageLocationDisplay() {
        val baseDir = FileUtils.getScriptlerBaseDir()
        val isFallback = FileUtils.isUsingFallbackStorage()
        textStorageLocation.text = if (isFallback) {
            "App-only storage:\n${baseDir.absolutePath}"
        } else {
            baseDir.absolutePath
        }
    }

    private fun showClearCacheConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear Package Cache")
            .setMessage(
                "This will remove all runtime-installed Python packages. " +
                "You can reinstall them later from the Package Manager.\n\n" +
                "Pre-bundled packages (requests, beautifulsoup4, lxml) will NOT be removed."
            )
            .setPositiveButton("Clear Cache") { _, _ -> clearPackageCache() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearPackageCache() {
        try {
            val runtimePip = RuntimePipManager(requireContext())
            val installedPackages = runtimePip.getInstalledPackages()

            if (installedPackages.isEmpty()) {
                Toast.makeText(requireContext(), "No runtime packages to clear", Toast.LENGTH_SHORT).show()
                return
            }

            var clearedCount = 0
            for (pkg in installedPackages) {
                val result = runtimePip.uninstallPackage(pkg.pipName)
                if (result.isSuccess) {
                    clearedCount++
                }
            }

            updateCacheSizeDisplay()
            Toast.makeText(
                requireContext(),
                "Cleared $clearedCount runtime package(s)",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Error clearing cache: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
            bytes >= 1_024 -> String.format("%.1f KB", bytes / 1_024.0)
            else -> "$bytes B"
        }
    }

    private fun openLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            // No browser available
        }
    }
}
