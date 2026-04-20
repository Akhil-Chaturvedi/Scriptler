package com.bytesmith.scriptler

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * Executes Python scripts using Chaquopy with:
 * - sys.path configuration for local module imports AND runtime-installed packages
 * - Import statement parsing for missing module detection
 * - Build-time and runtime package availability checking
 * - stdout/stderr capture
 *
 * sys.path order (highest priority first):
 * 1. Script folder (e.g., /storage/emulated/0/Documents/Scriptler/mango/)
 * 2. Runtime packages directory (e.g., /data/.../files/python_libs/)
 * 3. Chaquopy built-in packages (stdlib + build-time pip packages)
 */
class PythonExecutor(private val context: Context) {

    companion object {
        private const val TAG = "PythonExecutor"
    }

    data class ExecutionResult(
        val output: String,
        val isError: Boolean
    )

    fun execute(code: String, scriptFolder: String): ExecutionResult {
        // Ensure Python is started
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }

        val py = Python.getInstance()

        return try {
            // Execute via Chaquopy Python API:
            // 1. Configure sys.path to include the script folder
            // 2. Capture stdout and stderr via StringIO
            // 3. Execute the user's code
            val builtins = py.getBuiltins()
            val execFunc = builtins["exec"]!!
            
            // Set up sys.path first
            val sysModule = py.getModule("sys")
            val path = sysModule["path"]!!
            val insertMethod = path["insert"]!!
    
            // Priority 1: Script folder — allows import of local .py files
            insertMethod.call(0, scriptFolder)
    
            // Priority 2: Runtime-installed pure-Python packages directory
            // RuntimePipManager extracts wheels here; adding to sys.path makes them importable
            val runtimePip = RuntimePipManager(context)
            for (pathEntry in runtimePip.getSysPathEntries()) {
                insertMethod.call(1, pathEntry)
            }

            // Capture output using StringIO
            val ioModule = py.getModule("io")
            val stringIO = ioModule["StringIO"]!!
            val capturedOut = stringIO.call()
            val capturedErr = stringIO.call()

            // Redirect stdout/stderr
            sysModule["stdout"] = capturedOut
            sysModule["stderr"] = capturedErr

            try {
                // Execute the user's code
                execFunc.call(code)

                // Get captured output
                val stdout = capturedOut.call("getvalue").toString()
                val stderr = capturedErr.call("getvalue").toString()

                if (stderr.isNotEmpty()) {
                    ExecutionResult(stderr, true)
                } else {
                    ExecutionResult(
                        stdout.ifEmpty { "Script executed successfully (no output)" },
                        false
                    )
                }
            } catch (e: Exception) {
                val stderr = capturedErr.call("getvalue").toString()
                val errorMsg = if (stderr.isNotEmpty()) stderr else e.message ?: "Unknown Python error"
                ExecutionResult("Python Error: $errorMsg", true)
            } finally {
                // Restore stdout/stderr
                sysModule["stdout"] = sysModule["__stdout__"]
                sysModule["stderr"] = sysModule["__stderr__"]
            }
        } catch (e: Exception) {
            Log.e(TAG, "Python execution failed", e)
            ExecutionResult("Python Error: ${e.message}", true)
        }
    }

    /**
     * Parse import statements from Python code.
     * Returns a list of package names that are imported.
     */
    fun parseImports(code: String): List<String> {
        val imports = mutableListOf<String>()
        val importRegex = Regex("""^import\s+(\w+)|^from\s+(\w+)\s+import""", RegexOption.MULTILINE)

        importRegex.findAll(code).forEach { matchResult ->
            val packageName = matchResult.groupValues[1].ifEmpty { matchResult.groupValues[2] }
            if (packageName.isNotEmpty()) {
                imports.add(packageName)
            }
        }

        return imports.distinct()
    }

    /**
     * Check if an import is a local module (exists as .py file in script folder).
     */
    fun isLocalModule(importName: String, scriptFolder: String): Boolean {
        val folder = java.io.File(scriptFolder)
        val pyFile = java.io.File(folder, "$importName.py")
        val pyPackage = java.io.File(folder, "$importName/__init__.py")
        return pyFile.exists() || pyPackage.exists()
    }

    /**
     * Get the pip install name for an import name using the package name map.
     * For build-time packages, this maps import names to pip install names.
     * For runtime packages, RuntimePipManager handles the mapping.
     */
    fun getPipName(importName: String): String {
        return ModuleManager.getPackageNameMap(context)[importName] ?: importName
    }
    
    /**
     * Check if a Python module is available.
     * Checks both build-time (Chaquopy bundled) and runtime-installed packages.
     */
    fun isModuleAvailable(moduleName: String): Boolean {
        // Check runtime-installed packages first (fast, no Python interpreter needed)
        val runtimePip = RuntimePipManager(context)
        if (runtimePip.isInstalled(moduleName)) {
            return true
        }

        // Check build-time bundled packages
        return try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
            val py = Python.getInstance()
            py.getModule(moduleName)
            true
        } catch (e: Exception) {
            Log.d(TAG, "Module not available: $moduleName")
            false
        }
    }
}
