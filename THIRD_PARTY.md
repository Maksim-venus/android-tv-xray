# Third-party notices

This app embeds the following components. You must follow their licenses when redistributing.

## AndroidLibXrayLite (`libv2ray.aar`)

- Project: https://github.com/2dust/AndroidLibXrayLite
- Release used: **v26.1.13**
- License: **LGPL-3.0**
- What we use: gomobile bindings (`libv2ray.CoreController`, `Libv2ray.initCoreEnv`) and `libgojni.so` (armeabi-v7a, arm64-v8a).
- Download: https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.1.13/libv2ray.aar

LGPL-3.0 allows linking this library. The AAR is fetched at build time (`scripts/fetch-libv2ray.sh`) and is not modified.

## Xray-core

- Project: https://github.com/XTLS/Xray-core
- Embedded version: **v1.260113.0** (calendar 26.1.13)
- License: **MPL-2.0**
- Shipped inside AndroidLibXrayLite’s native library.
- Why not 1.8.3: published AndroidLibXrayLite 1.8.11 / 1.8.24 AARs expose `V2RayPoint` only and do not include the tun inbound required by this app’s VpnService path. v26.1.13 is the closest published AAR that still honors `allowInsecure` (no 2026-06-01 removal) and keeps `startLoop(config, tunFd)`.

## Loyalsoldier v2ray-rules-dat / geoip

- https://github.com/Loyalsoldier/v2ray-rules-dat
- https://github.com/Loyalsoldier/geoip
- Used for `geoip.dat` / `geosite.dat` / `direct-list.txt` (GPL-3.0 for the dat project).

## 17mon china_ip_list

- https://github.com/17mon/china_ip_list
- Bundled as `cn-cidr.txt`.

## AndroidX, Kotlin, Ktor, Room, ZXing

Standard Apache-2.0 / MIT dependencies listed in each module’s `build.gradle.kts`.
