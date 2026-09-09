package com.passwall.corexray

import java.io.File

data class GeodataHealth(
    val geositeOk: Boolean,
    val geoipOk: Boolean,
    val geositeError: String? = null,
    val geoipError: String? = null,
) {
    val geositeUsable: Boolean get() = geositeOk
    val geoipUsable: Boolean get() = geoipOk
}

/**
 * Walks v2ray GeoSiteList / GeoIPList protobuf enough to read country_code
 * (field 1 of each repeated entry). Used to reject truncated compact files
 * before handing them to Xray.
 */
object GeodataValidator {
    const val MIN_GEOSITE_BYTES = 100_000L
    const val MIN_GEOIP_BYTES = 10_000L

    fun inspect(geoip: File, geosite: File): GeodataHealth {
        val site = diagnose(geosite, required = "cn", minBytes = MIN_GEOSITE_BYTES)
        val ip = diagnose(geoip, required = "cn", minBytes = MIN_GEOIP_BYTES)
        return GeodataHealth(
            geositeOk = site == null,
            geoipOk = ip == null,
            geositeError = site,
            geoipError = ip,
        )
    }

    fun hasCode(file: File, code: String, minBytes: Long = 64): Boolean =
        diagnose(file, code, minBytes) == null

    fun diagnose(file: File, required: String, minBytes: Long): String? {
        if (!file.isFile) return "${file.name} 不存在"
        if (file.length() < minBytes) {
            return "${file.name} 过小（${file.length()} < $minBytes），可能是损坏的精简文件"
        }
        val data = runCatching { file.readBytes() }.getOrElse {
            return "${file.name} 无法读取：${it.message}"
        }
        val codes = runCatching { listCodes(data) }.getOrElse {
            return "${file.name} 不是完整的 GeoSite/GeoIP protobuf：${it.message}"
        }
        val want = required.lowercase()
        if (codes.none { it.equals(want, ignoreCase = true) }) {
            return "${file.name} 缺少列表 $required（已有 ${codes.take(8).joinToString()}）"
        }
        return null
    }

    fun listCodes(data: ByteArray): Set<String> {
        val out = linkedSetOf<String>()
        var i = 0
        while (i < data.size) {
            val (key, i1) = readVarint(data, i)
            i = i1
            val field = (key ushr 3).toInt()
            val wire = (key and 7L).toInt()
            if (wire != 2) error("unexpected wire $wire field $field")
            val (len, i2) = readVarint(data, i)
            i = i2
            val n = len.toInt()
            if (i + n > data.size) error("EOF reading entry field=$field")
            val msg = data.copyOfRange(i, i + n)
            i += n
            val code = firstStringField(msg) ?: continue
            out += code
        }
        return out
    }

    private fun firstStringField(msg: ByteArray): String? {
        var j = 0
        val (key, j1) = readVarint(msg, j)
        j = j1
        if ((key ushr 3).toInt() != 1 || (key and 7L).toInt() != 2) return null
        val (len, j2) = readVarint(msg, j)
        j = j2
        val n = len.toInt()
        if (j + n > msg.size) return null
        return String(msg, j, n, Charsets.UTF_8)
    }

    private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int> {
        var n = 0L
        var shift = 0
        var i = start
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            i++
            n = n or ((b and 0x7F).toLong() shl shift)
            if (b < 0x80) return n to i
            shift += 7
            if (shift > 70) error("bad varint")
        }
        error("truncated varint")
    }
}
