#!/usr/bin/env python3
"""DO NOT generate a compact geosite.dat for release.

Xray-core (AndroidLibXrayLite) rejected the hand-rolled compact file with:
  illegal domain rule: geosite:cn > failed to check code CN from geosite.dat > EOF

Use the official Loyalsoldier geosite.dat instead:

    ./scripts/fetch-geo-assets.sh
    ./scripts/validate-geodata.py core-xray/src/main/assets/xray/geosite.dat cn
"""
from __future__ import annotations

import sys


def main() -> int:
    print(
        "Refusing to write a compact geosite.dat.\n"
        "Run scripts/fetch-geo-assets.sh to download Loyalsoldier geosite.dat.",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
