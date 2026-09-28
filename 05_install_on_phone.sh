#!/usr/bin/env bash
# Installs the APK on a phone connected by USB (USB debugging must be on).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; source "$HERE/env.sh"
MODE="${1:-debug}"

BUILD_NUM="$(cat "$HERE/.build_number" 2>/dev/null || echo "")"
APK="$HERE/Blocker-$MODE-$BUILD_NUM.apk"

# Fallback to latest matching APK if exact build number is not present
if [ ! -f "$APK" ]; then
  APK="$(ls -t "$HERE"/Blocker-"$MODE"-*.apk 2>/dev/null | head -1 || true)"
fi

if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  echo "No APK found for mode '$MODE'. Run ./build_apk.sh $MODE first."
  exit 1
fi

echo "Installing $APK..."
adb devices
adb install -r "$APK"
echo "Installed. Open Blocker on the phone."
