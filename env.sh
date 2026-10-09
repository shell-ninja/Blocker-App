# Shared settings for all scripts (sourced, not run).
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Use Android SDK folder from this same Blocker-App directory
if [ -d "$HERE/Android/Sdk" ]; then
  export ANDROID_HOME="$HERE/Android/Sdk"
elif [ -d "$HERE/Android/cmdline-tools" ] || [ -d "$HERE/Android/platforms" ]; then
  export ANDROID_HOME="$HERE/Android"
elif [ -d "$HERE/android-sdk" ]; then
  export ANDROID_HOME="$HERE/android-sdk"
elif [ -d "$HERE/sdk" ]; then
  export ANDROID_HOME="$HERE/sdk"
elif [ -d "$HERE/Sdk" ]; then
  export ANDROID_HOME="$HERE/Sdk"
elif [ -d "$HERE/Android" ]; then
  export ANDROID_HOME="$HERE/Android"
else
  export ANDROID_HOME="$HERE/Android/Sdk"
fi

export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [ -d "$ANDROID_HOME/ndk/25.1.8937393" ]; then
  export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/25.1.8937393"
elif [ -d "$ANDROID_HOME/ndk" ]; then
  LATEST_NDK="$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort -V | tail -1 || true)"
  if [ -n "$LATEST_NDK" ]; then
    export ANDROID_NDK_HOME="$LATEST_NDK"
  fi
fi
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
