#!/usr/bin/env bash
# Fetch official Loyalsoldier geosite.dat (must contain cn).
# geoip.dat stays the small geoip-only-cn-private snapshot unless missing.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIR="$ROOT/core-xray/src/main/assets/xray"
mkdir -p "$DIR"

GEOSITE="$DIR/geosite.dat"
GEOIP="$DIR/geoip.dat"
MIN_GEOSITE="${MIN_GEOSITE_BYTES:-100000}"

download() {
  local url="$1" dest="$2"
  echo "Downloading $url"
  curl -fL --retry 4 --retry-delay 2 --max-time 180 -o "$dest.tmp" "$url"
  mv "$dest.tmp" "$dest"
}

need_geosite=1
if [[ -f "$GEOSITE" && $(stat -c%s "$GEOSITE") -ge $MIN_GEOSITE ]]; then
  if python3 "$ROOT/scripts/validate-geodata.py" "$GEOSITE" cn; then
    echo "geosite.dat already valid ($(stat -c%s "$GEOSITE") bytes)"
    need_geosite=0
  fi
fi

if [[ "$need_geosite" -eq 1 ]]; then
  downloaded=0
  for url in \
    "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geosite.dat" \
    "https://fastly.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geosite.dat" \
    "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"
  do
    if download "$url" "$GEOSITE"; then
      downloaded=1
      break
    fi
  done
  if [[ "$downloaded" -ne 1 ]]; then
    AAR="$ROOT/core-xray/libs/libv2ray.aar"
    if [[ -f "$AAR" ]]; then
      echo "Extracting geosite.dat from libv2ray.aar"
      unzip -p "$AAR" assets/geosite.dat > "$GEOSITE.tmp"
      mv "$GEOSITE.tmp" "$GEOSITE"
    fi
  fi
  python3 "$ROOT/scripts/validate-geodata.py" "$GEOSITE" cn
  echo "Saved $GEOSITE ($(stat -c%s "$GEOSITE") bytes)"
fi

if [[ ! -f "$GEOIP" || $(stat -c%s "$GEOIP") -lt 10000 ]]; then
  download "https://cdn.jsdelivr.net/gh/Loyalsoldier/geoip@release/geoip-only-cn-private.dat" "$GEOIP" \
    || download "https://github.com/Loyalsoldier/geoip/releases/latest/download/geoip-only-cn-private.dat" "$GEOIP"
fi
python3 "$ROOT/scripts/validate-geodata.py" --geoip "$GEOIP" cn
echo "geoip.dat ok ($(stat -c%s "$GEOIP") bytes)"
