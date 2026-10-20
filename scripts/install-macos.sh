#!/usr/bin/env bash
set -euo pipefail

# Script: install-macos.sh
# Purpose: 1-command build, install, permission check, and launch of PS3ControllerCompanion.app

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
MAC_DIR="${PROJECT_ROOT}/MacApp"
BUILD_DIR="${MAC_DIR}/build"
APP_BUNDLE="${BUILD_DIR}/PS3ControllerCompanion.app"
DEST_SYSTEM="/Applications/PS3ControllerCompanion.app"
DEST_USER="${HOME}/Applications/PS3ControllerCompanion.app"

AUTO_LAUNCH=true
HEADLESS_MODE=false

for arg in "$@"; do
    case "$arg" in
        --no-launch)
            AUTO_LAUNCH=false
            ;;
        --headless)
            HEADLESS_MODE=true
            ;;
        *)
            ;;
    esac
done

echo "=================================================="
echo "  Setting up PS3 Controller Companion on macOS"
echo "=================================================="

# Check Swift compiler / Xcode Command Line Tools
if ! command -v swift &> /dev/null; then
    echo "❌ Error: 'swift' compiler toolchain not found."
    echo "   Please install Xcode Command Line Tools by running:"
    echo "     xcode-select --install"
    exit 1
fi

# Determine if rebuild is needed (missing bundle or newer source files)
NEED_BUILD=false
if [ ! -d "${APP_BUNDLE}" ] || [ ! -f "${APP_BUNDLE}/Contents/MacOS/PS3ControllerCompanion" ]; then
    NEED_BUILD=true
else
    NEWER_FILES=$(find "${MAC_DIR}/Sources" "${MAC_DIR}/Package.swift" -type f -newer "${APP_BUNDLE}/Contents/MacOS/PS3ControllerCompanion" 2>/dev/null | head -n 1 || true)
    if [ -n "${NEWER_FILES}" ]; then
        echo "🔄 Source code modifications detected. Rebuilding bundle..."
        NEED_BUILD=true
    fi
fi

if [ "$NEED_BUILD" = true ]; then
    echo "⚙️ Building macOS companion app..."
    "${SCRIPT_DIR}/build-macos.sh"
fi

# Determine target install location
TARGET_DEST="${DEST_SYSTEM}"
if [ ! -w "/Applications" ]; then
    echo "ℹ️ /Applications is not writable without sudo. Installing to user directory: ${DEST_USER}"
    mkdir -p "${HOME}/Applications"
    TARGET_DEST="${DEST_USER}"
fi

# Gracefully terminate running instance before updating bundle
pkill -f "PS3ControllerCompanion" 2>/dev/null || true
sleep 0.5

echo "📦 Installing application to: ${TARGET_DEST}..."
rm -rf "${TARGET_DEST}"
cp -R "${APP_BUNDLE}" "${TARGET_DEST}"

# Re-sign installed copy with stable bundle identifier designated requirement for persistent TCC accessibility
codesign --force --deep -s - --requirements '=designated => identifier "com.antigravity.ps3companion"' "${TARGET_DEST}"

# Check Accessibility permissions
IS_TRUSTED="false"
if command -v swift &> /dev/null; then
    IS_TRUSTED=$(swift -e 'import ApplicationServices; print(AXIsProcessTrusted())' 2>/dev/null || echo "false")
fi

echo ""
echo "=================================================="
echo "✅ macOS Companion successfully installed!"
echo "   Location: ${TARGET_DEST}"
echo ""

if [ "${IS_TRUSTED}" = "true" ]; then
    echo "🔒 Accessibility Permission: GRANTED ✅"
else
    echo "⚠️  ACCESSIBILITY PERMISSION REQUIRED:"
    echo "   macOS requires Accessibility permissions for synthetic input injection."
    echo "   Opening System Settings > Privacy & Security > Accessibility now..."
    echo "   -> Please ensure 'PS3 Controller Companion' (or Terminal) is ENABLED."
    echo ""
    open "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility" 2>/dev/null || true
fi

# Launch application
if [ "$HEADLESS_MODE" = true ]; then
    echo "🚀 Starting headless daemon..."
    "${TARGET_DEST}/Contents/MacOS/PS3ControllerCompanion" --headless &
    echo "   Headless daemon running in background."
elif [ "$AUTO_LAUNCH" = true ]; then
    echo "🚀 Launching PS3 Controller Companion..."
    open "${TARGET_DEST}"
fi

echo ""
echo "🎮 RPCS3 Quick Setup:"
echo "   1. Open RPCS3 -> Configuration -> Pads (or click Pads toolbar icon)"
echo "   2. Select Player 1 -> Handler: Keyboard -> Device: Keyboard"
echo "   3. Launch your game (e.g. God of War III) and click inside the game window!"
echo "=================================================="
