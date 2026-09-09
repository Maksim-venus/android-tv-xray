package com.passwall.corexray

import android.content.Context
import com.passwall.data.log.RuntimeLog
import java.io.File

class RoutingAssetStore(private val context: Context) {

    fun directory(): File = File(context.filesDir, RoutingAssetCatalog.ASSET_DIR).apply { mkdirs() }

    fun file(name: String): File = File(directory(), name)

    fun configFile(): File = File(directory(), "xray-config.json")

    /**
     * Copy APK defaults into [directory].
     * Last-good remote downloads are kept unless they fail [GeodataValidator].
     */
    fun installBundledDefaults() {
        val dir = directory()
        for (name in RoutingAssetCatalog.bundledNames) {
            val dest = File(dir, name)
            val keep = dest.exists() && dest.length() > 0 && isUsable(name, dest)
            if (keep) continue
            if (dest.exists()) {
                RuntimeLog.warn("替换无效的 $name（${dest.length()} 字节）", "geo")
                dest.delete()
            }
            runCatching {
                context.assets.open("${RoutingAssetCatalog.ASSET_DIR}/$name").use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }.onFailure {
                RuntimeLog.warn("无法展开内置 $name：${it.message}", "geo")
            }
        }
    }

    fun health(): GeodataHealth = GeodataValidator.inspect(geoipFile(), geositeFile())

    fun extraDirectIps(): List<String> {
        val cidr = file(RoutingAssetCatalog.CN_CIDR)
        if (!cidr.isFile) return emptyList()
        return cidr.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("/") }
    }

    private fun isUsable(name: String, dest: File): Boolean = when (name) {
        RoutingAssetCatalog.GEOSITE ->
            GeodataValidator.hasCode(dest, "cn", GeodataValidator.MIN_GEOSITE_BYTES)
        RoutingAssetCatalog.GEOIP ->
            GeodataValidator.hasCode(dest, "cn", GeodataValidator.MIN_GEOIP_BYTES)
        else -> dest.length() > 0
    }

    fun geoipFile(): File = file(RoutingAssetCatalog.GEOIP)
    fun geositeFile(): File = file(RoutingAssetCatalog.GEOSITE)
}
