# Passwall TV — Android TV Xray 客户端 / Android TV Xray client

Minimal Android TV proxy client for mainland China. Dual APK (legacy + modern), Chinese TV UI, local Ktor web admin, VpnService + generated ChinaDNS-style Xray JSON.

GitHub upload is **later** (`https://github.com/Maksim-venus/android-tv-xray`). This Origin repo is the source of truth until that sync.

---

## English

### What you get

- **Home STOPPED:** large 启动 + top-right 设置. No sidebar.
- **Home RUNNING:** large 停止 + 设置 + 测试 + 代理正常.
- **Settings:** read-only node list (VLESS / VMess badges), select node, 允许不安全 SSL, 开启 HTTP 编辑 → LAN URL + QR. No on-TV text editors.
- **Web admin (Passwall-like):** 导入链接 / 订阅 / 测延迟 / TCP Ping. Chinese dark UI.
- **VpnService** starts/stops a TUN interface and writes split-routing config (`cn` → direct, else proxy).
- **Xray native is stubbed.** Drop in `libv2ray.aar` when ready — see [docs/NATIVE_XRAY.md](docs/NATIVE_XRAY.md).

### Modules

| Module | Role |
| --- | --- |
| `app` | TV Compose UI, `VpnService` + foreground notification, API 28+ permissions |
| `data` | Room, `vless://` / `vmess://` parsers, SSR recognition stub, subscription **fetch** stub |
| `core-xray` | start / stop / status, Xray JSON (ChinaDNS split + `allowInsecure`), bundled geoip/geosite, 7-day refresh, JNI TODO |
| `admin-web` | Ktor CIO on LAN, static admin, REST, TCP ping. Runs only when HTTP edit is on |

### Dual APK

| Flavor | applicationId | minSdk | ABI | When to use |
| --- | --- | --- | --- | --- |
| **legacy** | `com.passwall.tv.legacy` | **28** (Android 9) | `armeabi-v7a` + `arm64-v8a` | Old TV boxes, Linux kernel 4.x |
| **modern** | `com.passwall.tv` | **31** | `arm64-v8a` | Newer TVs |

Shared business code lives in `data`, `core-xray`, and `admin-web`. Flavors only change `minSdk`, ABI, IPv6 on the TUN, and `applicationId`.

```bash
./gradlew assembleLegacyRelease
./gradlew assembleModernRelease
# also: assembleLegacyDebug / assembleModernDebug
```

Outputs: `app/build/outputs/apk/<flavor>/release/`.

Release builds sign with the debug keystore so local assemble works. Replace that before any store upload.

### UI toolkit (API 28)

**Compose for Android + D-pad focus**, not Leanback.

`androidx.tv:tv-material` already supports minSdk 21, so both flavors share one Compose UI. Leanback is unused. Modern does not switch to a second toolkit — same screens, higher `minSdk` and 64-bit only.

### Android 9 / kernel 4.x notes (`legacy`)

- minSdk 28, 32-bit ABI kept.
- TUN MTU 1500; IPv6 off on legacy (some 4.x kernels mishandle IPv6 tun).
- No API 29+ `VpnService.Builder` helpers (`setMetered`, HTTP proxy).
- `packaging.jniLibs.useLegacyPackaging = true` so `.so` is extracted (friendlier on old boxes).
- Conservative deps; no NDK required for the default stub build.
- When you add libxray, build `armeabi-v7a` **and** `arm64-v8a`; avoid 16 KB-only page-size binaries for this flavor.

### Drop in Xray native

See [docs/NATIVE_XRAY.md](docs/NATIVE_XRAY.md). Until then:

- Config is real (VLESS/VMess outbound, geosite:cn / geoip:cn / private → `freedom`).
- Engine is `StubXrayEngine`: writes JSON, reports RUNNING, drains TUN. **No packet forwarding.**
- SSR: link is recognized; outbound is a TODO placeholder.

### Routing assets (geoip / geosite / ChinaDNS)

Defaults are **bundled in the APK** under `core-xray/src/main/assets/xray/` and copied to `filesDir/xray/` (next to `xray-config.json`) on first run. Last-good downloaded files are never overwritten by the APK copy.

| File | Bundled default | Purpose |
| --- | --- | --- |
| `geoip.dat` | Loyalsoldier `geoip-only-cn-private.dat` (~134KB) | `geoip:cn`, `geoip:private` |
| `geosite.dat` | Compact list from `scripts/generate-geosite.py` | `geosite:cn`, `geosite:geolocation-!cn`, `geosite:category-ads-all` |
| `direct-list.txt` | Loyalsoldier ChinaDNS-style domain list | cn → direct domains |
| `cn-cidr.txt` | [17mon/china_ip_list](https://github.com/17mon/china_ip_list) | ChinaDNS-style IPv4 CIDRs |

**Update behavior:** after **every successful proxy start**, the app checks `app_settings.routingAssetsUpdatedAt` in Room. If it is missing or older than **7 days**, it downloads the full Loyalsoldier files **once** (jsDelivr → Fastly → GitHub). Failure keeps the bundled/last-good files and does **not** block VPN start. A failed attempt is retried at most every 6 hours. The new timestamp is written only after all remote files succeed.

Remote URLs (see also `core-xray/src/main/assets/xray/SOURCES.txt`):

```
https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geoip.dat
https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geosite.dat
https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/direct-list.txt
https://fastly.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/<same>
https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/<same>
```

Regenerate the compact geosite default:

```bash
python3 scripts/generate-geosite.py
```

### Web admin

On Settings → 开启 HTTP 编辑, Ktor listens on `0.0.0.0:8787`. TV shows `http://<LAN>:8787` and a QR.

```
GET  /api/status
GET  /api/nodes
POST /api/nodes/import          { "text": "vless://...\\nvmess://..." }
POST /api/nodes/{id}/select
POST /api/nodes/{id}/ping
POST /api/nodes/latency
POST /api/nodes/tcp-ping
GET|POST|DELETE /api/subscriptions
POST /api/subscriptions/{id}/refresh   # stub fetch
PUT  /api/settings
POST /api/proxy/start|stop
```

Static preview (no device):

```bash
./scripts/preview-admin.sh 18787
```

### Build environment

- JDK 17+ (21 OK)
- Android SDK Platform 35, Build-Tools 35.0.0
- Gradle 8.11.1 / AGP 8.7.3 / Kotlin 2.0.21
- NDK **optional** (`-Ppasswall.enableNativeStub=true`)

```properties
# local.properties
sdk.dir=/path/to/Android/Sdk
```

```bash
./gradlew :data:test :core-xray:test
./gradlew assembleLegacyRelease assembleModernRelease
```

### Later GitHub sync

Keep this repo pushable. When you create `Maksim-venus/android-tv-xray`, add that remote and push `main`. Do not depend on GitHub for day-to-day work here.

---

## 中文

面向国内市场的 Android 电视 Xray 代理客户端：双 APK、中文遥控器界面、局域网 Web 管理、VpnService + 国内分流配置。

GitHub 仓库（`Maksim-venus/android-tv-xray`）**稍后上传**。当前以本 Origin 仓库为准。

### 功能

1. 首页未运行：中央「启动」+ 右上「设置」，无侧栏。
2. 首页运行中：中央「停止」+「设置」+ 右下「测试 / 代理正常」。
3. 设置：只读节点（VLESS / VMess 徽章）、选择节点、「允许不安全 SSL」、「开启 HTTP 编辑」后显示局域网 URL 与二维码。电视上不提供文本输入。
4. Web 管理（Passwall 风格）：导入链接、订阅、测延迟、TCP Ping。

### 双 APK

| 变体 | 包名 | minSdk | ABI | 用途 |
| --- | --- | --- | --- | --- |
| **legacy** | `com.passwall.tv.legacy` | 28（Android 9） | armeabi-v7a + arm64-v8a | 老盒子 / Linux 4.x 内核 |
| **modern** | `com.passwall.tv` | 31 | arm64-v8a | 新电视 |

```bash
./gradlew assembleLegacyRelease
./gradlew assembleModernRelease
```

### 界面实现

两款 APK **共用 Compose + 遥控器焦点**，不用 Leanback。`androidx.tv` 的 minSdk 为 21，Android 9 可用。

### 老设备注意

- 保留 32 位；TUN MTU 1500；legacy 关闭 IPv6。
- 不使用 API 29+ 的 VpnService API。
- 默认把 `.so` 解压到文件系统，兼容部分 4.x 内核加载器。
- 接入原生库时必须同时打 `armeabi-v7a`。

### 接入真实 Xray

见 [docs/NATIVE_XRAY.md](docs/NATIVE_XRAY.md)。当前为明确 Stub：配置文件会生成，流量不会真正转发。SSR 仅识别链接。订阅 HTTP 拉取为 TODO，正文解析（Base64 / 逐行链接）已实现。

### 模块

- `app`：电视 UI、VpnService、前台通知
- `data`：Room、分享链接解析、订阅拉取 Stub
- `core-xray`：启停与状态、分流 JSON、内置 geoip/geosite、7 天自动更新、JNI 预留

### 分流规则资源

APK 内置 `geoip.dat`（Loyalsoldier 仅 CN+内网）、精简 `geosite.dat`、`direct-list.txt`、`cn-cidr.txt`，首次运行复制到 `filesDir/xray/`。每次**代理启动成功后**检查 Room 字段 `routingAssetsUpdatedAt`：超过 **7 天**（或从未成功更新）则后台下载 Loyalsoldier 全量 geoip/geosite/direct-list（jsDelivr → Fastly → GitHub）。失败则继续用内置/上次成功文件，**不阻止 VPN 启动**。时间戳仅在全部下载成功后写入。地址见 `core-xray/src/main/assets/xray/SOURCES.txt`。
- `admin-web`：Ktor + 静态管理页 + REST + TCP Ping（仅 HTTP 编辑开启时）
