package edu.bistu.cs4029.ibistu.common.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.bistu.cs4029.ibistu.common.preferences.AppPreferences
import edu.bistu.cs4029.ibistu.login.AppDatabase
import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.login.LoginResult
import edu.bistu.cs4029.ibistu.login.ProfileEntity
import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.ScheduleData
import edu.bistu.cs4029.ibistu.schedule.ScheduleUtils
import edu.bistu.cs4029.ibistu.schedule.TermWeek
import edu.bistu.cs4029.ibistu.settings.AutoMuteScheduler

/** 跨页面共享的应用状态。 */
class AppState(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = AppPreferences(appContext)
    private val appDb = AppDatabase.getInstance(appContext)
    val login = BistuLogin(appContext)

    // ── 登录状态 ──
    var studentId by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoggingIn by mutableStateOf(false)
    var loginResult by mutableStateOf<LoginResult?>(null)
    var errorMessage by mutableStateOf("")
    var isRestoring by mutableStateOf(true)
    var showDebug by mutableStateOf(false)

    // ── 课表数据 ──
    var termName by mutableStateOf("")
    var courses by mutableStateOf<List<Course>>(emptyList())
    var currentWeek by mutableIntStateOf(1)
    var weekRange by mutableStateOf(1..20)
    var termWeeks by mutableStateOf<Map<Int, TermWeek>>(emptyMap())

    // ── 设置 ──
    var autoMuteEnabled by mutableStateOf(prefs.isAutoMuteEnabled)

    // ── 个人资料（从 Room 数据库加载） ──
    var nickname by mutableStateOf("")
    var realName by mutableStateOf("")
    var className by mutableStateOf("")
    var avatarStyle by mutableIntStateOf(0)
    var avatarUri by mutableStateOf("")
    var avatarVersion by mutableIntStateOf(0)
    var gender by mutableIntStateOf(0)

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

    /** 登录成功后，按学号加载已保存的个人资料。 */
    suspend fun loadProfile(sid: String) {
        val entity = appDb.profileDao().getByStudentId(sid)
        if (entity != null) {
            nickname = entity.nickname
            realName = entity.realName
            className = entity.className
            avatarStyle = entity.avatarStyle
            avatarUri = entity.avatarUri
            gender = entity.gender
        } else {
            // 首次登录，用学号初始化
            nickname = ""
            realName = ""
            className = ""
            avatarStyle = 0
            avatarUri = ""
            gender = 0
        }
    }

    /** 将当前个人资料保存到 Room 数据库（按 studentId upsert）。 */
    suspend fun saveProfile() {
        val entity = ProfileEntity(
            studentId = studentId,
            nickname = nickname,
            realName = realName,
            className = className,
            avatarStyle = avatarStyle,
            avatarUri = avatarUri,
            gender = gender
        )
        appDb.profileDao().insert(entity)
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

    /** 登录成功后保存学号，用于下次启动时加载个人资料。 */
    fun onLoginSuccess() {
        prefs.savedStudentId = studentId
    }

    /** 返回上次登录保存的学号（用于会话恢复）。 */
    fun getSavedStudentId(): String = prefs.savedStudentId

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
        // 保留 savedStudentId 和 Room 中的个人资料，下次同一学号登录时自动恢复
    }
}
