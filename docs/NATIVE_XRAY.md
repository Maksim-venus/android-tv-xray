# Drop in real Xray native

The APK ships a **VpnService shell** and a **Kotlin stub engine**. Packets from the TUN interface are drained, not forwarded. Generated `xray-config.json` is written to the app files directory on every start.

## Recommended AAR: AndroidLibXrayLite

1. Build or download `libv2ray.aar` from [2dust/AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite):

   ```bash
   # Go 1.21+, NDK r26+ (r26.1.10909125 is a known-good pin)
   gomobile bind -v -androidapi 21 -trimpath ./
   ```

2. Copy the AAR:

   ```text
   core-xray/libs/libv2ray.aar
   ```

3. In `core-xray/build.gradle.kts` add:

   ```kotlin
   dependencies {
       implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
   }
   ```

4. Replace `NativeXrayEngine` / `StubXrayEngine.start` with the gomobile API, typically:

   ```text
   Libv2ray.initCoreEnv(...)
   Libv2ray.runXray(configPath)   // or CoreController.StartLoop
   ```

5. Hand the TUN `ParcelFileDescriptor` to tun2socks / gVisor as required by that AAR. The drain thread in `StubXrayEngine` is the placeholder.

6. Place `geoip.dat` and `geosite.dat` where the core can read them (AAR assets or `filesDir`). Split routing in `XrayConfigGenerator` already references `geosite:cn` and `geoip:cn`.

## Optional C stub

```bash
./gradlew :core-xray:assembleDebug -Ppasswall.enableNativeStub=true
```

Requires NDK + CMake 3.22.1. Symbols:

- `Java_com_passwall_corexray_NativeXrayBridge_nativeVersion`
- `Java_com_passwall_corexray_NativeXrayBridge_nativeStart`
- `Java_com_passwall_corexray_NativeXrayBridge_nativeStop`

## ABI / NDK

| Flavor | minSdk | ABI | Notes |
| --- | --- | --- | --- |
| `legacy` | 28 | `armeabi-v7a`, `arm64-v8a` | Old TV boxes, 32-bit + 64-bit, extract `.so` (`useLegacyPackaging`) |
| `modern` | 31 | `arm64-v8a` | Newer TVs only |

Do **not** require 16 KB page-size-only natives for `legacy`. Prefer NDK r26 on kernel 4.x boxes; newer NDKs are fine for `modern`.
