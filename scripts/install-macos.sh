#!/usr/bin/env bash
set -euo pipefail

# Script: install-macos.sh
# Purpose: Install PS3ControllerCompanion.app into /Applications (or ~/Applications)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
MAC_DIR="${PROJECT_ROOT}/MacApp"
APP_BUNDLE="${MAC_DIR}/build/PS3ControllerCompanion.app"
DEST_SYSTEM="/Applications/PS3ControllerCompanion.app"
DEST_USER="${HOME}/Applications/PS3ControllerCompanion.app"

echo "=================================================="
echo "  Installing PS3 Controller Companion on macOS"
echo "=================================================="

# Build bundle if not present
if [ ! -d "${APP_BUNDLE}" ] || [ ! -f "${APP_BUNDLE}/Contents/MacOS/PS3ControllerCompanion" ]; then
    echo "⚙️ Application bundle not found. Triggering build-macos.sh..."
    "${SCRIPT_DIR}/build-macos.sh"
fi

# Determine target location
TARGET_DEST="${DEST_SYSTEM}"
if [ ! -w "/Applications" ]; then
    echo "ℹ️ System /Applications is not writable without sudo. Installing to user directory: ${DEST_USER}"
    mkdir -p "${HOME}/Applications"
    TARGET_DEST="${DEST_USER}"
fi

# Gracefully terminate running instance if active before updating files
pkill -f "PS3ControllerCompanion" 2>/dev/null || true
sleep 0.5

echo "📦 Copying ${APP_BUNDLE} to ${TARGET_DEST}..."
rm -rf "${TARGET_DEST}"
cp -R "${APP_BUNDLE}" "${TARGET_DEST}"

# Re-sign installed copy with stable bundle identifier designated requirement for persistent TCC accessibility
codesign --force --deep -s - --requirements '=designated => identifier "com.antigravity.ps3companion"' "${TARGET_DEST}"

echo ""
echo "=================================================="
echo "✅ macOS Companion successfully installed!"
echo "   Location: ${TARGET_DEST}"
echo ""
echo "⚠️ IMPORTANT PERMISSION REQUIREMENT:"
echo "   macOS requires Accessibility permissions for synthetic input."
echo "   Go to: System Settings > Privacy & Security > Accessibility"
echo "   Ensure 'PS3 Controller Companion' (or Terminal) is ENABLED."
echo ""
echo "   To launch the GUI application:"
echo "     open \"${TARGET_DEST}\""
echo ""
echo "   To launch in Headless Server mode:"
echo "     \"${TARGET_DEST}/Contents/MacOS/PS3ControllerCompanion\" --headless"
echo "=================================================="
