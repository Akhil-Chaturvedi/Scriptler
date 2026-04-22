package com.bytesmith.scriptler.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object FileUtils {

    private const val TAG = "FileUtils"
    private const val SCRIPTLER_DIR_NAME = "Scriptler"
    private const val LOGS_DIR_NAME = "logs"

    /**
     * Check if the app has permission to write to public external storage.
     * On Android 11+ (API 30+), this requires MANAGE_EXTERNAL_STORAGE.
     * On older versions, WRITE_EXTERNAL_STORAGE from manifest is sufficient.
     */
    fun hasExternalStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Get the base Scriptler directory.
     *
     * If the app has MANAGE_EXTERNAL_STORAGE permission (or is on Android < 11),
     * uses public Documents storage: /storage/emulated/0/Documents/Scriptler/
     *
     * If permission is not granted, falls back to app-specific external storage:
     * /storage/emulated/0/Android/data/com.bytesmith.scriptler/files/Scriptler/
     *
     * The fallback is still accessible to the user via file managers that support
     * app-specific directories, and it doesn't require any special permission.
     */
    fun getScriptlerBaseDir(): File {
        return if (hasExternalStorageAccess()) {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), SCRIPTLER_DIR_NAME)
        } else {
            // Fallback: app-specific external storage (no permission needed)
            File(Environment.getExternalStorageDirectory(), "Android/data/com.bytesmith.scriptler/files/Scriptler")
        }
    }

    /**
     * Get the preferred (public) Scriptler directory path, regardless of permission state.
     * Used for displaying the intended path to the user.
     */
    fun getPreferredBaseDirPath(): String {
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), SCRIPTLER_DIR_NAME).absolutePath
    }

    /**
     * Check if we're currently using the fallback (app-specific) storage.
     */
    fun isUsingFallbackStorage(): Boolean {
        return !hasExternalStorageAccess()
    }

    /**
     * Get the script folder for a specific script.
     * /storage/emulated/0/Documents/Scriptler/{scriptName}/
     */
    fun getScriptFolder(scriptName: String): File {
        return File(getScriptlerBaseDir(), scriptName)
    }

    /**
     * Get the script file path.
     * /storage/emulated/0/Documents/Scriptler/{scriptName}/{scriptName}.{ext}
     */
    fun getScriptFile(scriptName: String, language: String): File {
        val ext = when (language) {
            "python" -> "py"
            "javascript" -> "js"
            else -> "txt"
        }
        return File(getScriptFolder(scriptName), "$scriptName.$ext")
    }

    /**
     * Create the Scriptler base directory if it doesn't exist.
     */
    fun ensureScriptlerBaseDir(): Boolean {
        val dir = getScriptlerBaseDir()
        if (!dir.exists()) {
            val created = dir.mkdirs()
            if (created) {
                Log.d(TAG, "Scriptler base directory created: ${dir.absolutePath}")
            } else {
                Log.e(TAG, "Failed to create Scriptler base directory: ${dir.absolutePath}")
            }
            return created
        }
        return true
    }

    /**
     * Create a script folder in Documents/Scriptler/.
     */
    fun createScriptFolder(scriptName: String): Boolean {
        ensureScriptlerBaseDir()
        val scriptDir = getScriptFolder(scriptName)
        if (!scriptDir.exists()) {
            val created = scriptDir.mkdirs()
            if (created) {
                Log.d(TAG, "Script folder created: ${scriptDir.absolutePath}")
            } else {
                Log.e(TAG, "Failed to create script folder: ${scriptDir.absolutePath}")
            }
            return created
        }
        return true
    }

    /**
     * Save script code to the script file in Documents/Scriptler/{scriptName}/.
     * This is a suspend function that performs I/O on Dispatchers.IO.
     */
    suspend fun saveScript(scriptName: String, code: String, language: String) {
        withContext(Dispatchers.IO) {
            createScriptFolder(scriptName)
            val scriptFile = getScriptFile(scriptName, language)

            try {
                FileOutputStream(scriptFile).use { fos ->
                    OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                        writer.write(code)
                        Log.d(TAG, "Script saved: ${scriptFile.absolutePath}")
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error saving script: ${scriptFile.absolutePath}", e)
            }
        }
    }

    /**
     * Read script code from the script file in Documents/Scriptler/{scriptName}/.
     * This is a suspend function that performs I/O on Dispatchers.IO.
     */
    suspend fun readScript(scriptName: String, language: String): String {
        return withContext(Dispatchers.IO) {
            val scriptFile = getScriptFile(scriptName, language)

            if (!scriptFile.exists()) {
                Log.w(TAG, "Script file not found: ${scriptFile.absolutePath}")
                return@withContext ""
            }

            val stringBuilder = StringBuilder()
            try {
                FileInputStream(scriptFile).use { fis ->
                    InputStreamReader(fis, StandardCharsets.UTF_8).use { inputStreamReader ->
                        BufferedReader(inputStreamReader).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                stringBuilder.append(line).append('\n')
                            }
                            // Remove trailing newline
                            if (stringBuilder.isNotEmpty() && stringBuilder.last() == '\n') {
                                stringBuilder.deleteCharAt(stringBuilder.length - 1)
                            }
                            Log.d(TAG, "Script read successfully: ${scriptFile.absolutePath}")
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error reading script: ${scriptFile.absolutePath}", e)
                return@withContext ""
            }
            stringBuilder.toString()
        }
    }

    // --- Internal file handling (for script metadata JSON) ---

    /**
     * Write content to a private internal file in app's filesDir.
     * This is a suspend function that performs I/O on Dispatchers.IO.
     */
    suspend fun writeInternalFile(context: Context, fileName: String, content: String) {
        withContext(Dispatchers.IO) {
            try {
                context.openFileOutput(fileName, Context.MODE_PRIVATE).use { fos ->
                    OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                        writer.write(content)
                        Log.d(TAG, "Successfully wrote to internal file: $fileName")
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error writing to internal file: $fileName", e)
            }
        }
    }

    /**
     * Read content from a private internal file in app's filesDir.
     * This is a suspend function that performs I/O on Dispatchers.IO.
     */
    suspend fun readInternalFile(context: Context, fileName: String): String? {
        return withContext(Dispatchers.IO) {
            val stringBuilder = StringBuilder()
            try {
                context.openFileInput(fileName).use { fis ->
                    InputStreamReader(fis, StandardCharsets.UTF_8).use { inputStreamReader ->
                        BufferedReader(inputStreamReader).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                stringBuilder.append(line).append('\n')
                            }
                            if (stringBuilder.isNotEmpty() && stringBuilder.last() == '\n') {
                                stringBuilder.deleteCharAt(stringBuilder.length - 1)
                            }
                            Log.d(TAG, "Successfully read from internal file: $fileName")
                        }
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Internal file not found or error reading: $fileName - ${e.message}")
                return@withContext null
            }
            stringBuilder.toString()
        }
    }

    // --- Script deletion methods ---

    /**
     * Delete the entire script folder and all its contents.
     */
    fun deleteScriptFolder(scriptName: String): Boolean {
        val scriptDir = getScriptFolder(scriptName)
        return deleteRecursive(scriptDir)
    }

    /**
     * Delete a script code file and its directory if empty.
     */
    fun deleteScriptCodeFile(scriptName: String, language: String): Boolean {
        val scriptFile = getScriptFile(scriptName, language)

        var fileDeleted = false
        if (scriptFile.exists()) {
            fileDeleted = scriptFile.delete()
            if (fileDeleted) {
                Log.d(TAG, "Deleted script code file: ${scriptFile.absolutePath}")
            } else {
                Log.e(TAG, "Failed to delete script code file: ${scriptFile.absolutePath}")
            }
        } else {
            Log.w(TAG, "Script code file not found for deletion: ${scriptFile.absolutePath}")
        }

        // Optionally delete the script directory if it's empty
        val scriptDir = getScriptFolder(scriptName)
        if (scriptDir.exists() && scriptDir.isDirectory) {
            val contents = scriptDir.list()
            if (contents == null || contents.isEmpty()) {
                val dirDeleted = scriptDir.delete()
                if (dirDeleted) {
                    Log.d(TAG, "Deleted empty script directory: ${scriptDir.absolutePath}")
                }
            }
        }

        return fileDeleted
    }

    /**
     * Delete the script logs file (stored in app internal storage).
     */
    fun deleteScriptLogsFile(context: Context, scriptId: String): Boolean {
        val logsDir = File(context.filesDir, LOGS_DIR_NAME)
        if (!logsDir.exists()) {
            Log.w(TAG, "Logs directory not found for deletion: ${logsDir.absolutePath}")
            return false
        }
        val logsFile = File(logsDir, "${scriptId}_logs.json")

        var deleted = false
        if (logsFile.exists()) {
            deleted = logsFile.delete()
            if (deleted) {
                Log.d(TAG, "Deleted script logs file: ${logsFile.absolutePath}")
            } else {
                Log.e(TAG, "Failed to delete script logs file: ${logsFile.absolutePath}")
            }
        } else {
            Log.w(TAG, "Script logs file not found for deletion: ${logsFile.absolutePath}")
        }
        return deleted
    }

    // --- Script log management ---

    /**
     * Write a single log entry (append mode) to the script's log file.
     * This is a suspend function that performs I/O on Dispatchers.IO.
     */
    suspend fun writeScriptLog(context: Context, scriptId: String, logEntryJson: String) {
        withContext(Dispatchers.IO) {
            val logsDir = File(context.filesDir, LOGS_DIR_NAME)
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }
            val logsFile = File(logsDir, "${scriptId}_logs.json")

            try {
                FileOutputStream(logsFile, true).use { fos ->
                    OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                        writer.write(logEntryJson)
                        writer.write("\n")
                        Log.d(TAG, "Successfully wrote log entry to file: ${logsFile.absolutePath}")
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error writing log entry to file: ${logsFile.absolutePath}", e)
            }
        }
    }

    /**
     * Read all log entries for a script as raw string.
     * This is a suspend function that performs I/O on Dispatchers.IO.
     */
    suspend fun readScriptLogs(context: Context, scriptId: String): String? {
        return withContext(Dispatchers.IO) {
            val logsDir = File(context.filesDir, LOGS_DIR_NAME)
            if (!logsDir.exists()) {
                return@withContext null
            }
            val logsFile = File(logsDir, "${scriptId}_logs.json")

            if (!logsFile.exists()) {
                Log.w(TAG, "Script logs file not found: ${logsFile.absolutePath}")
                return@withContext null
            }

            val stringBuilder = StringBuilder()
            try {
                FileInputStream(logsFile).use { fis ->
                    InputStreamReader(fis, StandardCharsets.UTF_8).use { inputStreamReader ->
                        BufferedReader(inputStreamReader).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                stringBuilder.append(line).append('\n')
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error reading script logs: ${logsFile.absolutePath}", e)
                return@withContext null
            }
            stringBuilder.toString()
        }
    }

    // --- Rename script folder ---

    /**
     * Rename a script folder in Documents/Scriptler/.
     */
    fun renameScriptFolder(oldName: String, newName: String): Boolean {
        val oldDir = getScriptFolder(oldName)
        val newDir = getScriptFolder(newName)

        if (!oldDir.exists()) {
            Log.e(TAG, "Old script folder does not exist: ${oldDir.absolutePath}")
            return false
        }
        if (newDir.exists()) {
            Log.e(TAG, "New script folder already exists: ${newDir.absolutePath}")
            return false
        }

        val renamed = oldDir.renameTo(newDir)
        if (renamed) {
            Log.d(TAG, "Script folder renamed from $oldName to $newName")
        } else {
            Log.e(TAG, "Failed to rename script folder from $oldName to $newName")
        }
        return renamed
    }

    // --- Helper methods ---

    /**
     * Recursively delete a directory and all its contents.
     */
    private fun deleteRecursive(fileOrDir: File): Boolean {
        if (!fileOrDir.exists()) return false
        if (fileOrDir.isDirectory) {
            val children = fileOrDir.listFiles()
            if (children != null) {
                for (child in children) {
                    deleteRecursive(child)
                }
            }
        }
        val deleted = fileOrDir.delete()
        if (deleted) {
            Log.d(TAG, "Deleted: ${fileOrDir.absolutePath}")
        } else {
            Log.e(TAG, "Failed to delete: ${fileOrDir.absolutePath}")
        }
        return deleted
    }

    /**
     * Check if the Scriptler base directory exists.
     */
    fun scriptlerBaseDirExists(): Boolean {
        return getScriptlerBaseDir().exists()
    }

    /**
     * Get the list of files in a script folder.
     */
    fun getScriptFolderFiles(scriptName: String): Array<File>? {
        val folder = getScriptFolder(scriptName)
        return if (folder.exists() && folder.isDirectory) folder.listFiles() else null
    }
}
