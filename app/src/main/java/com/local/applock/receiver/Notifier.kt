package com.local.applock.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.local.applock.MainActivity
import com.local.applock.R

/**
 * 通知辅助。集中管理通知渠道与发送。
 */
object Notifier {

    private const val CHANNEL = "applock_status"
    private const val ID_ACCESSIBILITY_OFF = 1

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "服务状态", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun notifyAccessibilityOff(ctx: Context) {
        ensureChannel(ctx)
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = android.app.PendingIntent.getActivity(
            ctx, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("定时应用锁已失效")
            .setContentText("无障碍服务被系统关闭，点此重新开启")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_ACCESSIBILITY_OFF, n)
        } catch (_: Exception) {
        }
    }
}
