#!/usr/bin/env bash
# Installs the APK on a phone connected by USB (USB debugging must be on).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; source "$HERE/env.sh"
INPUT="${1:-debug}"
FLAG="${2:-}"
APK=""

APK_DIR="$HERE/apk"

# Direct APK file path or filename support
if [ -f "$INPUT" ]; then
  APK="$(realpath "$INPUT")"
elif [ -f "$APK_DIR/$INPUT" ]; then
  APK="$APK_DIR/$INPUT"
elif [ -f "$HERE/$INPUT" ]; then
  APK="$HERE/$INPUT"
elif [[ "$INPUT" == *.apk ]]; then
  echo "Error: APK file '$INPUT' not found in $APK_DIR or current directory."
  exit 1
fi

if [ -z "$APK" ]; then
  MODE="$INPUT"
  BUILD_NUM="$(cat "$HERE/.build_number" 2>/dev/null || echo "0")"

  # Determine target mode:
  TARGET_MODE="$MODE"
  if [ "$MODE" = "release" ] && [ "$FLAG" != "--final" ] && [ "$FLAG" != "-f" ]; then
    TARGET_MODE="release-debug"
  fi

  # Check in apk/ directory first, fallback to root
  for DIR in "$APK_DIR" "$HERE"; do
    if [ -f "$DIR/Blocker-$TARGET_MODE-$BUILD_NUM.apk" ]; then
      APK="$DIR/Blocker-$TARGET_MODE-$BUILD_NUM.apk"
      break
    fi
  done

  # If exact build number APK does not exist, check previous build number $((BUILD_NUM - 1))
  if [ -z "$APK" ] && [ "$BUILD_NUM" -gt 0 ] 2>/dev/null; then
    PREV=$((BUILD_NUM - 1))
    for DIR in "$APK_DIR" "$HERE"; do
      if [ -f "$DIR/Blocker-$TARGET_MODE-$PREV.apk" ]; then
        APK="$DIR/Blocker-$TARGET_MODE-$PREV.apk"
        break
      fi
    done
  fi

  # Fallback to latest matching APK for target mode
  if [ -z "$APK" ]; then
    APK="$(ls -t "$APK_DIR"/Blocker-"$TARGET_MODE"-*.apk "$HERE"/Blocker-"$TARGET_MODE"-*.apk 2>/dev/null | head -1 || true)"
  fi

  # If release-debug was targeted but not found, fallback to release
  if [ -z "$APK" ] || [ ! -f "$APK" ]; then
    if [ "$TARGET_MODE" = "release-debug" ]; then
      APK="$(ls -t "$APK_DIR"/Blocker-release-*.apk "$HERE"/Blocker-release-*.apk 2>/dev/null | head -1 || true)"
      TARGET_MODE="release"
    fi
  fi

  if [ -z "$APK" ] || [ ! -f "$APK" ]; then
    echo "No APK found for mode '$MODE'. Run ./build_apk.sh $MODE first."
    exit 1
  fi
fi

if [ -n "${MODE:-}" ] && [ "$MODE" = "release" ] && [ "$TARGET_MODE" = "release-debug" ]; then
  echo "=== Notice: Installing Test Version ==="
  echo "Installing test APK: $(basename "$APK")"
  echo "This version has identical code and blocklists, but 1-minute timers for testing."
  echo "To install the final locked-down release (real 1-30 day timers, no back door), run:"
  echo "    ./05_install_on_phone.sh release --final"
  echo
else
  echo "Installing $APK..."
fi

adb devices
adb install -r "$APK"
echo "Installed. Open Blocker on the phone."
