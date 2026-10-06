# Passwall TV — Android 电视 Xray 客户端

打包即可安装使用。内置 **Xray-core v1.260113.0**（AndroidLibXrayLite **v26.1.13**）：点「启动」后设备流量走 TUN → Xray，国内直连、国外走代理。该核心仍支持 `allowInsecure`。打开应用时先显示浅色启动页（图标和 Passwall TV），首页准备好后再进入，不再黑屏干等。

GitHub：`https://github.com/Maksim-venus/android-tv-xray`

---

## 傻瓜式安装（不会写代码也能用）

### 1. 选哪个 APK？

| 电视 / 盒子 | 选这个文件 |
| --- | --- |
| **Android 7.0+（API 24）/ 当贝 7.1 / 老盒子 / Linux 内核 4.x / 32 位机** | `Passwall-TV-Android7.0+-0.1.10.apk`（包名 `com.passwall.tv.legacy`） |
| **Android 12+ 较新的电视（64 位）** | `Passwall-TV-Android12+-0.1.10.apk`（包名 `com.passwall.tv`） |

不确定就先装 **Android7.0+**。两个可以同时装（包名不同）。Android 5.0–6.0 不能用：内置 `libgojni.so` 的最低 API 是 24。

成品路径（本机构建后，可直接拷走安装）：

- `/workspace/dist/Passwall-TV-Android7.0+-0.1.10.apk`
- `/workspace/dist/Passwall-TV-Android12+-0.1.10.apk`
- 云端下载：`/opt/cursor/artifacts/Passwall-TV-Android7.0+-0.1.10.apk`
- 云端下载：`/opt/cursor/artifacts/Passwall-TV-Android12+-0.1.10.apk`
- 构建原始输出：`app/build/outputs/apk/legacy/release/app-legacy-release.apk`
- 构建原始输出：`app/build/outputs/apk/modern/release/app-modern-release.apk`

### 2. 怎么装到电视上

**U 盘 / 文件管理器**

1. 把对应 APK 拷到 U 盘，插到电视。
2. 用电视自带「文件管理」打开 APK，允许「未知来源 / 安装未知应用」。
3. 安装完成后，在应用列表找到 **Passwall**。

**电脑 adb（同一局域网）**

```bash
adb connect 电视IP:5555
adb install -r dist/Passwall-TV-Android7.0+-0.1.10.apk
# 或
adb install -r dist/Passwall-TV-Android12+-0.1.10.apk
```

### 3. 第一次使用

电视上**没有键盘输入节点**。用手机浏览器导入：

1. 遥控器打开 Passwall → 右上角 **设置**。
2. 「允许不安全 SSL」开启后会写出 `tlsSettings.allowInsecure: true`（本版核心仍支持跳过证书校验）。官方没有可用的 Xray **1.8.3** AAR：1.8.x 的 libv2ray 没有 TUN / `startLoop`。
3. 打开 **开启 HTTP 编辑**，电视上会出现 `http://192.168.x.x:8787` 和二维码。
4. 手机扫码（或同一 Wi-Fi 打开该网址）。
5. 点 **导入链接**，粘贴你的 `vless://` 或 `vmess://`（一行一条），点导入。
6. 回到电视，在节点列表里选中刚导入的节点（蓝勾）。
7. 返回首页，点中间 **启动**，同意系统 VPN 授权。
8. 运行后点右下角 **测试**：经本地 SOCKS `127.0.0.1:10808` → Xray 出站，查询 `https://ipinfo.io/json` 的**出口 IP** 与国家代码，首页显示如 `🇯🇵 出口 1.2.3.4 · 62 ms`。ipinfo 失败时才回退 `generate_204`。状态栏 / Toast / 成功失败提示约 **5 秒后自动消失**。
9. 点 **停止** 会立刻停 Xray 并拆掉 TUN，并取消自动重连；失败会 Toast + 底部中文 + 网页日志，不会静默无反应。
10. **自动重连**：启动成功后会记住「VPN 要开着」和当前节点。当贝等盒子把进程杀掉之后，服务以 `START_STICKY` 回来，另外大约每 15 秒有一次看门狗。进程还在、但 Xray 核心退出或本地 `10808` 不再接受连接时，也会用同一节点把核心拉起来。点过「停止」就不会再拉起。杀进程后系统经常要重新点一次 VPN「允许」，这时会出现中文 Toast；应用不能替你点允许。若盒子里有自启动、后台运行或电池白名单，把 Passwall（legacy 包名 `com.passwall.tv.legacy`）加进去。连续启动失败会退避（2 秒起，最多 6 次）并写到网页日志，避免死循环；已经连上之后被杀掉不算进这 6 次。核心自己退出另有 6 次上限，稳定运行约 1 分钟后清零。

全新安装**没有预置节点**。未导入时点「启动」会提示「请先在设置或网页导入节点」。

首页：Clash 浅色界面；焦点是粗蓝色描边。未运行只有「启动」+「设置」；运行中是「停止」+「测试 / 外网探测结果」。失败会 Toast + 底部中文状态。

---

## English — which APK and how to install

| Device | APK |
| --- | --- |
| Android 7.0+ (API 24) / Dangbei 7.1 / old TV box / Linux 4.x / 32-bit | `Passwall-TV-Android7.0+-0.1.10.apk` |
| Newer 64-bit Android TV (API 31+) | `Passwall-TV-Android12+-0.1.10.apk` |

Sideload with a USB file manager or `adb install -r <apk>`. Fresh install has an empty node list. Enable HTTP edit on the TV, import `vless://` / `vmess://` from a phone on the same LAN, select the node, press 启动, accept the VPN dialog.

After a successful start the app remembers that the VPN should stay on. If the box kills the process it tries to restore the last node (`START_STICKY` plus a 15s watchdog). If the process stays up but the Xray core dies, or local SOCKS `127.0.0.1:10808` stops accepting, the same node is started again with backoff (6 tries). **停止** clears that and does not reconnect either path. Dangbei-class ROMs often show the VPN consent dialog again after a kill — accept it when prompted. If the box has a battery or autostart whitelist, add Passwall (`com.passwall.tv.legacy` for the legacy APK). Startup failures back off and stop after 6 tries; the reason is in the web admin log. A kill after the tunnel is already up does not use up those retries.

**测试** is a real HTTPS GET through the Xray SOCKS inbound (not a TUN-direct request from the app process, and not `measureDelay` alone). Primary target is `ipinfo.io` (exit IP + country flag). `generate_204` is fallback only. TV status / Toast auto-clear after 5 seconds. Web logs keep 7 days.

Pinned core **Xray v1.260113.0** still honors `tlsSettings.allowInsecure`. Official 1.8.3 AARs cannot drive this app’s TUN `startLoop` path.

---

## What is inside

- Real **Xray-core v1.260113.0** via [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) `libv2ray.aar` **v26.1.13** (LGPL-3.0). Native `libgojni.so` for `armeabi-v7a` + `arm64-v8a`. Verified: `startLoop(String, int)`, gVisor TUN, `allowInsecure` present, no “allowInsecure has been removed”.
- VpnService TUN fd is passed to `CoreController.startLoop(config, tunFd)` (`xray.tun.fd`). Config uses a **tun** inbound (gVisor) — not a drain stub.
- ChinaDNS-style split: `geosite:cn` / `geoip:cn` / private → direct; else → VLESS/VMess.
- TLS: when the insecure toggle is on, emit `allowInsecure: true`. Do not emit `verifyPeerCertByName` (this core uses `verifyPeerCertInNames`). Optional `pcs` → `pinnedPeerCertSha256`.
- Bundled **official Loyalsoldier** `geosite.dat` (must contain `cn`; compact generate-geosite.py is banned) plus `geoip-only-cn-private`. Invalid runtime files are replaced. If Xray still rejects geosite, start retries with IP-only `geoip:cn` rules. After a successful start, refresh those files when they are older than 7 days (or never updated). The web admin shows the last success time, or the Chinese failure reason, and has 「立即更新」.
- Local Passwall-like web admin when HTTP edit is on. Sidebar **日志** shows VPN/Xray ring-buffer lines (`GET /api/logs`), auto-refresh, error filter, clear; entries older than **7 days** are pruned on write and hourly. **外网探测** is `POST /api/proxy/probe` (ipinfo.io via SOCKS; returns `exitIp` / `country` / `flag`). Latest start failure is shown on the admin status panel.
- Licenses: [THIRD_PARTY.md](THIRD_PARTY.md). Native notes: [docs/NATIVE_XRAY.md](docs/NATIVE_XRAY.md).

### Dual APK (developers)

| Flavor | applicationId | minSdk | ABI |
| --- | --- | --- | --- |
| **legacy** | `com.passwall.tv.legacy` | 24 (Android 7.0) | armeabi-v7a + arm64-v8a |
| **modern** | `com.passwall.tv` | 31 | arm64-v8a |

```bash
./scripts/fetch-libv2ray.sh   # also runs automatically on assemble
./scripts/fetch-geo-assets.sh # official geosite.dat; also on assemble
./gradlew assembleLegacyRelease assembleModernRelease
./scripts/package-release-apks.sh
```

Published APKs are signed in GitHub Actions when a `v*` tag is pushed (`.github/workflows/release.yml`). The release keystore lives in repository secrets, not in git. A local `assemble*Release` without `RELEASE_KEYSTORE_FILE` / `RELEASE_KEYSTORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` falls back to the debug keystore and must not be uploaded.

UI is Compose (BOM 2024.12.01, library minSdk 21) + D-pad on both flavors. Leanback widgets are not used. The legacy floor is API 24 because AndroidLibXrayLite v26.1.13's `libv2ray.aar` manifest and `libgojni.so` ELF note both require API 24; Android 5.0–6.0 cannot load that binary, and older AARs have no TUN `startLoop`. Legacy avoids API 29+ VpnService helpers, keeps MTU 1500, turns IPv6 off, and sets `useLegacyPackaging` so `.so` is extracted on kernel 4.x. Below API 26 the process is started with `startService` (not `startForegroundService`) and notification channels are skipped. Share-link Base64 is decoded without `java.util.Base64` (API 26). `android:colorFocusedHighlight` is applied only on API 26+. A non-adaptive launcher icon is packaged for pre-26 devices. On API 34+ `startForeground` passes the special-use type.

Android 7.1 TV boxes still need a working `/dev/tun` (`VpnService.establish()`). Some vendor kernels omit TUN or break the VPN consent dialog; the app then reports that the TUN could not be created. Country-flag emoji may render as letters on Android 7.1’s emoji font; the exit IP text still shows.

### Modules

| Module | Role |
| --- | --- |
| `app` | TV UI, VpnService, VPN permission |
| `data` | Room, vless/vmess parsers, subscription fetch stub |
| `core-xray` | libv2ray engine, tun JSON, geo assets + 7-day update, SOCKS HTTPS probe |
| `admin-web` | Ktor + REST + TCP ping + `/api/proxy/probe` |

SSR is recognized only (no outbound). Subscription **HTTP fetch** is still a stub; paste links instead.

### Routing asset update

After every successful start, if Room `routingAssetsUpdatedAt` is missing or older than 7 days, download Loyalsoldier `geoip-only-cn-private.dat` (saved as `geoip.dat`), `geosite.dat`, and `direct-list.txt`. Mirrors start with `cdn.jsdmirror.com` / `jsd.onmicrosoft.cn` because the app is excluded from its own VPN and GitHub often fails in mainland China. A failed download keeps the bundled files, stores the Chinese reason, and the web admin shows it instead of staying on 「尚未更新」. 「立即更新」 retries immediately. URLs: `core-xray/src/main/assets/xray/SOURCES.txt`.

### Build

JDK 17+, Android SDK 35. `libv2ray.aar` is **not** in git (~51MB); `scripts/fetch-libv2ray.sh` pulls **v26.1.13**.

```properties
sdk.dir=/path/to/Android/Sdk
```
