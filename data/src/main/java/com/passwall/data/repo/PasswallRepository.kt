package com.passwall.data.repo

import com.passwall.data.db.AppDatabase
import com.passwall.data.db.SettingsEntity
import com.passwall.data.db.toEntity
import com.passwall.data.model.AppSettings
import com.passwall.data.model.NodeSource
import com.passwall.data.model.ProxyNode
import com.passwall.data.model.Subscription
import com.passwall.data.parser.ParseResult
import com.passwall.data.parser.ShareLinkParser
import com.passwall.data.parser.StubSubscriptionFetcher
import com.passwall.data.parser.SubscriptionFetcher
import com.passwall.data.parser.SubscriptionParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PasswallRepository(
    private val db: AppDatabase,
    private val subscriptionFetcher: SubscriptionFetcher = StubSubscriptionFetcher(),
) {
    private val nodes = db.nodeDao()
    private val subs = db.subscriptionDao()
    private val settings = db.settingsDao()

    val nodesFlow: Flow<List<ProxyNode>> = nodes.observeAll().map { list -> list.map { it.toModel() } }
    val subscriptionsFlow: Flow<List<Subscription>> =
        subs.observeAll().map { list -> list.map { it.toModel() } }
    val settingsFlow: Flow<AppSettings> =
        settings.observe().map { it?.toModel() ?: AppSettings() }

    suspend fun ensureSettings() {
        nodes.deleteBySource(NodeSource.SEED.name)
        nodes.deleteExampleHosts()
        val current = settings.get()
        if (current == null) {
            settings.upsert(SettingsEntity())
            return
        }
        val selected = current.selectedNodeId
        if (selected != null && nodes.getById(selected) == null) {
            settings.upsert(current.copy(selectedNodeId = null))
        }
    }

    suspend fun getSettings(): AppSettings = settings.get()?.toModel() ?: AppSettings()

    suspend fun getSelectedNode(): ProxyNode? {
        val selectedId = getSettings().selectedNodeId
        return (selectedId?.let { nodes.getById(it) } ?: nodes.getAll().firstOrNull())?.toModel()
    }

    suspend fun selectNode(id: Long) {
        val current = settings.get() ?: SettingsEntity()
        settings.upsert(current.copy(selectedNodeId = id))
    }

    suspend fun setAllowInsecure(enabled: Boolean) {
        val current = settings.get() ?: SettingsEntity()
        settings.upsert(current.copy(allowInsecureSsl = enabled))
    }

    suspend fun setHttpEditEnabled(enabled: Boolean) {
        val current = settings.get() ?: SettingsEntity()
        settings.upsert(current.copy(httpEditEnabled = enabled))
    }

    suspend fun setRoutingAssetsUpdatedAt(epochMs: Long) {
        val current = settings.get() ?: SettingsEntity()
        settings.upsert(current.copy(routingAssetsUpdatedAt = epochMs, routingAssetsAttemptedAt = epochMs))
    }

    suspend fun setRoutingAssetsAttemptedAt(epochMs: Long) {
        val current = settings.get() ?: SettingsEntity()
        settings.upsert(current.copy(routingAssetsAttemptedAt = epochMs))
    }

    suspend fun importLinks(text: String): ParseResult {
        val result = ShareLinkParser.parseBatch(text, NodeSource.MANUAL)
        if (result.nodes.isNotEmpty()) {
            nodes.insertAll(result.nodes.map { it.toEntity() })
        }
        return result
    }

    suspend fun addSubscription(name: String, url: String): Long {
        return subs.insert(
            com.passwall.data.db.SubscriptionEntity(name = name, url = url, enabled = true),
        )
    }

    suspend fun updateSubscription(sub: Subscription) {
        subs.update(sub.toEntity())
    }

    suspend fun deleteSubscription(id: Long) {
        nodes.deleteBySubscription(id)
        subs.deleteById(id)
    }

    suspend fun refreshSubscription(id: Long): ParseResult {
        val entity = subs.getById(id) ?: error("订阅不存在: $id")
        val body = subscriptionFetcher.fetch(entity.url)
        val parsed = SubscriptionParser.parseBody(body, NodeSource.SUBSCRIPTION)
        nodes.deleteBySubscription(id)
        if (parsed.nodes.isNotEmpty()) {
            nodes.insertAll(parsed.nodes.map { it.copy(subscriptionId = id).toEntity() })
        }
        subs.update(
            entity.copy(
                lastUpdatedAt = System.currentTimeMillis(),
                nodeCount = parsed.nodes.size,
            ),
        )
        return parsed
    }

    suspend fun getAllNodes(): List<ProxyNode> = nodes.getAll().map { it.toModel() }

    suspend fun getAllSubscriptions(): List<Subscription> = subs.getAll().map { it.toModel() }

    suspend fun updateLatency(id: Long, latencyMs: Long?) {
        nodes.updateLatency(id, latencyMs)
    }

    suspend fun deleteNode(id: Long) {
        nodes.deleteById(id)
    }
}

