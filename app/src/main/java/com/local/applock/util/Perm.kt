package com.local.applock.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager

/**
 * 权限检查与跳转辅助。
 */
object Perm {

    /** 无障碍服务是否已启用。 */
    fun isAccessibilityEnabled(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabled.any { it.resolveInfo?.serviceInfo?.packageName == ctx.packageName }
    }

    /** 通知是否已授权（Android 13+）。 */
    fun isNotificationEnabled(ctx: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 33) return true
        return androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    /** 跳转到无障碍设置页（已定位到本 App）。 */
    fun openAccessibilitySettings(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    /** 跳转到本 App 的通知设置。 */
    fun openNotificationSettings(ctx: Context) {
        try {
            val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (_: Exception) {
            openAppSettings(ctx)
        }
    }

    /** 跳转到本 App 详情页（自启动、省电策略等入口）。 */
    fun openAppSettings(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }
}
