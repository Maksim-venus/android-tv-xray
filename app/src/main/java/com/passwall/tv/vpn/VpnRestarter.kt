package com.passwall.tv.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.SystemClock
import com.passwall.data.log.RuntimeLog
import com.passwall.tv.MainActivity
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Restores the tunnel when the user left it on.
 *
 * API 25 (当贝 / Android 7.1): there is no background-start ban. [VpnService.prepare]
 * returns null while the box still treats the VPN permission as granted, and
 * [android.net.VpnService.Builder.establish] can run from the restarted service.
 * After many OEM kills the permission is gone; prepare() then returns an Intent
 * that only an Activity can show. We log that, toast in Chinese, and open the UI
 * at most once every few minutes.
 *
 * Force-stop from system settings cancels alarms and blocks START_STICKY until the
 * user opens the app. Opening Passwall still attempts one restore.
 */
internal object VpnRestarter {
    const val ACTION_REQUEST_VPN_CONSENT = "com.passwall.tv.vpn.REQUEST_CONSENT"

    private val gate = AtomicBoolean(false)
    private val gateAt = AtomicLong(0L)

    fun onServiceSettled() {
        gate.set(false)
    }

    fun onWake(context: Context, reason: String) {
        val app = context.applicationContext
        if (reason == "boot" || reason == "updated") {
            VpnPersist.setNextAlarmElapsed(app, 0L)
        }
        if (!VpnPersist.isWanted(app)) {
            VpnWatchdog.cancel(app)
            return
        }
        if (VpnPersist.isExhausted(app)) {
            VpnWatchdog.cancel(app)
            RuntimeLog.error(
                "自动重连已暂停（连续失败过多）。请打开 Passwall 手动点「启动」。当贝盒子请把应用加入自启动或电池白名单。",
                "vpn",
            )
            ProxyRuntime.toast(app, "自动重连已停止，请手动启动")
            return
        }
        tryRestore(context, reason, consentLauncher = null)
    }

    fun tryRestore(
        context: Context,
        reason: String,
        consentLauncher: ((Intent) -> Unit)?,
    ) {
        val app = context.applicationContext
        if (!VpnPersist.isWanted(app) || VpnPersist.isExhausted(app)) return
        if (ProxyRuntime.isRunning.value || ProxyRuntime.serviceAcceptedStart) {
            VpnWatchdog.scheduleHealthy(app)
            return
        }
        val prepare = runCatching { VpnService.prepare(app) }.getOrNull()
        if (prepare != null) {
            onConsentNeeded(context, prepare, consentLauncher)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!gate.compareAndSet(false, true)) {
            if (now - gateAt.get() < 20_000L) {
                RuntimeLog.info("自动重连已在进行（$reason）", "vpn")
                return
            }
        }
        gateAt.set(now)
        RuntimeLog.warn("代理未在运行，开始自动重连（$reason）", "vpn")
        val started = ProxyRuntime.startService(app, restore = true)
        if (!started) {
            gate.set(false)
            planRetry(app, "无法启动服务", retryable = true)
        }
    }

    fun onConsentNeeded(
        context: Context,
        prepare: Intent,
        consentLauncher: ((Intent) -> Unit)?,
    ) {
        val app = context.applicationContext
        if (!VpnPersist.isWanted(app)) return
        val (state, counted) = VpnPersist.recordHandledFailure(app, System.currentTimeMillis())
        if (counted) {
            RuntimeLog.error(
                "自动重连需要重新授予 VPN 权限（${state.failures}/${VpnRetryPolicy.MAX_FAILURES}）。" +
                    "当贝等盒子杀掉进程后常会再次弹出系统授权，应用无法代替你点允许。",
                "vpn",
            )
            ProxyRuntime.markError("需要重新允许 VPN")
            val nowElapsed = SystemClock.elapsedRealtime()
            if (VpnPersist.shouldToast(app, nowElapsed)) {
                VpnPersist.markToasted(app, nowElapsed)
                ProxyRuntime.toast(app, "代理已断开，请重新允许 VPN")
            }
            if (state.exhausted) {
                VpnWatchdog.cancel(app)
                RuntimeLog.error("自动重连已停止：系统反复要求重新授权 VPN。请手动点「启动」并允许。", "vpn")
                ProxyRuntime.toast(app, "自动重连已停止，请手动启动")
            } else {
                val delay = VpnRetryPolicy.delayFor(state.failures)
                VpnWatchdog.schedule(app, delay, "等待重新授权")
            }
        }
        if (VpnPersist.isExhausted(app)) return
        if (consentLauncher != null) {
            ProxyRuntime.markMessage("请允许 VPN 权限", autoClear = true)
            consentLauncher(prepare)
            return
        }
        val nowElapsed = SystemClock.elapsedRealtime()
        if (VpnPersist.shouldPromptConsentUi(app, nowElapsed)) {
            VpnPersist.markConsentUiPrompted(app, nowElapsed)
            launchConsentUi(app)
        }
    }

    fun onConsentDenied(context: Context) {
        val app = context.applicationContext
        ProxyRuntime.markError("未授予 VPN 权限，无法自动重连")
        ProxyRuntime.toast(context, "未允许 VPN，稍后将再试")
        val state = VpnPersist.read(app)
        if (!state.wanted || state.exhausted) return
        // Showing the dialog already counted a failure and scheduled a retry.
        val delay = VpnRetryPolicy.delayFor(state.failures.coerceAtLeast(1))
        VpnWatchdog.scheduleIfSooner(app, delay, "未授予 VPN 权限")
    }

    fun planRetry(context: Context, message: String, retryable: Boolean) {
        val app = context.applicationContext
        if (!VpnPersist.isWanted(app)) return
        val (state, counted) = VpnPersist.recordHandledFailure(app, System.currentTimeMillis())
        if (!counted) return
        if (!retryable) VpnPersist.exhaust(app)
        val exhausted = !retryable || state.exhausted || VpnPersist.isExhausted(app)
        if (exhausted) {
            VpnWatchdog.cancel(app)
            RuntimeLog.error("自动重连已停止：$message。请手动点「启动」。", "vpn")
            ProxyRuntime.toast(app, "自动重连已停止，请手动启动")
            return
        }
        val delay = VpnRetryPolicy.delayFor(state.failures)
        VpnWatchdog.schedule(app, delay, "失败后重试")
        RuntimeLog.warn(
            "启动失败，${delay / 1000} 秒后重试（${state.failures}/${VpnRetryPolicy.MAX_FAILURES}）：$message",
            "vpn",
        )
    }

    fun onServiceDestroyed(context: Context, explicitStop: Boolean) {
        onServiceSettled()
        val state = VpnPersist.read(context)
        if (!VpnAttemptMachine.shouldAutoRestart(state, explicitStop)) return
        val delay = VpnRetryPolicy.delayFor(state.failures)
        if (VpnWatchdog.scheduleIfSooner(context, delay, "服务被系统结束")) {
            RuntimeLog.warn("VPN 服务已结束，${delay / 1000} 秒内尝试自动重连", "vpn")
        }
    }

    private fun launchConsentUi(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_REQUEST_VPN_CONSENT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try {
            context.startActivity(intent)
            RuntimeLog.info("已打开界面以重新申请 VPN 权限", "vpn")
        } catch (t: Throwable) {
            RuntimeLog.error(
                "无法打开 VPN 授权界面：${t.message ?: t.javaClass.simpleName}。请手动打开 Passwall 并点启动。",
                "vpn",
            )
        }
    }
}
