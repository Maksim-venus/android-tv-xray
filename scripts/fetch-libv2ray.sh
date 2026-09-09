#!/usr/bin/env bash
# Downloads AndroidLibXrayLite (libv2ray.aar) — not committed.
#
# Exact Xray-core 1.8.3 AARs (AndroidLibXrayLite 1.8.11/1.8.24) use the old
# V2RayPoint API and have no tun inbound, so they cannot drive this app's
# VpnService startLoop(json, tunFd) path.
#
# Pin: last published AAR that still accepts tlsSettings.allowInsecure
# (no 2026-06-01 date bomb) AND has CoreController.startLoop + tun inbound.
# Xray-core inside: v1.260113.0 (calendar 26.1.13).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/core-xray/libs/libv2ray.aar"
STAMP="$ROOT/core-xray/libs/libv2ray.version"
VERSION="${LIBV2RAY_VERSION:-v26.1.13}"
URL="https://github.com/2dust/AndroidLibXrayLite/releases/download/${VERSION}/libv2ray.aar"
mkdir -p "$(dirname "$DEST")"
if [[ -f "$DEST" && -f "$STAMP" && "$(cat "$STAMP")" == "$VERSION" && $(stat -c%s "$DEST") -gt 1000000 ]]; then
    echo "libv2ray.aar $VERSION already present ($(stat -c%s "$DEST") bytes)"
    exit 0
fi
echo "Downloading $URL"
curl -fL --retry 4 --retry-delay 2 --max-time 180 -o "$DEST.tmp" "$URL"
mv "$DEST.tmp" "$DEST"
printf '%s\n' "$VERSION" > "$STAMP"
echo "Saved $DEST ($(stat -c%s "$DEST") bytes) pin=$VERSION"
