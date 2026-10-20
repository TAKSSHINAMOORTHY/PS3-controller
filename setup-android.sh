#!/usr/bin/env bash
set -euo pipefail

# 1-Command Setup for Android PS3 Controller:
# Checks device, builds APK, sets up USB reverse tunnel, installs, and launches app.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"${SCRIPT_DIR}/scripts/install-android.sh" "$@"
