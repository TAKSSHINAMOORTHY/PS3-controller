#!/usr/bin/env bash
set -euo pipefail

# Script: install-android.sh
# Purpose: Install and launch Android PS3 Controller on USB-connected device via ADB

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ANDROID_DIR="${PROJECT_ROOT}/AndroidApp"
APK_PATH="${ANDROID_DIR}/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE_NAME="com.antigravity.ps3controller"
ACTIVITY_NAME="${PACKAGE_NAME}/.MainActivity"

echo "=================================================="
echo "  Installing Android PS3 Controller via ADB"
echo "=================================================="

# Check ADB prerequisite
if ! command -v adb &> /dev/null; then
    echo "❌ Error: 'adb' command not found."
    echo "   Please install Android platform tools via Homebrew:"
    echo "     brew install android-platform-tools"
    echo "   Or add your Android SDK platform-tools directory to PATH."
    exit 1
fi

# Check connected devices
echo "🔍 Checking connected Android devices..."
DEVICES_OUTPUT=$(adb devices | grep -v "List of devices" | grep "device$" || true)

if [ -z "${DEVICES_OUTPUT}" ]; then
    echo "❌ Error: No authorized Android device detected."
    echo ""
    echo "   Troubleshooting steps:"
    echo "   1. Connect your Android phone using a USB data cable (not a charge-only cable)."
    echo "   2. Enable Developer Options (Settings > About Phone > Tap 'Build Number' 7 times)."
    echo "   3. Enable USB Debugging in Developer Options."
    echo "   4. Accept the 'Allow USB debugging?' RSA prompt on your phone screen."
    echo "   5. Run 'adb devices' to confirm your device is listed as 'device'."
    exit 1
fi

DEVICE_ID=$(echo "${DEVICES_OUTPUT}" | head -n 1 | awk '{print $1}')
echo "📱 Target device found: ${DEVICE_ID}"

# Build APK if missing
if [ ! -f "${APK_PATH}" ]; then
    echo "⚙️ APK not found. Triggering build script..."
    "${SCRIPT_DIR}/build-android.sh"
fi

# Configure USB reverse port forwarding for zero-latency local loopback
echo "🔌 Setting up zero-latency USB port forwarding (tcp:6000 -> tcp:6000)..."
adb reverse tcp:6000 tcp:6000

# Install APK
echo "📲 Installing APK onto device (${DEVICE_ID})..."
adb install -r "${APK_PATH}"

# Launch MainActivity
echo "🚀 Launching PS3 Controller on phone..."
adb shell am start -n "${ACTIVITY_NAME}"

echo ""
echo "=================================================="
echo "✅ Installation complete!"
echo "   App package:  ${PACKAGE_NAME}"
echo "   Active host:  127.0.0.1 (USB) or Wi-Fi IP"
echo "   Port:         6000"
echo "=================================================="
