package com.passwall.corexray

import android.util.Log
import com.passwall.data.log.RuntimeLog
import com.passwall.data.model.AppSettings
import com.passwall.data.repo.PasswallRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetSocketAddress
import java.net.Proxy

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
    /** Local SOCKS port while Xray is up, so a blocked LAN can still fetch via the node. */
    private val socksPort: () -> Int? = { null },
) {
    private val gate = Mutex()

    /**
     * Must be called only after VPN/proxy start has already succeeded, or from
     * the manual 「立即更新」 action. Failures keep last-good / bundled files
     * and never throw to the caller.
     *
     * @param force skip the 7-day / 6-hour gate (manual retry).
     */
    suspend fun refreshAfterSuccessfulStart(force: Boolean = false): RoutingAssetUpdateResult = gate.withLock {
        store.installBundledDefaults()
        val settings = runCatching { repository.getSettings() }.getOrDefault(AppSettings())
        val nowMs = now()
        if (!force && !RoutingAssetPolicy.shouldRefresh(
                settings.routingAssetsUpdatedAt,
                settings.routingAssetsAttemptedAt,
                nowMs,
            )
        ) {
            return RoutingAssetUpdateResult(attempted = false, success = true)
        }
        runCatching { repository.setRoutingAssetsAttemptedAt(nowMs) }
        val updated = mutableListOf<String>()
        val errors = mutableListOf<String>()
        for (asset in RoutingAssetCatalog.remoteFiles) {
            fetch(asset, updated, errors, proxy = null)
        }
        val stillBroken = RoutingAssetCatalog.remoteFiles.filter { asset ->
            errors.any { it.startsWith("${asset.fileName}:") }
        }
        val port = socksPort()
        if (stillBroken.isNotEmpty() && port != null) {
            RuntimeLog.warn("直连镜像失败，改经本地代理 127.0.0.1:$port 下载分流规则", "geo")
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
            for (asset in stillBroken) {
                errors.removeAll { it.startsWith("${asset.fileName}:") }
                fetch(asset, updated, errors, proxy)
            }
        }
        val requiredFailed = errors.any { line ->
            line.startsWith("${RoutingAssetCatalog.GEOIP}:") ||
                line.startsWith("${RoutingAssetCatalog.GEOSITE}:")
        }
        val optional = errors.filter { it.startsWith("${RoutingAssetCatalog.DIRECT_LIST}:") }
        if (!requiredFailed) {
            runCatching { repository.setRoutingAssetsUpdatedAt(nowMs) }
            Log.i(TAG, "routing assets updated: $updated")
            RuntimeLog.info("分流规则下载成功：${updated.joinToString()}", "geo")
            if (optional.isNotEmpty()) {
                RuntimeLog.warn("直连域名列表未更新，不影响 geoip/geosite：${optional.joinToString()}", "geo")
            }
            return RoutingAssetUpdateResult(attempted = true, success = true, updatedFiles = updated)
        }
        val message = errors.joinToString("；").take(220)
        runCatching { repository.setRoutingAssetsLastError(message) }
        Log.w(TAG, "routing asset update failed, keeping last-good: $message")
        RuntimeLog.warn("分流规则下载失败，继续使用 APK 内置或上次文件：$message", "geo")
        RoutingAssetUpdateResult(
            attempted = true,
            success = false,
            updatedFiles = updated,
            error = message,
        )
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
            val failure = fetchOne(asset, dest, proxy = null)
            if (failure == null) {
                RuntimeLog.info("已下载可用的 $name", "geo")
            } else {
                RuntimeLog.warn("$name 下载失败：$failure", "geo")
            }
        }
        return store.health()
    }

    private fun fetch(
        asset: RemoteAsset,
        updated: MutableList<String>,
        errors: MutableList<String>,
        proxy: Proxy?,
    ) {
        val failure = fetchOne(asset, store.file(asset.fileName), proxy)
        if (failure == null) updated += asset.fileName else errors += failure
    }

    /** @return null on success, or `fileName: reason` */
    private fun fetchOne(asset: RemoteAsset, dest: java.io.File, proxy: Proxy?): String? {
        val result = downloader.download(asset, dest, proxy)
        result.onSuccess {
            if (!acceptDownloaded(asset.fileName, dest)) {
                downloader.rollback(dest)
                return "${asset.fileName}: 下载后校验失败（缺少 cn 或文件损坏）"
            }
            downloader.discardBackup(dest)
        }
        result.onFailure { error ->
            downloader.rollback(dest)
            val detail = error.message ?: error.javaClass.simpleName
            return if (detail.startsWith("${asset.fileName}:")) detail else "${asset.fileName}: $detail"
        }
        return null
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
