#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/dist"
VERSION="${1:-0.1.5}"
mkdir -p "$DEST"
legacy="$ROOT/app/build/outputs/apk/legacy/release/app-legacy-release.apk"
modern="$ROOT/app/build/outputs/apk/modern/release/app-modern-release.apk"
cp -f "$legacy" "$DEST/Passwall-TV-legacy-${VERSION}.apk"
cp -f "$modern" "$DEST/Passwall-TV-modern-${VERSION}.apk"
if [[ -d /opt/cursor/artifacts ]]; then
  cp -f "$DEST/Passwall-TV-legacy-${VERSION}.apk" /opt/cursor/artifacts/
  cp -f "$DEST/Passwall-TV-modern-${VERSION}.apk" /opt/cursor/artifacts/
  cp -f "$DEST/INSTALL.txt" /opt/cursor/artifacts/ 2>/dev/null || true
fi
ls -lh "$DEST"
echo "legacy -> $DEST/Passwall-TV-legacy-${VERSION}.apk"
echo "modern -> $DEST/Passwall-TV-modern-${VERSION}.apk"
