package com.passwall.data.parser

import com.passwall.data.model.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ShareLinkParserTest {

    @Test
    fun parseVless() {
        val link = "vless://11111111-1111-1111-1111-111111111111@tokyo-1.example.com:443" +
            "?encryption=none&security=tls&sni=tokyo-1.example.com&type=ws&path=%2Fws&flow=xtls-rprx-vision#东京-1"
        val node = VlessParser.parse(link)
        assertEquals("东京-1", node.name)
        assertEquals(Protocol.VLESS, node.protocol)
        assertEquals("tokyo-1.example.com", node.host)
        assertEquals(443, node.port)
        assertEquals("tls", node.security)
        assertEquals("ws", node.network)
        assertEquals("/ws", node.path)
        assertEquals("xtls-rprx-vision", node.flow)
    }

    @Test
    fun parseVlessPcsAndVcn() {
        val pin = "e8e2d387fdbffeb38e9c9065cf30a97ee23c0e3d32ee6f78ffae40966befccc9"
        val link = "vless://11111111-1111-1111-1111-111111111111@1.2.3.4:443" +
            "?encryption=none&security=tls&sni=www.example.com&type=tcp" +
            "&allowInsecure=1&pcs=$pin&vcn=www.example.com#pcs"
        val node = VlessParser.parse(link)
        assertEquals(true, node.allowInsecure)
        assertEquals(pin, node.pinnedPeerCertSha256)
        assertEquals("www.example.com", node.verifyPeerCertByName)
    }

    @Test
    fun parseVmessJson() {
        val json = """{"v":"2","ps":"香港-2","add":"hk-2.example.com","port":"443","id":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee","aid":"0","net":"ws","type":"none","host":"hk-2.example.com","path":"/v","tls":"tls"}"""
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray())
        val node = VmessParser.parse("vmess://$encoded")
        assertEquals("香港-2", node.name)
        assertEquals(Protocol.VMESS, node.protocol)
        assertEquals("hk-2.example.com", node.host)
        assertEquals(443, node.port)
        assertEquals("ws", node.network)
        assertEquals("tls", node.security)
    }

    @Test
    fun parseSsrStubRecognizesLink() {
        val inner = "ssr.example.com:1234:origin:aes-256-cfb:plain:${Base64.getEncoder().encodeToString("pwd".toByteArray())}"
        val link = "ssr://${Base64.getEncoder().encodeToString(inner.toByteArray())}"
        val node = SsrParser.parse(link)
        assertEquals(Protocol.SSR, node.protocol)
        assertEquals("ssr.example.com", node.host)
        assertEquals(1234, node.port)
    }

    @Test
    fun parseBatchSkipsUnknown() {
        val result = ShareLinkParser.parseBatch("not-a-link\nvless://u@h:1?security=none#n")
        assertEquals(1, result.nodes.size)
        assertTrue(result.errors.isNotEmpty())
    }
}
