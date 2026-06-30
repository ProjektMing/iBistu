package edu.bistu.cs4029.ibistu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.login.LoginResult
import edu.bistu.cs4029.ibistu.text.SplashScreen
import edu.bistu.cs4029.ibistu.ui.theme.IBistuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IBistuTheme {
                var showSplash by rememberSaveable { mutableStateOf(true) }

                // 使用 Crossfade 包裹切换逻辑
                Crossfade(
                    targetState = showSplash,          // 监听这个状态的变化
                    animationSpec = tween(800)         // 动画时长 800 毫秒（你可以调为 500~1000）
                ) { isSplashVisible ->
                    if (isSplashVisible) {
                        SplashScreen(onTimeout = { showSplash = false })
                    } else {
                        IBistuApp()
                    }
                }
            }
        }
    }
}

/** 课表条目 */
data class Course(
    val name: String,
    val code: String,
    val credit: String,
    val teacher: String,
    val classroom: String,
    val campus: String,
    val week: String,
    val dayOfWeek: Int,
    val beginSection: Int,
    val endSection: Int,
    val beginTime: String,
    val endTime: String,
)

/** 应用状态 */
class AppState {
    val login = BistuLogin()
    var studentId by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoggingIn by mutableStateOf(false)
    var loginResult by mutableStateOf<LoginResult?>(null)
    var errorMsg by mutableStateOf("")
    var termName by mutableStateOf("")
    var courses by mutableStateOf<List<Course>>(emptyList())
}

@Composable
fun IBistuApp() {
    val state = remember { AppState() }
    var currentTab by remember { mutableStateOf(AppDestinations.HOME) }
    val scope = rememberCoroutineScope()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { dest ->
                item(
                    icon = { Icon(painterResource(dest.icon), contentDescription = dest.label) },
                    label = { Text(dest.label) },
                    selected = dest == currentTab,
                    onClick = { currentTab = dest }
                )
            }
        }
    ) {
        when (currentTab) {
            AppDestinations.HOME -> HomePage(state)
            AppDestinations.PROFILE -> ProfilePage(state, scope)
            AppDestinations.FAVORITES -> FavoritesPlaceholder()
        }
    }
}

@Composable
fun HomePage(state: AppState) {
    if (state.courses.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请先在 Profile 中登录", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(state.termName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(state.courses) { course ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(course.name, style = MaterialTheme.typography.titleSmall)
                            Text("${course.code}  ${course.credit}学分")
                            Text("周${dayLabel(course.dayOfWeek)} 第${course.beginSection}-${course.endSection}节  ${course.beginTime}-${course.endTime}")
                            Text("教师: ${course.teacher}  ${course.classroom}  ${course.campus}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfilePage(state: AppState, scope: kotlinx.coroutines.CoroutineScope) {
    val result = state.loginResult

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (result != null && result.isSuccess) {
            Text("✅ 已登录", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                state.loginResult = null
                state.courses = emptyList()
                state.termName = ""
            }) { Text("退出") }
            return
        }

        Text("iBistu 登录", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.studentId,
            onValueChange = { state.studentId = it; state.errorMsg = "" },
            label = { Text("学号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = { state.password = it; state.errorMsg = "" },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (state.studentId.isBlank() || state.password.isBlank()) {
                    state.errorMsg = "请输入学号和密码"
                    return@Button
                }
                state.isLoggingIn = true
                state.errorMsg = ""
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val r = state.login.fullLogin(state.studentId, state.password)
                            state.loginResult = r
                            if (r.isSuccess) {
                                // 查当前学期
                                val termJson = state.login.post(
                                    "https://jwxt.bistu.edu.cn/jwapp/sys/jwpubapp/modules/gg/cxmrxnxq.do",
                                    mapOf("CSDM" to "SYS", "ZCSDM" to "DQXNXQDM", "SFSY" to "1")
                                )
                                val xnxqdm = JSONObject(termJson)
                                    .getJSONObject("datas")
                                    .getJSONObject("cxmrxnxq")
                                    .getJSONArray("rows")
                                    .getJSONObject(0).getString("XNXQDM")
                                val xnxqmc = JSONObject(termJson)
                                    .getJSONObject("datas")
                                    .getJSONObject("cxmrxnxq")
                                    .getJSONArray("rows")
                                    .getJSONObject(0).getString("XNXQMC")
                                state.termName = xnxqmc

                                // 查课表
                                val scheduleJson = state.login.post(
                                    "https://jwxt.bistu.edu.cn/jwapp/sys/kbapp/api/wdkbcx/getMyScheduleDetail.do",
                                    mapOf("XNXQDM" to xnxqdm, "XQDM" to "10")
                                )
                                val list = JSONObject(scheduleJson)
                                    .getJSONObject("datas")
                                    .getJSONObject("getMyScheduleDetail")
                                    .getJSONArray("arrangedList")

                                val courses = mutableListOf<Course>()
                                for (i in 0 until list.length()) {
                                    val c = list.getJSONObject(i)
                                    courses.add(Course(
                                        name = c.getString("courseName"),
                                        code = c.getString("courseCode"),
                                        credit = c.getString("credit"),
                                        teacher = c.optString("weeksAndTeachers", ""),
                                        classroom = c.optString("placeName", ""),
                                        campus = c.optString("campusName", ""),
                                        week = c.optString("week", ""),
                                        dayOfWeek = c.optInt("dayOfWeek", 0),
                                        beginSection = c.optInt("beginSection", 0),
                                        endSection = c.optInt("endSection", 0),
                                        beginTime = c.optString("beginTime", ""),
                                        endTime = c.optString("endTime", ""),
                                    ))
                                }
                                state.courses = courses
                            } else {
                                state.errorMsg = r.message.ifBlank { "登录失败: code=${r.code}" }
                            }
                        }
                    } catch (e: Exception) {
                        state.errorMsg = "网络错误: ${e.message}"
                    } finally {
                        state.isLoggingIn = false
                    }
                }
            },
            enabled = !state.isLoggingIn,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isLoggingIn) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (state.isLoggingIn) "登录中..." else "登录")
        }

        if (state.errorMsg.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(state.errorMsg, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun FavoritesPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("暂无收藏")
    }
}

fun dayLabel(d: Int): String = when (d) {
    1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"
    5 -> "五"; 6 -> "六"; 7 -> "日"; else -> "?"
}

enum class AppDestinations(val label: String, val icon: Int) {
    HOME("课表", R.drawable.ic_home),
    FAVORITES("收藏", R.drawable.ic_favorite),
    PROFILE("登录", R.drawable.ic_account_box),
}
