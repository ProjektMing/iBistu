package edu.bistu.cs4029.ibistu.settings

import android.content.Context
import android.content.Intent
import edu.bistu.cs4029.ibistu.common.preferences.AppPreferences
import edu.bistu.cs4029.ibistu.common.receiver.TemplateReceiver
import edu.bistu.cs4029.ibistu.widget.ScheduleWidgetProvider

/** 开机后恢复桌面课表刷新，并在启用时恢复自动静音闹钟。 */
class AutoMuteBootReceiver : TemplateReceiver() {
    override fun onBootCompleted(context: Context) {
        ScheduleWidgetProvider.requestUpdate(context)
        val prefs = AppPreferences(context)
        if (!prefs.isAutoMuteEnabled) return
        context.startService(Intent(context, AutoMuteRescheduleService::class.java))
    }
}
