package com.passwall.corexray

import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.passwall.data.log.RuntimeLog
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

enum class XrayState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR,
}

data class XrayStatus(
    val state: XrayState = XrayState.STOPPED,
    val message: String = "",
    val configPath: String? = null,
    val nativeAvailable: Boolean = true,
    val lastError: String? = null,
    val usingStub: Boolean = false,
    val coreVersion: String = "",
)

data class DelayResult(
    val ok: Boolean,
    val latencyMs: Long? = null,
    val error: String? = null,
)

interface XrayEngine {
    val status: StateFlow<XrayStatus>
    fun start(config: GeneratedConfig, configFile: File, tun: ParcelFileDescriptor?)
    fun stop()
    fun isRunning(): Boolean
    fun measureProxyDelay(url: String = DEFAULT_PROBE_URL): DelayResult

    /**
     * Fired when this core stops without [stop]. [owner] lets a destroyed VpnService
     * ignore a clear from an older instance.
     */
    fun setOnUnexpectedStop(owner: Any, listener: () -> Unit)
    fun clearOnUnexpectedStop(owner: Any)
}

const val DEFAULT_PROBE_URL = "https://www.gstatic.com/generate_204"

/**
 * Real Xray-core via AndroidLibXrayLite (libv2ray.aar).
 *
 * startLoop(config, tunFd) sets xray.tun.fd; the generated JSON uses a `tun`
 * inbound so gVisor reads the VpnService descriptor. No drain-only stub.
 */
class Libv2rayEngine : XrayEngine {
    private val running = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val generation = java.util.concurrent.atomic.AtomicInteger(0)
    private val _status = MutableStateFlow(XrayStatus(coreVersion = versionOrUnknown()))
    override val status: StateFlow<XrayStatus> = _status.asStateFlow()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var controller: CoreController? = null

    @Volatile
    private var unexpectedStop: (() -> Unit)? = null

    @Volatile
    private var unexpectedStopOwner: Any? = null

    override fun setOnUnexpectedStop(owner: Any, listener: () -> Unit) {
        unexpectedStopOwner = owner
        unexpectedStop = listener
    }

    override fun clearOnUnexpectedStop(owner: Any) {
        if (unexpectedStopOwner === owner) {
            unexpectedStopOwner = null
            unexpectedStop = null
        }
    }

    @Synchronized
    override fun start(config: GeneratedConfig, configFile: File, tun: ParcelFileDescriptor?) {
        stop()
        val gen = generation.incrementAndGet()
        stopRequested.set(false)
        _status.value = XrayStatus(
            state = XrayState.STARTING,
            message = "正在启动 Xray…",
            usingStub = false,
            coreVersion = versionOrUnknown(),
        )
        if (tun == null) {
            throw IllegalStateException("VpnService TUN 未建立，无法启动 Xray")
        }
        configFile.parentFile?.mkdirs()
        // startLoop() sets the process env; also stamp the fd into JSON `env`
        // so Xray-core can attach the VpnService descriptor if either path is used.
        val json = XrayConfigGenerator.injectTunFd(config.json, tun.fd)
        configFile.writeText(json)
        val assetDir = configFile.parentFile?.absolutePath
            ?: throw IllegalStateException("缺少 Xray 资源目录")
        Libv2ray.initCoreEnv(assetDir, "")
        val core = Libv2ray.newCoreController(callbackFor(gen))
        controller = core
        try {
            core.startLoop(json, tun.fd)
        } catch (t: Throwable) {
            controller = null
            running.set(false)
            _status.value = XrayStatus(
                state = XrayState.ERROR,
                message = t.message ?: t.javaClass.simpleName,
                lastError = t.message,
                usingStub = false,
                coreVersion = versionOrUnknown(),
            )
            RuntimeLog.error("Xray 启动失败：${t.message ?: t.javaClass.simpleName}", "xray")
            throw t
        }
        // v26.1.13 sets IsRunning before core.Start() returns. If it is already
        // false, the instance died inside startLoop.
        if (!core.isRunning) {
            controller = null
            running.set(false)
            val message = "Xray 核心启动后立即退出"
            _status.value = XrayStatus(
                state = XrayState.ERROR,
                message = message,
                lastError = message,
                usingStub = false,
                coreVersion = versionOrUnknown(),
            )
            RuntimeLog.error(message, "xray")
            throw IllegalStateException(message)
        }
        running.set(true)
        _status.value = XrayStatus(
            state = XrayState.RUNNING,
            message = "Xray 已运行 ${versionOrUnknown()}",
            configPath = configFile.absolutePath,
            nativeAvailable = true,
            usingStub = false,
            coreVersion = versionOrUnknown(),
        )
        Log.i(TAG, "started ${versionOrUnknown()} tunFd=${tun.fd}")
        RuntimeLog.info("Xray 已运行 ${versionOrUnknown()} tunFd=${tun.fd}", "xray")
    }

    @Synchronized
    override fun stop() {
        stopRequested.set(true)
        generation.incrementAndGet()
        val core = controller
        controller = null
        running.set(false)
        if (core != null) {
            runCatching { core.stopLoop() }
        }
        _status.value = XrayStatus(
            state = XrayState.STOPPED,
            message = "已停止",
            nativeAvailable = true,
            usingStub = false,
            coreVersion = versionOrUnknown(),
        )
    }

    override fun isRunning(): Boolean = running.get() && (controller?.isRunning == true)

    override fun measureProxyDelay(url: String): DelayResult {
        val core = controller
        if (core == null || !isRunning()) {
            return DelayResult(false, error = "代理未运行")
        }
        return try {
            val ms = core.measureDelay(url)
            if (ms >= 0) DelayResult(true, ms)
            else DelayResult(false, error = "探测失败")
        } catch (t: Throwable) {
            val message = t.message ?: t.javaClass.simpleName
            if (measureErrorMeansCoreGone(message)) {
                noteCoreGone(generation.get(), "measureDelay：$message")
            }
            DelayResult(false, error = message)
        }
    }

    private fun callbackFor(gen: Int) = object : CoreCallbackHandler {
        override fun startup(): Long {
            Log.i(TAG, "core startup gen=$gen")
            return 0
        }

        override fun shutdown(): Long {
            // This AAR's StopLoop does not call shutdown(). Hook it anyway so a
            // newer libv2ray that does exit the loop still wakes the watcher.
            noteCoreGone(gen, "shutdown 回调")
            return 0
        }

        override fun onEmitStatus(code: Long, message: String?): Long {
            val text = message?.trim().orEmpty()
            if (text.isNotEmpty()) Log.i(TAG, "core: $text")
            if (coreStatusMeansStopped(text)) noteCoreGone(gen, "核心状态：$text")
            return 0
        }
    }

    private fun noteCoreGone(gen: Int, reason: String) {
        if (generation.get() != gen || stopRequested.get()) return
        if (!running.compareAndSet(true, false)) return
        controller = null
        _status.value = XrayStatus(
            state = XrayState.ERROR,
            message = "Xray 核心已退出",
            lastError = reason,
            usingStub = false,
            coreVersion = versionOrUnknown(),
        )
        RuntimeLog.error("Xray 核心已退出：$reason", "xray")
        val listener = unexpectedStop
        mainHandler.post { listener?.invoke() }
    }

    companion object {
        private const val TAG = "passwall-xray"

        internal fun coreStatusMeansStopped(message: String): Boolean {
            val text = message.trim().lowercase()
            return text == "core stopped" || text.contains("core stopped")
        }

        internal fun measureErrorMeansCoreGone(message: String): Boolean {
            val text = message.lowercase()
            return text.contains("core instance is nil") || text.contains("core is not running")
        }
        fun versionOrUnknown(): String =
            runCatching { Libv2ray.checkVersionX() }.getOrDefault("libv2ray")
    }
}
