package com.bytesmith.scriptler

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Manages runtime installation of pure-Python packages from PyPI.
 *
 * Chaquopy 17.0 does NOT support runtime pip.install() — packages with native C extensions
 * must be declared in build.gradle at build time. However, pure-Python packages (no .so files)
 * can be downloaded as wheels from PyPI, extracted to internal storage, and added to sys.path.
 *
 * This class handles:
 * - Querying the PyPI JSON API for package metadata
 * - Detecting pure-Python wheels (tagged py3-none-any or py2.py3-none-any)
 * - Downloading and extracting .whl files to filesDir/python_libs/
 * - Tracking installed packages in SharedPreferences
 * - Uninstalling packages and freeing storage
 */
class RuntimePipManager(private val context: Context) {

    companion object {
        private const val TAG = "RuntimePipManager"
        private const val PREFS_NAME = "scriptler_runtime_pip"
        private const val INSTALLED_PACKAGES_KEY = "runtime_installed_packages"
        private const val PYTHON_LIBS_DIR = "python_libs"
        private const val PYPI_JSON_API_BASE = "https://pypi.org/pypi"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_DEPENDENCY_DEPTH = 5
        private const val PYPI_CACHE_MAX_SIZE = 50

        /** Regex to identify pure-Python wheel filenames.
         * Wheel filename format: {name}-{version}-{python}-{abi}-{platform}.whl
         * Pure-Python wheels have: python=py3 (or py2.py3), abi=none, platform=any
         *
         * Uses a non-capturing alternation group (?:py3|py2\.py3) instead of a
         * character class — the old [py3|py2\.py3] matched individual characters
         * (p, y, 3, |, ., 2) which caused false positives on invalid wheel names.
         */
        private val PURE_PYTHON_WHEEL_REGEX = Regex(
            """-(?:py3|py2\.py3)-none-any\.whl$"""
        )

        /** Regex to extract the package name from a PEP 508 requirement string.
         * Examples: "charset-normalizer>=2,<4" → "charset-normalizer"
         * "urllib3>=1.21.1,<3" → "urllib3"
         */
        private val REQUIREMENT_NAME_REGEX = Regex("""^([a-zA-Z0-9][a-zA-Z0-9._-]*)""")

        /** In-memory LRU cache for PyPI API responses to avoid rate-limiting. */
        private val pypiCache = object : LinkedHashMap<String, PyPIPackageInfo>(
            PYPI_CACHE_MAX_SIZE, 0.75f, true
        ) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PyPIPackageInfo>?): Boolean {
                return size > PYPI_CACHE_MAX_SIZE
            }
        }
    }

    /** Information about a package retrieved from PyPI. */
    data class PyPIPackageInfo(
        val name: String,
        val version: String,
        val summary: String,
        val isPurePython: Boolean,
        val wheelUrl: String?,
        val wheelFilename: String?,
        val wheelSize: Long,
        val requiresDist: List<String>
    )

    /** Tracks a package installed at runtime. */
    data class InstalledPackage(
        val name: String,
        val pipName: String,
        val version: String,
        val sizeBytes: Long,
        val installDate: Long,
        val wheelUrl: String
    )

    // ---- Public API ----

    /**
     * Get the path to the runtime Python packages directory.
     * This directory should be added to sys.path before script execution.
     */
    fun getPythonLibsPath(): String {
        return File(context.filesDir, PYTHON_LIBS_DIR).absolutePath
    }

    /**
     * Check if a package is already installed at runtime.
     */
    fun isInstalled(importName: String): Boolean {
        return getInstalledPackages().any {
            it.name.equals(importName, ignoreCase = true) ||
            it.pipName.equals(importName, ignoreCase = true)
        }
    }

    /**
     * Get all packages installed at runtime.
     */
    fun getInstalledPackages(): List<InstalledPackage> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(INSTALLED_PACKAGES_KEY, null) ?: return emptyList()

        return try {
            val type = object : TypeToken<List<InstalledPackage>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading installed packages", e)
            emptyList()
        }
    }

    /**
     * Get the total storage used by runtime-installed packages.
     */
    fun getTotalInstalledSize(): Long {
        return getInstalledPackages().sumOf { it.sizeBytes }
    }

    /**
     * Check whether the device currently has internet connectivity.
     * Returns true if there is an active network with internet capability.
     */
    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Check whether the device is currently on a metered (mobile data) connection.
     * Returns true if the active network is metered (e.g., cellular).
     */
    fun isMeteredConnection(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return cm.isActiveNetworkMetered
    }

    /**
     * Get available storage bytes in the app's internal files directory.
     */
    fun getAvailableStorageBytes(): Long {
        val stat = StatFs(context.filesDir.absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    /**
     * Query the PyPI JSON API for package information.
     * Returns package metadata including whether a pure-Python wheel is available.
     * Results are cached in-memory to avoid rate-limiting.
     *
     * @param packageName The pip install name (e.g., "requests", "beautifulsoup4")
     * @return Result containing PyPIPackageInfo on success, or an error message on failure
     */
    fun queryPackage(packageName: String): Result<PyPIPackageInfo> {
        // Check in-memory cache first
        synchronized(pypiCache) {
            pypiCache[packageName]?.let {
                Log.d(TAG, "PyPI cache hit for: $packageName")
                return Result.success(it)
            }
        }

        // Check network connectivity before making the API call
        if (!isNetworkAvailable()) {
            return Result.failure(
                Exception("No internet connection. Please connect to the internet to search for packages.")
            )
        }

        return try {
            val url = URL("$PYPI_JSON_API_BASE/$packageName/json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return Result.failure(
                    Exception("PyPI API returned HTTP $responseCode for package '$packageName'")
                )
            }

            val response = BufferedReader(InputStreamReader(connection.inputStream)).readText()
            connection.disconnect()

            val result = parsePyPIResponse(response)
            // Cache successful results
            if (result.isSuccess) {
                synchronized(pypiCache) {
                    pypiCache[packageName] = result.getOrThrow()
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query PyPI for package: $packageName", e)
            Result.failure(e)
        }
    }

    /**
     * Search for packages on PyPI using the simple search API.
     * Returns a list of matching package names.
     * Note: PyPI doesn't have a proper search API, so this uses the simple project lookup.
     *
     * @param query The search query (package name)
     * @return Result containing a list of PyPIPackageInfo objects
     */
    fun searchPackages(query: String): Result<List<PyPIPackageInfo>> {
        // PyPI doesn't have a search API, so we try the exact name first
        // and then try common variations
        val results = mutableListOf<PyPIPackageInfo>()

        // Try exact name
        val exactResult = queryPackage(query)
        if (exactResult.isSuccess) {
            results.add(exactResult.getOrThrow())
        }

        // Try the import-name to pip-name mapping
        val packageNameMap = ModuleManager.getPackageNameMap(context)
        val pipName = packageNameMap[query]
        if (pipName != null && pipName != query) {
            val mappedResult = queryPackage(pipName)
            if (mappedResult.isSuccess) {
                val info = mappedResult.getOrThrow()
                // Avoid duplicate if the mapped name resolved to the same package
                if (results.none { it.name.equals(info.name, ignoreCase = true) }) {
                    results.add(info)
                }
            }
        }

        return Result.success(results)
    }

    /**
     * Install a pure-Python package from PyPI at runtime.
     *
     * This method:
     * 1. Queries PyPI for the package metadata
     * 2. Finds the best pure-Python wheel
     * 3. Downloads the wheel file
     * 4. Extracts it to the python_libs directory
     * 5. Records the installation in SharedPreferences
     *
     * @param packageName The pip install name
     * @param onProgress Optional callback for progress updates (message, percent 0-100)
     * @return Result containing the InstalledPackage on success, or an error on failure
     */
    fun installPackage(
        packageName: String,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<InstalledPackage> {
        onProgress?.invoke("Querying PyPI for $packageName...", 5)

        // Check network connectivity before any network operations
        if (!isNetworkAvailable()) {
            return Result.failure(
                Exception("No internet connection. Please connect to the internet to install packages.")
            )
        }

        // Step 1: Query PyPI
        val infoResult = queryPackage(packageName)
        if (infoResult.isFailure) {
            return Result.failure(infoResult.exceptionOrNull() ?: Exception("Unknown error"))
        }

        val info = infoResult.getOrThrow()

        if (!info.isPurePython) {
            return Result.failure(
                Exception(
                    "Package '$packageName' requires native C extensions and cannot be " +
                    "installed at runtime. It must be pre-bundled in the app's build.gradle. " +
                    "You can request it to be added in a future app update."
                )
            )
        }

        if (info.wheelUrl == null) {
            return Result.failure(
                Exception("No compatible pure-Python wheel found for '$packageName'")
            )
        }

        // Check available storage before downloading (need at least 2x wheel size for safety)
        val requiredSpace = info.wheelSize * 2
        val availableBytes = getAvailableStorageBytes()
        if (availableBytes < requiredSpace) {
            val neededStr = ModuleManager.formatSize(requiredSpace)
            val availableStr = ModuleManager.formatSize(availableBytes)
            return Result.failure(
                Exception("Not enough storage space. Need at least $neededStr but only $availableStr available.")
            )
        }

        // Check if already installed
        if (isInstalled(info.name)) {
            val existing = getInstalledPackages().find {
                it.name.equals(info.name, ignoreCase = true)
            }
            if (existing != null && existing.version == info.version) {
                onProgress?.invoke("$packageName ${info.version} already installed", 100)
                return Result.success(existing)
            }
            // Different version — uninstall old one first
            uninstallPackage(existing!!.pipName)
        }

        // Step 2: Download the wheel
        onProgress?.invoke("Downloading ${info.wheelFilename}...", 20)
        val wheelFile = downloadWheel(info.wheelUrl, info.wheelFilename ?: "$packageName.whl", onProgress)
            .getOrElse { error ->
                return Result.failure(Exception("Download failed: ${error.message}"))
            }

        try {
            // Step 3: Extract the wheel to python_libs
            onProgress?.invoke("Extracting package files...", 75)
            val libsDir = getOrCreatePythonLibsDir()
            val extractedSize = extractWheel(wheelFile, libsDir)
                .getOrElse { error ->
                    return Result.failure(Exception("Extraction failed: ${error.message}"))
                }

            // Step 4: Record the installation
            val installedPackage = InstalledPackage(
                name = normalizePackageName(info.name),
                pipName = packageName,
                version = info.version,
                sizeBytes = extractedSize,
                installDate = System.currentTimeMillis(),
                wheelUrl = info.wheelUrl
            )
            recordInstalledPackage(installedPackage)

            onProgress?.invoke("$packageName ${info.version} installed successfully!", 100)
            Log.i(TAG, "Installed $packageName ${info.version} ($extractedSize bytes)")
            return Result.success(installedPackage)

        } finally {
            // Clean up the downloaded wheel file
            wheelFile.delete()
        }
    }

    /**
     * Install a package along with its dependencies (basic resolution).
     * Uses a try-and-retry approach: install the package, then check if
     * any declared dependencies are missing and install those too.
     *
     * @param packageName The pip install name
     * @param onProgress Optional callback for progress updates
     * @param depth Current recursion depth (used internally)
     * @return Result containing a list of all installed packages
     */
    fun installPackageWithDependencies(
        packageName: String,
        onProgress: ((String, Int) -> Unit)? = null,
        depth: Int = 0
    ): Result<List<InstalledPackage>> {
        if (depth > MAX_DEPENDENCY_DEPTH) {
            return Result.failure(Exception("Max dependency depth exceeded for $packageName"))
        }

        val allInstalled = mutableListOf<InstalledPackage>()

        // Install the main package
        val mainResult = installPackage(packageName, onProgress)
        if (mainResult.isFailure) {
            // If already installed, that's fine — continue to dependencies
            val existing = getInstalledPackages().find {
                it.name.equals(packageName, ignoreCase = true) ||
                it.pipName.equals(packageName, ignoreCase = true)
            }
            if (existing != null) {
                allInstalled.add(existing)
            } else {
                return Result.failure(mainResult.exceptionOrNull() ?: Exception("Install failed"))
            }
        } else {
            allInstalled.add(mainResult.getOrThrow())
        }

        // Get the package info to find dependencies
        val infoResult = queryPackage(packageName)
        if (infoResult.isSuccess) {
            val info = infoResult.getOrThrow()
            val deps = parseBaseDependencies(info.requiresDist)

            for (dep in deps) {
                // Skip if already installed (either runtime or build-time)
                if (isInstalled(dep)) continue
                if (isBuildTimePackage(dep)) continue

                onProgress?.invoke("Installing dependency: $dep...", 0)
                val depResult = installPackageWithDependencies(dep, onProgress, depth + 1)
                if (depResult.isSuccess) {
                    allInstalled.addAll(depResult.getOrThrow())
                } else {
                    Log.w(TAG, "Failed to install dependency $dep: ${depResult.exceptionOrNull()?.message}")
                    // Don't fail the whole install if a dependency fails — it might be optional
                }
            }
        }

        return Result.success(allInstalled)
    }

    /**
     * Uninstall a runtime-installed package.
     * Removes the package files from python_libs and the tracking entry.
     */
    fun uninstallPackage(pipName: String): Result<Unit> {
        val installed = getInstalledPackages().find {
            it.pipName.equals(pipName, ignoreCase = true) ||
            it.name.equals(pipName, ignoreCase = true)
        }

        if (installed == null) {
            return Result.failure(Exception("Package '$pipName' is not installed at runtime"))
        }

        val libsDir = getOrCreatePythonLibsDir()

        // Remove the package directory (e.g., python_libs/requests/)
        val packageDir = File(libsDir, installed.name)
        if (packageDir.exists()) {
            deleteRecursively(packageDir)
        }

        // Also try the pip name as directory name (some packages use different casing)
        val pipNameDir = File(libsDir, installed.pipName)
        if (pipNameDir.exists() && pipNameDir != packageDir) {
            deleteRecursively(pipNameDir)
        }

        // Remove the dist-info directory (e.g., python_libs/requests-2.31.0.dist-info/)
        val distInfoPattern = Regex("${Regex.escape(installed.name)}-.*\\.dist-info")
        val pipDistInfoPattern = Regex("${Regex.escape(installed.pipName)}-.*\\.dist-info")
        libsDir.listFiles()?.forEach { file ->
            if (file.isDirectory &&
                (distInfoPattern.matches(file.name) || pipDistInfoPattern.matches(file.name))) {
                deleteRecursively(file)
            }
        }

        // Remove from tracking
        removeInstalledPackage(installed.pipName)

        Log.i(TAG, "Uninstalled $pipName")
        return Result.success(Unit)
    }

    /**
     * Check if a package name refers to a build-time (pre-bundled) package.
     * These are packages declared in build.gradle's chaquopy pip block.
     */
    fun isBuildTimePackage(importName: String): Boolean {
        return try {
            val executor = PythonExecutor(context)
            executor.isModuleAvailable(importName)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the list of directories that should be added to sys.path
     * for runtime-installed packages to be importable.
     *
     * Returns the main python_libs directory plus any top-level package directories
     * that contain namespace packages.
     */
    fun getSysPathEntries(): List<String> {
        val entries = mutableListOf<String>()
        val libsDir = File(context.filesDir, PYTHON_LIBS_DIR)

        if (libsDir.exists()) {
            // Add the main libs directory — this is sufficient for most packages
            entries.add(libsDir.absolutePath)
        }

        return entries
    }

    // ---- Private helpers ----

    /**
     * Parse the PyPI JSON API response to extract package information.
     */
    private fun parsePyPIResponse(json: String): Result<PyPIPackageInfo> {
        return try {
            val root = Gson().fromJson(json, Map::class.java) as? Map<String, Any>
                ?: return Result.failure(Exception("Invalid PyPI response format"))

            @Suppress("UNCHECKED_CAST")
            val info = root["info"] as? Map<String, Any>
                ?: return Result.failure(Exception("No 'info' field in PyPI response"))

            val name = info["name"] as? String ?: "unknown"
            val version = info["version"] as? String ?: "0.0.0"
            val summary = info["summary"] as? String ?: ""
            val requiresDist = (info["requires_dist"] as? List<String>) ?: emptyList()

            @Suppress("UNCHECKED_CAST")
            val urls = root["urls"] as? List<Map<String, Any>>
                ?: return Result.failure(Exception("No 'urls' field in PyPI response"))

            // Find the best pure-Python wheel
            var bestWheel: Map<String, Any>? = null
            var bestWheelSize: Long = Long.MAX_VALUE

            for (url in urls) {
                val filename = url["filename"] as? String ?: continue
                val packagetype = url["packagetype"] as? String ?: continue

                if (packagetype == "bdist_wheel" && isPurePythonWheel(filename)) {
                    val size = (url["size"] as? Number)?.toLong() ?: Long.MAX_VALUE
                    // Prefer py3 wheels over py2.py3, and smaller wheels
                    val isPy3Only = filename.contains("-py3-none-any.whl")
                    val bestIsPy3Only = bestWheel?.let {
                        (it["filename"] as? String)?.contains("-py3-none-any.whl") ?: false
                    } ?: false

                    when {
                        bestWheel == null -> {
                            bestWheel = url
                            bestWheelSize = size
                        }
                        isPy3Only && !bestIsPy3Only -> {
                            // Prefer py3-only over py2.py3
                            bestWheel = url
                            bestWheelSize = size
                        }
                        isPy3Only == bestIsPy3Only && size < bestWheelSize -> {
                            // Same python tag, prefer smaller
                            bestWheel = url
                            bestWheelSize = size
                        }
                    }
                }
            }

            val isPurePython = bestWheel != null
            val wheelUrl = bestWheel?.get("url") as? String
            val wheelFilename = bestWheel?.get("filename") as? String
            val wheelSize = (bestWheel?.get("size") as? Number)?.toLong() ?: 0L

            Result.success(
                PyPIPackageInfo(
                    name = name,
                    version = version,
                    summary = summary,
                    isPurePython = isPurePython,
                    wheelUrl = wheelUrl,
                    wheelFilename = wheelFilename,
                    wheelSize = wheelSize,
                    requiresDist = requiresDist
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse PyPI response", e)
            Result.failure(e)
        }
    }

    /**
     * Check if a wheel filename indicates a pure-Python wheel.
     * Pure-Python wheels have platform tag "any" and abi tag "none".
     */
    private fun isPurePythonWheel(filename: String): Boolean {
        return PURE_PYTHON_WHEEL_REGEX.containsMatchIn(filename)
    }

    /**
     * Download a wheel file from the given URL to a temporary file.
     * Reports download progress via the onProgress callback (20-70% range).
     *
     * @param url The direct download URL for the wheel file
     * @param filename The filename to save as
     * @param onProgress Optional progress callback (message, percent 0-100)
     */
    private fun downloadWheel(
        url: String,
        filename: String,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<File> {
        return try {
            val tempDir = File(context.cacheDir, "wheel_downloads").also { it.mkdirs() }
            val tempFile = File(tempDir, filename)

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return Result.failure(Exception("Download failed with HTTP $responseCode"))
            }

            val contentLength = connection.contentLengthLong.let { if (it > 0) it else -1L }
            var totalBytesRead = 0L

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Report progress in the 20-70% range (download phase)
                        if (contentLength > 0 && onProgress != null) {
                            val downloadPercent = (totalBytesRead * 100 / contentLength).toInt()
                            val overallPercent = 20 + (downloadPercent * 50 / 100) // map 0-100% → 20-70%
                            val sizeStr = ModuleManager.formatSize(totalBytesRead)
                            onProgress("Downloading $filename... $sizeStr", overallPercent)
                        }
                    }
                }
            }

            connection.disconnect()
            Result.success(tempFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download wheel from $url", e)
            Result.failure(e)
        }
    }

    /**
     * Extract a wheel (ZIP) file to the target directory.
     * Returns the total size of extracted files.
     */
    private fun extractWheel(wheelFile: File, targetDir: File): Result<Long> {
        return try {
            var totalSize: Long = 0

            ZipInputStream(FileInputStream(wheelFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val outFile = File(targetDir, entry.name)

                    // Security: prevent path traversal
                    if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                        Log.w(TAG, "Skipping suspicious path: ${entry.name}")
                        entry = zipIn.nextEntry
                        continue
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        // Ensure parent directories exist
                        outFile.parentFile?.mkdirs()

                        FileOutputStream(outFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalSize += bytesRead
                            }
                        }
                    }

                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            Result.success(totalSize)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract wheel: ${wheelFile.name}", e)
            Result.failure(e)
        }
    }

    /**
     * Get or create the python_libs directory in internal storage.
     */
    private fun getOrCreatePythonLibsDir(): File {
        val dir = File(context.filesDir, PYTHON_LIBS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Normalize a Python package name for consistent tracking.
     * PEP 503: lowercase, replace hyphens/underscores/dots with single hyphen.
     */
    private fun normalizePackageName(name: String): String {
        return name.lowercase()
            .replace(Regex("[._-]+"), "-")
            .replace(Regex("^-|-$"), "")
    }

    /**
     * Parse base (non-extra, non-platform-specific) dependencies from requires_dist.
     * Skips dependencies that are:
     * - Extra-specific (e.g., "socks ; extra == 'socks'")
     * - Platform-specific for non-Linux (e.g., "win-inet-pton ; sys_platform == 'win32'")
     * - Python version-specific for versions we don't support
     */
    private fun parseBaseDependencies(requiresDist: List<String>): List<String> {
        val deps = mutableListOf<String>()

        for (req in requiresDist) {
            // Skip extra-specific requirements
            if (req.contains("extra ==") || req.contains("extra ==")) continue

            // Skip Windows-specific requirements
            if (req.contains("sys_platform == \"win32\"") ||
                req.contains("sys_platform == 'win32'")) continue

            // Skip macOS-specific requirements
            if (req.contains("sys_platform == \"darwin\"") ||
                req.contains("sys_platform == 'darwin'")) continue

            // Skip requirements for Python < 3.10 (we use Python 3.10)
            if (req.contains("python_version < \"3") ||
                req.contains("python_version < '3")) continue

            // Extract the package name
            val match = REQUIREMENT_NAME_REGEX.find(req)
            if (match != null) {
                val depName = match.groupValues[1]
                // Skip standard library modules that might appear as dependencies
                if (!isStdlibModule(depName)) {
                    deps.add(depName)
                }
            }
        }

        return deps.distinct()
    }

    /**
     * Check if a module name is part of Python's standard library.
     * These should never be installed as packages.
     */
    private fun isStdlibModule(name: String): Boolean {
        val stdlibModules = setOf(
            "os", "sys", "json", "re", "math", "time", "datetime", "collections",
            "itertools", "functools", "pathlib", "logging", "unittest", "io",
            "hashlib", "hmac", "socket", "http", "urllib", "email", "html",
            "xml", "csv", "sqlite3", "threading", "multiprocessing", "subprocess",
            "argparse", "configparser", "tempfile", "shutil", "glob", "fnmatch",
            "struct", "ctypes", "typing", "dataclasses", "abc", "copy", "pprint",
            "textwrap", "string", "random", "statistics", "decimal", "fractions",
            "enum", "operator", "contextlib", "traceback", "inspect", "ast",
            "dis", "gc", "weakref", "types", "importlib", "pkgutil",
            "platform", "signal", "resource", "mmap", "errno", "faulthandler",
            "atexit", "tracemalloc", "warnings", "__future__"
        )
        return name in stdlibModules
    }

    // ---- SharedPreferences tracking ----

    private fun recordInstalledPackage(packageInfo: InstalledPackage) {
        val packages = getInstalledPackages().toMutableList()
        // Remove existing entry for the same package
        packages.removeAll {
            it.pipName.equals(packageInfo.pipName, ignoreCase = true) ||
            it.name.equals(packageInfo.name, ignoreCase = true)
        }
        packages.add(packageInfo)
        saveInstalledPackages(packages)
    }

    private fun removeInstalledPackage(pipName: String) {
        val packages = getInstalledPackages().toMutableList()
        packages.removeAll {
            it.pipName.equals(pipName, ignoreCase = true) ||
            it.name.equals(pipName, ignoreCase = true)
        }
        saveInstalledPackages(packages)
    }

    private fun saveInstalledPackages(packages: List<InstalledPackage>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(packages)
        prefs.edit().putString(INSTALLED_PACKAGES_KEY, json).apply()
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }
}
