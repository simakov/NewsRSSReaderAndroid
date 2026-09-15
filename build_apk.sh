#!/bin/sh
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21
fi

if [ ! -f local.properties ]; then
    echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
fi

./gradlew :app:assembleDebug

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "Build succeeded but APK not found at $APK_PATH" >&2
    exit 1
fi

cp "$APK_PATH" "$HOME/Downloads/app-debug.apk"

echo "APK copied to $HOME/Downloads/app-debug.apk"
