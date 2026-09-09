#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${1:-18787}"
DIR="$ROOT/admin-web/src/main/assets/admin"
echo "Passwall admin preview: http://127.0.0.1:${PORT}"
cd "$DIR"
exec python3 -m http.server "$PORT" --bind 127.0.0.1
