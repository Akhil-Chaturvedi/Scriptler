@echo off
REM Scriptler Build Wrapper
REM Runs the build config resolver before Gradle to ensure compatible versions
REM
REM Usage:
REM   build.bat [options] [gradle_args...]
REM   Options:
REM     --chaquopy VERSION   Pin Chaquopy version (e.g. 16.0.0)
REM     --python VERSION     Pin Python version (e.g. 3.10)

setlocal enabledelayedexpansion

set RESOLVER_ARGS=
set GRADLE_ARGS=
set SKIP_NEXT=

for %%A in (%*) do (
    if defined SKIP_NEXT (
        set SKIP_NEXT=
    ) else if "%%A"=="--chaquopy" (
        set SKIP_NEXT=1
    ) else if "%%A"=="--python" (
        set SKIP_NEXT=1
    ) else (
        set "GRADLE_ARGS=!GRADLE_ARGS! %%A"
    )
)

REM Re-parse to build resolver args (need to handle --flag value pairs)
set RESOLVER_ARGS=
set IS_FLAG_VALUE=0
for %%A in (%*) do (
    if "!IS_FLAG_VALUE!"=="1" (
        set "RESOLVER_ARGS=!RESOLVER_ARGS! %%A"
        set IS_FLAG_VALUE=0
    ) else if "%%A"=="--chaquopy" (
        set "RESOLVER_ARGS=!RESOLVER_ARGS! --chaquopy"
        set IS_FLAG_VALUE=1
    ) else if "%%A"=="--python" (
        set "RESOLVER_ARGS=!RESOLVER_ARGS! --python"
        set IS_FLAG_VALUE=1
    )
)

REM Run the resolver with flags
python scripts\resolve-build-config.py %RESOLVER_ARGS% 2>nul
if %ERRORLEVEL% EQU 9009 (
    py scripts\resolve-build-config.py %RESOLVER_ARGS%
)
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Build config resolution failed. See above for details.
    exit /b 1
)

REM Run Gradle with remaining arguments
call gradlew.bat %GRADLE_ARGS%
