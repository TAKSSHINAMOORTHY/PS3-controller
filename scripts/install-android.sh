#!/usr/bin/env bash
set -euo pipefail

# Script: install-android.sh
# Purpose: 1-command build, USB port forward, install, and launch of Android PS3 Controller

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ANDROID_DIR="${PROJECT_ROOT}/AndroidApp"
APK_PATH="${ANDROID_DIR}/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE_NAME="com.antigravity.ps3controller"
ACTIVITY_NAME="${PACKAGE_NAME}/.MainActivity"

AUTO_LAUNCH=true
WAIT_FOR_DEVICE=true

for arg in "$@"; do
    case "$arg" in
        --no-launch)
            AUTO_LAUNCH=false
            ;;
        --no-wait)
            WAIT_FOR_DEVICE=false
            ;;
        *)
            ;;
    esac
done

echo "=================================================="
echo "  Setting up Android PS3 Controller via ADB"
echo "=================================================="

# Check ADB prerequisite
if ! command -v adb &> /dev/null; then
    echo "❌ Error: 'adb' (Android Debug Bridge) command not found."
    if command -v brew &> /dev/null; then
        echo "   👉 Install via Homebrew:"
        echo "        brew install android-platform-tools"
    else
        echo "   Please install Android Platform Tools and add 'adb' to your PATH."
    fi
    exit 1
fi

# Check Java prerequisite (needed if APK must be compiled)
if ! command -v javac &> /dev/null && ! command -v java &> /dev/null; then
    echo "❌ Error: Java Development Kit (JDK 17+) not found."
    if command -v brew &> /dev/null; then
        echo "   👉 Install via Homebrew:"
        echo "        brew install openjdk@17"
    fi
    exit 1
fi

# Check connected Android devices
echo "🔍 Checking for connected Android device..."
DEVICES_OUTPUT=$(adb devices | grep -v "List of devices" | grep "device$" || true)

if [ -z "${DEVICES_OUTPUT}" ] && [ "$WAIT_FOR_DEVICE" = true ]; then
    echo "ℹ️  No authorized device detected immediately."
    echo ""
    echo "   Quick Phone Setup:"
    echo "   1. Connect phone to Mac via USB (data cable, not charge-only)."
    echo "   2. Settings > About Phone > Tap 'Build Number' 7 times."
    echo "   3. Settings > Developer Options > Enable 'USB Debugging'."
    echo "   4. Accept the 'Allow USB debugging?' RSA prompt on phone."
    echo ""
    echo "⏳ Waiting for Android device (plug in your phone now, or press Ctrl+C to cancel)..."

    for i in {1..20}; do
        sleep 1
        DEVICES_OUTPUT=$(adb devices | grep -v "List of devices" | grep "device$" || true)
        if [ -n "${DEVICES_OUTPUT}" ]; then
            echo "📱 Device detected!"
            break
        fi

        # Check if device is plugged in but unauthorized
        UNAUTH=$(adb devices | grep -v "List of devices" | grep "unauthorized$" || true)
        if [ -n "${UNAUTH}" ]; then
            echo "⚠️  Device connected but UNAUTHORIZED: Please unlock your phone and tap 'Allow' on the RSA prompt!"
        fi
    done
fi

if [ -z "${DEVICES_OUTPUT}" ]; then
    echo ""
    echo "❌ Error: No authorized Android device detected."
    echo "   Ensure your phone is connected, USB Debugging is ON, and authorization is accepted."
    echo "   Run 'adb devices' to verify your phone is recognized."
    exit 1
fi

DEVICE_ID=$(echo "${DEVICES_OUTPUT}" | head -n 1 | awk '{print $1}')
echo "📱 Target device: ${DEVICE_ID}"

# Determine if APK rebuild is needed (missing APK or source files newer than APK)
NEED_BUILD=false
if [ ! -f "${APK_PATH}" ]; then
    NEED_BUILD=true
else
    NEWER_FILES=$(find "${ANDROID_DIR}/app/src" "${ANDROID_DIR}/build.gradle.kts" -type f -newer "${APK_PATH}" 2>/dev/null | head -n 1 || true)
    if [ -n "${NEWER_FILES}" ]; then
        echo "🔄 Source code modifications detected. Rebuilding APK..."
        NEED_BUILD=true
    fi
fi

if [ "$NEED_BUILD" = true ]; then
    echo "⚙️ Building Android APK with Gradle..."
    "${SCRIPT_DIR}/build-android.sh"
fi

# Configure USB reverse port forwarding for zero-latency local loopback
echo "🔌 Configuring zero-latency USB reverse tunnel (tcp:6000 -> tcp:6000)..."
adb reverse tcp:6000 tcp:6000

# Install APK onto device
echo "📲 Installing APK onto device (${DEVICE_ID})..."
adb install -r "${APK_PATH}"

# Launch MainActivity
if [ "$AUTO_LAUNCH" = true ]; then
    echo "🚀 Launching PS3 Controller on phone..."
    adb shell am start -n "${ACTIVITY_NAME}"
fi

echo ""
echo "=================================================="
echo "✅ Android Controller successfully installed & connected!"
echo "   App package:  ${PACKAGE_NAME}"
echo "   Mode:         Zero-latency USB reverse tunnel (port 6000)"
echo "   Next step:    Ensure Mac companion is running ('./setup-mac.sh')"
echo "=================================================="
