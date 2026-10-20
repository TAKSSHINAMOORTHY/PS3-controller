#!/usr/bin/env bash
set -euo pipefail

# 1-Command Setup for macOS Companion:
# Builds release binary, bundles to /Applications, checks Accessibility, and launches app.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"${SCRIPT_DIR}/scripts/install-macos.sh" "$@"
