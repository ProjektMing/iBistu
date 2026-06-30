package edu.bistu.cs4029.ibistu

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bistu.cs4029.ibistu.common.base.BaseActivity
import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.login.LoginResult
import edu.bistu.cs4029.ibistu.text.SplashScreen
import edu.bistu.cs4029.ibistu.common.ui.theme.IBistuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "iBistuMain"

class MainActivity : BaseActivity() {
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

    @Composable
    override fun Content() {
        IBistuApp()
    }
}

// ── 数据模型 ────────────────────────────────────────────────────

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

/** 教学周信息（从 getTermWeeks.do 获取） */
data class TermWeek(
    val weekNum: Int,      // serialNumber
    val startDate: String, // 如 "2025-09-01"
    val endDate: String,   // 如 "2025-09-07"
)

/** 应用状态 */
class AppState(context: Context) {
    val login = BistuLogin(context.applicationContext)
    var studentId by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoggingIn by mutableStateOf(false)
    var loginResult by mutableStateOf<LoginResult?>(null)
    var errorMsg by mutableStateOf("")
    var termName by mutableStateOf("")
    var courses by mutableStateOf<List<Course>>(emptyList())
    var isRestoring by mutableStateOf(true)  // 正在从 SQLite 恢复 session
    var showDebug by mutableStateOf(false)   // 连续点标题 5 次开启
    var currentWeek by mutableIntStateOf(1) // 当前选中的周次
    var weekRange by mutableStateOf(1..20)  // 所有课程涉及的最大周次范围
    var termWeeks by mutableStateOf<Map<Int, TermWeek>>(emptyMap()) // 周次→日期映射
}

// ── 网络请求 ────────────────────────────────────────────────────

/** 从教务系统获取课表数据，填充到 AppState */
private suspend fun fetchSchedule(state: AppState) {
    // 查当前学期
    val termJson = state.login.post(
        "https://jwxt.bistu.edu.cn/jwapp/sys/jwpubapp/modules/gg/cxmrxnxq.do",
        mapOf("CSDM" to "SYS", "ZCSDM" to "DQXNXQDM", "SFSY" to "1")
    )
    val rows = JSONObject(termJson)
        .getJSONObject("datas")
        .getJSONObject("cxmrxnxq")
        .getJSONArray("rows")
    val xnxqdm = rows.getJSONObject(0).getString("XNXQDM")
    val xnxqmc = rows.getJSONObject(0).getString("XNXQMC")
    state.termName = xnxqmc
    Log.d(TAG, "fetchSchedule: term=$xnxqmc")

    // 查教学周日期（getTermWeeks.do）
    try {
        Log.d(TAG, "fetchSchedule: calling getTermWeeks XNXQDM=$xnxqdm")
        val weeksJson = state.login.post(
            "https://jwxt.bistu.edu.cn/jwapp/sys/kbbpapp/api/schoolCalendar/getTermWeeks.do",
            mapOf("XNXQDM" to xnxqdm)
        )
        Log.d(TAG, "fetchSchedule: getTermWeeks raw=${weeksJson.take(500)}")

        val root = JSONObject(weeksJson)
        Log.d(TAG, "fetchSchedule: root keys=${root.keys().asSequence().toList()}")
        val datas = root.getJSONObject("datas")
        Log.d(TAG, "fetchSchedule: datas keys=${datas.keys().asSequence().toList()}")
        val weeksArr = datas.getJSONArray("getTermWeeks")
        Log.d(TAG, "fetchSchedule: rows length=${weeksArr.length()}")

        val map = mutableMapOf<Int, TermWeek>()
        for (i in 0 until weeksArr.length()) {
            val w = weeksArr.getJSONObject(i)
            val wn = w.optInt("serialNumber", 0)
            val sd = w.optString("startDate", "")
            val ed = w.optString("endDate", "")
            Log.d(TAG, "fetchSchedule: week[$i] serialNumber=$wn start=$sd end=$ed")
            if (wn > 0) {
                map[wn] = TermWeek(weekNum = wn, startDate = sd, endDate = ed)
            }
        }
        state.termWeeks = map
        Log.d(TAG, "fetchSchedule: termWeeks map=${map.mapKeys { it.key }.mapValues { "${it.value.startDate}~${it.value.endDate}" }}")
    } catch (e: Exception) {
        Log.w(TAG, "fetchSchedule: getTermWeeks FAILED: ${e.message}", e)
    }

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
    Log.d(TAG, "fetchSchedule: ${courses.size} courses loaded")

    // 计算周次范围并设置当前周（默认跳到最早有课的那一周）
    val range = ScheduleUtils.getWeekRange(courses)
    state.weekRange = range
    state.currentWeek = range.first
}

// ── 应用入口 ────────────────────────────────────────────────────

@Composable
fun IBistuApp() {
    val context = LocalContext.current
    val state = remember { AppState(context) }
    var currentTab by remember { mutableStateOf(AppDestinations.HOME) }
    val scope = rememberCoroutineScope()

    // 启动时从 SQLite 恢复 Cookie，尝试自动恢复登录
    LaunchedEffect(Unit) {
        try {
            state.login.restoreCookies()
        } catch (e: Exception) {
            Log.w(TAG, "restore failed: ${e.message}")
        }

        // 如果有已保存的 cookie，尝试直接用它们获取课表
        if (state.login.getAllCookies().isNotEmpty()) {
            Log.d(TAG, "auto-restoring session...")
            try {
                withContext(Dispatchers.IO) { fetchSchedule(state) }
                Log.d(TAG, "auto-restore success, ${state.courses.size} courses")
            } catch (e: Exception) {
                Log.w(TAG, "auto-restore failed: ${e.message}")
                state.login.clearAllCookies()
            }
        }
        state.isRestoring = false
    }

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

// ── 周课表页面（7 列网格布局） ─────────────────────────────────

/** 格式化日期字符串 "2026-06-29 00:00:00" → "6月29日" */
private fun formatDateRange(start: String, end: String): String {
    fun format(s: String): String {
        val dateOnly = s.substringBefore(" ")  // 去掉时间部分
        val parts = dateOnly.split("-")
        if (parts.size < 3) return s
        val month = parts[1].trimStart('0')
        val day = parts[2].trimStart('0')
        return "${month}月${day}日"
    }
    return "${format(start)} — ${format(end)}"
}

@Composable
fun HomePage(state: AppState) {
    // Debug: log termWeeks state on each recomposition
    LaunchedEffect(state.termWeeks, state.currentWeek) {
        Log.d(TAG, "HomePage: currentWeek=${state.currentWeek} termWeeks.size=${state.termWeeks.size}")
        val tw = state.termWeeks[state.currentWeek]
        Log.d(TAG, "HomePage: termWeeks[${state.currentWeek}]=${tw?.run { "$startDate~$endDate" } ?: "null"}")
    }

    if (state.courses.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请先在 Profile 中登录", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        // ── 学期名称 ──
        Text(
            text = state.termName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        // ── 周次导航 ──
        WeekNavigator(
            currentWeek = state.currentWeek,
            weekRange = state.weekRange,
            onPrev = { if (state.currentWeek > state.weekRange.first) state.currentWeek-- },
            onNext = { if (state.currentWeek < state.weekRange.last) state.currentWeek++ }
        )

        // ── 当前周日期 ──
        val tw = state.termWeeks[state.currentWeek]
        if (tw != null && tw.startDate.isNotBlank()) {
            Text(
                text = formatDateRange(tw.startDate, tw.endDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(8.dp))

        // ── 周课表（13 节时间表） ──
        WeeklyTimeTable(
            courses = state.courses,
            currentWeek = state.currentWeek
        )
    }
}

/** 周次导航栏：◀ 第 X 周 ▶ */
@Composable
private fun WeekNavigator(
    currentWeek: Int,
    weekRange: IntRange,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPrev,
            enabled = currentWeek > weekRange.first
        ) {
            Text("<", style = MaterialTheme.typography.titleLarge)
        }

        Text(
            text = "第 $currentWeek 周",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        IconButton(
            onClick = onNext,
            enabled = currentWeek < weekRange.last
        ) {
            Text(">", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** 13 节时间表网格（按节次行定位，课程纵向跨行显示时长） */
@Composable
private fun WeeklyTimeTable(courses: List<Course>, currentWeek: Int) {
    val rowHeight = 52.dp
    val sectionLabelWidth = 38.dp

    // 筛选当前周的课程
    val weekCourses = remember(courses, currentWeek) {
        courses.filter { ScheduleUtils.isCourseInWeek(it.week, currentWeek) }
    }

    if (weekCourses.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("本周无课", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    // 记录每个日期的哪些节次有课程 (dayOfWeek → set of sections)
    val starterMap = remember(weekCourses) {
        val map = mutableMapOf<Int, MutableMap<Int, Course>>()
        weekCourses.forEach { c ->
            map.getOrPut(c.dayOfWeek) { mutableMapOf() }[c.beginSection] = c
        }
        map
    }

    val occupied = remember(weekCourses) {
        val map = mutableMapOf<Int, MutableSet<Int>>()
        weekCourses.forEach { c ->
            val set = map.getOrPut(c.dayOfWeek) { mutableSetOf() }
            for (s in c.beginSection..c.endSection) set.add(s)
        }
        map
    }

    Column {
        // ── 星期头部行（左侧对齐节次标签宽度） ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(vertical = 6.dp)
        ) {
            Spacer(Modifier.width(sectionLabelWidth))
            for (day in 1..7) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        ScheduleUtils.dayLabels[day] ?: "?",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // ── 时间表主体 ──
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val totalWidth = maxWidth
            val dayWidth = (totalWidth - sectionLabelWidth) / 7

            // 背景：13 个节次行 + 分隔线
            Column(Modifier.fillMaxWidth()) {
                for (section in 1..13) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                    ) {
                        // 节次编号 + 时间
                        val info = sectionTimes.getOrNull(section - 1)
                        Box(
                            modifier = Modifier
                                .width(sectionLabelWidth)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$section",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (info != null) {
                                    Text(
                                        info.start,
                                        fontSize = 7.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        // 7 天占位列（带竖分隔线）
                        for (day in 1..7) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                // 右侧竖线（除最后一列）
                                if (day < 7) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(0.5.dp)
                                            .fillMaxHeight()
                                            .background(Color(0xFFE0E0E0))
                                    )
                                }
                            }
                        }
                    }

                    // 行间横分隔线
                    if (section < 13) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = Color(0xFFE0E0E0)
                        )
                    }
                }
            }

            // 前景：课程卡片绝对定位
            weekCourses.forEach { course ->
                val topOffset = (rowHeight + 0.5.dp) * (course.beginSection - 1).toFloat()
                val spanCount = course.endSection - course.beginSection + 1
                val cardHeight = (rowHeight + 0.5.dp) * spanCount.toFloat() - 2.dp
                val leftOffset = sectionLabelWidth + dayWidth * (course.dayOfWeek - 1).toFloat()
                val pad = 1.5.dp

                Box(
                    modifier = Modifier
                        .offset(x = leftOffset + pad, y = topOffset + pad)
                        .width(dayWidth - pad * 2)
                        .height(cardHeight)
                ) {
                    TimeTableCell(course)
                }
            }
        }
    }
}

/** 时间表中的课程卡片（纵向拉伸） */
@Composable
private fun TimeTableCell(course: Course) {
    val bgColor = courseColor(course.name.hashCode())

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 3.dp)
        ) {
            Text(
                text = course.name,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 11.sp
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = "${course.beginTime.take(5)}-${course.endTime.take(5)}",
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (course.classroom.isNotBlank()) {
                Text(
                    text = course.classroom,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── 节次时间映射（一天 13 节课） ──────────────────────────────

private data class SectionInfo(val number: Int, val start: String, val end: String)

/** 北京信息科技大学作息时间（典型值，可按需调整） */
private val sectionTimes = listOf(
    SectionInfo(1,  "08:00", "08:45"),
    SectionInfo(2,  "08:50", "09:35"),
    SectionInfo(3,  "09:55", "10:40"),
    SectionInfo(4,  "10:45", "11:30"),
    SectionInfo(5,  "11:35", "12:20"),
    SectionInfo(6,  "13:30", "14:15"),
    SectionInfo(7,  "14:20", "15:05"),
    SectionInfo(8,  "15:25", "16:10"),
    SectionInfo(9,  "16:15", "17:00"),
    SectionInfo(10, "17:05", "17:50"),
    SectionInfo(11, "18:30", "19:15"),
    SectionInfo(12, "19:20", "20:05"),
    SectionInfo(13, "20:10", "20:55"),
)

// ── 课程颜色映射 ───────────────────────────────────────────────

private val courseColors = listOf(
    Color(0xFFE3F2FD), // 浅蓝
    Color(0xFFE8F5E9), // 浅绿
    Color(0xFFFFF3E0), // 浅橙
    Color(0xFFF3E5F5), // 浅紫
    Color(0xFFE0F7FA), // 浅青
    Color(0xFFFFEBEE), // 浅红
    Color(0xFFF1F8E9), // 草绿
    Color(0xFFFFF8E1), // 浅黄
    Color(0xFFE8EAF6), // 靛蓝
    Color(0xFFFCE4EC), // 粉红
)

private fun courseColor(hash: Int): Color {
    val idx = (hash and Int.MAX_VALUE) % courseColors.size
    return courseColors[idx]
}

// ── Profile 页面（保持不变） ────────────────────────────────────

@Composable
fun ProfilePage(state: AppState, scope: kotlinx.coroutines.CoroutineScope) {
    val result = state.loginResult
    var debugTaps by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.isRestoring) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Spacer(Modifier.height(8.dp))
            Text("恢复登录中...", style = MaterialTheme.typography.bodyMedium)
            return
        }

        if (result != null && result.isSuccess || state.courses.isNotEmpty()) {
            Text("✅ 已登录", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                state.login.clearAllCookies()
                state.loginResult = null
                state.courses = emptyList()
                state.termName = ""
                state.studentId = ""
                state.password = ""
            }) { Text("退出") }
            return
        }

        Text(
            "iBistu 登录",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.clickable {
                debugTaps++
                if (debugTaps >= 5) {
                    state.showDebug = !state.showDebug
                    debugTaps = 0
                }
            }
        )
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
                                fetchSchedule(state)
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

        // Debug: dump DB to logcat (连续点标题 5 次开启)
        if (state.showDebug) {
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = {
                scope.launch {
                    try {
                        state.login.dumpDbToLog()
                        state.errorMsg = "DB dumped to logcat"
                    } catch (e: Exception) {
                        state.errorMsg = "dump failed: ${e.message}"
                    }
                }
            }) { Text("📋 Dump DB") }
        }
    }
}

@Composable
fun FavoritesPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("暂无收藏")
    }
}

enum class AppDestinations(val label: String, val icon: Int) {
    HOME("课表", R.drawable.ic_home),
    FAVORITES("收藏", R.drawable.ic_favorite),
    PROFILE("登录", R.drawable.ic_account_box),
}
