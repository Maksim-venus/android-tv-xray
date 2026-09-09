package com.passwall.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.passwall.data.model.AppSettings
import com.passwall.data.model.NodeSource
import com.passwall.data.model.Protocol
import com.passwall.data.model.ProxyNode
import com.passwall.data.model.Subscription

@Entity(tableName = "nodes")
data class NodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val uuid: String? = null,
    val password: String? = null,
    val alterId: Int = 0,
    val security: String = "tls",
    val network: String = "tcp",
    val path: String? = null,
    val hostHeader: String? = null,
    val sni: String? = null,
    val flow: String? = null,
    val encryption: String? = "none",
    val fingerprint: String? = null,
    val publicKey: String? = null,
    val shortId: String? = null,
    val spiderX: String? = null,
    val allowInsecure: Boolean = false,
    val rawLink: String = "",
    val source: String = NodeSource.MANUAL.name,
    val subscriptionId: Long? = null,
    val online: Boolean = true,
    val latencyMs: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toModel(): ProxyNode = ProxyNode(
        id = id,
        name = name,
        protocol = Protocol.fromWire(protocol),
        host = host,
        port = port,
        uuid = uuid,
        password = password,
        alterId = alterId,
        security = security,
        network = network,
        path = path,
        hostHeader = hostHeader,
        sni = sni,
        flow = flow,
        encryption = encryption,
        fingerprint = fingerprint,
        publicKey = publicKey,
        shortId = shortId,
        spiderX = spiderX,
        allowInsecure = allowInsecure,
        rawLink = rawLink,
        source = runCatching { NodeSource.valueOf(source) }.getOrDefault(NodeSource.MANUAL),
        subscriptionId = subscriptionId,
        online = online,
        latencyMs = latencyMs,
        createdAt = createdAt,
    )
}

fun ProxyNode.toEntity(): NodeEntity = NodeEntity(
    id = id,
    name = name,
    protocol = protocol.wireName,
    host = host,
    port = port,
    uuid = uuid,
    password = password,
    alterId = alterId,
    security = security,
    network = network,
    path = path,
    hostHeader = hostHeader,
    sni = sni,
    flow = flow,
    encryption = encryption,
    fingerprint = fingerprint,
    publicKey = publicKey,
    shortId = shortId,
    spiderX = spiderX,
    allowInsecure = allowInsecure,
    rawLink = rawLink,
    source = source.name,
    subscriptionId = subscriptionId,
    online = online,
    latencyMs = latencyMs,
    createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt,
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val lastUpdatedAt: Long? = null,
    val nodeCount: Int = 0,
) {
    fun toModel(): Subscription = Subscription(id, name, url, enabled, lastUpdatedAt, nodeCount)
}

fun Subscription.toEntity(): SubscriptionEntity =
    SubscriptionEntity(id, name, url, enabled, lastUpdatedAt, nodeCount)

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val selectedNodeId: Long? = null,
    val allowInsecureSsl: Boolean = false,
    val httpEditEnabled: Boolean = false,
    val httpPort: Int = AppSettings.DEFAULT_HTTP_PORT,
) {
    fun toModel(): AppSettings = AppSettings(
        selectedNodeId = selectedNodeId,
        allowInsecureSsl = allowInsecureSsl,
        httpEditEnabled = httpEditEnabled,
        httpPort = httpPort,
    )
}
