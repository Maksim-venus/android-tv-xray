package com.passwall.corexray

import android.util.Log
import com.passwall.data.log.RuntimeLog
import com.passwall.data.model.AppSettings
import com.passwall.data.repo.PasswallRepository

data class RoutingAssetUpdateResult(
    val attempted: Boolean,
    val success: Boolean,
    val updatedFiles: List<String> = emptyList(),
    val error: String? = null,
)

class RoutingAssetUpdater(
    private val store: RoutingAssetStore,
    private val repository: PasswallRepository,
    private val downloader: RoutingAssetDownloader = RoutingAssetDownloader(),
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    /**
     * Must be called only after VPN/proxy start has already succeeded.
     * Failures keep last-good / bundled files and never throw to the caller.
     */
    suspend fun refreshAfterSuccessfulStart(): RoutingAssetUpdateResult {
        store.installBundledDefaults()
        val settings = runCatching { repository.getSettings() }.getOrDefault(AppSettings())
        val nowMs = now()
        if (!RoutingAssetPolicy.shouldRefresh(settings.routingAssetsUpdatedAt, settings.routingAssetsAttemptedAt, nowMs)) {
            return RoutingAssetUpdateResult(attempted = false, success = true)
        }
        runCatching { repository.setRoutingAssetsAttemptedAt(nowMs) }
        val updated = mutableListOf<String>()
        val errors = mutableListOf<String>()
        for (asset in RoutingAssetCatalog.remoteFiles) {
            val dest = store.file(asset.fileName)
            val result = downloader.download(asset, dest)
            result.onSuccess {
                if (!acceptDownloaded(asset.fileName, dest)) {
                    errors += "${asset.fileName}: 下载后校验失败（缺少 cn 或文件损坏）"
                } else {
                    updated += asset.fileName
                }
            }
            result.onFailure { errors += "${asset.fileName}: ${it.message ?: it.javaClass.simpleName}" }
        }
        return if (errors.isEmpty()) {
            runCatching { repository.setRoutingAssetsUpdatedAt(nowMs) }
            Log.i(TAG, "routing assets updated: $updated")
            RuntimeLog.info("分流规则下载成功：${updated.joinToString()}", "geo")
            RoutingAssetUpdateResult(attempted = true, success = true, updatedFiles = updated)
        } else {
            Log.w(TAG, "routing asset update failed, keeping last-good: $errors")
            RuntimeLog.warn("分流规则下载失败：$errors", "geo")
            RoutingAssetUpdateResult(
                attempted = true,
                success = false,
                updatedFiles = updated,
                error = errors.joinToString("; "),
            )
        }
    }

    /**
     * Blocking repair used before VPN start when bundled/runtime geodata is invalid.
     */
    fun repairInvalid(health: GeodataHealth): GeodataHealth {
        val needed = buildList {
            if (!health.geositeOk) add(RoutingAssetCatalog.GEOSITE)
            if (!health.geoipOk) add(RoutingAssetCatalog.GEOIP)
        }
        for (name in needed) {
            val asset = RoutingAssetCatalog.remoteFiles.firstOrNull { it.fileName == name } ?: continue
            val dest = store.file(name)
            RuntimeLog.warn("正在下载可用的 $name…", "geo")
            val result = downloader.download(asset, dest)
            result.onSuccess {
                if (!acceptDownloaded(name, dest)) {
                    RuntimeLog.warn("$name 下载后仍无法识别 cn 列表", "geo")
                } else {
                    RuntimeLog.info("已下载可用的 $name", "geo")
                }
            }
            result.onFailure {
                RuntimeLog.warn("$name 下载失败：${it.message}", "geo")
            }
        }
        return store.health()
    }

    private fun acceptDownloaded(fileName: String, dest: java.io.File): Boolean = when (fileName) {
        RoutingAssetCatalog.GEOSITE ->
            GeodataValidator.hasCode(dest, "cn", GeodataValidator.MIN_GEOSITE_BYTES)
        RoutingAssetCatalog.GEOIP ->
            GeodataValidator.hasCode(dest, "cn", GeodataValidator.MIN_GEOIP_BYTES)
        else -> dest.length() > 0
    }

    companion object {
        private const val TAG = "passwall-geo"
    }
}
