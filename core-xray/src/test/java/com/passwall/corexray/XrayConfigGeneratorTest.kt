package com.passwall.corexray

import com.passwall.data.model.AppSettings
import com.passwall.data.model.Protocol
import com.passwall.data.model.ProxyNode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigGeneratorTest {

    @Test
    fun generatesChinaSplitRoutingAndOmitsAllowInsecureWhenOff() {
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
        assertTrue(json.contains("\"serverName\": \"tokyo-1.example.com\""))
        assertFalse(json.contains("allowInsecure"))
        assertNoNewerOnlyTlsFields(json)
    }

    @Test
    fun insecureToggleEmitsAllowInsecureTrue() {
        val generated = XrayConfigGenerator.generate(
            sampleVless(),
            AppSettings(allowInsecureSsl = true),
        )
        assertTrue(generated.json.contains("\"allowInsecure\": true"))
        assertTrue(generated.json.contains("\"tlsSettings\""))
        assertNoNewerOnlyTlsFields(generated.json)
        assertTrue(generated.notes.any { it.contains("allowInsecure") })
    }

    @Test
    fun nodeFlagAlsoEnablesAllowInsecure() {
        val node = sampleVless().copy(allowInsecure = true)
        val json = XrayConfigGenerator.generate(node, AppSettings(allowInsecureSsl = false)).json
        assertTrue(json.contains("\"allowInsecure\": true"))
        assertNoNewerOnlyTlsFields(json)
    }

    @Test
    fun pinIsOptionalAndDoesNotReplaceAllowInsecure() {
        val pin = "e8e2d387fdbffeb38e9c9065cf30a97ee23c0e3d32ee6f78ffae40966befccc9"
        val node = sampleVless().copy(allowInsecure = true, pinnedPeerCertSha256 = pin)
        val json = XrayConfigGenerator.generate(node, AppSettings(allowInsecureSsl = true)).json
        assertTrue(json.contains("\"allowInsecure\": true"))
        assertTrue(json.contains("\"pinnedPeerCertSha256\": \"$pin\""))
        assertNoNewerOnlyTlsFields(json)
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
        val json = XrayConfigGenerator.generate(node, AppSettings(allowInsecureSsl = true)).json
        assertTrue(json.contains("\"allowInsecure\": true"))
        assertTrue(json.contains("\"protocol\": \"vmess\""))
        assertTrue(json.contains("\"network\": \"ws\""))
        assertTrue(json.contains("\"security\": \"tls\""))
        assertTrue(json.contains("\"wsSettings\""))
        assertTrue(json.contains("\"serverName\": \"cdn.example.com\""))
        assertTrue(json.contains("\"fingerprint\": \"chrome\""))
        assertTrue(json.contains("\"path\": \"/v\""))
        assertNoNewerOnlyTlsFields(json)
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
        assertTrue(json.contains("\"realitySettings\""))
        assertTrue(json.contains("\"publicKey\": \"pub\""))
        assertFalse(json.contains("\"tlsSettings\""))
        assertFalse(json.contains("allowInsecure"))
        assertNoNewerOnlyTlsFields(json)
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

    private fun assertNoNewerOnlyTlsFields(json: String) {
        assertFalse(json.contains("verifyPeerCertByName"))
    }
}
