# libv2ray.aar (not committed)

Download with:

```bash
./scripts/fetch-libv2ray.sh
```

Pinned release: **AndroidLibXrayLite v26.1.13**
https://github.com/2dust/AndroidLibXrayLite/releases/tag/v26.1.13

Embedded Xray-core: **v1.260113.0** (still accepts `tlsSettings.allowInsecure`).

There is no usable official AAR for Xray-core **1.8.3**: those 1.8.x AndroidLibXrayLite builds use `V2RayPoint` and have no tun inbound, so `CoreController.startLoop(json, tunFd)` cannot work.

`assemble*` depends on this file. Gradle runs the fetch script automatically. The stamp file `libv2ray.version` records the pin so a leftover newer AAR is replaced.
