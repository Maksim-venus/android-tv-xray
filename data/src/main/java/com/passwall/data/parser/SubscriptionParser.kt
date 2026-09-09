package com.passwall.data.parser

import com.passwall.data.model.NodeSource
import com.passwall.data.model.ProxyNode
import java.nio.charset.StandardCharsets

object SubscriptionParser {
    /**
     * Accepts either a raw list of share links or a standard base64-encoded subscription body.
     */
    fun parseBody(body: String, source: NodeSource = NodeSource.SUBSCRIPTION): ParseResult {
        val trimmed = body.trim()
        val decoded = runCatching {
            if (looksLikeShareList(trimmed)) trimmed
            else String(decodeBase64(trimmed), StandardCharsets.UTF_8)
        }.getOrDefault(trimmed)
        return ShareLinkParser.parseBatch(decoded, source)
    }

    private fun looksLikeShareList(text: String): Boolean {
        val first = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return false
        return first.contains("://")
    }
}

/**
 * HTTP fetch is stubbed for the first slice. The parser above is real so a later
 * OkHttp/Cronet drop-in can feed bytes straight into [SubscriptionParser.parseBody].
 */
interface SubscriptionFetcher {
    suspend fun fetch(url: String): String
}

class StubSubscriptionFetcher : SubscriptionFetcher {
    override suspend fun fetch(url: String): String {
        // TODO(subscription-fetch): GET the URL with a China-friendly User-Agent,
        // follow redirects, and return the raw body. Do not block the TV UI thread.
        throw UnsupportedOperationException(
            "订阅拉取尚未实现（StubSubscriptionFetcher）。URL=$url。请在 data 模块替换为真实 HTTP 实现。",
        )
    }
}
