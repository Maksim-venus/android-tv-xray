package com.passwall.corexray

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GeodataValidatorTest {
    @Test
    fun packagedGeositeContainsCn() {
        val geosite = File("src/main/assets/xray/geosite.dat")
        val geoip = File("src/main/assets/xray/geoip.dat")
        assertTrue("geosite.dat must be fetched before test", geosite.isFile)
        assertTrue(geosite.length() > GeodataValidator.MIN_GEOSITE_BYTES)
        assertTrue(GeodataValidator.hasCode(geosite, "cn", GeodataValidator.MIN_GEOSITE_BYTES))
        assertTrue(GeodataValidator.hasCode(geoip, "cn", GeodataValidator.MIN_GEOIP_BYTES))
        val health = GeodataValidator.inspect(geoip, geosite)
        assertTrue(health.geositeOk)
        assertTrue(health.geoipOk)
    }

    @Test
    fun rejectsTruncatedGeosite() {
        val tmp = File.createTempFile("geosite", ".dat")
        tmp.writeBytes(byteArrayOf(0x0A, 0x20, 0x0A, 0x02, 'c'.code.toByte(), 'n'.code.toByte()))
        assertFalse(GeodataValidator.hasCode(tmp, "cn", minBytes = 1))
        tmp.delete()
    }

    @Test
    fun fallbackConfigOmitsGeositeRules() {
        val node = com.passwall.data.model.ProxyNode(
            name = "n",
            protocol = com.passwall.data.model.Protocol.VLESS,
            host = "example.com",
            port = 443,
            uuid = "11111111-1111-1111-1111-111111111111",
        )
        val generated = XrayConfigGenerator.generate(
            node = node,
            settings = com.passwall.data.model.AppSettings(),
            health = GeodataHealth(geositeOk = false, geoipOk = true, geositeError = "EOF"),
        )
        assertFalse(generated.usedGeosite)
        assertFalse(generated.json.contains("geosite:cn"))
        assertTrue(generated.json.contains("geoip:cn"))
        assertTrue(generated.notes.any { it.contains("geosite.dat 无效") })
    }
}