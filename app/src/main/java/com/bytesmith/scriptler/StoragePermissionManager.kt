package com.bytesmith.scriptler

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * Manages storage permissions for Scriptler.
 *
 * On Android 11+ (API 30+), we need MANAGE_EXTERNAL_STORAGE to write to
 * /storage/emulated/0/Documents/Scriptler/. Without it, the app silently fails
 * to create directories or write files.
 *
 * This class handles:
 * - Checking if the permission is granted
 * - Showing an explanation dialog before requesting
 * - Creating the system settings intent for the user to grant it
 * - Providing a fallback to app-specific storage if permission is denied
 * - Tracking whether the first-run setup has been completed
 */
object StoragePermissionManager {

    private const val TAG = "StoragePermManager"
    private const val PREFS_FIRST_RUN_COMPLETE = "first_run_setup_complete"
    const val REQUEST_CODE_MANAGE_STORAGE = 1001

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

    /**
     * Create an intent to request MANAGE_EXTERNAL_STORAGE permission.
     * Returns null if the intent cannot be created.
     * 
     * Use this with ActivityResultLauncher:
     * ```
     * val intent = StoragePermissionManager.createStoragePermissionIntent(activity)
     * if (intent != null) {
     *     storagePermissionLauncher.launch(intent)
     * }
     * ```
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
     * Show a dialog explaining why Scriptler needs storage permission.
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
        AlertDialog.Builder(activity)
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
     * Get the request code used for the storage permission intent.
     */
    fun getRequestCode(): Int = REQUEST_CODE_MANAGE_STORAGE
}
