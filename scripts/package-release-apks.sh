#!/usr/bin/env bash
# Copy flavor APKs to dist using the minimum-Android filename.
# Names follow minSdk: API 24 -> Passwall-TV-Android7.0+-<ver>.apk
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/dist"
VERSION="${1:-0.1.10}"
mkdir -p "$DEST"
if [[ -f "$ROOT/INSTALL.txt" ]]; then
  cp -f "$ROOT/INSTALL.txt" "$DEST/INSTALL.txt"
fi

android_label() {
  case "$1" in
    21) echo "5.0" ;;
    22) echo "5.1" ;;
    23) echo "6.0" ;;
    24) echo "7.0" ;;
    25) echo "7.1" ;;
    26) echo "8.0" ;;
    27) echo "8.1" ;;
    28) echo "9" ;;
    29) echo "10" ;;
    30) echo "11" ;;
    31) echo "12" ;;
    32) echo "12.1" ;;
    33) echo "13" ;;
    34) echo "14" ;;
    35) echo "15" ;;
    *) echo "API$1" ;;
  esac
}

if [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/build-tools/35.0.0/aapt" ]]; then
  AAPT="$ANDROID_HOME/build-tools/35.0.0/aapt"
elif [[ -x "$HOME/android-sdk/build-tools/35.0.0/aapt" ]]; then
  AAPT="$HOME/android-sdk/build-tools/35.0.0/aapt"
else
  AAPT=""
fi

sdk_of() {
  if [[ -n "$AAPT" ]]; then
    "$AAPT" dump badging "$1" | sed -n "s/.*sdkVersion:'\([0-9][0-9]*\)'.*/\1/p" | head -1
  fi
}

legacy="$ROOT/app/build/outputs/apk/legacy/release/app-legacy-release.apk"
modern="$ROOT/app/build/outputs/apk/modern/release/app-modern-release.apk"
legacy_sdk="$(sdk_of "$legacy")"
modern_sdk="$(sdk_of "$modern")"
legacy_sdk="${legacy_sdk:-24}"
modern_sdk="${modern_sdk:-31}"
legacy_name="Passwall-TV-Android$(android_label "$legacy_sdk")+-${VERSION}.apk"
modern_name="Passwall-TV-Android$(android_label "$modern_sdk")+-${VERSION}.apk"
cp -f "$legacy" "$DEST/$legacy_name"
cp -f "$modern" "$DEST/$modern_name"
if [[ -d /opt/cursor/artifacts ]]; then
  cp -f "$DEST/$legacy_name" /opt/cursor/artifacts/
  cp -f "$DEST/$modern_name" /opt/cursor/artifacts/
  cp -f "$DEST/INSTALL.txt" /opt/cursor/artifacts/ 2>/dev/null || true
fi
ls -lh "$DEST"
echo "legacy -> $DEST/$legacy_name"
echo "modern -> $DEST/$modern_name"
