package edu.bistu.cs4029.ibistu.common.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用偏好设置封装（基于 SharedPreferences）。
 * 持久化自动静音开关状态、解除静音时间戳和原始勿扰模式状态。
 */
class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 自动静音是否开启，默认 false。 */
    var isAutoMuteEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_MUTE, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_MUTE, value).apply()

    /** 启动时是否显示第二屏的一句话，默认 true。 */
    var showSplashGreeting: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SPLASH_GREETING, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SPLASH_GREETING, value).apply()

    var showCrazyThursdayReminder: Boolean
        get() = prefs.getBoolean(KEY_CRAZY_THURSDAY_REMINDER, true)
        set(value) = prefs.edit().putBoolean(KEY_CRAZY_THURSDAY_REMINDER, value).apply()
    /**
     * 解除静音的目标时间戳（毫秒），0 表示当前没有待解除的静音。
     * 用于处理课程重叠：每节课都会将解除时间延长 45 分钟。
     */
    var unmuteUntil: Long
        get() = prefs.getLong(KEY_UNMUTE_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_UNMUTE_UNTIL, value).apply()

    /**
     * 静音前系统原始的勿扰模式过滤级别。
     * 解除静音时恢复到此级别。
     * 默认值为 INTERRUPTION_FILTER_ALL (1)。
     */
    var savedInterruptionFilter: Int
        get() = prefs.getInt(KEY_SAVED_FILTER, 1) // INTERRUPTION_FILTER_ALL = 1
        set(value) = prefs.edit().putInt(KEY_SAVED_FILTER, value).apply()

    /** 课表快照 JSON，供 BroadcastReceiver 独立恢复闹钟使用。 */
    var scheduleSnapshot: String?
        get() = prefs.getString(KEY_SCHEDULE_SNAPSHOT, null)
        set(value) {
            if (value != null) {
                prefs.edit().putString(KEY_SCHEDULE_SNAPSHOT, value).apply()
            } else {
                prefs.edit().remove(KEY_SCHEDULE_SNAPSHOT).apply()
            }
        }

    /** 上次用户选择的学期名称，用于启动时恢复，默认空字符串。 */
    var selectedTermName: String
        get() = prefs.getString(KEY_SELECTED_TERM, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SELECTED_TERM, value).apply()

    /** 清除课表快照。 */
    fun clearScheduleSnapshot() {
        prefs.edit().remove(KEY_SCHEDULE_SNAPSHOT).apply()
    }

    companion object {
        private const val PREFS_NAME = "ibistu_prefs"
        private const val KEY_AUTO_MUTE = "auto_mute_enabled"
        private const val KEY_SHOW_SPLASH_GREETING = "show_splash_greeting"
        private const val KEY_CRAZY_THURSDAY_REMINDER = "crazy_thursday_reminder"
        private const val KEY_UNMUTE_UNTIL = "unmute_until"
        private const val KEY_SAVED_FILTER = "saved_interruption_filter"
        private const val KEY_SCHEDULE_SNAPSHOT = "schedule_snapshot"
        private const val KEY_SELECTED_TERM = "selected_term_name"
    }
}
