package com.bytesmith.scriptler

import android.content.Context
import android.util.Log
import com.bytesmith.scriptler.models.Script
import com.bytesmith.scriptler.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Unified interface for script execution.
 * Delegates to PythonExecutor or JavaScriptExecutor based on script language.
 *
 * Features:
 * - Auto-import detection for Python scripts
 * - Friendly error messages for common Python/JS errors
 * - Execution timeout enforcement
 * - Categorization of missing packages (installable vs native-only)
 */
class ScriptRunner(private val context: Context) {

    companion object {
        private const val TAG = "ScriptRunner"
        private const val EXECUTION_TIMEOUT_MS = 60_000L // 60 seconds
    }

    data class ExecutionResult(
        val output: String,
        val isError: Boolean
    )

    /**
     * Result of checking a script's imports before execution.
     * Contains lists of missing packages categorized by whether they can be installed at runtime.
     */
    data class ImportCheckResult(
        val missingPackages: List<String>,
        val installablePackages: List<String>, // Pure-Python, can be installed at runtime
        val nativeOnlyPackages: List<String>   // Require C extensions, must be pre-bundled
    )

    /**
     * Execute a script with timeout enforcement.
     * If execution exceeds EXECUTION_TIMEOUT_MS, it will be interrupted and a friendly
     * timeout message will be returned.
     */
    fun execute(script: Script): ExecutionResult {
        val code = runBlocking(Dispatchers.IO) {
            FileUtils.readScript(script.name, script.language)
        }
        if (code.isEmpty()) {
            return ExecutionResult(
                "⚠️ Script file is empty or not found.\n\n" +
                "Make sure the script file exists in:\n" +
                "${FileUtils.getScriptFolder(script.name).absolutePath}",
                true
            )
        }

        val scriptFolder = FileUtils.getScriptFolder(script.name).absolutePath

        return when (script.language) {
            "python" -> executeWithTimeout("Python") { executePython(code, scriptFolder) }
            "javascript" -> executeWithTimeout("JavaScript") { executeJavaScript(code, scriptFolder) }
            else -> ExecutionResult("❌ Unsupported language: ${script.language}. Only Python and JavaScript are supported.", true)
        }
    }

    /**
     * Execute a block of code with a timeout.
     * If the execution exceeds EXECUTION_TIMEOUT_MS, returns a friendly timeout error.
     */
    private fun executeWithTimeout(language: String, block: () -> ExecutionResult): ExecutionResult {
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit(block)

        return try {
            future.get(EXECUTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            Log.w(TAG, "$language execution timed out after ${EXECUTION_TIMEOUT_MS}ms")
            ExecutionResult(
                "⏱️ Script timed out after ${EXECUTION_TIMEOUT_MS / 1000} seconds.\n\n" +
                "This usually means your script is stuck in an infinite loop or waiting for input.\n\n" +
                "Tips:\n" +
                "• Check for infinite loops (while True without break)\n" +
                "• Make sure input() is not being called\n" +
                "• Add timeouts to network requests",
                true
            )
        } catch (e: Exception) {
            Log.e(TAG, "$language execution failed", e)
            ExecutionResult(makeFriendlyError(language, e), true)
        } finally {
            executor.shutdownNow()
        }
    }

    /**
     * Check a Python script's imports and categorize missing packages.
     * This should be called before execution to determine if any packages need installation.
     *
     * @param script The script to check
     * @return ImportCheckResult with categorized missing packages
     */
    fun checkImports(script: Script): ImportCheckResult {
        if (script.language != "python") {
            return ImportCheckResult(emptyList(), emptyList(), emptyList())
        }

        val code = runBlocking(Dispatchers.IO) {
            FileUtils.readScript(script.name, script.language)
        }
        if (code.isEmpty()) {
            return ImportCheckResult(emptyList(), emptyList(), emptyList())
        }

        val scriptFolder = FileUtils.getScriptFolder(script.name).absolutePath
        val executor = PythonExecutor(context)
        
        // Use ImportDetector to extract imports from the script
        val imports = ImportDetector.extractImports(code)
    
        val missing = mutableListOf<String>()
        val installable = mutableListOf<String>()
        val nativeOnly = mutableListOf<String>()
    
        val runtimePip = RuntimePipManager(context)
    
        for (importName in imports) {
            // Skip local modules (files in the script folder)
            if (executor.isLocalModule(importName, scriptFolder)) continue
    
            // Skip stdlib modules using ImportDetector
            if (ImportDetector.isStdlibModule(importName)) continue
    
            // Skip already available packages (build-time or runtime)
            if (executor.isModuleAvailable(importName)) continue
    
            // This import is missing — categorize it
            missing.add(importName)
    
            // Use analyzePackage for better pure-Python vs native detection
            val analysisResult = runtimePip.analyzePackage(importName)
            if (analysisResult.isSuccess) {
                val analysis = analysisResult.getOrThrow()
                if (analysis.isPurePython) {
                    installable.add(importName)
                } else {
                    nativeOnly.add(importName)
                }
            } else {
                // Couldn't query PyPI — assume it might be installable
                // (will be verified when the user tries to install)
                installable.add(importName)
            }
        }

        return ImportCheckResult(
            missingPackages = missing.distinct(),
            installablePackages = installable.distinct(),
            nativeOnlyPackages = nativeOnly.distinct()
        )
    }

    /**
     * Execute a Python script after installing missing packages.
     * This is called after the user approves installation in ModuleInstallDialog.
     *
     * @param script The script to execute
     * @param packagesToInstall List of pip package names to install before execution
     * @param onProgress Callback for installation progress
     * @return ExecutionResult
     */
    fun executeWithInstall(
        script: Script,
        packagesToInstall: List<String>,
        onProgress: ((String, Int) -> Unit)? = null
    ): ExecutionResult {
        val runtimePip = RuntimePipManager(context)

        // Install missing packages first
        for (pipName in packagesToInstall) {
            val result = runtimePip.installPackageWithDependencies(pipName, onProgress)
            if (result.isFailure) {
                Log.w(TAG, "Failed to install $pipName: ${result.exceptionOrNull()?.message}")
                // Continue anyway — the script might still work if the package is optional
            }
        }

        // Now execute the script
        return execute(script)
    }

    private fun executePython(code: String, scriptFolder: String): ExecutionResult {
        return try {
            val executor = PythonExecutor(context)
            val result = executor.execute(code, scriptFolder)
            ExecutionResult(
                makeFriendlyOutput(result.output, result.isError, "Python"),
                result.isError
            )
        } catch (e: Exception) {
            Log.e(TAG, "Python execution error", e)
            ExecutionResult(makeFriendlyError("Python", e), true)
        }
    }

    private fun executeJavaScript(code: String, scriptFolder: String): ExecutionResult {
        return try {
            val executor = JavaScriptExecutor()
            val result = executor.execute(code, scriptFolder)
            ExecutionResult(
                makeFriendlyOutput(result.output, result.isError, "JavaScript"),
                result.isError
            )
        } catch (e: Exception) {
            Log.e(TAG, "JavaScript execution error", e)
            ExecutionResult(makeFriendlyError("JavaScript", e), true)
        }
    }

    /**
     * Convert raw error output into user-friendly messages.
     * Handles common Python and JavaScript error patterns.
     */
    private fun makeFriendlyOutput(output: String, isError: Boolean, language: String): String {
        if (!isError) return output

        // Python error patterns
        if (language == "Python") {
            return makeFriendlyPythonError(output)
        }

        // JavaScript error patterns
        if (language == "JavaScript") {
            return makeFriendlyJSError(output)
        }

        return output
    }

    /**
     * Convert Python tracebacks into user-friendly messages.
     */
    private fun makeFriendlyPythonError(rawError: String): String {
        // ModuleNotFoundError — missing package
        val moduleNotFoundRegex = Regex("ModuleNotFoundError:\\s*No module named '?(\\w+)'?")
        val moduleMatch = moduleNotFoundRegex.find(rawError)
        if (moduleMatch != null) {
            val moduleName = moduleMatch.groupValues[1]
            return "📦 Missing package: '$moduleName'\n\n" +
                   "This script requires the '$moduleName' package which is not installed.\n\n" +
                   "Tap 'Run Now' again — Scriptler will offer to install it automatically " +
                   "(if it's a pure-Python package).\n\n" +
                   "If it's a native package (like numpy, pandas), it needs to be bundled " +
                   "in the app at build time."
        }

        // ImportError — similar to ModuleNotFoundError
        val importErrorRegex = Regex("ImportError:\\s*(.+)")
        val importMatch = importErrorRegex.find(rawError)
        if (importMatch != null) {
            val detail = importMatch.groupValues[1]
            return "📦 Import error: $detail\n\n" +
                   "This usually means a package is installed but something is wrong with it.\n" +
                   "Try reinstalling the package from the Package Manager."
        }

        // SyntaxError
        val syntaxErrorRegex = Regex("SyntaxError:\\s*(.+)")
        val syntaxMatch = syntaxErrorRegex.find(rawError)
        if (syntaxMatch != null) {
            val detail = syntaxMatch.groupValues[1]
            return "✏️ Syntax error: $detail\n\n" +
                   "There's a typo or formatting issue in your code. Check for:\n" +
                   "• Missing colons (:) after if/for/while/def\n" +
                   "• Mismatched parentheses or brackets\n" +
                   "• Incorrect indentation"
        }

        // NameError — undefined variable
        val nameErrorRegex = Regex("NameError:\\s*name '(\\w+)' is not defined")
        val nameMatch = nameErrorRegex.find(rawError)
        if (nameMatch != null) {
            val varName = nameMatch.groupValues[1]
            return "❓ Undefined variable: '$varName'\n\n" +
                   "The variable '$varName' is used before being defined.\n\n" +
                   "Check for:\n" +
                   "• Typos in variable names\n" +
                   "• Using a variable outside its scope\n" +
                   "• Missing import statement"
        }

        // TypeError
        val typeErrorRegex = Regex("TypeError:\\s*(.+)")
        val typeMatch = typeErrorRegex.find(rawError)
        if (typeMatch != null) {
            val detail = typeMatch.groupValues[1]
            return "🔢 Type error: $detail\n\n" +
                   "You're trying to use a value in a way that's not supported.\n" +
                   "For example, adding a string to a number, or calling a non-function."
        }

        // FileNotFoundError / PermissionError
        val fileErrorRegex = Regex("(FileNotFoundError|PermissionError|OSError):\\s*(.+)")
        val fileMatch = fileErrorRegex.find(rawError)
        if (fileMatch != null) {
            val errorType = fileMatch.groupValues[1]
            val detail = fileMatch.groupValues[2]
            return when (errorType) {
                "FileNotFoundError" -> "📁 File not found: $detail\n\n" +
                    "The script is trying to access a file that doesn't exist.\n" +
                    "Make sure the file is in the same folder as the script."
                "PermissionError" -> "🔒 Permission denied: $detail\n\n" +
                    "The script doesn't have permission to access this file or path.\n" +
                    "Make sure the file is in the script's folder under Documents/Scriptler/."
                else -> "⚠️ OS error: $detail"
            }
        }

        // ConnectionError / network errors
        val networkErrorRegex = Regex("(ConnectionError|ConnectionRefusedError|TimeoutError|URLError):\\s*(.+)")
        val networkMatch = networkErrorRegex.find(rawError)
        if (networkMatch != null) {
            val detail = networkMatch.groupValues[2]
            return "🌐 Network error: $detail\n\n" +
                   "The script couldn't connect to the internet or the server.\n\n" +
                   "Check:\n" +
                   "• Your device has internet access\n" +
                   "• The URL is correct\n" +
                   "• The server is online"
        }

        // Generic Python error — just clean up the traceback slightly
        // Remove very long tracebacks, keep the error type and message
        val lines = rawError.lines()
        if (lines.size > 10) {
            // Keep first 2 lines and last 3 lines (the actual error)
            val first = lines.take(2)
            val last = lines.takeLast(3)
            return "⚠️ Python error:\n\n${first.joinToString("\n")}\n...\n${last.joinToString("\n")}"
        }

        return rawError
    }

    /**
     * Convert JavaScript errors into user-friendly messages.
     */
    private fun makeFriendlyJSError(rawError: String): String {
        // ReferenceError — undefined variable
        val refErrorRegex = Regex("ReferenceError:\\s*\"(\\w+)\" is not defined")
        val refMatch = refErrorRegex.find(rawError)
        if (refMatch != null) {
            val varName = refMatch.groupValues[1]
            return "❓ Undefined variable: '$varName'\n\n" +
                   "The variable '$varName' is used before being defined.\n" +
                   "Make sure you've declared it with var, let, or const."
        }

        // SyntaxError
        val syntaxErrorRegex = Regex("SyntaxError:\\s*(.+)")
        val syntaxMatch = syntaxErrorRegex.find(rawError)
        if (syntaxMatch != null) {
            val detail = syntaxMatch.groupValues[1]
            return "✏️ Syntax error: $detail\n\n" +
                   "There's a typo or formatting issue in your code. Check for:\n" +
                   "• Missing brackets or parentheses\n" +
                   "• Missing commas between function arguments\n" +
                   "• Unterminated strings"
        }

        // TypeError
        val typeErrorRegex = Regex("TypeError:\\s*(.+)")
        val typeMatch = typeErrorRegex.find(rawError)
        if (typeMatch != null) {
            val detail = typeMatch.groupValues[1]
            return "🔢 Type error: $detail\n\n" +
                   "You're trying to use a value in a way that's not supported."
        }

        return rawError
    }

    /**
     * Create a friendly error message from an exception.
     */
    private fun makeFriendlyError(language: String, e: Exception): String {
        val message = e.message ?: "Unknown error"

        // Check for common infrastructure errors
        return when {
            message.contains("Python not started", ignoreCase = true) ->
                "⚠️ Python interpreter failed to start.\n\n" +
                "This is an internal error. Try restarting the app."

            message.contains("AndroidPlatform", ignoreCase = true) ->
                "⚠️ Python platform not available.\n\n" +
                "This is an internal error. Try restarting the app."

            message.contains("Rhino", ignoreCase = true) ->
                "⚠️ JavaScript engine not available.\n\n" +
                "This is an internal error. Try restarting the app."

            else -> "❌ $language execution error:\n\n$message"
        }
    }

    }
