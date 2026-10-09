#!/usr/bin/env bash
# One script to go from a fresh Arch Linux machine to a Blocker APK.
#
# Usage:
#   ./build_apk.sh            # first run: installs tools + SDK if needed, creates the project, builds debug
#   ./build_apk.sh debug      # (re)build the debug APK (1-minute timers, for testing)
#   ./build_apk.sh release    # (re)build the release APK (real 1-30 day timers, no back door)
#
# Safe to re-run: it skips any step that's already done and only re-copies your source/blocklists
# and rebuilds the APK. Installing on your phone is a separate step: ./05_install_on_phone.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/env.sh"
MODE="${1:-debug}"
[ "$MODE" = debug ] || [ "$MODE" = release ] || [ "$MODE" = release-debug ] || { echo "Usage: ./build_apk.sh [debug|release|release-debug]"; exit 1; }
PROJ="${BLOCKER_DIR:-$HERE/project}"

LOG_FILE="$HERE/build.log"
exec > >(tee -a "$LOG_FILE") 2>&1
echo "=== Build started at $(date '+%Y-%m-%d %H:%M:%S') [Mode: $MODE] ==="

step() { echo; echo "=== $1 ==="; }

# ---------- 1. System packages (Java 17, Node, adb, unzip, python, git) ----------
step "Checking system packages"
NEED=()
command -v node >/dev/null 2>&1 || NEED+=("nodejs")
command -v adb >/dev/null 2>&1 || NEED+=("android-tools")
command -v unzip >/dev/null 2>&1 || NEED+=("unzip")
command -v git >/dev/null 2>&1 || NEED+=("git")
[ -x /usr/lib/jvm/java-17-openjdk/bin/java ] || NEED+=("jdk17-openjdk")
if [ "${#NEED[@]}" -gt 0 ]; then
  NODE_PKG=nodejs
  for p in nodejs-lts-jod nodejs-lts-iron; do
    pacman -Si "$p" >/dev/null 2>&1 && NODE_PKG="$p" && break
  done
  PKGS=()
  for n in "${NEED[@]}"; do [ "$n" = nodejs ] && PKGS+=("$NODE_PKG" npm) || PKGS+=("$n"); done
  echo "Installing: ${PKGS[*]}"
  sudo pacman -S --needed "${PKGS[@]}"
else
  echo "Java 17, Node, adb, unzip and git are already installed."
fi
if [ "$(archlinux-java get 2>/dev/null)" != "java-17-openjdk" ]; then
  sudo -n archlinux-java set java-17-openjdk >/dev/null 2>&1 || true
fi

install_ndk_if_needed() {
  local NDK_DEST="$ANDROID_HOME/ndk/25.1.8937393"
  if [ -d "$NDK_DEST" ] && [ -f "$NDK_DEST/source.properties" ]; then
    return 0
  fi

  local NDK_ZIP="${ANDROID_NDK_ZIP:-}"
  if [ -z "$NDK_ZIP" ]; then
    NDK_ZIP="$(ls -t "$HERE"/android-ndk-r25b*.zip "$HERE"/*ndk*r25b*.zip "$HERE"/android-ndk-*.zip "$HERE"/*ndk*.zip 2>/dev/null | head -1 || true)"
  fi
  if [ -z "$NDK_ZIP" ]; then
    NDK_ZIP="$(ls -t "$HOME"/Downloads/android-ndk-r25b*.zip "$HOME"/Downloads/*ndk*r25b*.zip "$HOME"/Downloads/android-ndk-*.zip 2>/dev/null | head -1 || true)"
  fi

  if [ -n "$NDK_ZIP" ] && [ -f "$NDK_ZIP" ]; then
    echo "Unpacking Android NDK from $NDK_ZIP..."
    mkdir -p "$ANDROID_HOME/ndk"
    local NDK_TMP
    NDK_TMP="$(mktemp -d)"
    unzip -q "$NDK_ZIP" -d "$NDK_TMP"
    local UNPACKED_DIR
    UNPACKED_DIR="$(ls -d "$NDK_TMP"/android-ndk-* 2>/dev/null | head -1 || true)"
    if [ -z "$UNPACKED_DIR" ]; then
      UNPACKED_DIR="$NDK_TMP"
    fi
    rm -rf "$NDK_DEST"
    mv "$UNPACKED_DIR" "$NDK_DEST"
    rm -rf "$NDK_TMP"

    cat <<'EOF' > "$NDK_DEST/package.xml"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/02" xmlns:ns3="http://schemas.android.com/repository/android/generic/02" xmlns:ns4="http://schemas.android.com/repository/android/common/01" xmlns:ns5="http://schemas.android.com/repository/android/generic/01" xmlns:ns6="http://schemas.android.com/sdk/android/repo/addon2/01" xmlns:ns7="http://schemas.android.com/sdk/android/repo/repository2/01" xmlns:ns8="http://schemas.android.com/sdk/android/repo/sys-img2/01">
    <license id="android-sdk-license" type="text"/>
    <localPackage path="ndk;25.1.8937393" obsolete="false">
        <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns3:genericDetailsType"/>
        <revision>
            <major>25</major>
            <minor>1</minor>
            <micro>8937393</micro>
        </revision>
        <display-name>NDK (Side by side) 25.1.8937393</display-name>
    </localPackage>
</ns2:repository>
EOF
    echo "Android NDK 25.1.8937393 ready at $NDK_DEST."
  fi
}

setup_gradle_wrapper_if_needed() {
  local WRAPPER_PROPS="$PROJ/android/gradle/wrapper/gradle-wrapper.properties"
  if [ ! -f "$WRAPPER_PROPS" ]; then
    return 0
  fi

  local DIST_URL
  DIST_URL="$(grep 'distributionUrl=' "$WRAPPER_PROPS" | cut -d= -f2- | tr -d '\\' | tr -d '\r' || true)"
  local GRADLE_ZIP_NAME
  GRADLE_ZIP_NAME="$(basename "$DIST_URL" 2>/dev/null || echo "gradle-8.3-all.zip")"
  local GRADLE_BASE_NAME="${GRADLE_ZIP_NAME%.zip}"

  # Default distribution hash for https://services.gradle.org/distributions/gradle-8.3-all.zip
  local HASH="6en3ugtfdg5xnpx44z4qbwgas"

  local G_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
  local TARGET_DIR="$G_USER_HOME/wrapper/dists/$GRADLE_BASE_NAME/$HASH"

  if [ -d "$TARGET_DIR" ] && [ -f "$TARGET_DIR/$GRADLE_ZIP_NAME.ok" ]; then
    return 0
  fi

  local GRADLE_ZIP="${GRADLE_ZIP:-}"
  if [ -z "$GRADLE_ZIP" ]; then
    GRADLE_ZIP="$(ls -t "$HERE"/gradle-*.zip "$HERE"/*gradle*.zip 2>/dev/null | head -1 || true)"
  fi
  if [ -z "$GRADLE_ZIP" ]; then
    GRADLE_ZIP="$(ls -t "$HOME"/Downloads/gradle-*.zip "$HOME"/Downloads/*gradle*.zip 2>/dev/null | head -1 || true)"
  fi

  if [ -n "$GRADLE_ZIP" ] && [ -f "$GRADLE_ZIP" ]; then
    echo "Installing Gradle from local archive: $GRADLE_ZIP..."
    mkdir -p "$TARGET_DIR"
    rm -f "$TARGET_DIR/$GRADLE_ZIP_NAME.part"
    cp "$GRADLE_ZIP" "$TARGET_DIR/$GRADLE_ZIP_NAME"
    unzip -q -o "$TARGET_DIR/$GRADLE_ZIP_NAME" -d "$TARGET_DIR"
    touch "$TARGET_DIR/$GRADLE_ZIP_NAME.ok"
    echo "Gradle wrapper ready at $TARGET_DIR."
  fi
}

setup_build_tools_if_needed() {
  if [ -d "$ANDROID_HOME/build-tools/34.0.0" ] && [ ! -d "$ANDROID_HOME/build-tools/33.0.1" ]; then
    echo "Setting up build-tools 33.0.1 locally from 34.0.0..."
    mkdir -p "$ANDROID_HOME/build-tools/33.0.1"
    cp -a "$ANDROID_HOME/build-tools/34.0.0/"* "$ANDROID_HOME/build-tools/33.0.1/"
    sed -i "s/34.0.0/33.0.1/g" "$ANDROID_HOME/build-tools/33.0.1/source.properties" 2>/dev/null || true
    cat <<'EOF' > "$ANDROID_HOME/build-tools/33.0.1/package.xml"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/02" xmlns:ns3="http://schemas.android.com/repository/android/generic/02">
    <license id="android-sdk-license" type="text"/>
    <localPackage path="build-tools;33.0.1" obsolete="false">
        <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns3:genericDetailsType"/>
        <revision>
            <major>33</major>
            <minor>0</minor>
            <micro>1</micro>
        </revision>
        <display-name>Android SDK Build-Tools 33.0.1</display-name>
    </localPackage>
</ns2:repository>
EOF
  fi
}

# ---------- 2. Android SDK ----------
step "Checking Android SDK"
install_ndk_if_needed
setup_build_tools_if_needed

if command -v sdkmanager >/dev/null 2>&1 && [ -d "$ANDROID_HOME/platforms" ] && ls "$ANDROID_HOME/platforms" | grep -q android-34; then
  echo "Android SDK already set up at $ANDROID_HOME."
elif [ -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
  echo "Found existing SDK tools at $ANDROID_HOME/cmdline-tools/latest — fetching missing packages."
  yes | sdkmanager --licenses >/dev/null 2>&1 || true
  SDK_PKGS=("platform-tools" "platforms;android-34" "build-tools;34.0.0" "build-tools;33.0.1" "cmake;3.22.1")
  if [ ! -d "$ANDROID_HOME/ndk/25.1.8937393" ]; then
    SDK_PKGS+=("ndk;25.1.8937393")
  fi
  sdkmanager "${SDK_PKGS[@]}"
else
  echo "No Android SDK found at $ANDROID_HOME — looking for a command line tools zip you already downloaded..."
  ZIP="${ANDROID_CMDLINE_ZIP:-}"
  if [ -z "$ZIP" ]; then
    ZIP="$(ls -t "$HERE"/commandlinetools-linux-*.zip 2>/dev/null | head -1 || true)"
  fi
  if [ -z "$ZIP" ]; then
    ZIP="$(ls -t "$HERE"/*commandline*.zip 2>/dev/null | head -1 || true)"
  fi
  if [ -z "$ZIP" ]; then
    ZIP="$(ls -t "$HERE"/*cmdline*.zip 2>/dev/null | head -1 || true)"
  fi
  if [ -z "$ZIP" ]; then
    ZIP="$(ls -t "$HOME"/Downloads/commandlinetools-linux-*.zip 2>/dev/null | head -1 || true)"
  fi
  if [ -z "$ZIP" ]; then
    ZIP="$(ls -t "$HOME"/Downloads/*cmdline*.zip 2>/dev/null | head -1 || true)"
  fi
  if [ -z "$ZIP" ]; then
    ZIP="$(find "$HOME" -maxdepth 4 -iname 'commandlinetools-linux-*.zip' 2>/dev/null | head -1 || true)"
  fi
  if [ -z "$ZIP" ]; then
    echo "Couldn't find a commandlinetools-linux-*.zip under $HERE, ~/Downloads or ~."
    echo "Download it from https://developer.android.com/studio#command-line-tools-only (Linux),"
    echo "then either place it in $HERE or re-run with the path set:"
    echo "    ANDROID_CMDLINE_ZIP=/path/to/commandlinetools-linux-XXXXXXX_latest.zip ./build_apk.sh"
    exit 1
  fi
  echo "Using $ZIP"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  TMP="$(mktemp -d)"
  unzip -q "$ZIP" -d "$TMP"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$TMP/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  yes | sdkmanager --licenses >/dev/null 2>&1 || true
  SDK_PKGS=("platform-tools" "platforms;android-34" "build-tools;34.0.0" "build-tools;33.0.1" "cmake;3.22.1")
  if [ ! -d "$ANDROID_HOME/ndk/25.1.8937393" ]; then
    SDK_PKGS+=("ndk;25.1.8937393")
  fi
  sdkmanager "${SDK_PKGS[@]}"
fi

# ---------- 3. Blocklists, whitelist and app icon ----------
step "Importing blocklists/block-list.md, my_list.txt and whitelist.txt"
python3 "$HERE/import_lists.py"
step "Generating the app icon from assets/app-icon-source.png"
python3 "$HERE/generate_icons.py"
python3 "$HERE/embed_icon.py"
python3 "$HERE/embed_shell_ninja_logo.py"
step "Setting the app version"
python3 "$HERE/set_version.py"

# ---------- 4. Create the React Native project (first run only) ----------
if [ ! -d "$PROJ" ]; then
  step "Creating the React Native project in $PROJ"
  npx --yes @react-native-community/cli@12 init Blocker --version 0.73.6 \
    --package-name com.blocker --directory "$PROJ" --pm npm --skip-git-init
  echo "sdk.dir=$ANDROID_HOME" > "$PROJ/android/local.properties"
  if [ -d "$ANDROID_HOME/ndk/25.1.8937393" ]; then
    echo "ndk.dir=$ANDROID_HOME/ndk/25.1.8937393" >> "$PROJ/android/local.properties"
  fi
else
  echo "$PROJ already exists — reusing it."
fi

# Always ensure sdk.dir and ndk.dir point to current ANDROID_HOME in local.properties
if [ -d "$PROJ/android" ]; then
  {
    echo "sdk.dir=$ANDROID_HOME"
    if [ -d "$ANDROID_HOME/ndk/25.1.8937393" ]; then
      echo "ndk.dir=$ANDROID_HOME/ndk/25.1.8937393"
    fi
  } > "$PROJ/android/local.properties"
fi

# ---------- 5. Copy in Blocker's code and native modules, icon, manifest entries ----------
step "Copying source into the project"
python3 "$HERE/patch_android.py" "$PROJ" "$HERE/source" || {
  echo "The patch script printed warnings above \u2014 follow them, then re-run ./build_apk.sh $MODE."
  exit 1
}

# ---------- 6. JS dependencies (icons) ----------
if [ ! -d "$PROJ/node_modules/lucide-react-native" ] || [ ! -d "$PROJ/node_modules/react-native-svg" ]; then
  step "Installing JS dependencies"
  ( cd "$PROJ" && npm install --no-audit --no-fund lucide-react-native@0.462.0 react-native-svg@15.8.0 )
fi

# ---------- 7. Build ----------
step "Building the $MODE APK"
setup_gradle_wrapper_if_needed
cd "$PROJ/android"
chmod +x gradlew
BUILD_NUM="$(cat "$HERE/.build_number" 2>/dev/null || echo "0")"
APP_VER="$(cat "$HERE/VERSION" 2>/dev/null || echo "1.0.0" | tr -d '[:space:]')"

# Define variables clearly
APK_DIR="${HERE}/apk"
# Use hyphens instead of colons for cross-platform filesystem safety
TIMESTAMP=$(date '+%Y-%m-%d_%H-%M-%S')

mkdir -p "${APK_DIR}"

# Store target files in an array for clean, scalable management
TARGET_APKS=(
    "${APK_DIR}/Blocker-release-${APP_VER}.apk"
    "${APK_DIR}/Blocker-release-debug-${APP_VER}.apk"
)

# Process each APK independently
for APK in "${TARGET_APKS[@]}"; do
    if [[ -f "${APK}" ]]; then
        BACKUP_NAME="${APK}.back-${TIMESTAMP}"
        mv "${APK}" "${BACKUP_NAME}"
        
        # Log the action (using basename to keep the log output clean)
        echo "[INFO] Backed up: $(basename "${APK}") -> $(basename "${BACKUP_NAME}")"
    fi
done


if [ "$MODE" = "release" ]; then
  # Build both Release and Release-Debug so the release version can be tested before finally installing
  ./gradlew assembleRelease assembleDebug "-PreactNativeArchitectures=${ARCH:-arm64-v8a}"
  OUT_RELEASE="$APK_DIR/Blocker-release-$APP_VER.apk"
  OUT_DEBUG="$APK_DIR/Blocker-release-debug-$APP_VER.apk"
  cp "app/build/outputs/apk/release/app-release.apk" "$OUT_RELEASE"
  cp "app/build/outputs/apk/debug/app-debug.apk" "$OUT_DEBUG"
  OUT="$OUT_RELEASE"
elif [ "$MODE" = "release-debug" ]; then
  ./gradlew assembleDebug "-PreactNativeArchitectures=${ARCH:-arm64-v8a}"
  OUT="$APK_DIR/Blocker-release-debug-$APP_VER.apk"
  cp "app/build/outputs/apk/debug/app-debug.apk" "$OUT"
else
  # debug
  ./gradlew assembleDebug "-PreactNativeArchitectures=${ARCH:-arm64-v8a}"
  OUT="$APK_DIR/Blocker-release-debug-$APP_VER.apk"
  cp "app/build/outputs/apk/debug/app-debug.apk" "$OUT"
fi

step "Done"
if [ "$MODE" = "release" ]; then
  echo "Release APK ready:        $OUT_RELEASE (real 1-30 day timers, no back door)"
  echo "Release-Debug APK ready:  $OUT_DEBUG (1-minute timers for testing)"
  echo "Version: $(cat "$HERE/VERSION" 2>/dev/null || echo "?") (build $BUILD_NUM)"
  echo
  echo "To test on your phone first:"
  echo "  ./05_install_on_phone.sh release"
  echo "  (automatically installs the release-debug test version)"
  echo
  echo "When ready for the final locked-down release:"
  echo "  ./05_install_on_phone.sh release --final"
else
  echo "APK ready: $OUT"
  echo "Version: $(cat "$HERE/VERSION" 2>/dev/null || echo "?") (build $BUILD_NUM)"
  echo "Copy it to your phone and tap it, or run: ./05_install_on_phone.sh $MODE"
fi
echo "Build log saved to: $LOG_FILE"
echo "=== Build finished at $(date '+%Y-%m-%d %H:%M:%S') ==="

if [ "${2:-}" = "--install" ] || [ "${2:-}" = "-i" ]; then
  step "Installing on connected phone"
  "$HERE/05_install_on_phone.sh" "$MODE"
fi

# Bump build number for the next build
python3 "$HERE/set_version.py" --bump >/dev/null 2>&1 || echo $((BUILD_NUM + 1)) > "$HERE/.build_number"

# Kill java running after the script
killall java 2>/dev/null || true
