package edu.bistu.cs4029.ibistu.common.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.login.LoginResult
import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.ScheduleData

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
    var isRestoring by mutableStateOf(true)
    var showDebug by mutableStateOf(false)

    fun applySchedule(schedule: ScheduleData) {
        termName = schedule.termName
        courses = schedule.courses
    }

    fun clearSession() {
        login.clearAllCookies()
        loginResult = null
        courses = emptyList()
        termName = ""
        studentId = ""
        password = ""
        errorMessage = ""
    }
}
