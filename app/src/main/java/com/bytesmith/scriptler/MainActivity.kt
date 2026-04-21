package com.bytesmith.scriptler

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.bytesmith.scriptler.utils.FileUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView

class MainActivity : AppCompatActivity(), CreateScriptDialogFragment.CreateScriptDialogListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var scriptRepository: ScriptRepository
    
    // ActivityResultLauncher for storage permission
    private lateinit var storagePermissionLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply theme based on settings
        applyThemeFromSettings()

        setContentView(R.layout.activity_main)

        // Register ActivityResultLauncher before use
        storagePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { _ ->
            handleStoragePermissionResult()
        }

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
        if (StoragePermissionManager.isFirstRun(this)) {
            if (StoragePermissionManager.hasStoragePermission()) {
                // Permission already granted (e.g., on Android < 11)
                FileUtils.ensureScriptlerBaseDir()
                StoragePermissionManager.markFirstRunComplete(this)
            } else {
                // First run on Android 11+ — show explanation and request permission
                StoragePermissionManager.showPermissionRationaleDialog(
                    activity = this,
                    onPermissionGranted = { launchStoragePermissionRequest() },
                    onPermissionDenied = {
                        FileUtils.ensureScriptlerBaseDir()
                        StoragePermissionManager.markFirstRunComplete(this)
                        Toast.makeText(
                            this,
                            "Using app-only storage. Scripts are in:\n${FileUtils.getScriptlerBaseDir().absolutePath}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        } else {
            // Not first run — just ensure directory exists
            FileUtils.ensureScriptlerBaseDir()
        }
    }

    /**
     * Handle the result of the storage permission request from ActivityResultLauncher.
     */
    private fun handleStoragePermissionResult() {
        if (StoragePermissionManager.hasStoragePermission()) {
            // Permission granted — create directory in public Documents
            FileUtils.ensureScriptlerBaseDir()
            StoragePermissionManager.markFirstRunComplete(this)
            Toast.makeText(this, "Storage access granted!", Toast.LENGTH_SHORT).show()
        } else {
            // Permission denied — use fallback storage
            FileUtils.ensureScriptlerBaseDir()
            StoragePermissionManager.markFirstRunComplete(this)
            Toast.makeText(
                this,
                "Using app-only storage. Grant \"All files access\" in Settings for full access.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Launch storage permission request using ActivityResultLauncher.
     */
    private fun launchStoragePermissionRequest() {
        val intent = StoragePermissionManager.createStoragePermissionIntent(this)
        if (intent != null) {
            storagePermissionLauncher.launch(intent)
        } else {
            // Android version doesn't support this intent - just mark as complete
            FileUtils.ensureScriptlerBaseDir()
            StoragePermissionManager.markFirstRunComplete(this)
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

    /**
     * Callback from CreateScriptDialogFragment when user creates a new script.
     */
    override fun onScriptCreateClick(scriptName: String, scriptLanguage: String) {
        val intent = Intent(this, ScriptEditorActivity::class.java).apply {
            putExtra("script_name", scriptName)
            putExtra("script_language", scriptLanguage)
            putExtra("is_new_script", true)
        }
        startActivity(intent)
    }
}
