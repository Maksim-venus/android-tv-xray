package com.passwall.corexray

import com.passwall.data.model.AppSettings
import com.passwall.data.model.Protocol
import com.passwall.data.model.ProxyNode
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigGeneratorTest {
    @Test
    fun generatesChinaSplitRouting() {
        val node = ProxyNode(
            name = "东京-1",
            protocol = Protocol.VLESS,
            host = "tokyo-1.example.com",
            port = 443,
            uuid = "11111111-1111-1111-1111-111111111111",
            security = "tls",
            sni = "tokyo-1.example.com",
        )
        val json = XrayConfigGenerator.generate(node, AppSettings(allowInsecureSsl = true)).json
        assertTrue(json.contains("geosite:cn"))
        assertTrue(json.contains("geoip:cn"))
        assertTrue(json.contains("geoip:private"))
        assertTrue(json.contains("\"allowInsecure\": true"))
        assertTrue(json.contains("vless"))
        assertTrue(json.contains("223.5.5.5"))
        assertTrue(json.contains("outboundTag"))
        assertTrue(json.contains("\"protocol\": \"tun\""))
        assertTrue(json.contains("tun-in"))
    }
}
