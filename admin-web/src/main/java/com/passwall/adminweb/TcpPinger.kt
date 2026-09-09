package com.passwall.adminweb

import com.passwall.data.model.ProxyNode
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.max

data class PingResult(
    val nodeId: Long,
    val host: String,
    val port: Int,
    val ok: Boolean,
    val latencyMs: Long?,
    val error: String? = null,
)

object TcpPinger {
    fun ping(node: ProxyNode, timeoutMs: Int = 3_000): PingResult {
        val started = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(node.host, node.port), timeoutMs)
            }
            val ms = max(1L, (System.nanoTime() - started) / 1_000_000)
            PingResult(node.id, node.host, node.port, true, ms)
        } catch (t: Throwable) {
            PingResult(
                nodeId = node.id,
                host = node.host,
                port = node.port,
                ok = false,
                latencyMs = null,
                error = t.message ?: t.javaClass.simpleName,
            )
        }
    }
}
