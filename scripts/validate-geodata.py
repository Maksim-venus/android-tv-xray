#!/usr/bin/env python3
"""Validate v2ray/Xray geosite.dat or geoip.dat contains a country code (default cn)."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path


def varint(buf: bytes, i: int) -> tuple[int, int]:
    n = 0
    shift = 0
    while i < len(buf):
        b = buf[i]
        i += 1
        n |= (b & 0x7F) << shift
        if b < 0x80:
            return n, i
        shift += 7
        if shift > 70:
            raise ValueError("bad varint")
    raise EOFError("truncated varint")


def codes(data: bytes) -> set[str]:
    found: set[str] = set()
    i = 0
    while i < len(data):
        key, i = varint(data, i)
        field, wire = key >> 3, key & 7
        if wire != 2:
            raise ValueError(f"unexpected wire {wire} field {field}")
        ln, i = varint(data, i)
        if i + ln > len(data):
            raise EOFError(f"truncated entry field={field} need={ln} have={len(data) - i}")
        msg = data[i : i + ln]
        i += ln
        j = 0
        k, j = varint(msg, j)
        if (k >> 3) != 1 or (k & 7) != 2:
            continue
        ln2, j = varint(msg, j)
        found.add(msg[j : j + ln2].decode("utf-8", "replace"))
    return found


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("file")
    p.add_argument("code", nargs="?", default="cn")
    p.add_argument("--geoip", dest="file_geoip", help="validate geoip.dat instead")
    args = p.parse_args()
    path = Path(args.file_geoip or args.file)
    if not path.is_file():
        print(f"missing {path}", file=sys.stderr)
        return 2
    data = path.read_bytes()
    if len(data) < 64:
        print(f"{path} too small ({len(data)} bytes)", file=sys.stderr)
        return 2
    try:
        found = {c.lower() for c in codes(data)}
    except Exception as e:
        print(f"{path} protobuf error: {e}", file=sys.stderr)
        return 2
    want = args.code.lower()
    if want not in found:
        print(f"{path} missing code {want}; have {sorted(found)[:20]}…", file=sys.stderr)
        return 1
    print(f"{path.name}: {len(data)} bytes, {len(found)} lists, has {want}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
