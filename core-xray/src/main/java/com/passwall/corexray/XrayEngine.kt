package com.passwall.corexray

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
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
    val nativeAvailable: Boolean = false,
    val lastError: String? = null,
    val usingStub: Boolean = true,
)

interface XrayEngine {
    val status: StateFlow<XrayStatus>
    fun start(config: GeneratedConfig, configFile: File, tun: ParcelFileDescriptor?)
    fun stop()
    fun isRunning(): Boolean
}

/**
 * In-process stub used until libxray / AndroidLibXrayLite is dropped in.
 *
 * It writes the generated JSON, reports RUNNING, and drains the TUN fd so the
 * VpnService interface does not back-pressure. Packets are NOT forwarded.
 *
 * JNI TODO: replace [start]/[stop] with gomobile bindings, e.g.
 * `libv2ray.Libv2ray.runXray(configPath)` or `CoreController.StartLoop`.
 */
class StubXrayEngine : XrayEngine {
    private val running = AtomicBoolean(false)
    private val _status = MutableStateFlow(XrayStatus())
    override val status: StateFlow<XrayStatus> = _status.asStateFlow()

    @Volatile
    private var drainThread: Thread? = null

    override fun start(config: GeneratedConfig, configFile: File, tun: ParcelFileDescriptor?) {
        stop()
        _status.value = XrayStatus(state = XrayState.STARTING, message = "正在写入配置…", usingStub = true)
        configFile.parentFile?.mkdirs()
        configFile.writeText(config.json)
        running.set(true)
        if (tun != null) {
            drainThread = Thread({ drainTun(tun) }, "passwall-tun-drain").also { it.start() }
        }
        _status.value = XrayStatus(
            state = XrayState.RUNNING,
            message = "Xray 引擎为本地 Stub（尚未加载 libxray）。配置已生成。",
            configPath = configFile.absolutePath,
            nativeAvailable = NativeXrayBridge.isAvailable(),
            usingStub = true,
        )
    }

    override fun stop() {
        running.set(false)
        drainThread?.interrupt()
        drainThread = null
        _status.value = XrayStatus(
            state = XrayState.STOPPED,
            message = "已停止",
            nativeAvailable = NativeXrayBridge.isAvailable(),
            usingStub = true,
        )
    }

    override fun isRunning(): Boolean = running.get() && _status.value.state == XrayState.RUNNING

    private fun drainTun(tun: ParcelFileDescriptor) {
        // TODO(jni): hand this fd to tun2socks / libxray instead of discarding packets.
        try {
            FileInputStream(tun.fileDescriptor).use { input ->
                val buf = ByteArray(32767)
                while (running.get()) {
                    val n = input.read(buf)
                    if (n <= 0) break
                }
            }
        } catch (_: Throwable) {
            // Closed when VpnService tears down the interface.
        }
    }
}

/**
 * Optional JNI boundary. The default APK does not ship a compiled .so.
 * After you drop in `libv2ray.aar` (see README), call these methods from [XrayEngine].
 */
object NativeXrayBridge {
    private val loaded: Boolean by lazy {
        runCatching { System.loadLibrary("xray_stub") }.isSuccess
    }

    fun isAvailable(): Boolean = loaded

    @JvmStatic
    external fun nativeVersion(): String

    @JvmStatic
    external fun nativeStart(configPath: String): Int

    @JvmStatic
    external fun nativeStop(): Int
}

class NativeXrayEngine(
    private val fallback: XrayEngine = StubXrayEngine(),
) : XrayEngine {
    override val status: StateFlow<XrayStatus> get() = fallback.status

    override fun start(config: GeneratedConfig, configFile: File, tun: ParcelFileDescriptor?) {
        if (!NativeXrayBridge.isAvailable()) {
            fallback.start(config, configFile, tun)
            return
        }
        configFile.parentFile?.mkdirs()
        configFile.writeText(config.json)
        val code = runCatching { NativeXrayBridge.nativeStart(configFile.absolutePath) }.getOrDefault(-1)
        if (code != 0) {
            fallback.start(config, configFile, tun)
        }
    }

    override fun stop() {
        if (NativeXrayBridge.isAvailable()) {
            runCatching { NativeXrayBridge.nativeStop() }
        }
        fallback.stop()
    }

    override fun isRunning(): Boolean = fallback.isRunning()
}
