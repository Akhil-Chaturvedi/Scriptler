#!/bin/bash
# Scriptler Build Wrapper
# Runs the build config resolver before Gradle to ensure compatible versions
#
# Usage:
#   build.sh [options] [gradle_args...]
#   Options:
#     --chaquopy VERSION   Pin Chaquopy version (e.g. 16.0.0)
#     --python VERSION     Pin Python version (e.g. 3.10)

RESOLVER_ARGS=()
GRADLE_ARGS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --chaquopy)
            RESOLVER_ARGS+=("--chaquopy" "$2")
            shift 2
            ;;
        --python)
            RESOLVER_ARGS+=("--python" "$2")
            shift 2
            ;;
        *)
            GRADLE_ARGS+=("$1")
            shift
            ;;
    esac
done

python3 scripts/resolve-build-config.py "${RESOLVER_ARGS[@]}"
if [ $? -ne 0 ]; then
    echo "ERROR: Build config resolution failed. See above for details."
    exit 1
fi

./gradlew "${GRADLE_ARGS[@]}"
