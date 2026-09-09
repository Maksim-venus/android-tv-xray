package com.passwall.corexray

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

data class ReachabilityResult(
    val ok: Boolean,
    val latencyMs: Long? = null,
    val httpStatus: Int? = null,
    val url: String? = null,
    val error: String? = null,
    val message: String,
)

/**
 * HTTP(S) reachability through the local Xray SOCKS inbound.
 *
 * The app package is excluded from the VPN TUN (`addDisallowedApplication`),
 * so a direct HttpURLConnection would go out the LAN NIC and is not a proxy test.
 * Connecting via `127.0.0.1:10808` forces the request through Xray routing → outbound.
 */
object ProxyReachability {
    val DEFAULT_URLS = listOf(
        "https://www.gstatic.com/generate_204",
        "https://www.google.com/generate_204",
        "https://cp.cloudflare.com/generate_204",
    )

    const val DEFAULT_SOCKS_HOST = "127.0.0.1"
    const val DEFAULT_TIMEOUT_MS = 8_000

    fun isSuccessStatus(code: Int): Boolean = code == 204 || code == 200

    fun formatSuccess(url: String, code: Int, ms: Long): String =
        "外网可达 HTTP $code · ${ms} ms（经代理 ${shortHost(url)}）"

    fun formatFailure(detail: String): String = "外网不可达：$detail"

    fun probe(
        socksHost: String = DEFAULT_SOCKS_HOST,
        socksPort: Int = DEFAULT_SOCKS_PORT,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        urls: List<String> = DEFAULT_URLS,
    ): ReachabilityResult {
        if (urls.isEmpty()) {
            return ReachabilityResult(ok = false, error = "没有探测地址", message = formatFailure("没有探测地址"))
        }
        val errors = mutableListOf<String>()
        for (url in urls) {
            val one = probeOne(url, socksHost, socksPort, timeoutMs)
            if (one.ok) return one
            errors += "${shortHost(url)}：${one.error ?: "失败"}"
        }
        val detail = errors.joinToString("；")
        return ReachabilityResult(ok = false, error = detail, message = formatFailure(detail))
    }

    fun probeOne(
        url: String,
        socksHost: String = DEFAULT_SOCKS_HOST,
        socksPort: Int = DEFAULT_SOCKS_PORT,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): ReachabilityResult {
        val started = System.nanoTime()
        var conn: HttpURLConnection? = null
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort))
            conn = URL(url).openConnection(proxy) as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "PasswallTV/0.1.4")
            conn.useCaches = false
            val code = conn.responseCode
            val ms = ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1L)
            if (isSuccessStatus(code)) {
                ReachabilityResult(
                    ok = true,
                    latencyMs = ms,
                    httpStatus = code,
                    url = url,
                    message = formatSuccess(url, code, ms),
                )
            } else {
                ReachabilityResult(
                    ok = false,
                    latencyMs = ms,
                    httpStatus = code,
                    url = url,
                    error = "HTTP $code",
                    message = formatFailure("${shortHost(url)} HTTP $code"),
                )
            }
        } catch (t: Throwable) {
            ReachabilityResult(
                ok = false,
                url = url,
                error = t.message ?: t.javaClass.simpleName,
                message = formatFailure("${shortHost(url)} ${t.message ?: t.javaClass.simpleName}"),
            )
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun shortHost(url: String): String = try {
        URL(url).host
    } catch (_: Throwable) {
        url
    }
}
