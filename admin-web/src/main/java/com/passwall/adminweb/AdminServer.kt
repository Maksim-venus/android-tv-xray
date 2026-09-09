package com.passwall.adminweb

import android.content.Context
import com.passwall.data.repo.PasswallRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.delete
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicReference

class AdminServer(
    private val context: Context,
    private val repository: PasswallRepository,
    private val runtime: AdminRuntime,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = AtomicReference<ApplicationEngine?>(null)

    val isRunning: Boolean get() = engine.get() != null

    fun start(port: Int) {
        if (engine.get() != null) return
        val server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(CallLogging)
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
            }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorDto(cause.message ?: cause.javaClass.simpleName),
                    )
                }
            }
            routing {
                get("/") { call.respondBytes(asset("index.html"), ContentType.Text.Html) }
                get("/index.html") { call.respondBytes(asset("index.html"), ContentType.Text.Html) }
                get("/styles.css") { call.respondBytes(asset("styles.css"), ContentType.Text.CSS) }
                get("/app.js") {
                    call.respondBytes(asset("app.js"), ContentType.Application.JavaScript)
                }
                get("/favicon.ico") { call.respondText("", status = HttpStatusCode.NoContent) }

                route("/api") {
                    get("/status") {
                        val settings = repository.getSettings()
                        val selected = repository.getSelectedNode()
                        call.respond(
                            StatusDto(
                                running = runtime.isRunning(),
                                usingStub = runtime.usingStub(),
                                selectedNodeId = selected?.id,
                                selectedNodeName = selected?.name,
                                allowInsecureSsl = settings.allowInsecureSsl,
                                httpEditEnabled = settings.httpEditEnabled,
                                lanUrl = LanAddress.httpUrl(settings.httpPort),
                                message = runtime.statusMessage(),
                                routingAssetsUpdatedAt = settings.routingAssetsUpdatedAt,
                            ),
                        )
                    }
                    get("/nodes") {
                        val selectedId = repository.getSettings().selectedNodeId
                        call.respond(
                            repository.getAllNodes().map {
                                NodeDto(
                                    id = it.id,
                                    name = it.name,
                                    protocol = it.protocol.badge,
                                    host = it.host,
                                    port = it.port,
                                    latencyMs = it.latencyMs,
                                    online = it.online,
                                    selected = it.id == selectedId,
                                    source = it.source.name,
                                )
                            },
                        )
                    }
                    post("/nodes/import") {
                        val req = call.receive<ImportRequest>()
                        val result = repository.importLinks(req.text)
                        call.respond(ImportResponse(result.nodes.size, result.errors))
                    }
                    post("/nodes/{id}/select") {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("无效 id"))
                        repository.selectNode(id)
                        call.respond(OkDto())
                    }
                    post("/nodes/{id}/ping") {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("无效 id"))
                        val node = repository.getAllNodes().firstOrNull { it.id == id }
                            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorDto("节点不存在"))
                        val result = TcpPinger.ping(node)
                        if (result.ok) repository.updateLatency(id, result.latencyMs)
                        call.respond(result.toDto())
                    }
                    post("/nodes/latency") {
                        val results = repository.getAllNodes().map { node ->
                            val result = TcpPinger.ping(node)
                            if (result.ok) repository.updateLatency(node.id, result.latencyMs)
                            result.toDto()
                        }
                        call.respond(results)
                    }
                    post("/nodes/tcp-ping") {
                        val selected = repository.getSelectedNode()
                            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("未选择节点"))
                        val result = TcpPinger.ping(selected)
                        if (result.ok) repository.updateLatency(selected.id, result.latencyMs)
                        call.respond(result.toDto())
                    }
                    delete("/nodes/{id}") {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorDto("无效 id"))
                        repository.deleteNode(id)
                        call.respond(OkDto())
                    }
                    get("/subscriptions") {
                        call.respond(
                            repository.getAllSubscriptions().map {
                                SubscriptionDto(it.id, it.name, it.url, it.enabled, it.lastUpdatedAt, it.nodeCount)
                            },
                        )
                    }
                    post("/subscriptions") {
                        val req = call.receive<SubscriptionCreateRequest>()
                        val id = repository.addSubscription(req.name, req.url)
                        call.respond(OkDto(id = id))
                    }
                    post("/subscriptions/{id}/refresh") {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("无效 id"))
                        val result = runCatching { repository.refreshSubscription(id) }
                            .getOrElse {
                                return@post call.respond(
                                    HttpStatusCode.NotImplemented,
                                    ErrorDto(it.message ?: "订阅拉取为 Stub"),
                                )
                            }
                        call.respond(ImportResponse(result.nodes.size, result.errors))
                    }
                    delete("/subscriptions/{id}") {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorDto("无效 id"))
                        repository.deleteSubscription(id)
                        call.respond(OkDto())
                    }
                    put("/settings") {
                        val req = call.receive<SettingsUpdateRequest>()
                        req.allowInsecureSsl?.let { repository.setAllowInsecure(it) }
                        req.httpEditEnabled?.let { repository.setHttpEditEnabled(it) }
                        req.selectedNodeId?.let { repository.selectNode(it) }
                        call.respond(OkDto())
                    }
                    post("/proxy/start") {
                        runtime.startProxy()
                        call.respond(OkDto())
                    }
                    post("/proxy/stop") {
                        runtime.stopProxy()
                        call.respond(OkDto())
                    }
                }
            }
        }
        engine.set(server)
        scope.launch { server.start(wait = true) }
    }

    fun stop() {
        val current = engine.getAndSet(null) ?: return
        runCatching { current.stop(1_000, 2_000) }
    }

    private fun asset(name: String): ByteArray =
        context.assets.open("admin/$name").use { it.readBytes() }
}

private fun PingResult.toDto() = PingDto(nodeId, host, port, ok, latencyMs, error)
