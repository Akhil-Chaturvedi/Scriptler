package com.bytesmith.scriptler

import android.content.Context
import android.util.Log
import com.bytesmith.scriptler.models.Script
import com.bytesmith.scriptler.models.ScriptLog
import com.bytesmith.scriptler.utils.FileUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.UUID

class ScriptRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ScriptRepository"
        private const val SCRIPTS_FILE_NAME = "scripts_metadata.json"

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

    init {
        loadScripts()
    }

    private fun loadScripts() {
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

    private fun saveScripts() {
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

        saveScripts()
    }

    fun deleteScript(id: String) {
        val scriptToRemove = scripts.find { it.id == id }
        if (scriptToRemove != null) {
            scripts.remove(scriptToRemove)
            FileUtils.deleteScriptFolder(scriptToRemove.name)
            FileUtils.deleteScriptLogsFile(context, scriptToRemove.id)
            saveScripts()
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
                saveScripts()
                Log.d(TAG, "Script renamed from $oldName to $newName")
                return true
            } else {
                Log.e(TAG, "Failed to rename script folder from $oldName to $newName")
                return false
            }
        }
        return false
    }

    // --- Log management ---

    fun addLogForScript(scriptId: String, log: ScriptLog) {
        if (scriptId.isEmpty()) {
            Log.e(TAG, "Script ID is empty, cannot add log.")
            return
        }
        val logJson = gson.toJson(log)
        FileUtils.writeScriptLog(context, scriptId, logJson)
        Log.d(TAG, "Log added for script ID: $scriptId")
    }

    fun getLogsForScript(scriptId: String): List<ScriptLog> {
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

    fun getLogCountForScript(scriptId: String): Int {
        return getLogsForScript(scriptId).size
    }

    fun createNewScript(name: String, language: String): Script {
        val id = UUID.randomUUID().toString()
        val script = Script(
            id = id,
            name = name,
            language = language
        )
        // Create the script folder and initial file
        FileUtils.createScriptFolder(name)

        // Write default template code
        val template = when (language) {
            "python" -> "# Write your Python code here\n\ndef main():\n    print(\"Hello from Scriptler!\")\n\nif __name__ == \"__main__\":\n    main()\n"
            else -> "// Write your JavaScript code here\n\nfunction main() {\n    console.log(\"Hello from Scriptler!\");\n    return \"Script executed successfully\";\n}\n\nmain();\n"
        }
        FileUtils.saveScript(name, template, language)

        saveOrUpdateScript(script)
        return script
    }
}
