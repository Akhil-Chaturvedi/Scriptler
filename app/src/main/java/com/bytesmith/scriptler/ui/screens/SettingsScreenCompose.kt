package com.bytesmith.scriptler.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.bytesmith.scriptler.StoragePermissionManager
import com.bytesmith.scriptler.RuntimePipManager
import com.bytesmith.scriptler.utils.FileUtils
import com.bytesmith.scriptler.ui.components.*
import com.bytesmith.scriptler.ui.theme.*

// Import common states to avoid overload resolution ambiguity
import com.bytesmith.scriptler.ui.components.CommonLoadingState
import com.bytesmith.scriptler.ui.components.CommonErrorState as CommonErrorState

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = Primary,
            modifier = Modifier.padding(bottom = PaddingMedium)
        )

        ScriptlerCard(content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenCompose(
    darkTheme: Boolean = true,
    editorFontSize: Int = 14,
    autoSave: Boolean = true,
    notificationsEnabled: Boolean = false,
    packageCount: Int = 0,
    packageSize: Long = 0L,
    storageLocation: String = "",
    storageMode: String = "Default Path",
    isLoading: Boolean = false,
    isClearingData: Boolean = false,
    error: String? = null,
    onNavigateToScripts: () -> Unit = {},
    onUpdateDarkTheme: (Boolean) -> Unit = {},
    onUpdateEditorFontSize: (Int) -> Unit = {},
    onUpdateAutoSave: (Boolean) -> Unit = {},
    onUpdateNotifications: (Boolean) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onClearAllData: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onClearError: () -> Unit = {},
    onStorageModeChanged: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // State for the change storage location dialog
    var showChangeStorageDialog by remember { mutableStateOf(false) }

    // Activity result launchers for storage changes
    val manageStorageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (StoragePermissionManager.hasStoragePermission()) {
            StoragePermissionManager.setStorageMode(context, StoragePermissionManager.StorageMode.DEFAULT_PATH)
            FileUtils.ensureScriptlerBaseDir()
            onStorageModeChanged()
            Toast.makeText(context, "Switched to default storage location", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permission not granted", Toast.LENGTH_SHORT).show()
        }
    }

    val safDirectoryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val persisted = StoragePermissionManager.persistSafUri(context, uri)
            if (persisted) {
                StoragePermissionManager.setStorageMode(context, StoragePermissionManager.StorageMode.SAF_CUSTOM)
                FileUtils.ensureScriptlerBaseDir()
                onStorageModeChanged()
                Toast.makeText(context, "Custom storage location set!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to set custom location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    // Change Storage Location Dialog
    if (showChangeStorageDialog) {
        AlertDialog(
            onDismissRequest = { showChangeStorageDialog = false },
            title = {
                Text(
                    text = "Change Storage Location",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Choose where Scriptler stores your scripts. " +
                                "Changing location will not move existing scripts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    // Option 1: Default Path
                    StorageChangeOption(
                        icon = Icons.Default.Folder,
                        title = "Default Location",
                        description = "/Documents/Scriptler/",
                        isCurrent = storageMode == "Default Path",
                        onClick = {
                            showChangeStorageDialog = false
                            val intent = StoragePermissionManager.createStoragePermissionIntent(
                                context as android.app.Activity
                            )
                            if (intent != null) {
                                manageStorageLauncher.launch(intent)
                            } else {
                                // Pre-Android 11, just set the mode
                                StoragePermissionManager.setStorageMode(
                                    context, StoragePermissionManager.StorageMode.DEFAULT_PATH
                                )
                                FileUtils.ensureScriptlerBaseDir()
                                onStorageModeChanged()
                            }
                        }
                    )

                    // Option 2: Custom Location
                    StorageChangeOption(
                        icon = Icons.Default.CreateNewFolder,
                        title = "Custom Location",
                        description = "Pick a folder via system picker",
                        isCurrent = storageMode == "Custom Location",
                        onClick = {
                            showChangeStorageDialog = false
                            safDirectoryLauncher.launch(null)
                        }
                    )

                    // Option 3: App-Only
                    StorageChangeOption(
                        icon = Icons.Default.Security,
                        title = "App-Only Storage",
                        description = "Private, not visible in file managers",
                        isCurrent = storageMode == "App-Only Storage",
                        onClick = {
                            showChangeStorageDialog = false
                            StoragePermissionManager.setStorageMode(
                                context, StoragePermissionManager.StorageMode.APP_ONLY
                            )
                            FileUtils.ensureScriptlerBaseDir()
                            onStorageModeChanged()
                            Toast.makeText(context, "Switched to app-only storage", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangeStorageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            ScriptlerCenteredTopAppBar(
                title = "Settings",
                leftAction = {
                    ScriptlerIconButton(
                        onClick = onNavigateToScripts,
                        containerColor = Color.Transparent,
                        contentColor = OnSurface
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                rightAction = {
                    if (!isLoading && !isClearingData) {
                        ScriptlerIconButton(
                            onClick = onRefresh,
                            containerColor = Color.Transparent,
                            contentColor = OnSurface
                        ) {
                            Icon(Icons.Default.History, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        containerColor = Surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = PaddingLarge),
            verticalArrangement = Arrangement.spacedBy(PaddingLarge)
        ) {
            when {
                isLoading -> {
                    CommonLoadingState("Loading settings...")
                }
                error != null -> {
                    CommonErrorState(
                        message = error,
                        onRetry = onClearError
                    )
                }
                else -> {
                    // Appearance Section
                    SettingsSection(title = "Appearance") {
                        // Dark Theme
                        ScriptlerSettingItem(
                            icon = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                            label = "Dark Theme",
                            description = "Use high-contrast dark environment",
                            trailing = {
                                ScriptlerSwitch(
                                    checked = darkTheme,
                                    onCheckedChange = onUpdateDarkTheme
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(PaddingSmall))

                        // Editor Font Size
                        ScriptlerSettingItem(
                            icon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                            label = "Editor Font Size",
                            description = "Adjust code readability",
                            trailing = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(PaddingSmall),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ScriptlerIconButton(
                                        onClick = {
                                    val newSize = (editorFontSize - 1).coerceAtLeast(10)
                                    onUpdateEditorFontSize(newSize)
                                },
                                containerColor = SurfaceContainerHigh,
                                contentColor = OnSurface
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease font size")
                            }

                            Text(
                                text = editorFontSize.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurface,
                                modifier = Modifier.width(32.dp)
                            )

                            ScriptlerIconButton(
                                onClick = {
                                    val newSize = (editorFontSize + 1).coerceAtMost(24)
                                    onUpdateEditorFontSize(newSize)
                                },
                                containerColor = SurfaceContainerHigh,
                                contentColor = OnSurface
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase font size")
                            }
                        }
                    }
                )
            }

            // Editor Section
            SettingsSection(title = "Editor") {
                // Auto Save
                ScriptlerSettingItem(
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    label = "Auto Save",
                    description = "Automatically save changes on blur",
                    trailing = {
                        ScriptlerSwitch(
                            checked = autoSave,
                            onCheckedChange = onUpdateAutoSave
                        )
                    }
                )
            }

            // Notifications Section
            SettingsSection(title = "Notifications") {
                // Enable Notifications
                ScriptlerSettingItem(
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    label = "Enable Notifications",
                    description = "Receive alerts for script execution",
                    trailing = {
                        ScriptlerSwitch(
                            checked = notificationsEnabled,
                            onCheckedChange = onUpdateNotifications
                        )
                    }
                )
            }

            // Storage Section
            SettingsSection(title = "Storage") {
                // Storage Location
                ScriptlerSettingItem(
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = "Storage Location",
                    description = "$storageMode\n$storageLocation",
                    trailing = {
                        ScriptlerTextButton(
                            onClick = { showChangeStorageDialog = true }
                        ) {
                            Text("Change")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(PaddingSmall))

                // Package Cache
                val packageCacheTrailing: @Composable () -> Unit = if (packageCount > 0) {
                    {
                        ScriptlerTextButton(
                            onClick = {
                                // Show clear cache confirmation
                            }
                        ) {
                            Text("Clear Cache")
                        }
                    }
                } else {
                    { }
                }
                
                ScriptlerSettingItem(
                    icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                    label = "Package Cache",
                    description = if (packageCount > 0) {
                        "$packageCount package(s), ${formatFileSize(packageSize)}"
                    } else {
                        "No runtime packages installed"
                    },
                    trailing = packageCacheTrailing
                )
            }

            // About Section
            SettingsSection(title = "About") {
                // Version
                ScriptlerSettingItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = "Scriptler",
                    description = try {
                        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        "v${packageInfo.versionName}"
                    } catch (e: Exception) {
                        "v1.0.0"
                    },
                    trailing = { }
                )

                Spacer(modifier = Modifier.height(PaddingSmall))

                // GitHub
                ScriptlerSettingItem(
                    icon = { Icon(Icons.Default.Code, contentDescription = null) },
                    label = "GitHub Repository",
                    description = "scriptler",
                    trailing = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open GitHub")
                    }
                )
                }
            }
            }
        }
    }
}

/**
 * A single storage change option row in the dialog.
 */
@Composable
private fun StorageChangeOption(
    icon: ImageVector,
    title: String,
    description: String,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isCurrent) Primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isCurrent) Primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isCurrent) Primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (isCurrent) {
                Text(
                    text = "Current",
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
