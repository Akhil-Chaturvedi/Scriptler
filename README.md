# Scriptler

An Android app for writing, scheduling, and executing Python and JavaScript scripts on-device. Scripts run locally on the phone -- no server, no cloud, no registration.

## What It Does

- Users write Python or JavaScript scripts in a built-in code editor
- Scripts can be scheduled to run automatically (interval, daily, weekly)
- Scripts execute on the device using real interpreters (Chaquopy for Python, Rhino for JavaScript)
- Execution output and errors are captured in per-script logs
- Users can add data files alongside their scripts via file manager

## Architecture

### Language Stack

| Layer | Technology |
|-------|-----------|
| App language | Kotlin |
| Python execution | Chaquopy 17.0 (Python 3.10) |
| JavaScript execution | Mozilla Rhino 1.7.15 |
| Scheduling | WorkManager |
| Persistence | JSON files in app internal storage |
| UI | XML layouts, Material 3, dark-first theme |

### Module Loading Strategy

Scriptler uses a hybrid approach for Python package availability:

1. **Build-time bundling** -- Native C extension packages (those with `.so` files like `lxml`) are declared in `app/build.gradle` and cross-compiled for Android ABIs at build time. This is the only way to support native packages on Android.

2. **Runtime pure-Python downloader** -- `RuntimePipManager` queries the PyPI JSON API, filters for pure-Python wheels (`py3-none-any`), downloads them, and extracts them to `context.filesDir/python_libs/`. This supports thousands of packages (requests, jinja2, flask, pyyaml, schedule, etc.) without increasing APK size.

3. **Local module imports** -- The script folder is added to `sys.path` before execution, so `import my_helper` works if `my_helper.py` exists in the same directory.

**Constraint:** Native C extension packages cannot be loaded at runtime. Android 10+ enforces W^X security policy that blocks `dlopen()` from writable paths. Packages like numpy, pandas, Pillow, cryptography must be pre-bundled in the APK.

### Storage

Scripts are stored in a user-accessible directory:

```
/storage/emulated/0/Documents/Scriptler/
  mango/
    mango.py          -- Main script
    config.json       -- User-added data file
    my_helper.py      -- Local module importable by mango.py
  banana/
    banana.js
    notes.txt
```

On Android 11+ without `MANAGE_EXTERNAL_STORAGE` permission, the app falls back to app-specific external storage:

```
/storage/emulated/0/Android/data/com.bytesmith.scriptler/files/Scriptler/
```

App-internal storage (metadata, logs, runtime packages):

```
context.getFilesDir()/
  scripts_metadata.json
  logs/
    <script_id>_logs.json
  python_libs/          -- Runtime-installed pure-Python packages
```

### Key Components

```
app/src/main/java/com/bytesmith/scriptler/
  MainActivity.kt              -- Bottom nav: Scripts, Packages, Settings
  ScriptsFragment.kt           -- Script list with RecyclerView
  ScriptAdapter.kt             -- Script cards: name, language badge, play/pause, overflow menu
  ScriptEditorActivity.kt      -- Code editor with per-line copy buttons, auto-save
  ScriptDetailsActivity.kt     -- Execution logs, next run countdown, Run Now
  CreateScriptDialogFragment.kt-- Name + language (Python/JS) picker
  ScheduleDialogFragment.kt    -- Schedule type picker: none, interval, daily, weekly
  SettingsFragment.kt          -- Theme, font size, auto-save, notifications, cache management
  PackageManagerFragment.kt    -- Search/install/uninstall Python packages from PyPI

  ScriptRunner.kt              -- Unified execution interface, timeout enforcement, friendly errors
  PythonExecutor.kt            -- Chaquopy wrapper: sys.path config, import parsing, stdout capture
  JavaScriptExecutor.kt        -- Rhino wrapper: console.log, error reporting with line numbers
  RuntimePipManager.kt         -- PyPI API client, wheel downloader, package tracker
  ModuleManager.kt             -- Unified package tracking (build-time + runtime)
  ModuleInstallDialog.kt       -- Missing package prompt with install flow
  ScheduleManager.kt           -- WorkManager scheduling: interval, daily, weekly
  ScriptExecutionWorker.kt     -- WorkManager worker for background execution
  BootReceiver.kt              -- Re-registers schedules after device reboot
  NotificationUtils.kt         -- Execution result notifications
  StoragePermissionManager.kt  -- MANAGE_EXTERNAL_STORAGE request + SAF fallback
  ScriptRepository.kt          -- CRUD for scripts and logs via JSON files

  CustomEditor.kt              -- EditText subclass with line numbers and per-line copy buttons
  models/Script.kt             -- Data class: id, name, language, schedule, lastRun, nextRun, isActive
  models/ScriptLog.kt          -- Data class: id, scriptId, timestamp, runNumber, output, isError
  utils/FileUtils.kt           -- Script/log file I/O, directory management, storage fallback
  utils/DateUtils.kt           -- Date formatting, relative time, countdown
```

## Building

### Prerequisites

- Android Studio Hedgehog or later
- Android SDK with compileSdk 34
- JDK 17
- Internet connection (Chaquopy downloads Python wheels during build)

### Build Steps

```bash
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Build Configuration

The app targets:
- `minSdk 24` (Android 7.0)
- `targetSdk 34` (Android 14)
- ABIs: `arm64-v8a`, `x86_64`

Chaquopy Python version: 3.10

Pre-bundled packages (declared in `app/build.gradle`):
- `lxml` -- native C extension, must be build-time
- `requests` -- pure Python, pre-bundled for convenience
- `beautifulsoup4` -- pure Python, pre-bundled for convenience

## Permissions

| Permission | Purpose | Required |
|-----------|---------|----------|
| `MANAGE_EXTERNAL_STORAGE` | Read/write scripts in Documents/Scriptler/ | No -- app falls back to app-specific storage |
| `INTERNET` | Download pure-Python packages from PyPI at runtime | Yes -- for package installation |
| `POST_NOTIFICATIONS` | Show execution result notifications | No -- user can disable in settings |
| `FOREGROUND_SERVICE` | Required by WorkManager for reliable scheduling | Yes |
| `RECEIVE_BOOT_COMPLETED` | Re-register schedules after device reboot | Yes |

## Scheduling

Scripts can be scheduled in four modes:

| Mode | Configuration | WorkManager Mechanism |
|------|--------------|----------------------|
| None | No automatic execution | N/A |
| Interval | Every N minutes | `PeriodicWorkRequest` with N-minute repeat |
| Daily | At a specific time each day | `PeriodicWorkRequest` with 24-hour repeat + initial delay |
| Weekly | On a specific day + time each week | `PeriodicWorkRequest` with 7-day repeat + initial delay |

WorkManager constraints are set to not require battery-not-low or device-idle, since the user explicitly chose to schedule these scripts. Schedules survive app kill and device reboot (via `BootReceiver`).

## Error Handling

`ScriptRunner` converts raw interpreter errors into user-friendly messages:

| Error Type | User Message |
|-----------|-------------|
| `ModuleNotFoundError` | "This script requires the 'X' package. Install it from the Package Manager." |
| `SyntaxError` | "Syntax error on line N: details" |
| `NameError` | "Variable 'X' is not defined on line N" |
| `FileNotFoundError` | "File not found: 'path'. Make sure the file is in the script folder." |
| `PermissionError` | "Permission denied: 'path'. Check file permissions." |
| `ConnectionError` | "Network error: Could not connect to host. Check internet connection." |
| Execution timeout (60s) | "Script took too long to run (over 60 seconds) and was stopped." |
| Native-only package | "The 'X' package requires native libraries that cannot be installed at runtime." |

## Settings

| Setting | Storage Key | Default | Description |
|---------|-----------|---------|-------------|
| Dark theme | `dark_theme_enabled` | `true` | Toggles between dark and light Material 3 themes |
| Editor font size | `editor_font_size` | `14` | Font size in sp, range 10-24 |
| Auto-save | `auto_save_enabled` | `true` | Saves editor content every 30 seconds |
| Notifications | `notifications_enabled` | `false` | Show notification after script execution |

Settings are stored in `SharedPreferences` via `PreferenceManager`.

## Runtime Package Installation

When a Python script imports a package that is not available:

1. `PythonExecutor.parseImports()` scans the script for `import X` and `from X import Y` statements
2. `ScriptRunner.checkImports()` checks each import against available modules
3. If missing packages are found, `ModuleInstallDialog` is shown
4. For each missing package, `RuntimePipManager` queries `https://pypi.org/pypi/{name}/json`
5. If a pure-Python wheel exists (`py3-none-any`), it is downloaded and extracted to `filesDir/python_libs/`
6. If no pure-Python wheel exists, the user is told the package requires native libraries
7. Before execution, `PythonExecutor` adds `filesDir/python_libs/` and all subdirectories to `sys.path`

Import-name to pip-name mapping is maintained in `app/src/main/res/raw/package_name_map.json` to handle cases like `bs4` -> `beautifulsoup4`, `PIL` -> `Pillow`, `yaml` -> `PyYAML`.

### Dependency Resolution

v1 uses try-and-retry: install the requested package, run the script, if `ModuleNotFoundError` occurs for a dependency, install it and retry. Maximum 3 retries.

v2 (planned) will parse `requires_dist` from the PyPI JSON response for proper recursive dependency resolution.

## Known Limitations

1. **Native packages are build-time only** -- numpy, pandas, Pillow, cryptography, scipy, opencv-python cannot be installed at runtime. They must be added to `build.gradle` and the APK rebuilt.

2. **Not all pure-Python packages work** -- some have transitive dependencies on native packages that cannot be resolved.

3. **JavaScript has no module system** -- JS scripts are single-file. No `require()` or `import` support.

4. **WorkManager timing is approximate** -- Android may delay execution during doze mode. Exact-second precision is not guaranteed.

5. **Runtime packages are per-app** -- uninstalling the app removes all runtime-installed packages.

6. **60-second execution timeout** -- scripts that run longer are killed. This is not configurable in v1.

7. **No pip conflict resolution** -- if two runtime packages require different versions of the same dependency, the last-installed version wins.

## Project Structure

```
app/src/main/
  AndroidManifest.xml
  java/com/bytesmith/scriptler/
    MainActivity.kt
    ScriptsFragment.kt
    ScriptAdapter.kt
    ScriptEditorActivity.kt
    ScriptDetailsActivity.kt
    CreateScriptDialogFragment.kt
    ScheduleDialogFragment.kt
    SettingsFragment.kt
    PackageManagerFragment.kt
    ScriptRunner.kt
    PythonExecutor.kt
    JavaScriptExecutor.kt
    RuntimePipManager.kt
    ModuleManager.kt
    ModuleInstallDialog.kt
    ScheduleManager.kt
    ScriptExecutionWorker.kt
    BootReceiver.kt
    NotificationUtils.kt
    StoragePermissionManager.kt
    ScriptRepository.kt
    CustomEditor.kt
    models/
      Script.kt
      ScriptLog.kt
    utils/
      FileUtils.kt
      DateUtils.kt
  res/
    drawable/          -- 15 vector icons, 4 background drawables
    layout/            -- 9 layout XMLs
    menu/              -- Bottom nav, overflow menu
    raw/               -- package_name_map.json
    values/            -- colors.xml, strings.xml, themes.xml, dimens.xml
    values-night/      -- themes.xml (dark variant)
    mipmap-*/          -- Launcher icons
```
