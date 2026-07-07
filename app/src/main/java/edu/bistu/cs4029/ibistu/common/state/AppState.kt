package edu.bistu.cs4029.ibistu.common.state

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.bistu.cs4029.ibistu.common.preferences.AppPreferences
import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.login.LoginResult
import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.EmptyClassroom
import edu.bistu.cs4029.ibistu.schedule.EmptyClassroomQuery
import edu.bistu.cs4029.ibistu.schedule.CampusCodes
import edu.bistu.cs4029.ibistu.schedule.Exam
import edu.bistu.cs4029.ibistu.schedule.ScheduleData
import edu.bistu.cs4029.ibistu.schedule.ScheduleUtils
import edu.bistu.cs4029.ibistu.schedule.TermOption
import edu.bistu.cs4029.ibistu.schedule.TermWeek
import edu.bistu.cs4029.ibistu.schedule.fetchEmptyClassrooms
import edu.bistu.cs4029.ibistu.login.AndroidLogger
import edu.bistu.cs4029.ibistu.login.AppDatabase
import edu.bistu.cs4029.ibistu.login.LoginDatabase
import edu.bistu.cs4029.ibistu.login.RoomCookieStorage
import edu.bistu.cs4029.ibistu.schedule.CachedScheduleRepository
import edu.bistu.cs4029.ibistu.schedule.CachedExamRepository
import edu.bistu.cs4029.ibistu.settings.AutoMuteScheduler
import edu.bistu.cs4029.ibistu.widget.ScheduleWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 跨页面共享的应用状态。 */
class AppState(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = AppPreferences(appContext)
    val login = BistuLogin(
        RoomCookieStorage(LoginDatabase.getInstance(appContext).cookieDao()),
        AndroidLogger("iBistuLogin")
    )
    val scheduleRepo by lazy { CachedScheduleRepository(AppDatabase.getInstance(appContext)) }
    val examRepo by lazy { CachedExamRepository(AppDatabase.getInstance(appContext)) }

    var studentId by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoggingIn by mutableStateOf(false)
    var loginResult by mutableStateOf<LoginResult?>(null)
    var isLoggedIn by mutableStateOf(false)
    var errorMessage by mutableStateOf("")
    var termName by mutableStateOf("")
    var termCode by mutableStateOf("")
    /** 用户从下拉框选择的学期（持久化），默认空字符串。 */
    var selectedTermName by mutableStateOf(prefs.selectedTermName)
    /** 所有可选学期列表。 */
    var termOptions by mutableStateOf<List<TermOption>>(emptyList())
    /** 切换学期时是否正在加载课表。 */
    var isLoadingTerm by mutableStateOf(false)
    var courses by mutableStateOf<List<Course>>(emptyList())
    var currentWeek by mutableIntStateOf(1)
    var weekRange by mutableStateOf(1..20)
    var termWeeks by mutableStateOf<Map<Int, TermWeek>>(emptyMap())
    var isRestoring by mutableStateOf(true)
    var showDebug by mutableStateOf(false)
    var autoMuteEnabled by mutableStateOf(prefs.isAutoMuteEnabled)
    var showSplashGreeting by mutableStateOf(prefs.showSplashGreeting)
    var showCrazyThursdayReminder by mutableStateOf(prefs.showCrazyThursdayReminder)

    var exams by mutableStateOf<List<Exam>>(emptyList())
    var showExamPage by mutableStateOf(false)

    // ── 空教室查询 ──────────────────────────────────────────
    /** 长按位置对应的查询描述文本（如 "周一 第3-4节 09:50-11:30"）。 */
    var queryContextText by mutableStateOf("")
    /** 是否正在查询空教室。 */
    var isLoadingEmptyClassrooms by mutableStateOf(false)
    /** 查询到的空教室列表。 */
    var emptyClassrooms by mutableStateOf<List<EmptyClassroom>>(emptyList())
    /** 是否显示空教室结果 BottomSheet。 */
    var showEmptyClassroomSheet by mutableStateOf(false)
    /** 空教室查询的错误信息。 */
    var emptyClassroomError by mutableStateOf("")
    /** 查询位置是否为上课时段（有课程占据）。 */
    var isClassTimeQuery by mutableStateOf(false)
    private var emptyClassroomQueryJob: Job? = null
    private var emptyClassroomQueryGeneration = 0L

    fun applySchedule(schedule: ScheduleData) {
        termCode = schedule.termCode
        termName = schedule.termName
        courses = schedule.courses
        // 仅在获取到有效数据时才更新教学周日期，避免网络瞬时失败覆盖缓存
        if (schedule.termWeeks.isNotEmpty()) {
            termWeeks = schedule.termWeeks
        }
        exams = emptyList()
        // 周范围：使用学期实际周数（来自 termWeeks）和课程数据的较大值
        val courseMaxWeek = courses.flatMap { ScheduleUtils.getCourseWeeks(it.week) }.maxOrNull() ?: 0
        val termMaxWeek = termWeeks.keys.maxOrNull() ?: 0
        val maxWeek = maxOf(courseMaxWeek, termMaxWeek, 1)
        val minWeek = minOf(
            courses.flatMap { ScheduleUtils.getCourseWeeks(it.week) }.minOrNull() ?: 1,
            termWeeks.keys.minOrNull() ?: 1
        )
        weekRange = minWeek..(maxWeek + 1)
        currentWeek = weekRange.first

        // 首次加载或用户未手动选择时，自动选中当前学期
        if (selectedTermName.isBlank()) {
            selectedTermName = schedule.termName
            prefs.selectedTermName = schedule.termName
        }

        // 如果自动静音已开启，用新课表重新调度
        if (autoMuteEnabled && courses.isNotEmpty()) {
            AutoMuteScheduler.schedule(appContext, courses, termWeeks)
        }

        ScheduleWidgetProvider.requestUpdate(appContext)

        // 课表就绪后，后台拉取学期列表供下拉框使用
        loadTermList()
    }

    /** 更新用户选择的学期，同时持久化到 SharedPreferences。 */
    fun selectTerm(term: String) {
        selectedTermName = term
        prefs.selectedTermName = term
    }

    /** 加载学期列表并缓存。若已加载则跳过。 */
    fun loadTermList() {
        if (termOptions.isNotEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val list = scheduleRepo.fetchTermList(login)
                withContext(Dispatchers.Main) {
                    termOptions = list
                }
            } catch (_: Exception) {
                // 网络不可用时不干扰已有 UI
            }
        }
    }

    /** 切换到指定学期：请求网络 → 更新课表 + 考试安排 UI。 */
    fun switchToTerm(targetTermCode: String, targetTermName: String) {
        if (targetTermCode == termCode) return
        isLoadingTerm = true
        selectTerm(targetTermName)
        // FIXME: 当选中学期无课表数据时，仅静默回到原学期，缺少用户可见提示
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fresh = scheduleRepo.fetchAndCache(login, targetTermCode)
                val freshExams = examRepo.fetchAndCache(login, targetTermCode)
                withContext(Dispatchers.Main) {
                    applySchedule(fresh)
                    exams = freshExams
                    isLoadingTerm = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoadingTerm = false
                }
            }
        }
    }

    /** 设置启动时是否显示第二屏的一句话。 */
    fun toggleSplashGreeting(enabled: Boolean) {
        showSplashGreeting = enabled
        prefs.showSplashGreeting = enabled
    }

    fun toggleCrazyThursdayReminder(enabled: Boolean) {
        showCrazyThursdayReminder = enabled
        prefs.showCrazyThursdayReminder = enabled
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
        emptyClassroomQueryGeneration++
        emptyClassroomQueryJob?.cancel()
        emptyClassroomQueryJob = null
        // 先取消自动静音闹钟
        if (autoMuteEnabled) {
            AutoMuteScheduler.cancelAll(appContext)
        }

        loginResult = null
        isLoggedIn = false
        courses = emptyList()
        termWeeks = emptyMap()
        termName = ""
        termCode = ""
        selectedTermName = ""
        termOptions = emptyList()
        isLoadingTerm = false
        currentWeek = 1
        weekRange = 1..20
        studentId = ""
        password = ""
        errorMessage = ""
        exams = emptyList()
        showExamPage = false
        emptyClassrooms = emptyList()
        isLoadingEmptyClassrooms = false
        showEmptyClassroomSheet = false
        queryContextText = ""
        emptyClassroomError = ""
        isClassTimeQuery = false
        CoroutineScope(Dispatchers.IO).launch {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                login.clearAllCookies()
                scheduleRepo.clearCache()
                examRepo.clearCache()
            } finally {
                ScheduleWidgetProvider.requestUpdate(appContext)
            }
        }
        }
    }
}
