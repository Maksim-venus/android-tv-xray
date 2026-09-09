package com.passwall.corexray

/**
 * Bundled + remotely updated ChinaDNS / Xray routing assets.
 *
 * Defaults ship in the APK under `assets/xray/`. Runtime copies live in
 * `filesDir/xray/` next to `xray-config.json` so libxray can resolve
 * `geoip:cn` / `geosite:cn` without extra env setup.
 */
object RoutingAssetCatalog {
    const val ASSET_DIR = "xray"
    const val GEOIP = "geoip.dat"
    const val GEOSITE = "geosite.dat"
    const val DIRECT_LIST = "direct-list.txt"
    const val CN_CIDR = "cn-cidr.txt"

    val bundledNames = listOf(GEOIP, GEOSITE, DIRECT_LIST, CN_CIDR)

    /**
     * Files fetched on the 7-day refresh. [CN_CIDR] stays the 17mon snapshot
     * shipped in the APK (ChinaDNS companion); it is not on Loyalsoldier's release.
     */
    val remoteFiles = listOf(
        RemoteAsset(
            fileName = GEOIP,
            minBytes = 10_000,
            urls = listOf(
                "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geoip.dat",
                "https://fastly.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geoip.dat",
                "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat",
            ),
        ),
        RemoteAsset(
            fileName = GEOSITE,
            minBytes = 100_000,
            urls = listOf(
                "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geosite.dat",
                "https://fastly.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geosite.dat",
                "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat",
            ),
        ),
        RemoteAsset(
            fileName = DIRECT_LIST,
            minBytes = 100,
            urls = listOf(
                "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/direct-list.txt",
                "https://fastly.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/direct-list.txt",
                "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/direct-list.txt",
            ),
        ),
    )
}

data class RemoteAsset(
    val fileName: String,
    val minBytes: Long,
    val urls: List<String>,
)
