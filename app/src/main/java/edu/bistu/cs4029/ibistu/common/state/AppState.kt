package edu.bistu.cs4029.ibistu.common.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.bistu.cs4029.ibistu.common.preferences.AppPreferences
import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.login.LoginResult
import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.ScheduleData
import edu.bistu.cs4029.ibistu.schedule.ScheduleUtils
import edu.bistu.cs4029.ibistu.schedule.TermWeek
import edu.bistu.cs4029.ibistu.login.AppDatabase
import edu.bistu.cs4029.ibistu.schedule.CachedScheduleRepository
import edu.bistu.cs4029.ibistu.settings.AutoMuteScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 跨页面共享的应用状态。 */
class AppState(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = AppPreferences(appContext)
    val login = BistuLogin(appContext)
    val scheduleRepo by lazy { CachedScheduleRepository(AppDatabase.getInstance(appContext)) }

    var studentId by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoggingIn by mutableStateOf(false)
    var loginResult by mutableStateOf<LoginResult?>(null)
    var errorMessage by mutableStateOf("")
    var termName by mutableStateOf("")
    var courses by mutableStateOf<List<Course>>(emptyList())
    var currentWeek by mutableIntStateOf(1)
    var weekRange by mutableStateOf(1..20)
    var termWeeks by mutableStateOf<Map<Int, TermWeek>>(emptyMap())
    var isRestoring by mutableStateOf(true)
    var showDebug by mutableStateOf(false)
    var autoMuteEnabled by mutableStateOf(prefs.isAutoMuteEnabled)

    fun applySchedule(schedule: ScheduleData) {
        termName = schedule.termName
        courses = schedule.courses
        termWeeks = schedule.termWeeks
        weekRange = ScheduleUtils.getWeekRange(schedule.courses)
        currentWeek = weekRange.first

        // 如果自动静音已开启，用新课表重新调度
        if (autoMuteEnabled && courses.isNotEmpty()) {
            AutoMuteScheduler.schedule(appContext, courses, termWeeks)
        }
    }

    /** 设置自动静音开关并同步调度闹钟。 */
    fun toggleAutoMute(enabled: Boolean) {
        autoMuteEnabled = enabled
        prefs.isAutoMuteEnabled = enabled
        if (enabled && courses.isNotEmpty()) {
            AutoMuteScheduler.schedule(appContext, courses, termWeeks)
        } else if (!enabled) {
            AutoMuteScheduler.cancelAll(appContext)
        }
    }

    fun clearSession() {
        // 先取消自动静音闹钟
        if (autoMuteEnabled) {
            AutoMuteScheduler.cancelAll(appContext)
        }

        login.clearAllCookies()
        loginResult = null
        courses = emptyList()
        termWeeks = emptyMap()
        termName = ""
        currentWeek = 1
        weekRange = 1..20
        studentId = ""
        password = ""
        errorMessage = ""
        // 清除课表缓存（异步）
        CoroutineScope(Dispatchers.IO).launch {
            scheduleRepo.clearCache()
        }
    }
}
