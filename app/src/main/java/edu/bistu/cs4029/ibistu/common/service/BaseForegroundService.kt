package edu.bistu.cs4029.ibistu.common.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

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

    /**
     * 前台服务类型。具体子类必须在 Manifest 中声明相同的 foregroundServiceType。
     */
    protected open fun getDeclaredForegroundServiceType(): Int =
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST

    /** 通知正文，子类可按业务需要覆盖。 */
    protected open fun getNotificationContentText(): String = getChannelName()

    /** Service 被系统回收后的重启策略。 */
    protected open fun getStartMode(): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        ServiceCompat.startForeground(
            this,
            getNotificationId(),
            notification,
            getDeclaredForegroundServiceType()
        )
        onWork()
        return getStartMode()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 子类在此启动前台任务 */
    protected open fun onWork() {}

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            getChannelId(),
            getChannelName(),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, getChannelId())
            .setContentTitle(getChannelName())
            .setContentText(getNotificationContentText())
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf(startId)
    }

    override fun onDestroy() {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
