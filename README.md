# Passwall TV — Android 电视 Xray 客户端

打包即可安装使用。内置 **Xray-core**（AndroidLibXrayLite v26.9.9）：点「启动」后设备流量走 TUN → Xray，国内直连、国外走代理。

GitHub：`https://github.com/Maksim-venus/android-tv-xray`

---

## 傻瓜式安装（不会写代码也能用）

### 1. 选哪个 APK？

| 电视 / 盒子 | 选这个文件 |
| --- | --- |
| **Android 9 / 老盒子 / Linux 内核 4.x / 32 位机** | `Passwall-TV-legacy-0.1.4.apk`（包名 `com.passwall.tv.legacy`） |
| **Android 12+ 较新的电视（64 位）** | `Passwall-TV-modern-0.1.4.apk`（包名 `com.passwall.tv`） |

不确定就先装 **legacy**。两个可以同时装（包名不同）。

成品路径（本机构建后，可直接拷走安装）：

- `/workspace/dist/Passwall-TV-legacy-0.1.4.apk`
- `/workspace/dist/Passwall-TV-modern-0.1.4.apk`
- 云端下载：`/opt/cursor/artifacts/Passwall-TV-legacy-0.1.4.apk`
- 云端下载：`/opt/cursor/artifacts/Passwall-TV-modern-0.1.4.apk`
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
adb install -r dist/Passwall-TV-legacy-0.1.4.apk
# 或
adb install -r dist/Passwall-TV-modern-0.1.4.apk
```

### 3. 第一次使用

电视上**没有键盘输入节点**。用手机浏览器导入：

1. 遥控器打开 Passwall → 右上角 **设置**。
2. 「允许不安全 SSL」**不能再跳过证书校验**（当前 Xray 已删除 `allowInsecure`）。开启后按证书名验证（`verifyPeerCertByName` / `vcn`）。自签或私有证书请在 `vless://` 链接里带 `pcs=`（证书 SHA-256）。
3. 打开 **开启 HTTP 编辑**，电视上会出现 `http://192.168.x.x:8787` 和二维码。
4. 手机扫码（或同一 Wi-Fi 打开该网址）。
5. 点 **导入链接**，粘贴你的 `vless://` 或 `vmess://`（一行一条），点导入。
6. 回到电视，在节点列表里选中刚导入的节点（蓝勾）。
7. 返回首页，点中间 **启动**，同意系统 VPN 授权。
8. 运行后点右下角 **测试**：经本地 SOCKS `127.0.0.1:10808` → Xray 出站，向 `https://www.gstatic.com/generate_204`（失败再试 Google / Cloudflare）发真实 HTTPS 请求。成功条件是 HTTP **204 或 200**。首页不会把「启动成功」或 `measureDelay` 显示成「代理正常」。
9. 点 **停止** 会立刻停 Xray 并拆掉 TUN；失败会 Toast + 底部中文 + 网页日志，不会静默无反应。

全新安装**没有预置节点**。未导入时点「启动」会提示「请先在设置或网页导入节点」。

首页：Clash 浅色界面；焦点是粗蓝色描边。未运行只有「启动」+「设置」；运行中是「停止」+「测试 / 外网探测结果」。失败会 Toast + 底部中文状态。

---

## English — which APK and how to install

| Device | APK |
| --- | --- |
| Android 9 / old TV box / Linux 4.x / 32-bit | `Passwall-TV-legacy-0.1.4.apk` |
| Newer 64-bit Android TV (API 31+) | `Passwall-TV-modern-0.1.4.apk` |

Sideload with a USB file manager or `adb install -r <apk>`. Fresh install has an empty node list. Enable HTTP edit on the TV, import `vless://` / `vmess://` from a phone on the same LAN, select the node, press 启动, accept the VPN dialog.

**测试** is a real HTTPS GET through the Xray SOCKS inbound (not a TUN-direct request from the app process, and not `measureDelay` alone). Success = HTTP 204/200 from a `generate_204` URL.

This core rejects `tlsSettings.allowInsecure`. The SSL toggle maps to `verifyPeerCertByName`; share links may include `pcs` / `vcn`.

---

## What is inside

- Real **Xray-core** via [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) `libv2ray.aar` **v26.9.9** (LGPL-3.0). Native `libgojni.so` for `armeabi-v7a` + `arm64-v8a`.
- VpnService TUN fd is passed to `CoreController.startLoop(config, tunFd)` (`xray.tun.fd`). Config uses a **tun** inbound (gVisor) — not a drain stub.
- ChinaDNS-style split: `geosite:cn` / `geoip:cn` / private → direct; else → VLESS/VMess.
- TLS: generated JSON **never** contains `allowInsecure`. Optional `pinnedPeerCertSha256` (`pcs`) and `verifyPeerCertByName` (`vcn`).
- Bundled **official Loyalsoldier** `geosite.dat` (must contain `cn`; compact generate-geosite.py is banned) plus `geoip-only-cn-private`. Invalid runtime files are replaced. If Xray still rejects geosite, start retries with IP-only `geoip:cn` rules. 7-day refresh after a successful start.
- Local Passwall-like web admin when HTTP edit is on. Sidebar **日志** shows VPN/Xray ring-buffer lines (`GET /api/logs`), auto-refresh, error filter, clear. **外网探测** is `POST /api/proxy/probe` (HTTPS via SOCKS, not TCP ping). Latest start failure is shown on the admin status panel.
- Licenses: [THIRD_PARTY.md](THIRD_PARTY.md). Native notes: [docs/NATIVE_XRAY.md](docs/NATIVE_XRAY.md).

### Dual APK (developers)

| Flavor | applicationId | minSdk | ABI |
| --- | --- | --- | --- |
| **legacy** | `com.passwall.tv.legacy` | 28 | armeabi-v7a + arm64-v8a |
| **modern** | `com.passwall.tv` | 31 | arm64-v8a |

```bash
./scripts/fetch-libv2ray.sh   # also runs automatically on assemble
./scripts/fetch-geo-assets.sh # official geosite.dat; also on assemble
./gradlew assembleLegacyRelease assembleModernRelease
./scripts/package-release-apks.sh
```

Release APKs are signed with the **debug keystore** so you can sideload immediately. Replace the keystore before any store upload.

UI is Compose + D-pad on both flavors (Leanback not used). Legacy avoids API 29+ VpnService helpers, MTU 1500, IPv6 off, `useLegacyPackaging` so `.so` is extracted on kernel 4.x.

### Modules

| Module | Role |
| --- | --- |
| `app` | TV UI, VpnService, VPN permission |
| `data` | Room, vless/vmess parsers, subscription fetch stub |
| `core-xray` | libv2ray engine, tun JSON, geo assets + 7-day update, SOCKS HTTPS probe |
| `admin-web` | Ktor + REST + TCP ping + `/api/proxy/probe` |

SSR is recognized only (no outbound). Subscription **HTTP fetch** is still a stub; paste links instead.

### Routing asset update

After every successful start, if Room `routingAssetsUpdatedAt` is missing or older than 7 days, download Loyalsoldier `geoip.dat` / `geosite.dat` / `direct-list.txt` (jsDelivr → Fastly → GitHub). Keep last-good on failure. URLs: `core-xray/src/main/assets/xray/SOURCES.txt`.

### Build

JDK 17+, Android SDK 35. `libv2ray.aar` is **not** in git (~59MB); `scripts/fetch-libv2ray.sh` pulls v26.9.9.

```properties
sdk.dir=/path/to/Android/Sdk
```
