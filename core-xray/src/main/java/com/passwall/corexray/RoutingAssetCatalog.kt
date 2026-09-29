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
    /**
     * geoip is the small cn+private file (same as the APK default, ~140KB).
     * The full geoip.dat is ~19MB and often times out on a TV box.
     * geosite stays the official Loyalsoldier dat (~11MB) because routing
     * uses `geosite:cn` / `geolocation-!cn` / `category-ads-all`.
     *
     * Order is mainland-reachable mirrors first. GitHub is last: the app is
     * excluded from its own TUN, so these downloads go out the LAN, where
     * GitHub and sometimes jsDelivr are blocked.
     */
    val remoteFiles = listOf(
        RemoteAsset(
            fileName = GEOIP,
            minBytes = 10_000,
            urls = mirrorUrls(
                ghPath = "Loyalsoldier/geoip@release/geoip-only-cn-private.dat",
                githubRelease = "https://github.com/Loyalsoldier/geoip/releases/latest/download/geoip-only-cn-private.dat",
            ),
        ),
        RemoteAsset(
            fileName = GEOSITE,
            minBytes = 100_000,
            urls = mirrorUrls(
                ghPath = "Loyalsoldier/v2ray-rules-dat@release/geosite.dat",
                githubRelease = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat",
            ),
        ),
        RemoteAsset(
            fileName = DIRECT_LIST,
            minBytes = 100,
            urls = mirrorUrls(
                ghPath = "Loyalsoldier/v2ray-rules-dat@release/direct-list.txt",
                githubRelease = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/direct-list.txt",
            ),
        ),
    )

    /** jsDelivr China mirrors, then global CDNs, then a GitHub proxy, then GitHub. */
    fun mirrorUrls(ghPath: String, githubRelease: String): List<String> = listOf(
        "https://cdn.jsdmirror.com/gh/$ghPath",
        "https://jsd.onmicrosoft.cn/gh/$ghPath",
        "https://gcore.jsdelivr.net/gh/$ghPath",
        "https://testingcf.jsdelivr.net/gh/$ghPath",
        "https://cdn.jsdelivr.net/gh/$ghPath",
        "https://fastly.jsdelivr.net/gh/$ghPath",
        "https://ghfast.top/$githubRelease",
        githubRelease,
    )
}

data class RemoteAsset(
    val fileName: String,
    val minBytes: Long,
    val urls: List<String>,
)
