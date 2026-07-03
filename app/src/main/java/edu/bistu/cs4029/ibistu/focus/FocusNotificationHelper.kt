package edu.bistu.cs4029.ibistu.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * 专注通知辅助类。
 * 在计时进行时发送系统常驻通知，显示当前待办和计时状态。
 */
object FocusNotificationHelper {
    private const val CHANNEL_ID = "focus_timer"
    private const val NOTIFICATION_ID = 1001

    /** 初始化通知渠道（只需调用一次）。 */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "专注计时",
            NotificationManager.IMPORTANCE_LOW  // 低优先级：不弹出打扰，只在通知栏显示
        ).apply {
            description = "专注计时进行中的常驻通知"
            setShowBadge(false)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    /** 发送或更新专注通知。 */
    fun notify(context: Context, taskName: String, timeText: String, isPaused: Boolean = false) {
        ensureChannel(context)
        val contentText = if (isPaused) "已暂停 — $timeText" else timeText
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("专注中：$taskName")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(!isPaused)  // 运行时不可滑动清除，暂停时可清除
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    /** 取消专注通知。 */
    fun cancel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            createChannel(context)
        }
    }
}
