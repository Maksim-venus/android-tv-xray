package com.passwall.corexray

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyReachabilityTest {

    @Test
    fun successStatusesAre204And200Only() {
        assertTrue(ProxyReachability.isSuccessStatus(204))
        assertTrue(ProxyReachability.isSuccessStatus(200))
        assertFalse(ProxyReachability.isSuccessStatus(301))
        assertFalse(ProxyReachability.isSuccessStatus(403))
        assertFalse(ProxyReachability.isSuccessStatus(0))
    }

    @Test
    fun successMessageMentionsProxyAndHttp() {
        val text = ProxyReachability.formatSuccess("https://www.gstatic.com/generate_204", 204, 62)
        assertTrue(text.contains("外网可达"))
        assertTrue(text.contains("204"))
        assertTrue(text.contains("62"))
        assertTrue(text.contains("经代理"))
        assertFalse(text.contains("代理正常"))
    }

    @Test
    fun defaultUrlsAreGenerate204ThroughWellKnownHosts() {
        assertTrue(ProxyReachability.DEFAULT_URLS.any { it.contains("gstatic.com/generate_204") })
        assertTrue(ProxyReachability.DEFAULT_URLS.any { it.contains("google.com/generate_204") })
        assertTrue(ProxyReachability.DEFAULT_URLS.all { it.startsWith("https://") })
    }

    @Test
    fun emptyUrlListFailsClearly() {
        val result = ProxyReachability.probe(urls = emptyList())
        assertFalse(result.ok)
        assertTrue(result.message.contains("外网不可达"))
    }

    @Test
    fun tlsPolicyMapsToggleToVcnNotAllowInsecure() {
        val policy = TlsPolicy.from(
            com.passwall.data.model.ProxyNode(
                name = "n",
                protocol = com.passwall.data.model.Protocol.VLESS,
                host = "1.2.3.4",
                port = 443,
                sni = "www.example.com",
                allowInsecure = true,
            ),
            com.passwall.data.model.AppSettings(allowInsecureSsl = true),
        )
        assertEquals("www.example.com", policy.serverName)
        assertEquals("www.example.com", policy.verifyPeerCertByName)
        assertTrue(policy.note!!.contains("allowInsecure") || policy.note!!.contains("证书名"))
        assertEquals(null, policy.pinnedPeerCertSha256)
    }

    @Test
    fun normalizePinAcceptsColonFingerprint() {
        val raw = "E8:E2:D3:87:FD:BF:FE:B3:8E:9C:90:65:CF:30:A9:7E:E2:3C:0E:3D:32:EE:6F:78:FF:AE:40:96:6B:EF:CC:C9"
        val pin = TlsPolicy.normalizePin(raw)
        assertEquals("E8E2D387FDBFFEB38E9C9065CF30A97EE23C0E3D32EE6F78FFAE40966BEFCCC9", pin)
    }
}
