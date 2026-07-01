package edu.bistu.cs4029.ibistu.common.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.login.LoginResult
import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.ScheduleData
import edu.bistu.cs4029.ibistu.schedule.ScheduleUtils
import edu.bistu.cs4029.ibistu.schedule.TermWeek

/** 跨页面共享的应用状态。 */
class AppState(context: Context) {
    val login = BistuLogin(context.applicationContext)

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

    fun applySchedule(schedule: ScheduleData) {
        termName = schedule.termName
        courses = schedule.courses
        termWeeks = schedule.termWeeks
        weekRange = ScheduleUtils.getWeekRange(schedule.courses)
        currentWeek = weekRange.first
    }

    fun clearSession() {
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
    }
}
