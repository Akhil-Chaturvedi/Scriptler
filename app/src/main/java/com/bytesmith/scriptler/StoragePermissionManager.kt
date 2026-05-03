package com.bytesmith.scriptler

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * Manages storage permissions and storage mode for Scriptler.
 *
 * Supports three storage modes:
 * - **DEFAULT_PATH**: Uses MANAGE_EXTERNAL_STORAGE to write to /storage/emulated/0/Documents/Scriptler/
 * - **SAF_CUSTOM**: Uses Storage Access Framework (ACTION_OPEN_DOCUMENT_TREE) for user-chosen directory
 * - **APP_ONLY**: Falls back to app-specific external storage (no special permissions needed)
 *
 * On Android 11+ (API 30+), DEFAULT_PATH mode requires MANAGE_EXTERNAL_STORAGE.
 * SAF_CUSTOM mode works on API 21+ via ACTION_OPEN_DOCUMENT_TREE.
 * APP_ONLY mode works on all versions with no special permissions.
 */
object StoragePermissionManager {

    private const val TAG = "StoragePermManager"
    private const val PREFS_FIRST_RUN_COMPLETE = "first_run_setup_complete"
    private const val PREFS_STORAGE_MODE = "storage_mode"
    private const val PREFS_SAF_TREE_URI = "saf_tree_uri"

    const val REQUEST_CODE_MANAGE_STORAGE = 1001
    const val REQUEST_CODE_OPEN_DOCUMENT_TREE = 1002

    /**
     * Storage mode enum representing how scripts are stored on device.
     */
    enum class StorageMode {
        /** Uses MANAGE_EXTERNAL_STORAGE + direct File API for /Documents/Scriptler/ */
        DEFAULT_PATH,
        /** Uses SAF (ACTION_OPEN_DOCUMENT_TREE) + DocumentFile for user-chosen directory */
        SAF_CUSTOM,
        /** Uses app-specific external storage, no special permissions needed */
        APP_ONLY
    }

    // --- Permission Checks ---

    /**
     * Check if the app has permission to write to external storage.
     * On Android 11+ this checks MANAGE_EXTERNAL_STORAGE.
     * On older versions, WRITE_EXTERNAL_STORAGE is sufficient (granted at install time).
     */
    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // WRITE_EXTERNAL_STORAGE is granted via manifest on older versions
        }
    }

    /**
     * Check if this is the first time the app has been launched.
     */
    fun isFirstRun(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return !prefs.getBoolean(PREFS_FIRST_RUN_COMPLETE, false)
    }

    /**
     * Mark the first-run setup as complete.
     */
    fun markFirstRunComplete(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean(PREFS_FIRST_RUN_COMPLETE, true).apply()
        Log.d(TAG, "First run setup marked as complete")
    }

    // --- Storage Mode ---

    /**
     * Get the current storage mode from preferences.
     */
    fun getStorageMode(context: Context): StorageMode {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val modeStr = prefs.getString(PREFS_STORAGE_MODE, null)
        return when (modeStr) {
            "default" -> StorageMode.DEFAULT_PATH
            "saf_custom" -> StorageMode.SAF_CUSTOM
            "app_only" -> StorageMode.APP_ONLY
            else -> {
                // Not explicitly set — infer from current state
                if (hasStoragePermission()) {
                    StorageMode.DEFAULT_PATH
                } else {
                    StorageMode.APP_ONLY
                }
            }
        }
    }

    /**
     * Set the storage mode in preferences.
     */
    fun setStorageMode(context: Context, mode: StorageMode) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val modeStr = when (mode) {
            StorageMode.DEFAULT_PATH -> "default"
            StorageMode.SAF_CUSTOM -> "saf_custom"
            StorageMode.APP_ONLY -> "app_only"
        }
        prefs.edit()
            .putString(PREFS_STORAGE_MODE, modeStr)
            .apply()
        Log.d(TAG, "Storage mode set to: $modeStr")
    }

    // --- MANAGE_EXTERNAL_STORAGE ---

    /**
     * Create an intent to request MANAGE_EXTERNAL_STORAGE permission.
     * Returns null if the intent cannot be created (Android < 11).
     */
    fun createStoragePermissionIntent(activity: Activity): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
        }
        return null
    }

    /**
     * Get the request code used for the storage permission intent.
     */
    fun getRequestCode(): Int = REQUEST_CODE_MANAGE_STORAGE

    // --- SAF (Storage Access Framework) ---

    /**
     * Create an intent to open the system directory picker via SAF.
     * Allows the user to select or create a directory for Scriptler storage.
     * Available on API 21+ (Android 5.0+).
     */
    fun createSafDirectoryPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
            // Optionally suggest starting directory
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra("android.provider.extra.INITIAL_URI",
                    Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments"))
            }
        }
    }

    /**
     * Save the SAF tree URI and take persistable permission.
     * Must be called when the user selects a directory via ACTION_OPEN_DOCUMENT_TREE.
     *
     * @param context Activity context
     * @param treeUri The URI returned from the directory picker
     * @return true if the permission was successfully persisted
     */
    fun persistSafUri(context: Context, treeUri: Uri): Boolean {
        return try {
            // Take persistable URI permission
            val contentResolver = context.contentResolver
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            // Save the URI string to preferences
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit()
                .putString(PREFS_SAF_TREE_URI, treeUri.toString())
                .apply()

            Log.d(TAG, "SAF tree URI persisted: $treeUri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist SAF URI: $treeUri", e)
            false
        }
    }

    /**
     * Get the stored SAF tree URI.
     * Returns null if no SAF URI has been stored or if the URI is no longer valid.
     */
    fun getSafTreeUri(context: Context): Uri? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val uriString = prefs.getString(PREFS_SAF_TREE_URI, null) ?: return null

        val uri = Uri.parse(uriString)

        // Verify the permission is still held
        if (!isSafUriValid(context, uri)) {
            Log.w(TAG, "SAF URI is no longer valid, clearing it")
            clearSafUri(context)
            return null
        }

        return uri
    }

    /**
     * Check if the stored SAF URI is still accessible.
     */
    fun isSafUriValid(context: Context, uri: Uri): Boolean {
        return try {
            val persistedUris = context.contentResolver.persistedUriPermissions
            persistedUris.any { it.uri == uri }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking SAF URI validity", e)
            false
        }
    }

    /**
     * Clear the stored SAF URI and release the persistable permission.
     */
    fun clearSafUri(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val uriString = prefs.getString(PREFS_SAF_TREE_URI, null)

        if (uriString != null) {
            try {
                val uri = Uri.parse(uriString)
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing SAF URI permission", e)
            }
        }

        prefs.edit()
            .remove(PREFS_SAF_TREE_URI)
            .apply()
    }

    /**
     * Validate the current storage setup on app start.
     * Returns true if everything is properly configured, false if re-setup is needed.
     */
    fun validateStorageSetup(context: Context): Boolean {
        val mode = getStorageMode(context)
        return when (mode) {
            StorageMode.DEFAULT_PATH -> hasStoragePermission()
            StorageMode.SAF_CUSTOM -> getSafTreeUri(context) != null
            StorageMode.APP_ONLY -> true // Always valid
        }
    }

    /**
     * Show a dialog explaining why Scriptler needs storage permission.
     * This is the legacy dialog used by the old MainActivity.
     *
     * @param activity The activity to show the dialog from
     * @param onPermissionGranted Called when user clicks "Grant Access" - caller should launch the permission intent
     * @param onPermissionDenied Called when user clicks "Use App-Only Storage"
     */
    fun showPermissionRationaleDialog(
        activity: Activity,
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit
    ) {
        android.app.AlertDialog.Builder(activity)
            .setTitle("Welcome to Scriptler!")
            .setMessage(
                "Scriptler saves your scripts in the Documents/Scriptler/ folder so you can " +
                "access them from any file manager.\n\n" +
                "To do this, Scriptler needs \"All files access\" permission.\n\n" +
                "Your scripts and data stay on your device — nothing is uploaded.\n\n" +
                "If you prefer, you can use app-only storage (scripts won't be visible in file manager)."
            )
            .setPositiveButton("Grant Access") { _, _ ->
                onPermissionGranted()
            }
            .setNegativeButton("Use App-Only Storage") { dialog, _ ->
                Log.d(TAG, "User declined storage permission, using app-specific storage")
                dialog.dismiss()
                onPermissionDenied()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Get a human-readable description of the current storage mode.
     */
    fun getStorageModeDescription(context: Context): String {
        return when (getStorageMode(context)) {
            StorageMode.DEFAULT_PATH -> "Default: /storage/emulated/0/Documents/Scriptler/"
            StorageMode.SAF_CUSTOM -> {
                val uri = getSafTreeUri(context)
                if (uri != null) {
                    "Custom: ${uri.lastPathSegment ?: "SAF directory"}"
                } else {
                    "Custom (not configured)"
                }
            }
            StorageMode.APP_ONLY -> "App-only storage"
        }
    }
}
