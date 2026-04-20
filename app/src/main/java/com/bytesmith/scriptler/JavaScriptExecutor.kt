package com.bytesmith.scriptler

import android.util.Log
import org.mozilla.javascript.Context
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable

/**
 * Executes JavaScript scripts using the Rhino engine with:
 * - console.log() and print() support for output capture
 * - Error handling with line numbers
 * - No module system (single-file scripts)
 */
class JavaScriptExecutor {

    companion object {
        private const val TAG = "JavaScriptExecutor"
    }

    data class ExecutionResult(
        val output: String,
        val isError: Boolean
    )

    @Suppress("UNUSED_PARAMETER")
    fun execute(code: String, scriptFolder: String): ExecutionResult {
        val cx = Context.enter()
        return try {
            // Set optimization level to -1 for interpreted mode (better Android compatibility)
            cx.optimizationLevel = -1
            cx.languageVersion = Context.VERSION_ES6

            val scope: Scriptable = cx.initStandardObjects()
    
            // Add console.log() and print() functions that capture output
            val wrappedCode = """
                var __output = [];
                var console = {
                    log: function() {
                        var args = Array.prototype.slice.call(arguments);
                        __output.push(args.join(' '));
                    },
                    error: function() {
                        var args = Array.prototype.slice.call(arguments);
                        __output.push('ERROR: ' + args.join(' '));
                    },
                    warn: function() {
                        var args = Array.prototype.slice.call(arguments);
                        __output.push('WARN: ' + args.join(' '));
                    }
                };
                function print() {
                    var args = Array.prototype.slice.call(arguments);
                    __output.push(args.join(' '));
                }

                $code

                __output.join('\n');
            """.trimIndent()

            val result = cx.evaluateString(scope, wrappedCode, "script", 1, null)

            val output = result?.toString()?.trim() ?: ""
            val finalOutput = if (output.isNotEmpty() && output != "undefined") {
                output
            } else {
                "Script executed successfully (no output)"
            }

            ExecutionResult(finalOutput, false)
        } catch (e: RhinoException) {
        Log.e(TAG, "JavaScript Rhino error", e)
        ExecutionResult(
        "JavaScript Error at line ${e.lineNumber()}: ${e.message}\n${e.scriptStackTrace}",
        true
        )
        } catch (e: Exception) {
            Log.e(TAG, "JavaScript execution error", e)
            ExecutionResult("JavaScript Error: ${e.message}", true)
        } finally {
            Context.exit()
        }
    }
}
