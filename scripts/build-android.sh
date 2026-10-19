#!/usr/bin/env bash
set -euo pipefail

# Script: build-android.sh
# Purpose: Build Android PS3 Controller Debug APK

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ANDROID_DIR="${PROJECT_ROOT}/AndroidApp"
APK_PATH="${ANDROID_DIR}/app/build/outputs/apk/debug/app-debug.apk"

echo "=================================================="
echo "  Building Android PS3 Controller APK"
echo "=================================================="

# Check prerequisites
if ! command -v javac &> /dev/null && ! command -v java &> /dev/null; then
    echo "❌ Error: Java Development Kit (JDK) is required but not found in PATH."
    echo "   Please install JDK 17 or later (e.g. 'brew install openjdk@17')."
    exit 1
fi

if [ ! -f "${ANDROID_DIR}/gradlew" ]; then
    echo "❌ Error: Gradle wrapper not found at ${ANDROID_DIR}/gradlew"
    exit 1
fi

chmod +x "${ANDROID_DIR}/gradlew"

echo "📦 Running Gradle assembleDebug..."
cd "${ANDROID_DIR}"
./gradlew assembleDebug

if [ -f "${APK_PATH}" ]; then
    APK_SIZE=$(du -h "${APK_PATH}" | cut -f1)
    echo ""
    echo "=================================================="
    echo "✅ Android build succeeded!"
    echo "   Artifact: ${APK_PATH}"
    echo "   Size:     ${APK_SIZE}"
    echo "=================================================="
else
    echo "❌ Error: Expected APK artifact not found at ${APK_PATH}"
    exit 1
fi
