package com.passwall.corexray

import com.passwall.data.model.AppSettings
import com.passwall.data.model.Protocol
import com.passwall.data.model.ProxyNode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigGeneratorTest {

    @Test
    fun generatesChinaSplitRoutingWithoutAllowInsecure() {
        val json = XrayConfigGenerator.generate(sampleVless(), AppSettings(allowInsecureSsl = false)).json
        assertTrue(json.contains("geosite:cn"))
        assertTrue(json.contains("geoip:cn"))
        assertTrue(json.contains("geoip:private"))
        assertTrue(json.contains("vless"))
        assertTrue(json.contains("223.5.5.5"))
        assertTrue(json.contains("outboundTag"))
        assertTrue(json.contains("\"protocol\": \"tun\""))
        assertTrue(json.contains("tun-in"))
        assertTrue(json.contains("\"port\": 0"))
        assertNoAllowInsecure(json)
        assertTrue(json.contains("\"serverName\": \"tokyo-1.example.com\""))
        assertFalse(json.contains("verifyPeerCertByName"))
        assertFalse(json.contains("pinnedPeerCertSha256"))
    }

    @Test
    fun insecureToggleNeverEmitsAllowInsecureAndUsesVcn() {
        val generated = XrayConfigGenerator.generate(
            sampleVless(),
            AppSettings(allowInsecureSsl = true),
        )
        assertNoAllowInsecure(generated.json)
        assertTrue(generated.json.contains("\"verifyPeerCertByName\": \"tokyo-1.example.com\""))
        assertTrue(generated.json.contains("\"tlsSettings\""))
        assertTrue(generated.notes.any { it.contains("allowInsecure") || it.contains("证书名") })
    }

    @Test
    fun nodePinEmitsPcsWhenToggleOn() {
        val pin = "e8e2d387fdbffeb38e9c9065cf30a97ee23c0e3d32ee6f78ffae40966befccc9"
        val node = sampleVless().copy(
            allowInsecure = true,
            pinnedPeerCertSha256 = pin,
        )
        val generated = XrayConfigGenerator.generate(node, AppSettings(allowInsecureSsl = true))
        assertNoAllowInsecure(generated.json)
        assertTrue(generated.json.contains("\"pinnedPeerCertSha256\": \"$pin\""))
        assertTrue(generated.json.contains("\"verifyPeerCertByName\""))
    }

    @Test
    fun vmessWsTlsHasValidStreamSettings() {
        val node = ProxyNode(
            name = "香港-2",
            protocol = Protocol.VMESS,
            host = "hk-2.example.com",
            port = 443,
            uuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            security = "tls",
            network = "ws",
            path = "/v",
            hostHeader = "hk-2.example.com",
            sni = "cdn.example.com",
            fingerprint = "chrome",
        )
        val json = XrayConfigGenerator.generate(node, AppSettings()).json
        assertNoAllowInsecure(json)
        assertTrue(json.contains("\"protocol\": \"vmess\""))
        assertTrue(json.contains("\"network\": \"ws\""))
        assertTrue(json.contains("\"security\": \"tls\""))
        assertTrue(json.contains("\"wsSettings\""))
        assertTrue(json.contains("\"serverName\": \"cdn.example.com\""))
        assertTrue(json.contains("\"fingerprint\": \"chrome\""))
        assertTrue(json.contains("\"path\": \"/v\""))
    }

    @Test
    fun realityOutboundHasNoTlsAllowInsecure() {
        val node = sampleVless().copy(
            security = "reality",
            publicKey = "pub",
            shortId = "abcd",
            spiderX = "/",
            fingerprint = "chrome",
        )
        val json = XrayConfigGenerator.generate(node, AppSettings(allowInsecureSsl = true)).json
        assertNoAllowInsecure(json)
        assertTrue(json.contains("\"realitySettings\""))
        assertTrue(json.contains("\"publicKey\": \"pub\""))
        assertFalse(json.contains("\"tlsSettings\""))
    }

    @Test
    fun injectsTunFdIntoConfigEnv() {
        val raw = """{"inbounds":[{"protocol":"tun"}]}"""
        val stamped = XrayConfigGenerator.injectTunFd(raw, 77)
        assertTrue(stamped.contains("\"xray.tun.fd\""))
        assertTrue(stamped.contains("77"))
    }

    private fun sampleVless() = ProxyNode(
        name = "东京-1",
        protocol = Protocol.VLESS,
        host = "tokyo-1.example.com",
        port = 443,
        uuid = "11111111-1111-1111-1111-111111111111",
        security = "tls",
        sni = "tokyo-1.example.com",
    )

    private fun assertNoAllowInsecure(json: String) {
        assertFalse(json.contains("allowInsecure"))
        assertFalse(json.contains("allow_insecure"))
    }
}
