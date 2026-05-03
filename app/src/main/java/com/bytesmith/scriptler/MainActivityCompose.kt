package com.bytesmith.scriptler

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.bytesmith.scriptler.ui.navigation.Screen
import com.bytesmith.scriptler.ui.navigation.ScriptlerNavHost
import com.bytesmith.scriptler.ui.theme.*
import com.bytesmith.scriptler.utils.FileUtils

/**
 * Main launcher activity using Jetpack Compose.
 *
 * Handles first-run storage setup with an AlertDialog overlay offering 3 options:
 * 1. Use default path (/Documents/Scriptler/) with MANAGE_EXTERNAL_STORAGE
 * 2. Choose custom location via SAF (ACTION_OPEN_DOCUMENT_TREE)
 * 3. Use app-only storage (no permissions needed)
 */
class MainActivityCompose : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivityCompose"
    }

    private lateinit var manageStorageLauncher: ActivityResultLauncher<Intent>
    private lateinit var safDirectoryLauncher: ActivityResultLauncher<Uri?>

    private var setupComplete = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FileUtils.initialize(applicationContext)

        manageStorageLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { _ ->
            handleManageStorageResult()
        }

        safDirectoryLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            handleSafDirectoryResult(uri)
        }

        val needsSetup = StoragePermissionManager.isFirstRun(this) ||
                !StoragePermissionManager.validateStorageSetup(this)

        setupComplete.value = !needsSetup

        if (!needsSetup) {
            FileUtils.ensureScriptlerBaseDir()
        }

        NotificationUtils.createNotificationChannel(this)

        setContent {
            ScriptlerTheme {
                Box {
                    val navController = rememberNavController()
                    ScriptlerNavHost(
                        navController = navController,
                        startDestination = Screen.Scripts.route
                    )

                    if (!setupComplete.value) {
                        StorageSetupDialog(
                            onUseDefaultPath = { launchManageStorageRequest() },
                            onChooseCustomLocation = { launchSafDirectoryPicker() },
                            onUseAppOnlyStorage = { handleAppOnlyStorage() }
                        )
                    }
                }
            }
        }
    }

    private fun launchManageStorageRequest() {
        val intent = StoragePermissionManager.createStoragePermissionIntent(this)
        if (intent != null) {
            manageStorageLauncher.launch(intent)
        } else {
            StoragePermissionManager.setStorageMode(this, StoragePermissionManager.StorageMode.DEFAULT_PATH)
            FileUtils.ensureScriptlerBaseDir()
            StoragePermissionManager.markFirstRunComplete(this)
            setupComplete.value = true
            Toast.makeText(this, "Using default storage location", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchSafDirectoryPicker() {
        safDirectoryLauncher.launch(null)
    }

    private fun handleManageStorageResult() {
        if (StoragePermissionManager.hasStoragePermission()) {
            StoragePermissionManager.setStorageMode(this, StoragePermissionManager.StorageMode.DEFAULT_PATH)
            FileUtils.ensureScriptlerBaseDir()
            StoragePermissionManager.markFirstRunComplete(this)
            setupComplete.value = true
            Toast.makeText(this, "Storage access granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Permission denied. You can choose a custom location or use app-only storage.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun handleSafDirectoryResult(uri: Uri?) {
        if (uri != null) {
            val persisted = StoragePermissionManager.persistSafUri(this, uri)
            if (persisted) {
                StoragePermissionManager.setStorageMode(this, StoragePermissionManager.StorageMode.SAF_CUSTOM)
                FileUtils.ensureScriptlerBaseDir()
                StoragePermissionManager.markFirstRunComplete(this)
                setupComplete.value = true
                Toast.makeText(this, "Custom storage location set!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to set custom location. Please try again.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "No directory selected.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleAppOnlyStorage() {
        StoragePermissionManager.setStorageMode(this, StoragePermissionManager.StorageMode.APP_ONLY)
        FileUtils.ensureScriptlerBaseDir()
        StoragePermissionManager.markFirstRunComplete(this)
        setupComplete.value = true
        Toast.makeText(
            this,
            "Using app-only storage. Scripts are in:\n${FileUtils.getScriptlerBaseDir().absolutePath}",
            Toast.LENGTH_LONG
        ).show()
    }

    @Deprecated("Use the Compose UI instead")
    fun onScriptCreateClick(scriptName: String, scriptLanguage: String) {
        val intent = Intent(this, ScriptEditorActivity::class.java).apply {
            putExtra("script_name", scriptName)
            putExtra("script_language", scriptLanguage)
            putExtra("is_new_script", true)
        }
        startActivity(intent)
    }
}

/**
 * Simple AlertDialog overlay for first-run storage setup.
 * Matches the style of the previous AlertDialog.Builder dialog
 * but with 3 buttons instead of 2.
 */
@Composable
fun StorageSetupDialog(
    onUseDefaultPath: () -> Unit,
    onChooseCustomLocation: () -> Unit,
    onUseAppOnlyStorage: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Non-cancelable */ },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = {
            Text(
                text = "Welcome to Scriptler!",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Scriptler saves your scripts in the Documents/Scriptler/ folder " +
                        "so you can access them from any file manager.\n\n" +
                        "To do this, Scriptler needs \"All files access\" permission.\n\n" +
                        "Your scripts and data stay on your device — nothing is uploaded.\n\n" +
                        "You can also choose a custom folder or use app-only storage.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Column(
                horizontalAlignment = Alignment.End
            ) {
                TextButton(onClick = onUseDefaultPath) {
                    Text("GRANT ACCESS (RECOMMENDED)")
                }
                TextButton(onClick = onChooseCustomLocation) {
                    Text("CHOOSE CUSTOM LOCATION")
                }
                TextButton(onClick = onUseAppOnlyStorage) {
                    Text("USE APP-ONLY STORAGE")
                }
            }
        }
    )
}
