#!/usr/bin/env bash
# Installs JDK 21 (Temurin), Android command-line tools, platform-tools and the SDK
# packages this project needs into ~/.local/opt (no root needed). Safe to re-run.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/env.sh"
PLATFORM="platforms;android-37.0"
BUILD_TOOLS="build-tools;37.0.0"
CMDLINE_ZIP="commandlinetools-linux-15859902_latest.zip"
mkdir -p "$MTV_OPT"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "Installing JDK 21 ..."
  url=$(curl -fsSL "https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse" \
        | python3 -c 'import json,sys;d=json.load(sys.stdin)[0]["binary"]["package"];print(d["link"],d["checksum"])')
  set -- $url
  curl -fsSL -o "$TMP/jdk.tgz" "$1"
  echo "$2  $TMP/jdk.tgz" | sha256sum -c -
  mkdir -p "$JAVA_HOME" && tar -xzf "$TMP/jdk.tgz" -C "$JAVA_HOME" --strip-components=1
fi

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Installing Android command-line tools ..."
  curl -fsSL -o "$TMP/cmdline.zip" "https://dl.google.com/android/repository/$CMDLINE_ZIP"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  unzip -q "$TMP/cmdline.zip" -d "$TMP/c"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$TMP/c/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi

echo "Installing SDK packages ..."
yes | sdkmanager --licenses >/dev/null || true
sdkmanager "platform-tools" "$PLATFORM" "$BUILD_TOOLS" >/dev/null
echo "Toolchain ready: $(java -version 2>&1 | head -1); $(adb version | head -1)"
