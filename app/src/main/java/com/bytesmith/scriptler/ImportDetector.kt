package com.bytesmith.scriptler

import android.util.Log
import java.util.regex.Pattern

/**
 * Detects and extracts Python import statements from script content.
 * 
 * Handles all standard Python import forms:
 * - import os
 * - import os, sys
 * - import os.path
 * - from os import path
 * - from os.path import join, dirname
 * - import requests as req
 * - from datetime import datetime as dt
 */
object ImportDetector {
    
    private const val TAG = "ImportDetector"
    
    // Pattern for: import X or import X, Y, Z
    // Captures the module name (first part before any dot or comma)
    private val IMPORT_REGEX = Regex("""^\s*import\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\.[a-zA-Z_][a-zA-Z0-9_]*)*)""")
    
    // Pattern for: from X import Y
    // Captures the parent module (os from "from os import")
    private val FROM_IMPORT_REGEX = Regex("""^\s*from\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\.[a-zA-Z_][a-zA-Z0-9_]*)*)\s+import""")
    
    // Pattern to split comma-separated imports: "os, sys, path" -> ["os", "sys", "path"]
    private val COMMA_SPLIT_REGEX = Regex(""",\s*""")
    
    // Pattern to extract base name before "as alias" or dot: "os.path" -> "os", "req" (from "import requests as req") -> "requests"
    private val BASE_NAME_REGEX = Regex("""^([a-zA-Z_][a-zA-Z0-9_]*)""")
    
    /**
     * Extract all unique top-level module names from a Python script.
     * 
     * @param scriptContent The full Python script content
     * @return Set of unique module names (e.g., "os", "sys", "requests", "numpy")
     */
    fun extractImports(scriptContent: String): Set<String> {
        val imports = mutableSetOf<String>()
        val seen = mutableSetOf<String>()
        
        scriptContent.lines().forEach { line ->
            val trimmedLine = line.trim()
            
            // Skip comments
            if (trimmedLine.startsWith("#")) return@forEach
            
            // Match: import X or import X, Y, Z
            IMPORT_REGEX.find(trimmedLine)?.let { match ->
                val moduleList = match.groupValues[1]
                // Split by comma for multiple imports: "os, sys, path"
                COMMA_SPLIT_REGEX.split(moduleList).forEach { module ->
                    val baseName = extractBaseName(module.trim())
                    if (baseName.isNotEmpty() && seen.add(baseName)) {
                        imports.add(baseName)
                    }
                }
            }
            
            // Match: from X import Y
            FROM_IMPORT_REGEX.find(trimmedLine)?.let { match ->
                val parentModule = match.groupValues[1]
                val baseName = extractBaseName(parentModule)
                if (baseName.isNotEmpty() && seen.add(baseName)) {
                    imports.add(baseName)
                }
            }
        }
        
        Log.d(TAG, "Extracted ${imports.size} unique imports: $imports")
        return imports
    }
    
    /**
     * Extract the base module name from a full path or aliased import.
     * 
     * Examples:
     * - "os.path" -> "os"
     * - "requests" -> "requests"
     * - "requests as req" -> "requests"
     * - "datetime.datetime" -> "datetime"
     * 
     * @param modulePath The module path or import statement
     * @return The base module name
     */
    private fun extractBaseName(modulePath: String): String {
        // First, remove "as ALIAS" part if present
        val withoutAlias = modulePath.split(Regex("""\s+as\s+"""))[0].trim()
        
        // Then get the first component before any dot
        BASE_NAME_REGEX.find(withoutAlias)?.let {
            return it.groupValues[1]
        }
        
        return ""
    }
    
    /**
     * Check if a module name is a Python stdlib module.
     * This is a quick check to avoid querying PyPI for built-in modules.
     * 
     * @param moduleName The module name to check
     * @return true if it's a stdlib module
     */
    fun isStdlibModule(moduleName: String): Boolean {
        return STDLIB_MODULES.contains(moduleName.lowercase())
    }
    
    /**
     * Comprehensive list of Python 3.10 stdlib modules.
     * Used to avoid querying PyPI for built-in modules.
     */
    private val STDLIB_MODULES = setOf(
        // Core built-ins
        "os", "sys", "json", "re", "math", "time", "datetime", "collections",
        "itertools", "functools", "pathlib", "logging", "unittest", "io",
        "hashlib", "hmac", "socket", "http", "urllib", "email", "html",
        "xml", "csv", "sqlite3", "threading", "multiprocessing", "subprocess",
        "argparse", "configparser", "tempfile", "shutil", "glob", "fnmatch",
        "struct", "ctypes", "typing", "dataclasses", "abc", "copy", "pprint",
        "textwrap", "string", "random", "statistics", "decimal", "fractions",
        "enum", "operator", "contextlib", "traceback", "inspect", "ast",
        "ssl", "base64", "binascii", "zlib", "gzip", "bz2", "lzma",
        "zipfile", "tarfile", "pickle", "shelve", "dbm", "code", "codeop",
        "compile", "dis", "token", "tokenize", "ast", "platform", "warnings",
        "weakref", "gc", "types", "functools", "copyreg", "pickle", "shelve",
        "marshal", "dbm", "sqlite3", "bsddb", "gdbm", "turtle", "cmd",
        "shlex", "tokenize", "keyword", "ast", "symtable", "stat", "filecmp",
        "difflib", "poplib", "smtplib", "uuid", "socket", "ssl", "select",
        "asyncore", "asyncio", "signal", "mmap", "readline", "rlcompleter",
        // Additional common stdlib modules
        "venv", "pkgutil", "zipimport", "sysconfig", "imp", "builtins",
        "exec", "eval", "format", "input", "print", "open", "file", "buffer",
        "property", "classmethod", "staticmethod", "super", "range", "map",
        "filter", "zip", "enumerate", "sorted", "reversed", "len", "abs",
        "all", "any", "sum", "min", "max", "pow", "round", "divmod", "hex",
        "oct", "bin", "chr", "ord", "repr", "ascii", "format", "slice",
        "tuple", "list", "dict", "set", "frozenset", "complex", "float", "int",
        "bool", "bytes", "bytearray", "memoryview", "object", "Exception",
        "BaseException", "SystemExit", "KeyboardInterrupt", "GeneratorExit"
    )
}