package edu.bistu.cs4029.ibistu.common.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 前台 Service 基类。
 *
 * 自动创建通知渠道并启动前台通知。
 * 适用于需要持久运行的后台任务（如文件下载、数据同步）。
 *
 * 使用方式：
 * ```
 * class DownloadService : BaseForegroundService() {
 *     override fun getChannelId() = "download_channel"
 *     override fun getChannelName() = "下载服务"
 *     override fun getNotificationId() = 1001
 *
 *     override fun onWork() {
 *         scope.launch {
 *             // 前台任务
 *         }
 *     }
 * }
 * ```
 */
abstract class BaseForegroundService : Service() {

    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 通知渠道 ID */
    protected abstract fun getChannelId(): String

    /** 通知渠道名称 */
    protected abstract fun getChannelName(): String

    /** 通知 ID */
    protected abstract fun getNotificationId(): Int

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(getNotificationId(), notification)
        onWork()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 子类在此启动前台任务 */
    protected open fun onWork() {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                getChannelId(),
                getChannelName(),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, getChannelId())
            .setContentTitle(getChannelName())
            .setContentText("服务运行中")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
