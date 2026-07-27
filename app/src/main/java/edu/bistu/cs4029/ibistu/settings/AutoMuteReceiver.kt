package edu.bistu.cs4029.ibistu.settings

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import edu.bistu.cs4029.ibistu.common.preferences.AppPreferences
import edu.bistu.cs4029.ibistu.common.receiver.TemplateReceiver
import kotlin.math.max

/**
 * 处理自动静音相关的广播事件：
 * - ACTION_MUTE:     上课时间到，开启勿扰模式，并安排 45 分钟后解除
 * - ACTION_UNMUTE:   检查是否到达解除时间，是则恢复原始勿扰模式
 * - ACTION_RESCHEDULE: 每日凌晨重新计算并安排当天的课程闹钟
 */
class AutoMuteReceiver : TemplateReceiver() {

    override fun onUnhandledAction(context: Context, intent: Intent) {
        val prefs = AppPreferences(context)
        val nm = context.getSystemService(NotificationManager::class.java)

        when (intent.action) {
            ACTION_MUTE -> handleMute(context, prefs, nm)
            ACTION_UNMUTE -> handleUnmute(prefs, nm)
            ACTION_RESCHEDULE -> handleReschedule(context, prefs)
        }
    }

    /** 上课时间到：开启勿扰模式，计算并保存解除时间。 */
    private fun handleMute(
        context: Context,
        prefs: AppPreferences,
        nm: NotificationManager
    ) {
        if (!prefs.isAutoMuteEnabled) {
            Log.d(TAG, "MUTE ignored: auto-mute disabled")
            return
        }

        if (!nm.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "MUTE ignored: notification policy access not granted")
            return
        }

        val currentFilter = nm.currentInterruptionFilter
        if (currentFilter != NotificationManager.INTERRUPTION_FILTER_NONE) {
            prefs.savedInterruptionFilter = currentFilter
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            Log.d(TAG, "DND enabled (INTERRUPTION_FILTER_NONE)")
        }

        // 延长解除时间：取 max(已有解除时间, 现在 + 45 分钟)
        val muteDuration = 45 * 60 * 1000L
        val existingUnmute = prefs.unmuteUntil
        val newUnmute = max(existingUnmute, System.currentTimeMillis() + muteDuration)
        prefs.unmuteUntil = newUnmute

        // 安排解除闹钟
        scheduleUnmuteAlarm(context, newUnmute)
        Log.d(TAG, "Unmute scheduled at $newUnmute")
    }

    /** 检查是否可以解除静音。 */
    private fun handleUnmute(prefs: AppPreferences, nm: NotificationManager) {
        if (!prefs.isAutoMuteEnabled) {
            Log.d(TAG, "UNMUTE: auto-mute disabled, restoring filter")
            restoreFilter(nm, prefs)
            return
        }

        val unmuteUntil = prefs.unmuteUntil
        if (unmuteUntil == 0L) {
            Log.d(TAG, "UNMUTE ignored: no pending unmute")
            return
        }

        if (System.currentTimeMillis() >= unmuteUntil) {
            Log.d(TAG, "UNMUTE: time reached, restoring filter")
            restoreFilter(nm, prefs)
        } else {
            Log.d(TAG, "UNMUTE deferred: mute was extended by overlapping course")
        }
    }

    /** 恢复原始勿扰模式并清除状态。 */
    private fun restoreFilter(nm: NotificationManager, prefs: AppPreferences) {
        if (!nm.isNotificationPolicyAccessGranted) {
            prefs.unmuteUntil = 0L
            Log.w(TAG, "Restore skipped: notification policy access not granted")
            return
        }
        val saved = prefs.savedInterruptionFilter
        nm.setInterruptionFilter(saved)
        prefs.unmuteUntil = 0L
        Log.d(TAG, "Filter restored to $saved")
    }

    /** 重新调度（每日凌晨或开机触发）。 */
    private fun handleReschedule(context: Context, prefs: AppPreferences) {
        if (!prefs.isAutoMuteEnabled) {
            Log.d(TAG, "Reschedule skipped: auto-mute disabled")
            return
        }
        Log.d(TAG, "Rescheduling auto-mute alarms")
        AutoMuteScheduler.reschedule(context)
    }

    companion object {
        private const val TAG = "AutoMuteReceiver"

        const val ACTION_MUTE = "edu.bistu.cs4029.ibistu.ACTION_MUTE"
        const val ACTION_UNMUTE = "edu.bistu.cs4029.ibistu.ACTION_UNMUTE"
        const val ACTION_RESCHEDULE = "edu.bistu.cs4029.ibistu.ACTION_RESCHEDULE"

        /** 安排解除静音的闹钟 */
        fun scheduleUnmuteAlarm(context: Context, triggerAtMillis: Long) {
            val alarm = context.getSystemService(AlarmManager::class.java) ?: return
            if (!alarm.canScheduleExactAlarms()) {
                Log.w(TAG, "Cannot schedule unmute alarm: exact alarm permission not granted")
                return
            }
            val intent = Intent(context, AutoMuteReceiver::class.java).apply {
                action = ACTION_UNMUTE
            }
            val pending = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_UNMUTE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }

        /** 取消已安排的解除静音闹钟。 */
        fun cancelUnmuteAlarm(context: Context) {
            val alarm = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, AutoMuteReceiver::class.java).apply {
                action = ACTION_UNMUTE
            }
            val pending = PendingIntent.getBroadcast(
                context, REQUEST_CODE_UNMUTE, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            ) ?: return
            alarm.cancel(pending)
            pending.cancel()
        }

        private const val REQUEST_CODE_UNMUTE = 9999
    }
}
