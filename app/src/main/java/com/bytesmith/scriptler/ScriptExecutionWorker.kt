package com.bytesmith.scriptler

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.bytesmith.scriptler.models.ScriptLog
import com.bytesmith.scriptler.utils.FileUtils
import java.util.UUID

class ScriptExecutionWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "ScriptExecutionWorker"
    }

    override fun doWork(): Result {
        val scriptId = inputData.getString("script_id") ?: return Result.failure()
        val scriptName = inputData.getString("script_name") ?: return Result.failure()
        val scriptLanguage = inputData.getString("script_language") ?: return Result.failure()

        Log.d(TAG, "Executing script: $scriptName (ID: $scriptId, Language: $scriptLanguage)")

        val scriptRepository = ScriptRepository.getInstance(applicationContext)
        val script = scriptRepository.getScriptById(scriptId)

        if (script == null) {
            Log.e(TAG, "Script not found with ID: $scriptId")
            return Result.failure()
        }

        return try {
            val scriptRunner = ScriptRunner(applicationContext)

            // For Python scripts, auto-install missing packages before execution
            // (scheduled runs have no UI, so we install silently)
            var result = scriptRunner.execute(script)

            // If execution failed with a ModuleNotFoundError, try auto-installing missing packages
            if (result.isError && script.language == "python" && isModuleNotFoundError(result.output)) {
                Log.d(TAG, "ModuleNotFoundError detected, attempting auto-install for: $scriptName")
                val checkResult = scriptRunner.checkImports(script)
                val installablePackages = checkResult.installablePackages

                if (installablePackages.isNotEmpty()) {
                    val runtimePip = RuntimePipManager(applicationContext)
                    if (runtimePip.isNetworkAvailable()) {
                        // Get pip names for the missing import names
                        val executor = PythonExecutor(applicationContext)
                        val pipNames = installablePackages.map { executor.getPipName(it) }

                        Log.d(TAG, "Auto-installing packages for scheduled run: $pipNames")
                        for (pipName in pipNames) {
                            val installResult = runtimePip.installPackageWithDependencies(pipName)
                            if (installResult.isFailure) {
                                Log.w(TAG, "Failed to auto-install $pipName: ${installResult.exceptionOrNull()?.message}")
                            }
                        }
                        // Re-run the script after installing
                        result = scriptRunner.execute(script)
                    } else {
                        Log.w(TAG, "No internet connection — cannot auto-install missing packages for scheduled run")
                    }
                }
            }

            // Create log entry
            val logEntry = ScriptLog(
                id = UUID.randomUUID().toString(),
                scriptId = scriptId,
                timestamp = System.currentTimeMillis(),
                runNumber = scriptRepository.getLogCountForScript(scriptId) + 1,
                output = result.output,
                status = if (result.isError) "error" else "success",
                isError = result.isError
            )
            scriptRepository.addLogForScript(scriptId, logEntry)

            // Update script lastRun and nextRun
            val updatedScript = script.copy(lastRun = System.currentTimeMillis())
            scriptRepository.saveOrUpdateScript(updatedScript)

            // Send notification if enabled
            val preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val notificationsEnabled = preferences.getBoolean("notifications_enabled", false)
            if (notificationsEnabled) {
                val notifTitle = "$scriptName ${if (result.isError) "Failed" else "Completed"}"
                val notifMessage = if (result.isError) "Error: ${NotificationUtils.truncateForNotification(result.output)}"
                                   else NotificationUtils.truncateForNotification(result.output)
                NotificationUtils.sendNotification(applicationContext, notifTitle, notifMessage)
            }

            Log.d(TAG, "Script execution completed: $scriptName")
            if (result.isError) Result.failure() else Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing script: $scriptName", e)

            // Log the error
            val logEntry = ScriptLog(
                id = UUID.randomUUID().toString(),
                scriptId = scriptId,
                timestamp = System.currentTimeMillis(),
                runNumber = scriptRepository.getLogCountForScript(scriptId) + 1,
                output = "Execution error: ${e.message}",
                status = "error",
                isError = true
            )
            scriptRepository.addLogForScript(scriptId, logEntry)

            Result.failure()
        }
    }

    /**
     * Check if the error output indicates a Python ModuleNotFoundError.
     * This is used to trigger auto-install for scheduled runs.
     */
    private fun isModuleNotFoundError(output: String): Boolean {
        return output.contains("ModuleNotFoundError") || output.contains("No module named")
    }
}
