#!/usr/bin/env bash
# Downloads AndroidLibXrayLite (libv2ray.aar) — not committed (≈59MB).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/core-xray/libs/libv2ray.aar"
VERSION="${LIBV2RAY_VERSION:-v26.9.9}"
URL="https://github.com/2dust/AndroidLibXrayLite/releases/download/${VERSION}/libv2ray.aar"
mkdir -p "$(dirname "$DEST")"
if [[ -f "$DEST" && $(stat -c%s "$DEST") -gt 1000000 ]]; then
  echo "libv2ray.aar already present ($(stat -c%s "$DEST") bytes)"
  exit 0
fi
echo "Downloading $URL"
curl -fL --retry 4 --retry-delay 2 --max-time 180 -o "$DEST.tmp" "$URL"
mv "$DEST.tmp" "$DEST"
echo "Saved $DEST ($(stat -c%s "$DEST") bytes)"
