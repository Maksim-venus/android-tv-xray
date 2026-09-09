package com.passwall.corexray

import android.content.Context
import java.io.File

class RoutingAssetStore(private val context: Context) {

    fun directory(): File = File(context.filesDir, RoutingAssetCatalog.ASSET_DIR).apply { mkdirs() }

    fun file(name: String): File = File(directory(), name)

    fun configFile(): File = File(directory(), "xray-config.json")

    /**
     * Copy APK defaults into [directory] when a runtime file is missing or empty.
     * Never overwrites a last-good downloaded file.
     */
    fun installBundledDefaults() {
        val dir = directory()
        for (name in RoutingAssetCatalog.bundledNames) {
            val dest = File(dir, name)
            if (dest.exists() && dest.length() > 0) continue
            runCatching {
                context.assets.open("${RoutingAssetCatalog.ASSET_DIR}/$name").use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    fun geoipFile(): File = file(RoutingAssetCatalog.GEOIP)
    fun geositeFile(): File = file(RoutingAssetCatalog.GEOSITE)
}
