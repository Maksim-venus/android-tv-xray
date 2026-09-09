package com.passwall.corexray

/** Pinned AndroidLibXrayLite / Xray-core used by this app build. */
object XrayPins {
    const val LIBV2RAY_RELEASE = "v26.1.13"
    const val XRAY_CORE = "v1.260113.0"
    const val DISPLAY = "Xray 26.1.13（libv2ray $LIBV2RAY_RELEASE）"
    const val WHY_NOT_183 =
        "官方 AndroidLibXrayLite 1.8.3/1.8.24 没有 tun inbound，Java API 也是旧的 V2RayPoint，无法走本应用的 startLoop(json, tunFd)。"
}
