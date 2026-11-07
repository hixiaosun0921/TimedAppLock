package com.local.applock.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.local.applock.MainActivity
import com.local.applock.R
import com.local.applock.data.Rule
import com.local.applock.data.RuleStore
import java.time.ZonedDateTime

/**
 * 核心拦截服务。常驻，监听窗口状态变化。
 *
 * 拦截策略：命中即回桌面（performGlobalAction(GLOBAL_ACTION_HOME)）。
 * 不用遮罩层——遮罩会因 TYPE_ACCESSIBILITY_OVERLAY 的窗口焦点抖动导致"闪现"，
 * 且 FLAG_NOT_FOCUSABLE 会穿透返回键/手势。回桌面方案最干净：目标 App 一启动就被弹回，
 * 效果等同于"应用被隐藏、点不开"。
 *
 * 关键设计：
 * 1. 服务层常驻 Handler tick 兜底，保证"21:59 已在目标 App、22:00 到点"也能被踢出。
 * 2. 只监听 TYPE_WINDOW_STATE_CHANGED，避免事件风暴。
 * 3. 命中后加防抖，避免 HOME 之后的一串窗口事件反复触发 Toast/回桌面。
 */
class BlockService : AccessibilityService() {

    companion object {
        private const val TAG = "AppLock"
        private val IGNORED_PKGS = setOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.miui.home",
            "com.android.quicksearchbox",
            "com.miui.personalassistant",
            "miui.systemui.plugin"
        )
        const val ACTION_CHECK = "com.local.applock.ACTION_CHECK"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentFg: String? = null
    /** 上次弹回桌面的时间戳（毫秒），用于防抖。 */
    private var lastKickAt = 0L
    /** 上次被弹回的目标包名，用于避免同一包名连续重复弹。 */
    private var lastKickedPkg: String? = null

    /** 常驻 tick，每 30 秒跑一次。成本可忽略，无轮询网络/CPU 密集操作。 */
    private val tick = object : Runnable {
        override fun run() {
            reevaluate(currentFg, ZonedDateTime.now())
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        startForegroundCompat()
        handler.post(tick)
        Log.d(TAG, "服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val now = ZonedDateTime.now()

        // 自己包名的事件直接忽略
        if (pkg == packageName) return

        // 防抖：包名没变就跳过
        if (pkg == currentFg) return

        currentFg = pkg
        Log.d(TAG, "前台切换到: $pkg  ${now.hour}:${now.minute}:${now.second}")

        // 桌面/系统界面：不需要拦截，但重置防抖状态
        if (shouldIgnore(pkg)) {
            lastKickedPkg = null
            return
        }

        reevaluate(pkg, now)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 外部（如 BootReceiver、MainActivity）主动请求重算一次
        reevaluate(currentFg, ZonedDateTime.now())
        return START_STICKY
    }

    /**
     * 把服务提升为前台服务，挂常驻通知。
     * HyperOS 的 SmartPower 会把普通后台无障碍服务判为 idle 并结束，
     * 前台服务优先级高，不会被杀。
     */
    private fun startForegroundCompat() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    "applock_fg",
                    "定时应用锁",
                    NotificationManager.IMPORTANCE_MIN
                )
            )
            val pi = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(this, "applock_fg")
                .setContentTitle("定时应用锁运行中")
                .setContentText("正在保护你锁定的应用")
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
            startForeground(1001, n)
            Log.d(TAG, "已启动前台服务")
        } catch (e: Exception) {
            Log.e(TAG, "前台服务启动失败: ${e.message}")
        }
    }

    private fun shouldIgnore(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg in IGNORED_PKGS) return true
        if (pkg.contains("inputmethod", ignoreCase = true)) return true
        if (pkg.contains("ime", ignoreCase = true) && pkg.contains("keyboard", ignoreCase = true)) return true
        return false
    }

    private fun reevaluate(pkg: String?, now: ZonedDateTime) {
        val rules = RuleStore.activeRules(this, now)
        if (pkg != null && rules.any { it.pkg == pkg }) {
            // 命中规则
            kickHome(pkg, rules.first { it.pkg == pkg })
        }
    }

    /**
     * 弹回桌面。带防抖：同一包名在 1.5 秒内只弹一次。
     *
     * performGlobalAction(GLOBAL_ACTION_HOME) 是标准 API，不受 Android 10+ 后台启动
     * Activity 限制（startActivity 从无障碍服务里可能被静默忽略）。这里先用 HOME，
     * 失败再补 startActivity 兜底。
     */
    private fun kickHome(pkg: String, rule: Rule) {
        val nowMs = System.currentTimeMillis()
        if (pkg == lastKickedPkg && nowMs - lastKickAt < 1500L) {
            Log.d(TAG, "防抖跳过: $pkg")
            return
        }
        lastKickedPkg = pkg
        lastKickAt = nowMs

        Log.d(TAG, "命中规则，弹回桌面: $pkg (${rule.label})")
        val ok = performGlobalAction(GLOBAL_ACTION_HOME)
        Log.d(TAG, "performGlobalAction(HOME) 返回: $ok")
        if (!ok) {
            // HOME 失败，尝试启动桌面 Intent 兜底
            try {
                val home = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(home)
                Log.d(TAG, "已启动桌面 Intent 兜底")
            } catch (e: Exception) {
                Log.e(TAG, "启动桌面也失败: ${e.message}")
            }
        }
    }
}
