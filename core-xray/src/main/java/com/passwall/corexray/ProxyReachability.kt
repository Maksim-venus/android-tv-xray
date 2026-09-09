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
    val exitIp: String? = null,
    val country: String? = null,
    val flag: String? = null,
)

/**
 * HTTP(S) reachability through the local Xray SOCKS inbound.
 *
 * The app package is excluded from the VPN TUN (`addDisallowedApplication`),
 * so a direct HttpURLConnection would go out the LAN NIC and is not a proxy test.
 * Connecting via `127.0.0.1:10808` forces the request through Xray routing → outbound.
 *
 * Primary: ipinfo.io exit IP + ISO country. Fallback: generate_204 if ipinfo fails.
 */
object ProxyReachability {
    const val IPINFO_JSON = "https://ipinfo.io/json"
    const val IPINFO_COUNTRY = "https://ipinfo.io/country"

    val IPINFO_URLS = listOf(
        "https://ipinfo.io/json",
        "http://ipinfo.io/json",
        "https://ipinfo.io/ip",
    )

    val FALLBACK_URLS = listOf(
        "https://www.gstatic.com/generate_204",
        "https://www.google.com/generate_204",
        "https://cp.cloudflare.com/generate_204",
    )

    /** generate_204 fallbacks (ipinfo is always tried first). */
    val DEFAULT_URLS = FALLBACK_URLS

    const val DEFAULT_SOCKS_HOST = "127.0.0.1"
    const val DEFAULT_TIMEOUT_MS = 8_000
    const val USER_AGENT = "PasswallTV/0.1.6"

    fun isSuccessStatus(code: Int): Boolean = code == 204 || code == 200

    fun formatExit(flag: String, ip: String, ms: Long): String =
        "$flag 出口 $ip · ${ms} ms"

    fun formatFallbackSuccess(url: String, code: Int, ms: Long): String =
        "外网可达（无出口信息）· HTTP $code · ${ms} ms（${shortHost(url)}）"

    fun formatSuccess(url: String, code: Int, ms: Long): String =
        formatFallbackSuccess(url, code, ms)

    fun formatFailure(detail: String): String = "外网不可达：$detail"

    fun probe(
        socksHost: String = DEFAULT_SOCKS_HOST,
        socksPort: Int = DEFAULT_SOCKS_PORT,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        ipinfoUrls: List<String> = IPINFO_URLS,
        fallbackUrls: List<String> = FALLBACK_URLS,
    ): ReachabilityResult {
        if (ipinfoUrls.isEmpty() && fallbackUrls.isEmpty()) {
            return ReachabilityResult(ok = false, error = "没有探测地址", message = formatFailure("没有探测地址"))
        }
        val errors = mutableListOf<String>()
        for (url in ipinfoUrls) {
            val one = probeIpInfo(url, socksHost, socksPort, timeoutMs)
            if (one.ok) return one
            errors += "ipinfo（${shortHost(url)}）：${one.error ?: "失败"}"
        }
        for (url in fallbackUrls) {
            val one = probeOne(url, socksHost, socksPort, timeoutMs)
            if (one.ok) {
                return one.copy(
                    message = formatFallbackSuccess(url, one.httpStatus ?: 0, one.latencyMs ?: 0L),
                )
            }
            errors += "${shortHost(url)}：${one.error ?: "失败"}"
        }
        val detail = errors.joinToString("；")
        return ReachabilityResult(ok = false, error = detail, message = formatFailure(detail))
    }

    fun probeIpInfo(
        url: String,
        socksHost: String = DEFAULT_SOCKS_HOST,
        socksPort: Int = DEFAULT_SOCKS_PORT,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): ReachabilityResult {
        val fetched = socksGet(url, socksHost, socksPort, timeoutMs, readBody = true)
        if (fetched.error != null && fetched.code == 0) {
            return ReachabilityResult(
                ok = false,
                url = url,
                error = fetched.error,
                message = formatFailure("${shortHost(url)} ${fetched.error}"),
            )
        }
        if (!isSuccessStatus(fetched.code)) {
            return ReachabilityResult(
                ok = false,
                latencyMs = fetched.ms,
                httpStatus = fetched.code,
                url = url,
                error = "HTTP ${fetched.code}",
                message = formatFailure("${shortHost(url)} HTTP ${fetched.code}"),
            )
        }
        var info = IpInfoParser.parse(fetched.body)
        if (info == null) {
            return ReachabilityResult(
                ok = false,
                latencyMs = fetched.ms,
                httpStatus = fetched.code,
                url = url,
                error = "无法解析出口 IP",
                message = formatFailure("ipinfo 返回无法解析"),
            )
        }
        if (info.country == null) {
            val extra = socksGet(IPINFO_COUNTRY, socksHost, socksPort, timeoutMs, readBody = true)
            if (isSuccessStatus(extra.code)) {
                val cc = IpInfoParser.parseCountry(extra.body)
                if (cc != null) info = info.copy(country = cc)
            }
        }
        val flag = info.flag
        return ReachabilityResult(
            ok = true,
            latencyMs = fetched.ms,
            httpStatus = fetched.code,
            url = url,
            message = formatExit(flag, info.ip, fetched.ms),
            exitIp = info.ip,
            country = info.country,
            flag = flag,
        )
    }

    fun probeOne(
        url: String,
        socksHost: String = DEFAULT_SOCKS_HOST,
        socksPort: Int = DEFAULT_SOCKS_PORT,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): ReachabilityResult {
        val fetched = socksGet(url, socksHost, socksPort, timeoutMs, readBody = false)
        if (fetched.error != null && fetched.code == 0) {
            return ReachabilityResult(
                ok = false,
                url = url,
                error = fetched.error,
                message = formatFailure("${shortHost(url)} ${fetched.error}"),
            )
        }
        return if (isSuccessStatus(fetched.code)) {
            ReachabilityResult(
                ok = true,
                latencyMs = fetched.ms,
                httpStatus = fetched.code,
                url = url,
                message = formatFallbackSuccess(url, fetched.code, fetched.ms),
            )
        } else {
            ReachabilityResult(
                ok = false,
                latencyMs = fetched.ms,
                httpStatus = fetched.code,
                url = url,
                error = "HTTP ${fetched.code}",
                message = formatFailure("${shortHost(url)} HTTP ${fetched.code}"),
            )
        }
    }

    private data class SocksResponse(
        val code: Int,
        val ms: Long,
        val body: String,
        val error: String? = null,
    )

    private fun socksGet(
        url: String,
        socksHost: String,
        socksPort: Int,
        timeoutMs: Int,
        readBody: Boolean,
    ): SocksResponse {
        val started = System.nanoTime()
        var conn: HttpURLConnection? = null
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort))
            conn = URL(url).openConnection(proxy) as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")
            conn.useCaches = false
            val code = conn.responseCode
            val ms = ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1L)
            val body = if (readBody) readLimited(conn) else ""
            SocksResponse(code = code, ms = ms, body = body)
        } catch (t: Throwable) {
            SocksResponse(
                code = 0,
                ms = 0,
                body = "",
                error = t.message ?: t.javaClass.simpleName,
            )
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun readLimited(conn: HttpURLConnection, limit: Int = 8_192): String {
        val stream = try {
            if (conn.responseCode >= 400) conn.errorStream else conn.inputStream
        } catch (_: Throwable) {
            conn.errorStream
        } ?: return ""
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText().take(limit) }
    }

    fun shortHost(url: String): String = try {
        URL(url).host
    } catch (_: Throwable) {
        url
    }
}
