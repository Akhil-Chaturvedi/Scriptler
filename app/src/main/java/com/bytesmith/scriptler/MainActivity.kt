package com.bytesmith.scriptler

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.bytesmith.scriptler.utils.FileUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_MANAGE_STORAGE = 1001
        private const val PREFS_FIRST_RUN_COMPLETE = "first_run_setup_complete"
    }

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var scriptRepository: ScriptRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply theme based on settings
        applyThemeFromSettings()

        setContentView(R.layout.activity_main)

        // Initialize the ScriptRepository singleton
        scriptRepository = ScriptRepository.getInstance(applicationContext)

        // Create notification channel
        NotificationUtils.createNotificationChannel(this)

        bottomNavigationView = findViewById(R.id.bottom_navigation)

        bottomNavigationView.setOnItemSelectedListener(NavigationBarView.OnItemSelectedListener { item: MenuItem ->
            val selectedFragment: Fragment? = when (item.itemId) {
                R.id.navigation_scripts -> ScriptsFragment()
                R.id.navigation_packages -> PackageManagerFragment()
                R.id.navigation_settings -> SettingsFragment()
                else -> null
            }

            if (selectedFragment != null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit()
                return@OnItemSelectedListener true
            }
            false
        })

        // Load the default fragment when the activity is created
        if (savedInstanceState == null) {
            bottomNavigationView.selectedItemId = R.id.navigation_scripts
        }

        // Run first-run setup or ensure directory exists
        handleStorageSetup()
    }

    /**
     * Handle storage permission and directory setup.
     * On first run: show explanation dialog, then request permission.
     * On subsequent runs: just ensure the directory exists.
     */
    private fun handleStorageSetup() {
        if (isFirstRun()) {
            if (hasStoragePermission()) {
                // Permission already granted (e.g., on Android < 11)
                FileUtils.ensureScriptlerBaseDir()
                markFirstRunComplete()
            } else {
                // First run on Android 11+ — show explanation and request permission
                showFirstRunPermissionDialog()
            }
        } else {
            // Not first run — just ensure directory exists
            FileUtils.ensureScriptlerBaseDir()
        }
    }

    /**
     * Check if the app has permission to write to public external storage.
     */
    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Check if this is the first time the app has been launched.
     */
    private fun isFirstRun(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return !prefs.getBoolean(PREFS_FIRST_RUN_COMPLETE, false)
    }

    /**
     * Mark the first-run setup as complete.
     */
    private fun markFirstRunComplete() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putBoolean(PREFS_FIRST_RUN_COMPLETE, true).apply()
    }

    /**
     * Show a dialog explaining why Scriptler needs storage permission,
     * then request it if the user agrees.
     */
    private fun showFirstRunPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Welcome to Scriptler!")
            .setMessage(
                "Scriptler saves your scripts in the Documents/Scriptler/ folder so you can " +
                "access them from any file manager.\n\n" +
                "To do this, Scriptler needs \"All files access\" permission.\n\n" +
                "Your scripts and data stay on your device — nothing is uploaded.\n\n" +
                "If you prefer, you can use app-only storage (scripts won't be visible in file manager)."
            )
            .setPositiveButton("Grant Access") { _, _ ->
                requestStoragePermission()
            }
            .setNegativeButton("Use App-Only Storage") { dialog, _ ->
                dialog.dismiss()
                // Use fallback storage — still create the directory
                FileUtils.ensureScriptlerBaseDir()
                markFirstRunComplete()
                Toast.makeText(
                    this,
                    "Using app-only storage. Scripts are in:\n${FileUtils.getScriptlerBaseDir().absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Request MANAGE_EXTERNAL_STORAGE permission by launching the system settings.
     */
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
            } catch (e: Exception) {
                // Fallback: open general app settings
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
                } catch (e2: Exception) {
                    // Can't open settings — use fallback storage
                    FileUtils.ensureScriptlerBaseDir()
                    markFirstRunComplete()
                }
            }
        }
    }

    /**
     * Handle the result of the storage permission request.
     */
    @Deprecated("Override onActivityResult for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_MANAGE_STORAGE) {
            if (hasStoragePermission()) {
                // Permission granted — create directory in public Documents
                FileUtils.ensureScriptlerBaseDir()
                markFirstRunComplete()
                Toast.makeText(this, "Storage access granted!", Toast.LENGTH_SHORT).show()
            } else {
                // Permission denied — use fallback storage
                FileUtils.ensureScriptlerBaseDir()
                markFirstRunComplete()
                Toast.makeText(
                    this,
                    "Using app-only storage. Grant \"All files access\" in Settings for full access.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun navigateToSettings() {
        bottomNavigationView.selectedItemId = R.id.navigation_settings
    }

    fun navigateToScripts() {
        bottomNavigationView.selectedItemId = R.id.navigation_scripts
    }

    private fun applyThemeFromSettings() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val isDarkTheme = preferences.getBoolean("dark_theme_enabled", true)
        if (isDarkTheme) {
            setTheme(R.style.Theme_Scriptler)
        } else {
            setTheme(R.style.Theme_Scriptler_Light)
        }
    }
}
