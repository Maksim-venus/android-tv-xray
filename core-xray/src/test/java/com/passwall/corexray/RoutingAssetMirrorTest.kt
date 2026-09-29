package com.passwall.corexray

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class RoutingAssetMirrorTest {
    @Test
    fun mainlandMirrorsComeBeforeGithub() {
        val geoip = RoutingAssetCatalog.remoteFiles.first { it.fileName == RoutingAssetCatalog.GEOIP }
        assertTrue(geoip.urls.first().startsWith("https://cdn.jsdmirror.com/"))
        assertTrue(geoip.urls.first().contains("geoip-only-cn-private.dat"))
        assertTrue(geoip.urls[1].contains("jsd.onmicrosoft.cn"))
        assertTrue(geoip.urls.last().startsWith("https://github.com/"))
        val geosite = RoutingAssetCatalog.remoteFiles.first { it.fileName == RoutingAssetCatalog.GEOSITE }
        assertTrue(geosite.urls.first().contains("geosite.dat"))
        assertEquals(geoip.urls.size, geosite.urls.size)
    }

    @Test
    fun downloadErrorsAreChinese() {
        assertTrue(
            DownloadErrors.describe(
                "https://cdn.jsdelivr.net/gh/Loyalsoldier/geoip@release/geoip-only-cn-private.dat",
                UnknownHostException("cdn.jsdelivr.net"),
            ).contains("DNS 解析失败"),
        )
        assertTrue(
            DownloadErrors.describe("https://github.com/x", SocketTimeoutException("timeout"))
                .startsWith("github.com 连接超时"),
        )
        assertEquals(
            "gcore.jsdelivr.net 无法连接",
            DownloadErrors.describe("https://gcore.jsdelivr.net/x", ConnectException("refused")),
        )
        assertEquals(
            "cdn.jsdmirror.com HTTP 403",
            DownloadErrors.describe("https://cdn.jsdmirror.com/x", IllegalStateException("HTTP 403")),
        )
    }
}
