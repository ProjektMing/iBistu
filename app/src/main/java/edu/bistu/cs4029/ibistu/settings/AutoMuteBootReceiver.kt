package edu.bistu.cs4029.ibistu.settings

import android.content.Context
import android.content.Intent
import edu.bistu.cs4029.ibistu.common.preferences.AppPreferences
import edu.bistu.cs4029.ibistu.common.receiver.TemplateReceiver

/** 开机后通过短时后台 Service 恢复自动静音闹钟。 */
class AutoMuteBootReceiver : TemplateReceiver() {
    override fun onBootCompleted(context: Context) {
        val prefs = AppPreferences(context)
        if (!prefs.isAutoMuteEnabled && !prefs.isClassReminderEnabled) return
        context.startService(Intent(context, AutoMuteRescheduleService::class.java))
    }
}
