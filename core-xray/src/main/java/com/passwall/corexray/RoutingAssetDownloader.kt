package com.passwall.corexray

import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException

class RoutingAssetDownloader(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 180_000,
) {
    fun download(asset: RemoteAsset, dest: File, proxy: Proxy? = null): Result<File> {
        val errors = mutableListOf<String>()
        for (url in asset.urls) {
            val result = runCatching { downloadUrl(url, dest, asset.minBytes, proxy) }
            if (result.isSuccess) return result
            val error = result.exceptionOrNull() ?: IOException("unknown")
            errors += DownloadErrors.describe(url, error)
        }
        val summary = errors.joinToString("；").ifBlank { "没有可用的下载地址" }
        return Result.failure(IOException("${asset.fileName}: $summary"))
    }

    /**
     * Put the previous file back when the new bytes failed validation.
     * No-op if this attempt never replaced [dest].
     */
    fun rollback(dest: File) {
        val previous = File(dest.absolutePath + ".bak")
        if (!previous.exists()) return
        dest.delete()
        if (!previous.renameTo(dest)) {
            previous.copyTo(dest, overwrite = true)
            previous.delete()
        }
    }

    fun discardBackup(dest: File) {
        File(dest.absolutePath + ".bak").delete()
    }

    private fun downloadUrl(url: String, dest: File, minBytes: Long, proxy: Proxy?): File {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.absolutePath + ".tmp")
        tmp.delete()
        val connection = open(url, proxy)
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            connection.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            val size = tmp.length()
            if (size < minBytes) {
                tmp.delete()
                error("文件过小（$size < $minBytes）")
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
                if (previous != null && previous.exists() && !dest.exists()) previous.renameTo(dest)
                error("无法写入 ${dest.name}")
            }
            return dest
        } finally {
            connection.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun open(url: String, proxy: Proxy?): HttpURLConnection {
        val parsed = URL(url)
        val connection = if (proxy == null) {
            parsed.openConnection()
        } else {
            parsed.openConnection(proxy)
        } as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("User-Agent", "PasswallTV/0.1.8 (Android TV Xray)")
        connection.setRequestProperty("Accept", "*/*")
        return connection
    }
}

object DownloadErrors {
    fun describe(url: String, error: Throwable): String {
        val host = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url
        val reason = when (error) {
            is UnknownHostException -> "DNS 解析失败"
            is SocketTimeoutException -> "连接超时"
            is ConnectException -> "无法连接"
            else -> friendlyMessage(error)
        }
        return "$host $reason"
    }

    private fun friendlyMessage(error: Throwable): String {
        val msg = error.message?.trim().orEmpty()
        if (msg.isEmpty()) return error.javaClass.simpleName
        return msg.substringBefore(" for ").take(80)
    }
}
