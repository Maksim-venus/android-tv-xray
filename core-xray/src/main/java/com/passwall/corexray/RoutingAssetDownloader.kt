package com.passwall.corexray

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class RoutingAssetDownloader(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 120_000,
) {
    fun download(asset: RemoteAsset, dest: File): Result<File> {
        var lastError: Throwable? = null
        for (url in asset.urls) {
            val result = runCatching { downloadUrl(url, dest, asset.minBytes) }
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull()
        }
        return Result.failure(lastError ?: IllegalStateException("no URL for ${asset.fileName}"))
    }

    private fun downloadUrl(url: String, dest: File, minBytes: Long): File {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.absolutePath + ".tmp")
        tmp.delete()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("User-Agent", "PasswallTV/0.1 (Android TV Xray)")
            setRequestProperty("Accept", "*/*")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code for $url")
            connection.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            val size = tmp.length()
            if (size < minBytes) {
                tmp.delete()
                error("download too small ($size < $minBytes) from $url")
            }
            val previous = if (dest.exists()) File(dest.absolutePath + ".bak") else null
            if (previous != null) {
                previous.delete()
                if (!dest.renameTo(previous)) dest.copyTo(previous, overwrite = true)
            }
            val moved = tmp.renameTo(dest) || runCatching {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
                true
            }.getOrDefault(false)
            if (!moved) {
                if (previous != null && !dest.exists()) previous.renameTo(dest)
                error("cannot replace ${dest.name}")
            }
            previous?.delete()
            return dest
        } finally {
            connection.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }
}
