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
 * - Launching the system settings intent for the user to grant it
 * - Providing a fallback to app-specific storage if permission is denied
 * - Tracking whether the first-run setup has been completed
 */
object StoragePermissionManager {

    private const val TAG = "StoragePermManager"
    private const val PREFS_FIRST_RUN_COMPLETE = "first_run_setup_complete"
    private const val REQUEST_CODE_MANAGE_STORAGE = 1001

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
     * Request MANAGE_EXTERNAL_STORAGE permission by launching the system settings.
     * On Android 11+, this opens the "All files access" settings page.
     * On older versions, this is a no-op (permission is already granted).
     */
    fun requestStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
            } catch (e: Exception) {
                Log.e(TAG, "Could not open manage storage settings", e)
                // Fallback: open general app settings
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
                } catch (e2: Exception) {
                    Log.e(TAG, "Could not open app settings either", e2)
                }
            }
        }
    }

    /**
     * Show a dialog explaining why Scriptler needs storage permission,
     * then request it if the user agrees.
     */
    fun showPermissionRationaleDialog(activity: Activity, onResult: (granted: Boolean) -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Storage Access Required")
            .setMessage(
                "Scriptler needs access to your device storage to save scripts and data files " +
                "in the Documents/Scriptler/ folder.\n\n" +
                "This allows you to:\n" +
                "• Access your scripts via any file manager\n" +
                "• Add data files (CSV, JSON, etc.) to script folders\n" +
                "• Share scripts between devices\n\n" +
                "Your scripts and data stay on your device — nothing is uploaded."
            )
            .setPositiveButton("Grant Access") { _, _ ->
                requestStoragePermission(activity)
                // onResult will be called in onActivityResult
            }
            .setNegativeButton("Use App-Only Storage") { dialog, _ ->
                Log.d(TAG, "User declined storage permission, using app-specific storage")
                dialog.dismiss()
                onResult(false)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Handle the result of the storage permission request.
     * Call this from Activity.onActivityResult.
     *
     * @return true if permission is now granted, false otherwise
     */
    fun handlePermissionResult(activity: Activity): Boolean {
        val granted = hasStoragePermission()
        if (granted) {
            Log.d(TAG, "Storage permission granted after settings visit")
            markFirstRunComplete(activity)
        } else {
            Log.w(TAG, "Storage permission still not granted after settings visit")
        }
        return granted
    }

    /**
     * Get the request code used for the storage permission intent.
     */
    fun getRequestCode(): Int = REQUEST_CODE_MANAGE_STORAGE
}
