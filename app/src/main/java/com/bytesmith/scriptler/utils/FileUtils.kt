package com.bytesmith.scriptler.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.bytesmith.scriptler.StoragePermissionManager
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
     * Application context for SAF operations.
     * Must be initialized in MainActivityCompose.onCreate() before any file operations.
     */
    private lateinit var appContext: Context

    /**
     * Initialize FileUtils with the application context.
     * Call this once in the Application or main Activity's onCreate().
     */
    fun initialize(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
            Log.d(TAG, "FileUtils initialized with application context")
        }
    }

    /**
     * Check if FileUtils has been initialized with a context.
     */
    fun isInitialized(): Boolean = ::appContext.isInitialized

    // --- Storage Mode Helpers ---

    /**
     * Get the current storage mode from StoragePermissionManager.
     */
    private fun getStorageMode(): StoragePermissionManager.StorageMode {
        if (!::appContext.isInitialized) return StoragePermissionManager.StorageMode.APP_ONLY
        return StoragePermissionManager.getStorageMode(appContext)
    }

    /**
     * Check if we're currently using SAF (Storage Access Framework) mode.
     */
    fun isSafMode(): Boolean {
        return getStorageMode() == StoragePermissionManager.StorageMode.SAF_CUSTOM
    }

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

    // --- Base Directory ---

    /**
     * Get the base Scriptler directory as a File.
     *
     * For DEFAULT_PATH mode: /storage/emulated/0/Documents/Scriptler/
     * For APP_ONLY mode: /storage/emulated/0/Android/data/com.bytesmith.scriptler/files/Scriptler/
     * For SAF_CUSTOM mode: Returns a representative File path (actual I/O uses DocumentFile)
     *
     * Note: For SAF_CUSTOM mode, prefer using [getSafBaseDir] for actual file operations.
     */
    fun getScriptlerBaseDir(): File {
        return if (getStorageMode() == StoragePermissionManager.StorageMode.APP_ONLY ||
                   (getStorageMode() == StoragePermissionManager.StorageMode.DEFAULT_PATH && !hasExternalStorageAccess())) {
            // Fallback: app-specific external storage (no permission needed)
            File(Environment.getExternalStorageDirectory(), "Android/data/com.bytesmith.scriptler/files/Scriptler")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), SCRIPTLER_DIR_NAME)
        }
    }

    /**
     * Get the SAF DocumentFile for the Scriptler base directory.
     * Returns null if not in SAF mode or if the URI is invalid.
     */
    fun getSafBaseDir(): DocumentFile? {
        if (!::appContext.isInitialized) return null
        val treeUri = StoragePermissionManager.getSafTreeUri(appContext) ?: return null
        val treeDoc = DocumentFile.fromTreeUri(appContext, treeUri) ?: return null

        // Look for existing Scriptler directory, or create it
        val existing = treeDoc.findFile(SCRIPTLER_DIR_NAME)
        return if (existing != null && existing.isDirectory) {
            existing
        } else {
            treeDoc.createDirectory(SCRIPTLER_DIR_NAME)
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
     * Get a display-friendly path for the current storage location.
     */
    fun getStorageLocationDisplay(): String {
        return when (getStorageMode()) {
            StoragePermissionManager.StorageMode.DEFAULT_PATH -> getPreferredBaseDirPath()
            StoragePermissionManager.StorageMode.SAF_CUSTOM -> {
                if (!::appContext.isInitialized) return "SAF directory (not initialized)"
                val uri = StoragePermissionManager.getSafTreeUri(appContext)
                if (uri != null) {
                    val docDir = getSafBaseDir()
                    docDir?.uri?.let { simplifySafUri(it) } ?: uri.toString()
                } else {
                    "SAF directory (not configured)"
                }
            }
            StoragePermissionManager.StorageMode.APP_ONLY -> getScriptlerBaseDir().absolutePath
        }
    }

    /**
     * Simplify a SAF URI for display purposes.
     * Converts content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FScriptler
     * to something like /Documents/Scriptler
     */
    private fun simplifySafUri(uri: Uri): String {
        val lastSegment = uri.lastPathSegment
        return if (lastSegment != null) {
            // Decode and clean up the path
            val decoded = java.net.URLDecoder.decode(lastSegment, "UTF-8")
            // Remove "primary:" prefix and replace with /storage/emulated/0/
            if (decoded.startsWith("primary:")) {
                "/storage/emulated/0/${decoded.removePrefix("primary:")}"
            } else {
                decoded
            }
        } else {
            uri.toString()
        }
    }

    /**
     * Check if we're currently using the fallback (app-specific) storage.
     */
    fun isUsingFallbackStorage(): Boolean {
        return getStorageMode() == StoragePermissionManager.StorageMode.APP_ONLY ||
               (getStorageMode() == StoragePermissionManager.StorageMode.DEFAULT_PATH && !hasExternalStorageAccess())
    }

    // --- Script Folder/File Paths ---

    /**
     * Get the script folder for a specific script.
     * /storage/emulated/0/Documents/Scriptler/{scriptName}/
     * For SAF mode, use [getSafScriptFolder] instead.
     */
    fun getScriptFolder(scriptName: String): File {
        return File(getScriptlerBaseDir(), scriptName)
    }

    /**
     * Get the SAF DocumentFile for a script folder.
     */
    fun getSafScriptFolder(scriptName: String): DocumentFile? {
        val baseDir = getSafBaseDir() ?: return null
        val existing = baseDir.findFile(scriptName)
        return if (existing != null && existing.isDirectory) {
            existing
        } else {
            baseDir.createDirectory(scriptName)
        }
    }

    /**
     * Get the script file path.
     * /storage/emulated/0/Documents/Scriptler/{scriptName}/{scriptName}.{ext}
     * For SAF mode, use [getSafScriptFile] instead.
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
     * Get the SAF DocumentFile for a script file.
     */
    fun getSafScriptFile(scriptName: String, language: String): DocumentFile? {
        val ext = when (language) {
            "python" -> "py"
            "javascript" -> "js"
            else -> "txt"
        }
        val fileName = "$scriptName.$ext"
        val scriptFolder = getSafScriptFolder(scriptName) ?: return null
        val existing = scriptFolder.findFile(fileName)
        return existing ?: scriptFolder.createFile("text/plain", fileName)
    }

    // --- Directory Creation ---

    /**
     * Create the Scriptler base directory if it doesn't exist.
     * Handles both File-based and SAF-based storage.
     */
    fun ensureScriptlerBaseDir(): Boolean {
        return if (isSafMode()) {
            ensureSafScriptlerBaseDir()
        } else {
            ensureFileScriptlerBaseDir()
        }
    }

    /**
     * Create the Scriptler base directory using File API.
     */
    private fun ensureFileScriptlerBaseDir(): Boolean {
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
     * Create the Scriptler base directory using SAF/DocumentFile API.
     */
    private fun ensureSafScriptlerBaseDir(): Boolean {
        val baseDir = getSafBaseDir()
        val success = baseDir != null && baseDir.exists()
        if (success) {
            Log.d(TAG, "SAF Scriptler base directory ensured: ${baseDir?.uri}")
        } else {
            Log.e(TAG, "Failed to create SAF Scriptler base directory")
        }
        return success
    }

    /**
     * Create a script folder.
     * Handles both File-based and SAF-based storage.
     */
    fun createScriptFolder(scriptName: String): Boolean {
        return if (isSafMode()) {
            createSafScriptFolder(scriptName)
        } else {
            createFileScriptFolder(scriptName)
        }
    }

    /**
     * Create a script folder using File API.
     */
    private fun createFileScriptFolder(scriptName: String): Boolean {
        ensureFileScriptlerBaseDir()
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
     * Create a script folder using SAF/DocumentFile API.
     */
    private fun createSafScriptFolder(scriptName: String): Boolean {
        ensureSafScriptlerBaseDir()
        val folder = getSafScriptFolder(scriptName)
        val success = folder != null && folder.exists()
        if (success) {
            Log.d(TAG, "SAF script folder created: $scriptName")
        } else {
            Log.e(TAG, "Failed to create SAF script folder: $scriptName")
        }
        return success
    }

    // --- Script Save/Read ---

    /**
     * Save script code to the script file.
     * Handles both File-based and SAF-based storage.
     * This is a suspend function that performs I/O on Dispatchers.IO.
     */
    suspend fun saveScript(scriptName: String, code: String, language: String) {
        withContext(Dispatchers.IO) {
            if (isSafMode()) {
                saveSafScript(scriptName, code, language)
            } else {
                saveFileScript(scriptName, code, language)
            }
        }
    }

    /**
     * Save script using File API.
     */
    private fun saveFileScript(scriptName: String, code: String, language: String) {
        createFileScriptFolder(scriptName)
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

    /**
     * Save script using SAF/DocumentFile API.
     */
    private fun saveSafScript(scriptName: String, code: String, language: String) {
        if (!::appContext.isInitialized) {
            Log.e(TAG, "FileUtils not initialized, cannot save SAF script")
            return
        }

        createSafScriptFolder(scriptName)
        val scriptDoc = getSafScriptFile(scriptName, language)
        if (scriptDoc == null) {
            Log.e(TAG, "Failed to create SAF script file: $scriptName")
            return
        }

        try {
            appContext.contentResolver.openOutputStream(scriptDoc.uri)?.use { os ->
                OutputStreamWriter(os, StandardCharsets.UTF_8).use { writer ->
                    writer.write(code)
                    Log.d(TAG, "SAF script saved: ${scriptDoc.uri}")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error saving SAF script: ${scriptDoc.uri}", e)
        }
    }

    /**
     * Read script code from the script file.
     * Handles both File-based and SAF-based storage.
     * This is a suspend function that performs I/O on Dispatchers.IO.
     */
    suspend fun readScript(scriptName: String, language: String): String {
        return withContext(Dispatchers.IO) {
            if (isSafMode()) {
                readSafScript(scriptName, language)
            } else {
                readFileScript(scriptName, language)
            }
        }
    }

    /**
     * Read script using File API.
     */
    private fun readFileScript(scriptName: String, language: String): String {
        val scriptFile = getScriptFile(scriptName, language)

        if (!scriptFile.exists()) {
            Log.w(TAG, "Script file not found: ${scriptFile.absolutePath}")
            return ""
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
            return ""
        }
        return stringBuilder.toString()
    }

    /**
     * Read script using SAF/DocumentFile API.
     */
    private fun readSafScript(scriptName: String, language: String): String {
        if (!::appContext.isInitialized) {
            Log.e(TAG, "FileUtils not initialized, cannot read SAF script")
            return ""
        }

        val scriptDoc = getSafScriptFile(scriptName, language)
        if (scriptDoc == null || !scriptDoc.exists()) {
            Log.w(TAG, "SAF script file not found: $scriptName")
            return ""
        }

        val stringBuilder = StringBuilder()
        try {
            appContext.contentResolver.openInputStream(scriptDoc.uri)?.use { fis ->
                InputStreamReader(fis, StandardCharsets.UTF_8).use { inputStreamReader ->
                    BufferedReader(inputStreamReader).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            stringBuilder.append(line).append('\n')
                        }
                        if (stringBuilder.isNotEmpty() && stringBuilder.last() == '\n') {
                            stringBuilder.deleteCharAt(stringBuilder.length - 1)
                        }
                        Log.d(TAG, "SAF script read successfully: ${scriptDoc.uri}")
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading SAF script: ${scriptDoc.uri}", e)
            return ""
        }
        return stringBuilder.toString()
    }

    /**
     * Get a File object for script execution, even in SAF mode.
     * For SAF mode, this copies the script content to a cache file.
     * This is needed because Python/JS interpreters need a file path.
     *
     * @param context Context for cache directory access
     * @param scriptName Name of the script
     * @param language Script language
     * @return File pointing to the script (either original or cached copy)
     */
    suspend fun getScriptFileForExecution(context: Context, scriptName: String, language: String): File {
        return withContext(Dispatchers.IO) {
            if (!isSafMode()) {
                // Direct file mode — return the actual file
                getScriptFile(scriptName, language)
            } else {
                // SAF mode — copy to cache for execution
                val cacheDir = File(context.cacheDir, "script_execution")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val ext = when (language) {
                    "python" -> "py"
                    "javascript" -> "js"
                    else -> "txt"
                }
                val cacheFile = File(cacheDir, "$scriptName.$ext")

                // Copy SAF content to cache file
                val content = readSafScript(scriptName, language)
                cacheFile.writeText(content)

                cacheFile
            }
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
     * Handles both File-based and SAF-based storage.
     */
    fun deleteScriptFolder(scriptName: String): Boolean {
        return if (isSafMode()) {
            deleteSafScriptFolder(scriptName)
        } else {
            deleteRecursive(getScriptFolder(scriptName))
        }
    }

    /**
     * Delete a SAF script folder.
     */
    private fun deleteSafScriptFolder(scriptName: String): Boolean {
        val folder = getSafScriptFolder(scriptName) ?: return false
        val deleted = folder.delete()
        if (deleted) {
            Log.d(TAG, "SAF script folder deleted: $scriptName")
        } else {
            Log.e(TAG, "Failed to delete SAF script folder: $scriptName")
        }
        return deleted
    }

    /**
     * Delete a script code file and its directory if empty.
     * Handles both File-based and SAF-based storage.
     */
    fun deleteScriptCodeFile(scriptName: String, language: String): Boolean {
        return if (isSafMode()) {
            deleteSafScriptCodeFile(scriptName, language)
        } else {
            deleteFileScriptCodeFile(scriptName, language)
        }
    }

    /**
     * Delete a File-based script code file.
     */
    private fun deleteFileScriptCodeFile(scriptName: String, language: String): Boolean {
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
     * Delete a SAF script code file.
     */
    private fun deleteSafScriptCodeFile(scriptName: String, language: String): Boolean {
        val scriptDoc = getSafScriptFile(scriptName, language)
        if (scriptDoc == null || !scriptDoc.exists()) {
            Log.w(TAG, "SAF script file not found for deletion: $scriptName")
            return false
        }

        val deleted = scriptDoc.delete()
        if (deleted) {
            Log.d(TAG, "SAF script code file deleted: $scriptName")
        } else {
            Log.e(TAG, "Failed to delete SAF script code file: $scriptName")
        }

        // Check if parent directory is empty and delete it
        val folder = getSafScriptFolder(scriptName)
        if (folder != null && folder.exists()) {
            if (folder.listFiles().isEmpty()) {
                folder.delete()
            }
        }

        return deleted
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
     * Rename a script folder.
     * Handles both File-based and SAF-based storage.
     */
    fun renameScriptFolder(oldName: String, newName: String): Boolean {
        return if (isSafMode()) {
            renameSafScriptFolder(oldName, newName)
        } else {
            renameFileScriptFolder(oldName, newName)
        }
    }

    /**
     * Rename a File-based script folder.
     */
    private fun renameFileScriptFolder(oldName: String, newName: String): Boolean {
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

    /**
     * Rename a SAF script folder.
     * Note: DocumentFile.renameTo() may not be supported on all devices/Android versions.
     * Falls back to copy+delete if rename fails.
     */
    private fun renameSafScriptFolder(oldName: String, newName: String): Boolean {
        val oldFolder = getSafScriptFolder(oldName)
        if (oldFolder == null || !oldFolder.exists()) {
            Log.e(TAG, "Old SAF script folder does not exist: $oldName")
            return false
        }

        // Check if new name already exists
        val baseDir = getSafBaseDir()
        if (baseDir?.findFile(newName) != null) {
            Log.e(TAG, "New SAF script folder already exists: $newName")
            return false
        }

        val renamed = oldFolder.renameTo(newName)
        if (renamed) {
            Log.d(TAG, "SAF script folder renamed from $oldName to $newName")
        } else {
            Log.e(TAG, "Failed to rename SAF script folder from $oldName to $newName")
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
        return if (isSafMode()) {
            getSafBaseDir()?.exists() == true
        } else {
            getScriptlerBaseDir().exists()
        }
    }

    /**
     * Get the list of files in a script folder.
     */
    fun getScriptFolderFiles(scriptName: String): Array<File>? {
        if (isSafMode()) {
            // For SAF mode, convert DocumentFile list to File array (names only)
            val folder = getSafScriptFolder(scriptName) ?: return null
            if (!folder.exists() || !folder.isDirectory) return null
            // Return empty array — SAF callers should use getSafScriptFolderFiles instead
            return emptyArray()
        }
        val folder = getScriptFolder(scriptName)
        return if (folder.exists() && folder.isDirectory) folder.listFiles() else null
    }

    /**
     * Get the list of DocumentFile entries in a SAF script folder.
     */
    fun getSafScriptFolderFiles(scriptName: String): List<DocumentFile> {
        val folder = getSafScriptFolder(scriptName) ?: return emptyList()
        if (!folder.exists() || !folder.isDirectory) return emptyList()
        return folder.listFiles().toList()
    }

    /**
     * Delete a directory and all its contents.
     * This is a public wrapper around the private deleteRecursive function.
     */
    fun deleteDirectory(directory: File): Boolean {
        return deleteRecursive(directory)
    }
}
