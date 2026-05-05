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
| UI | Jetpack Compose, Material 3, dark/light theme with dynamic color |

### Module Loading Strategy

Scriptler uses a hybrid approach for Python package availability:

1. **Build-time bundling** -- Packages are declared in `prebundled_packages.txt` and installed via Chaquopy in `app/build.gradle`. Native C extension packages (those with `.so` files like `lxml`) are cross-compiled for Android ABIs at build time. This is the only way to support native packages on Android.

2. **Runtime pure-Python downloader** -- `RuntimePipManager` queries the PyPI JSON API, filters for pure-Python wheels (`py3-none-any`), downloads them, and extracts them to `context.filesDir/python_libs/`. This supports thousands of packages (requests, jinja2, flask, pyyaml, schedule, etc.) without increasing APK size.

3. **Local module imports** -- The script folder is added to `sys.path` before execution, so `import my_helper` works if `my_helper.py` exists in the same directory.

**Constraint:** Native C extension packages cannot be loaded at runtime. Android 10+ enforces W^X security policy that blocks `dlopen()` from writable paths. Packages like numpy, pandas, Pillow, cryptography must be pre-bundled in the APK.

### Storage

The app supports three storage modes, configured at first run:

| Mode | Path | Requirements |
|------|------|-------------|
| DEFAULT_PATH | `/storage/emulated/0/Documents/Scriptler/` | `MANAGE_EXTERNAL_STORAGE` permission |
| SAF_CUSTOM | User-chosen directory via Storage Access Framework | None (persisted URI permission) |
| APP_ONLY | `/storage/emulated/0/Android/data/com.bytesmith.scriptler/files/Scriptler/` | None |

Script folder structure (regardless of mode):

```
Scriptler/
  mango/
    mango.py          -- Main script
    config.json       -- User-added data file
    my_helper.py      -- Local module importable by mango.py
  banana/
    banana.js
    notes.txt
```

In SAF_CUSTOM mode, scripts are copied to cache before execution since Chaquopy and Rhino require file paths.

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
  MainActivityCompose.kt       -- Launcher activity, Compose host, first-run storage setup

  ui/
    screens/
      ScriptsListScreenCompose.kt   -- Script list with cards, language badge, toggle, FAB
      ScriptEditorScreenCompose.kt  -- Code editor with line numbers, auto-save
      ScriptDetailsScreenCompose.kt -- Execution logs, next run countdown, Run Now
      PackageManagerScreenCompose.kt-- Search/install/uninstall Python packages from PyPI
      SettingsScreenCompose.kt      -- Theme, font size, auto-save, notifications, cache
    viewmodel/
      ScriptsListViewModel.kt       -- StateFlow for script list, CRUD operations
      ScriptEditorViewModel.kt      -- StateFlow for editor state, save/load
      ScriptDetailsViewModel.kt     -- StateFlow for script details and logs
      PackageManagerViewModel.kt    -- StateFlow for package search/install
      SettingsViewModel.kt          -- StateFlow for settings state
    navigation/
      Screen.kt                -- Sealed class for route definitions
      ScriptlerNavHost.kt      -- NavHost with fade transitions, ViewModel wiring
    components/
      CreateScriptDialogCompose.kt -- Name + language picker dialog
      CodeEditor.kt            -- Compose code editor with line numbers
      ScriptlerButton.kt       -- Primary, outlined, text, icon button variants
      ScriptlerCard.kt         -- Card, card with gutter, setting item
      ScriptlerTextField.kt    -- Outlined and monospace text fields
      ScriptlerSwitch.kt       -- Styled switch toggle
      ScriptlerTopAppBar.kt    -- Standard and centered top app bars
      ScheduleButton.kt        -- Schedule type display button
      CommonStates.kt          -- Shared loading, error, empty state composables
    theme/
      Color.kt                 -- Color definitions, light/dark schemes
      Type.kt                  -- Typography scale (Space Grotesk, Inter, Roboto Mono)
      Spacing.kt               -- Spacing scale, icon sizes, elevation values
      Shapes.kt                -- Rounded corner shapes
      Theme.kt                 -- Material 3 theme wrapper, dynamic color support

  ScriptRunner.kt              -- Unified execution interface, timeout enforcement, friendly errors
  PythonExecutor.kt            -- Chaquopy wrapper: sys.path config, stdout capture, execution
  JavaScriptExecutor.kt        -- Rhino wrapper: console.log, error reporting with line numbers
  RuntimePipManager.kt         -- PyPI API client, wheel downloader, package tracker
  ModuleManager.kt             -- Unified package tracking (build-time + runtime)
  ModuleInstallDialog.kt       -- Missing package prompt with install flow
  ScheduleManager.kt           -- WorkManager scheduling: interval, daily, weekly
  ScheduleDialogFragment.kt    -- Schedule type picker dialog (none, interval, daily, weekly)
  ScriptExecutionWorker.kt     -- WorkManager worker for background execution
  BootReceiver.kt              -- Re-registers schedules after device reboot
  NotificationUtils.kt         -- Execution result notifications
  StoragePermissionManager.kt  -- Three storage modes: DEFAULT_PATH, SAF_CUSTOM, APP_ONLY
  ScriptRepository.kt          -- CRUD for scripts and logs via JSON files
  ImportDetector.kt            -- Scans script source for import statements

  models/Script.kt             -- Data class: id, name, language, schedule, lastRun, nextRun, isActive
  models/ScriptLog.kt          -- Data class: id, scriptId, timestamp, runNumber, output, isError
  utils/FileUtils.kt           -- Script/log file I/O, directory management, SAF support
  utils/DateUtils.kt           -- Date formatting, relative time, countdown
```

## Building

### Prerequisites

- Android Studio Hedgehog or later
- Android SDK with compileSdk 34
- JDK 17
- Internet connection (Chaquopy downloads Python wheels during build)

### Build Steps

Using the wrapper scripts (resolves Chaquopy + Python versions automatically):

```bash
# Windows
build.bat assembleDebug

# Linux/Mac
./build.sh assembleDebug
```

Or directly with Gradle (uses default versions: Chaquopy 17.0, Python 3.10):

```bash
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

#### Pinned Version Builds

The wrapper scripts accept `--chaquopy` and `--python` flags to pin specific versions. Incompatible packages are skipped with warnings instead of failing the build:

```bash
# Pin Chaquopy, auto-pick best Python
build.bat --chaquopy 16.0.0 assembleDebug

# Pin Python, auto-pick best Chaquopy
./build.sh --python 3.9 assembleDebug

# Pin both
build.bat --chaquopy 16.0.0 --python 3.9 assembleDebug
```

### Build Configuration

The app targets:
- `minSdk 24` (Android 7.0)
- `targetSdk 34` (Android 14)
- Default ABIs: `armeabi-v7a`, `arm64-v8a` (configurable via `build-config.properties`)

Chaquopy Python version is resolved automatically by `scripts/resolve-build-config.py`. The resolver reads `prebundled_packages.txt`, queries the Chaquopy PyPI repository for compatible wheels, and selects the highest Chaquopy + Python version combination that supports all declared packages. If no combination works, it fails with an actionable error.

The resolver can also be run manually:

```bash
python scripts/resolve-build-config.py
python scripts/resolve-build-config.py --chaquopy 16.0.0 --python 3.9  # pin versions
```

Output is written to `app/build-config.properties` (gitignored), which `app/build.gradle` reads at build time.

### Adding Packages

Packages are declared in `prebundled_packages.txt` in the repository root. This file is read by both:
- `app/build.gradle` (at build time to install packages via Chaquopy)
- The app itself (at runtime to display pre-bundled packages in the Package Manager)

Format: one package name per line. Lines starting with `#` or `//` are comments. Empty lines are ignored.

Example:
```
// Core Native Packages (Required)
lxml

// Pre-bundled Pure-Python Packages (For convenience)
requests
beautifulsoup4
```

**Native packages** (those with C extensions like `lxml`, `numpy`, `Pillow`) must be listed here because they require cross-compilation for Android. They cannot be installed at runtime due to Android's W^X security policy.

**Pure-Python packages** can be listed here for convenience (they'll be available in every build) or installed at runtime via the Package Manager (they'll only be downloaded when needed).

### CI/CD Build and Release

The project uses GitHub Actions (see `.github/workflows/build-release.yml`):

1. **Trigger** -- Push to `main` branch or manual `workflow_dispatch`
2. **Resolve** -- Runs `scripts/resolve-build-config.py` to determine Chaquopy + Python versions
3. **Build** -- Sets up resolved Python version, runs `./gradlew assembleDebug`
4. **Versioning** --
   - Main repo: auto-increments version in `app/build.gradle`
   - Fork: keeps current version, adds owner prefix to APK filename
5. **APK Naming** -- Format: `{Owner_}scriptler_v{x-y-z}_{modules}.apk`
   - Example (main repo): `scriptler_v1-0-1_lxml_requests_beautifulsoup4.apk`
   - Example (fork): `username_scriptler_v1-0-0_lxml.apk`
6. **Release** -- Creates GitHub Release with commit history and module list

To get a release build:
- **Main repo**: push to `main` branch
- **Fork**: go to Actions tab → "Build and Release Scriptler APK" → Run workflow

## Permissions

| Permission | Purpose | Required |
|-----------|---------|----------|
| `MANAGE_EXTERNAL_STORAGE` | Read/write scripts in Documents/Scriptler/ (DEFAULT_PATH mode) | No -- app supports SAF custom directory and app-only storage |
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

1. `ImportDetector.extractImports()` scans the script for `import X` and `from X import Y` statements
2. `ScriptRunner.checkImports()` checks each import against available modules
3. If missing packages are found, `ModuleInstallDialog` is shown
4. For each missing package, `RuntimePipManager` queries `https://pypi.org/pypi/{name}/json`
5. If a pure-Python wheel exists (`py3-none-any`), it is downloaded and extracted to `filesDir/python_libs/`
6. If no pure-Python wheel exists, the user is told the package requires native libraries
7. Before execution, `PythonExecutor` adds `filesDir/python_libs/` and all subdirectories to `sys.path`

Import-name to pip-name resolution is handled at runtime by querying the PyPI JSON API. When a user imports `bs4`, the system queries `https://pypi.org/pypi/bs4/json` and resolves it to the `beautifulsoup4` package. This works for most common import-name mismatches (`PIL` -> `Pillow`, `yaml` -> `PyYAML`, etc.).

### Dependency Resolution

`RuntimePipManager.installPackageWithDependencies()` parses `requires_dist` from the PyPI JSON response to recursively install dependencies. Each dependency's `requires_dist` is parsed in turn, up to a maximum depth of 5. Platform-specific dependencies (Windows, macOS) and extra-specific dependencies are filtered out. Build-time packages are skipped if already bundled. If a dependency fails to install, the process continues -- the failing dependency may be optional.

## Known Limitations

1. **Native packages are build-time only** -- numpy, pandas, Pillow, cryptography, scipy, opencv-python cannot be installed at runtime. They must be added to `prebundled_packages.txt` and the APK rebuilt.

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
    MainActivityCompose.kt          -- Launcher activity
    MainActivity.kt                 -- Legacy (not in manifest)
    ui/
      screens/                      -- Compose screen composables
        ScriptsListScreenCompose.kt
        ScriptEditorScreenCompose.kt
        ScriptDetailsScreenCompose.kt
        PackageManagerScreenCompose.kt
        SettingsScreenCompose.kt
      viewmodel/                    -- One ViewModel per screen
        ScriptsListViewModel.kt
        ScriptEditorViewModel.kt
        ScriptDetailsViewModel.kt
        PackageManagerViewModel.kt
        SettingsViewModel.kt
      navigation/
        Screen.kt                   -- Route definitions
        ScriptlerNavHost.kt         -- NavHost + ViewModel wiring
      components/                   -- Reusable composables
        CreateScriptDialogCompose.kt
        CodeEditor.kt
        ScriptlerButton.kt
        ScriptlerCard.kt
        ScriptlerTextField.kt
        ScriptlerSwitch.kt
        ScriptlerTopAppBar.kt
        ScheduleButton.kt
        CommonStates.kt
      theme/                        -- Design system
        Color.kt
        Type.kt
        Spacing.kt
        Shapes.kt
        Theme.kt
    ScriptRunner.kt
    PythonExecutor.kt
    JavaScriptExecutor.kt
    RuntimePipManager.kt
    ModuleManager.kt
    ModuleInstallDialog.kt
    ScheduleManager.kt
    ScheduleDialogFragment.kt
    ScriptExecutionWorker.kt
    BootReceiver.kt
    NotificationUtils.kt
    StoragePermissionManager.kt
    ScriptRepository.kt
    ImportDetector.kt
    models/
      Script.kt
      ScriptLog.kt
    utils/
      FileUtils.kt
      DateUtils.kt
  res/
    drawable/          -- Vector icons, background drawables
    layout/            -- Legacy layout XMLs (unused by Compose)
    menu/              -- Legacy menu XMLs (unused by Compose)
    raw/               -- package_name_map.json, prebundled_packages.txt
    values/            -- colors.xml, strings.xml, themes.xml, dimens.xml
    values-night/      -- themes.xml (dark variant)
    mipmap-*/          -- Launcher icons
scripts/
  resolve-build-config.py          -- Chaquopy/Python version resolver
build.bat                          -- Windows build wrapper
build.sh                           -- Linux/Mac build wrapper
prebundled_packages.txt            -- Build-time package declarations
```
