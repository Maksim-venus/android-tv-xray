# Xray native core (shipped)

Both APK flavors embed **AndroidLibXrayLite v26.9.9** (`libv2ray.aar`).

## Runtime path

1. `VpnService` creates a TUN (`10.0.85.2/32`, default route, DNS 8.8.8.8 + 223.5.5.5).
2. The app package is `addDisallowedApplication` so Xray’s own sockets do not loop into the TUN.
3. `Libv2ray.initCoreEnv(filesDir/xray, "")` so `geoip.dat` / `geosite.dat` resolve.
4. `CoreController.startLoop(json, tun.fd)` sets process env `xray.tun.fd`. The same fd is also written into the JSON root `env` object. The JSON has a `tun` inbound (gVisor) plus a local socks inbound on `127.0.0.1:10808`.
5. Routing: `geosite:cn` / `geoip:cn` / `geoip:private` → freedom; else → selected VLESS/VMess outbound.
6. 「测试」calls `CoreController.measureDelay` through the running core.

## Fetch the AAR (developers)

```bash
./scripts/fetch-libv2ray.sh
# or just assemble — preBuild downloads it
./gradlew assembleLegacyRelease assembleModernRelease
```

Pinned URL:

`https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.9.9/libv2ray.aar`

Licenses: [THIRD_PARTY.md](../THIRD_PARTY.md).

## ABI

| Flavor | ABIs taken from the AAR |
| --- | --- |
| legacy | `armeabi-v7a`, `arm64-v8a` (`libgojni.so`) |
| modern | `arm64-v8a` |

x86 / x86_64 slices in the AAR are stripped by `ndk.abiFilters`.
