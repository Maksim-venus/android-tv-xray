package com.passwall.adminweb

import com.passwall.data.log.RuntimeLogEntry
import kotlinx.serialization.Serializable

@Serializable
data class StatusDto(
    val running: Boolean,
    val usingStub: Boolean,
    val selectedNodeId: Long? = null,
    val selectedNodeName: String? = null,
    val allowInsecureSsl: Boolean,
    val httpEditEnabled: Boolean,
    val lanUrl: String,
    val version: String = "0.1.3",
    val message: String = "",
    val routingAssetsUpdatedAt: Long? = null,
    val lastError: String? = null,
    val lastErrorAt: Long? = null,
)

@Serializable
data class LogsDto(
    val items: List<RuntimeLogEntry>,
    val latestError: RuntimeLogEntry? = null,
)

@Serializable
data class NodeDto(
    val id: Long,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val latencyMs: Long? = null,
    val online: Boolean = true,
    val selected: Boolean = false,
    val source: String = "MANUAL",
)

@Serializable
data class SubscriptionDto(
    val id: Long,
    val name: String,
    val url: String,
    val enabled: Boolean,
    val lastUpdatedAt: Long? = null,
    val nodeCount: Int = 0,
)

@Serializable
data class ImportRequest(val text: String)

@Serializable
data class ImportResponse(
    val imported: Int,
    val errors: List<String> = emptyList(),
)

@Serializable
data class SubscriptionCreateRequest(
    val name: String,
    val url: String,
)

@Serializable
data class SettingsUpdateRequest(
    val allowInsecureSsl: Boolean? = null,
    val httpEditEnabled: Boolean? = null,
    val selectedNodeId: Long? = null,
)

@Serializable
data class PingDto(
    val nodeId: Long,
    val host: String,
    val port: Int,
    val ok: Boolean,
    val latencyMs: Long? = null,
    val error: String? = null,
)

@Serializable
data class ErrorDto(val error: String)

@Serializable
data class OkDto(val ok: Boolean = true, val id: Long? = null)
