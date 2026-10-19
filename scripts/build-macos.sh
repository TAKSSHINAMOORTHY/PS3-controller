#!/usr/bin/env bash
set -euo pipefail

# Script: build-macos.sh
# Purpose: Build native macOS PS3 Controller Companion application bundle

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
MAC_DIR="${PROJECT_ROOT}/MacApp"
BUILD_DIR="${MAC_DIR}/build"
APP_BUNDLE="${BUILD_DIR}/PS3ControllerCompanion.app"
BINARY_SOURCE="${MAC_DIR}/.build/release/MacApp"

echo "=================================================="
echo "  Building macOS PS3 Controller Companion"
echo "=================================================="

# Check Swift compiler
if ! command -v swift &> /dev/null; then
    echo "❌ Error: 'swift' toolchain not found."
    echo "   Please install Xcode or the Xcode Command Line Tools:"
    echo "     xcode-select --install"
    exit 1
fi

echo "📦 Compiling release binary with Swift Package Manager..."
cd "${MAC_DIR}"
swift build -c release

if [ ! -f "${BINARY_SOURCE}" ]; then
    echo "❌ Error: Compiled binary not found at ${BINARY_SOURCE}"
    exit 1
fi

echo "📂 Packaging into application bundle (${APP_BUNDLE})..."
mkdir -p "${APP_BUNDLE}/Contents/MacOS"
mkdir -p "${APP_BUNDLE}/Contents/Resources"

cp "${BINARY_SOURCE}" "${APP_BUNDLE}/Contents/MacOS/PS3ControllerCompanion"

if [ -f "${MAC_DIR}/Info.plist" ]; then
    cp "${MAC_DIR}/Info.plist" "${APP_BUNDLE}/Contents/Info.plist"
elif [ -f "${APP_BUNDLE}/Contents/Info.plist" ]; then
    echo "   Using existing bundle Info.plist."
else
    echo "⚠️ Generating fallback Info.plist..."
    cat <<EOF > "${APP_BUNDLE}/Contents/Info.plist"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>PS3ControllerCompanion</string>
    <key>CFBundleIdentifier</key>
    <string>com.antigravity.ps3companion</string>
    <key>CFBundleName</key>
    <string>PS3 Controller Companion</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0.0</string>
    <key>LSMinimumSystemVersion</key>
    <string>14.0</string>
    <key>NSHighResolutionCapable</key>
    <true/>
</dict>
</plist>
EOF
fi

# Ad-hoc sign for local execution
echo "✍️ Applying ad-hoc codesign..."
codesign --force --deep --sign - "${APP_BUNDLE}"

echo ""
echo "=================================================="
echo "✅ macOS build and packaging succeeded!"
echo "   Application Bundle: ${APP_BUNDLE}"
echo "   Executable:         ${APP_BUNDLE}/Contents/MacOS/PS3ControllerCompanion"
echo "   CLI Headless Tool:  ${BINARY_SOURCE}"
echo "=================================================="
