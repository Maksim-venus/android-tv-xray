package com.passwall.corexray

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
    private val _status = MutableStateFlow(XrayStatus(coreVersion = versionOrUnknown()))
    override val status: StateFlow<XrayStatus> = _status.asStateFlow()

    private val callback = object : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(p0: Long, p1: String?): Long {
            Log.i(TAG, "core: $p1")
            return 0
        }
    }

    @Volatile
    private var controller: CoreController? = null

    @Synchronized
    override fun start(config: GeneratedConfig, configFile: File, tun: ParcelFileDescriptor?) {
        stop()
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
        val core = Libv2ray.newCoreController(callback)
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
            DelayResult(false, error = t.message ?: t.javaClass.simpleName)
        }
    }

    companion object {
        private const val TAG = "passwall-xray"
        fun versionOrUnknown(): String =
            runCatching { Libv2ray.checkVersionX() }.getOrDefault("libv2ray")
    }
}
