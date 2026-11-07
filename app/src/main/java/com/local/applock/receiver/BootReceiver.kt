package com.local.applock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.local.applock.util.Perm

/**
 * 监听开机、改时间、改时区、应用更新。
 * 只做一件事：无障碍服务被系统回收时发通知提醒。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (!Perm.isAccessibilityEnabled(context)) {
                    Notifier.notifyAccessibilityOff(context)
                }
            }
        }
    }
}
