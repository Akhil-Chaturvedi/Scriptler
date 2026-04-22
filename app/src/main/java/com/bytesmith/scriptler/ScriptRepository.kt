package com.bytesmith.scriptler

import android.content.Context
import android.util.Log
import com.bytesmith.scriptler.models.Script
import com.bytesmith.scriptler.models.ScriptLog
import com.bytesmith.scriptler.utils.FileUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.lang.reflect.Type
import java.util.UUID

class ScriptRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ScriptRepository"
        private const val SCRIPTS_FILE_NAME = "scripts_metadata.json"
        private const val SAVE_DEBOUNCE_MS = 500L
        private const val LOG_FLUSH_INTERVAL_MS = 5000L

        @Volatile
        private var instance: ScriptRepository? = null

        fun getInstance(context: Context): ScriptRepository {
            return instance ?: synchronized(this) {
                instance ?: ScriptRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val gson = Gson()
    private var scripts: MutableList<Script> = mutableListOf()

    // Coroutine scope for debounced saves and log buffering
    private val repositoryJob = SupervisorJob()
    private val repositoryScope = CoroutineScope(Dispatchers.Main + repositoryJob)

    // Debounce mechanism for script saves
    private var pendingSaveJob: Job? = null

    // Log buffering mechanism
    private val logBuffer = mutableListOf<ScriptLog>()
    private var logFlushJob: Job? = null
    private val logBufferLock = Any()

    init {
        // Load scripts synchronously on init (must complete before app is usable)
        runBlocking(Dispatchers.IO) {
            loadScriptsInternal()
        }
    }

    private suspend fun loadScriptsInternal() {
        try {
            val json = FileUtils.readInternalFile(context, SCRIPTS_FILE_NAME)
            if (!json.isNullOrEmpty()) {
                val listType: Type = object : TypeToken<ArrayList<Script>>() {}.type
                val loaded: MutableList<Script>? = gson.fromJson(json, listType)
                if (loaded != null) {
                    scripts = loaded
                    Log.d(TAG, "Scripts loaded successfully: ${scripts.size}")
                } else {
                    scripts = mutableListOf()
                }
            } else {
                scripts = mutableListOf()
                Log.d(TAG, "No scripts file found or file is empty, starting with empty list.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading scripts", e)
            scripts = mutableListOf()
        }
    }

    private suspend fun saveScriptsInternal() {
        try {
            val json = gson.toJson(scripts)
            FileUtils.writeInternalFile(context, SCRIPTS_FILE_NAME, json)
            Log.d(TAG, "Scripts saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving scripts", e)
        }
    }

    fun getAllScripts(): List<Script> = scripts.toList()

    fun getScriptById(id: String): Script? {
        return scripts.find { it.id == id }
    }

    fun getScriptByName(name: String): Script? {
        return scripts.find { it.name.equals(name, ignoreCase = true) }
    }

    /**
     * Save or update a script with debouncing.
     * Changes are coalesced - only one save happens 500ms after the last change.
     */
    fun saveOrUpdateScript(script: Script) {
        if (script.id.isEmpty()) {
            Log.e(TAG, "Script ID is empty, cannot save or update.")
            return
        }

        val existingIndex = scripts.indexOfFirst { it.id == script.id }
        if (existingIndex >= 0) {
            scripts[existingIndex] = script
            Log.d(TAG, "Script updated in repository: ${script.name}")
        } else {
            scripts.add(script)
            Log.d(TAG, "Script added to repository: ${script.name}")
        }

        // Debounce the save - cancel any pending save and schedule a new one
        pendingSaveJob?.cancel()
        pendingSaveJob = repositoryScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            withContext(Dispatchers.IO) {
                saveScriptsInternal()
            }
        }
    }

    /**
     * Force an immediate save (bypass debouncing).
     * Use this when the app is about to exit or needs guaranteed persistence.
     */
    fun saveScriptsImmediate() {
        pendingSaveJob?.cancel()
        repositoryScope.launch {
            withContext(Dispatchers.IO) {
                saveScriptsInternal()
            }
        }
    }

    fun deleteScript(id: String) {
        val scriptToRemove = scripts.find { it.id == id }
        if (scriptToRemove != null) {
            scripts.remove(scriptToRemove)
            FileUtils.deleteScriptFolder(scriptToRemove.name)
            FileUtils.deleteScriptLogsFile(context, scriptToRemove.id)
            
            // Immediate save on delete (debouncing could cause data loss)
            repositoryScope.launch {
                withContext(Dispatchers.IO) {
                    saveScriptsInternal()
                }
            }
            Log.d(TAG, "Script deleted from repository and files: ${scriptToRemove.name}")
        } else {
            Log.w(TAG, "Attempted to delete non-existent script with ID: $id")
        }
    }

    fun renameScript(id: String, newName: String): Boolean {
        val script = scripts.find { it.id == id }
        if (script != null) {
            val oldName = script.name
            // Rename the folder on disk
            val renamed = FileUtils.renameScriptFolder(oldName, newName)
            if (renamed) {
                // Update the script object with new name
                val updatedScript = script.copy(name = newName)
                val index = scripts.indexOfFirst { it.id == id }
                if (index >= 0) {
                    scripts[index] = updatedScript
                }
                // Immediate save on rename
                repositoryScope.launch {
                    withContext(Dispatchers.IO) {
                        saveScriptsInternal()
                    }
                }
                Log.d(TAG, "Script renamed from $oldName to $newName")
                return true
            } else {
                Log.e(TAG, "Failed to rename script folder from $oldName to $newName")
                return false
            }
        }
        return false
    }

    // --- Log management with buffering ---

    /**
     * Add a log entry with buffering.
     * Logs are buffered in memory and flushed to disk every 5 seconds
     * or when the buffer reaches a certain size.
     */
    fun addLogForScript(scriptId: String, log: ScriptLog) {
        if (scriptId.isEmpty()) {
            Log.e(TAG, "Script ID is empty, cannot add log.")
            return
        }
        
        synchronized(logBufferLock) {
            logBuffer.add(log)
        }
        
        // Schedule flush if not already scheduled
        if (logFlushJob?.isActive != true) {
            logFlushJob = repositoryScope.launch {
                delay(LOG_FLUSH_INTERVAL_MS)
                flushLogBuffer()
            }
        }
        
        Log.d(TAG, "Log buffered for script ID: $scriptId (buffer size: ${logBuffer.size})")
    }

    /**
     * Flush the log buffer to disk immediately.
     */
    private suspend fun flushLogBuffer() {
        val logsToFlush: List<ScriptLog>
        synchronized(logBufferLock) {
            if (logBuffer.isEmpty()) return
            logsToFlush = logBuffer.toList()
            logBuffer.clear()
        }
        
        withContext(Dispatchers.IO) {
            for (log in logsToFlush) {
                val logJson = gson.toJson(log)
                FileUtils.writeScriptLog(context, log.scriptId, logJson)
            }
        }
        Log.d(TAG, "Flushed ${logsToFlush.size} logs to disk")
    }

    /**
     * Force flush all buffered logs.
     * Call this when the app is about to exit.
     */
    fun flushLogsOnExit() {
        logFlushJob?.cancel()
        if (logBuffer.isNotEmpty()) {
            // Synchronous flush for app exit
            runBlocking(Dispatchers.IO) {
                val logsToFlush: List<ScriptLog>
                synchronized(logBufferLock) {
                    if (logBuffer.isEmpty()) return@runBlocking
                    logsToFlush = logBuffer.toList()
                    logBuffer.clear()
                }
                for (log in logsToFlush) {
                    val logJson = gson.toJson(log)
                    FileUtils.writeScriptLog(context, log.scriptId, logJson)
                }
            }
        }
    }

    /**
     * Get logs for a script. This reads directly from file (not buffer)
     * to ensure we get all logs including any unflushed ones.
     */
    suspend fun getLogsForScript(scriptId: String): List<ScriptLog> {
        val logs = mutableListOf<ScriptLog>()
        if (scriptId.isEmpty()) {
            Log.e(TAG, "Script ID is empty, cannot get logs.")
            return logs
        }

        val rawLogsContent = FileUtils.readScriptLogs(context, scriptId)
        if (rawLogsContent.isNullOrEmpty()) {
            return logs
        }

        val logLines = rawLogsContent.split("\n")
        for (line in logLines) {
            if (line.trim().isNotEmpty()) {
                try {
                    val log = gson.fromJson(line, ScriptLog::class.java)
                    logs.add(log)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing log line for script ID $scriptId: $line", e)
                }
            }
        }

        return logs
    }

    fun clearLogsForScript(scriptId: String) {
        if (scriptId.isEmpty()) {
            Log.e(TAG, "Script ID is empty, cannot clear logs.")
            return
        }
        FileUtils.deleteScriptLogsFile(context, scriptId)
        Log.d(TAG, "Logs cleared for script ID: $scriptId")
    }

    suspend fun getLogCountForScript(scriptId: String): Int {
        return getLogsForScript(scriptId).size
    }

    /**
     * Create a new script. This is a suspend function because it involves I/O.
     */
    suspend fun createNewScript(name: String, language: String): Script {
        val id = UUID.randomUUID().toString()
        val script = Script(
            id = id,
            name = name,
            language = language
        )
        
        // Create the script folder and initial file (on IO dispatcher)
        withContext(Dispatchers.IO) {
            FileUtils.createScriptFolder(name)
        }
        
        // Write default template code
        val template = when (language) {
            "python" -> "# Write your Python code here\n\ndef main():\n    print(\"Hello from Scriptler!\")\n\nif __name__ == \"__main__\":\n    main()\n"
            else -> "// Write your JavaScript code here\n\nfunction main() {\n    console.log(\"Hello from Scriptler!\");\n    return \"Script executed successfully\";\n}\n\nmain();\n"
        }
        
        withContext(Dispatchers.IO) {
            FileUtils.saveScript(name, template, language)
        }
        
        saveOrUpdateScript(script)
        return script
    }
}
