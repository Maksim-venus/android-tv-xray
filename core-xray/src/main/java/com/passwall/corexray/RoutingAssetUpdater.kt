package com.passwall.corexray

import android.util.Log
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
            result.onSuccess { updated += asset.fileName }
            result.onFailure { errors += "${asset.fileName}: ${it.message ?: it.javaClass.simpleName}" }
        }
        return if (errors.isEmpty()) {
            runCatching { repository.setRoutingAssetsUpdatedAt(nowMs) }
            Log.i(TAG, "routing assets updated: $updated")
            RoutingAssetUpdateResult(attempted = true, success = true, updatedFiles = updated)
        } else {
            Log.w(TAG, "routing asset update failed, keeping last-good: $errors")
            RoutingAssetUpdateResult(
                attempted = true,
                success = false,
                updatedFiles = updated,
                error = errors.joinToString("; "),
            )
        }
    }

    companion object {
        private const val TAG = "passwall-geo"
    }
}
