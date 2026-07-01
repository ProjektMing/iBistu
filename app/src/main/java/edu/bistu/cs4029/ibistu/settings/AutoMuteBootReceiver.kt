package edu.bistu.cs4029.ibistu.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import edu.bistu.cs4029.ibistu.common.preferences.AppPreferences

class AutoMuteBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = AppPreferences(context)
        if (!prefs.isAutoMuteEnabled) return
        AutoMuteScheduler.reschedule(context)
    }
}
