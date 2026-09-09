# Xray native core (shipped)

Both APK flavors embed **AndroidLibXrayLite v26.1.13** (`libv2ray.aar`) with **Xray-core v1.260113.0**.

Exact Xray-core **1.8.3** is not used: the official 1.8.x `libv2ray.aar` builds (`1.8.11`, `1.8.24`) only expose `V2RayPoint.RunLoop` and have no tun inbound. This app needs `CoreController.startLoop(json, tunFd)` plus gVisor TUN.

v26.1.13 is the last well-verified published AAR that:

- still accepts `tlsSettings.allowInsecure` (no “feature has been removed” date bomb; that landed in Xray v26.2.6 / commit 2c92339)
- keeps `startLoop(String, int)`
- ships `armeabi-v7a` + `arm64-v8a`
- includes the tun inbound / `xray.tun.fd`

## Runtime path

1. `VpnService` creates a TUN (`10.0.85.2/32`, default route, DNS 8.8.8.8 + 223.5.5.5).
2. The app package is `addDisallowedApplication` so Xray’s own sockets do not loop into the TUN.
3. `Libv2ray.initCoreEnv(filesDir/xray, "")` so `geoip.dat` / `geosite.dat` resolve.
4. `CoreController.startLoop(json, tun.fd)` sets process env `xray.tun.fd`. The same fd is also written into the JSON root `env` object. The JSON has a `tun` inbound (gVisor) plus a local socks inbound on `127.0.0.1:10808`.
5. Routing: `geosite:cn` / `geoip:cn` / `geoip:private` → freedom; else → selected VLESS/VMess outbound.
6. 「测试」sends HTTPS GET to `https://ipinfo.io/json` via the local SOCKS inbound (`127.0.0.1:10808`) so the request goes through Xray routing → the selected outbound. The UI shows the exit IP and a country-flag emoji. `generate_204` is used only if ipinfo fails. `measureDelay` is optional extra “链路” timing only. TV status / Toast clear after 5 seconds.
7. TLS: when 「允许不安全 SSL」 is on, emit `allowInsecure: true`. Do not emit `verifyPeerCertByName` (this core uses `verifyPeerCertInNames`). Optional `pinnedPeerCertSha256` is accepted if the share link has `pcs`.

## Fetch the AAR (developers)

```bash
./scripts/fetch-libv2ray.sh
# or just assemble — preBuild downloads it
./gradlew assembleLegacyRelease assembleModernRelease
```

Pinned URL:

`https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.1.13/libv2ray.aar`

Licenses: [THIRD_PARTY.md](../THIRD_PARTY.md).

## ABI

| Flavor | ABIs taken from the AAR |
| --- | --- |
| legacy | `armeabi-v7a`, `arm64-v8a` (`libgojni.so`) |
| modern | `arm64-v8a` |

x86 / x86_64 slices in the AAR are stripped by `ndk.abiFilters`.
