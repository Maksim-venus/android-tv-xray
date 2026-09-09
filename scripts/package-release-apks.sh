#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/dist"
mkdir -p "$DEST"
legacy="$ROOT/app/build/outputs/apk/legacy/release/app-legacy-release.apk"
modern="$ROOT/app/build/outputs/apk/modern/release/app-modern-release.apk"
cp -f "$legacy" "$DEST/Passwall-TV-legacy-0.1.0.apk"
cp -f "$modern" "$DEST/Passwall-TV-modern-0.1.0.apk"
if [[ -d /opt/cursor/artifacts ]]; then
  cp -f "$DEST/Passwall-TV-legacy-0.1.0.apk" /opt/cursor/artifacts/
  cp -f "$DEST/Passwall-TV-modern-0.1.0.apk" /opt/cursor/artifacts/
fi
ls -lh "$DEST"
echo "legacy -> $DEST/Passwall-TV-legacy-0.1.0.apk"
echo "modern -> $DEST/Passwall-TV-modern-0.1.0.apk"
