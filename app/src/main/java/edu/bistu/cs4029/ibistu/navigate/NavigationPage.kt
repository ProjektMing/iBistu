package edu.bistu.cs4029.ibistu.navigate

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bistu.cs4029.ibistu.common.state.AppState
import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.ScheduleUtils
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** 导航页面：显示最近课程的教室并提供地图导航。 */
@Composable
fun NavigationPage(state: AppState, modifier: Modifier = Modifier) {
    if (!state.isLoggedIn && state.courses.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("请先在「登录」中登录以加载课表", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    if (state.courses.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("已登录，正在加载课表或当前学期暂无课程", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val context = LocalContext.current
    var nearest by remember(state.courses, state.currentWeek) {
        mutableStateOf(findNearestCourse(state.courses, state.currentWeek))
    }

    LaunchedEffect(state.courses, state.currentWeek) {
        while (true) {
            nearest = findNearestCourse(state.courses, state.currentWeek)
            delay(60_000L)
        }
    }

    var showMapPicker by remember { mutableStateOf(false) }

    // 当最近课程状态不再是 CourseFound 时，自动关闭地图选择弹窗
    LaunchedEffect(nearest) {
        if (nearest !is NearestResult.CourseFound) {
            showMapPicker = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 标题
        Text(
            text = "教室导航",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        // 天气信息栏
        WeatherBar()
        Spacer(Modifier.height(12.dp))

        // 课程信息卡片
        when (nearest) {
            is NearestResult.NoCourse -> {
                NoCourseCard((nearest as NearestResult.NoCourse).message)
            }
            is NearestResult.CourseFound -> {
                CourseInfoCard(nearest as NearestResult.CourseFound)
                Spacer(Modifier.height(16.dp))
                NavigateButton(onClick = { showMapPicker = true })
            }
        }
    }

    // 地图选择弹窗
    if (showMapPicker) {
        val nearestCourse = (nearest as? NearestResult.CourseFound)?.course
        if (nearestCourse != null) {
            MapPickerDialog(
                context = context,
                buildingName = decodeBuildingName(nearestCourse.classroom),
                classroom = nearestCourse.classroom,
                onDismiss = { showMapPicker = false }
            )
        }
    }
}

// ─── 数据类 ─────────────────────────────────────────

private sealed class NearestResult {
    data class CourseFound(val course: Course, val status: CourseStatus) : NearestResult()
    data class NoCourse(val message: String) : NearestResult()
}

private enum class CourseStatus {
    ONGOING,     // 正在上课
    UPCOMING,    // 即将上课
    TODAY_PAST   // 今天的课已结束
}

// ─── 逻辑函数 ───────────────────────────────────────

/** 查找当前或最近的课程。 */
private fun findNearestCourse(courses: List<Course>, currentWeek: Int): NearestResult {
    val now = Calendar.getInstance()
    val currentHour = now.get(Calendar.HOUR_OF_DAY)
    val currentMinute = now.get(Calendar.MINUTE)
    val nowTime = LocalTime.of(currentHour, currentMinute)

    // App 内星期: 1=周一 … 7=周日
    val calendarDayOfWeek = now.get(Calendar.DAY_OF_WEEK)
    val appDayOfWeek = (calendarDayOfWeek + 5) % 7 + 1

    // 筛选今天且在本周的课程，按开始时间排序
    val todayCourses = courses
        .filter { it.dayOfWeek == appDayOfWeek && ScheduleUtils.isCourseInWeek(it.week, currentWeek) }
        .sortedBy { parseTime(it.beginTime) ?: LocalTime.MAX }

    if (todayCourses.isEmpty()) {
        // 尝试找明天的第一节课
        val tomorrowDay = if (appDayOfWeek == 7) 1 else appDayOfWeek + 1
        val tomorrowLabel = ScheduleUtils.dayLabels[tomorrowDay] ?: "?"
        val tomorrowCourses = courses
            .filter { it.dayOfWeek == tomorrowDay && ScheduleUtils.isCourseInWeek(it.week, currentWeek) }
            .sortedBy { parseTime(it.beginTime) ?: LocalTime.MAX }
        return if (tomorrowCourses.isEmpty()) {
            NearestResult.NoCourse("今日无课")
        } else {
            val first = tomorrowCourses.first()
            NearestResult.NoCourse(
                "今日无课\n明天($tomorrowLabel) " +
                "${first.beginTime.take(5)} 在 ${decodeClassroom(first.classroom)} 有课"
            )
        }
    }

    // 使用 LocalTime 比较（兼容 H:mm 和 HH:mm 两种格式）
    for (course in todayCourses) {
        val begin = parseTime(course.beginTime) ?: continue
        val end = parseTime(course.endTime) ?: continue
        if (nowTime in begin..end) {
            return NearestResult.CourseFound(course, CourseStatus.ONGOING)
        }
        if (nowTime < begin) {
            return NearestResult.CourseFound(course, CourseStatus.UPCOMING)
        }
    }

    // 所有课都结束了 → 返回最后一节
    return NearestResult.CourseFound(todayCourses.last(), CourseStatus.TODAY_PAST)
}

/** 解析课表时间字符串（兼容 "H:mm" / "HH:mm"），解析失败返回 null。 */
private fun parseTime(time: String): LocalTime? {
    return try {
        LocalTime.parse(time, DateTimeFormatter.ofPattern("H:mm"))
    } catch (_: Exception) {
        try {
            LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: Exception) {
            null
        }
    }
}

/** 从教室字符串提取楼名（取 "-" 前缀，如 "教5-101" → "教5"），用于地图搜索。 */
private fun decodeBuildingName(classroom: String): String {
    if (classroom.isBlank()) return ""
    val dashIndex = classroom.indexOf('-')
    return if (dashIndex > 0) classroom.substring(0, dashIndex) else classroom
}

/** 返回教室原文（仅用于显示）。 */
private fun decodeClassroom(classroom: String): String = classroom

// ─── UI 组件 ────────────────────────────────────────

@Composable
private fun NoCourseCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CourseInfoCard(result: NearestResult.CourseFound) {
    val course = result.course
    val buildingName = decodeBuildingName(course.classroom)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 状态标签
            val statusText = when (result.status) {
                CourseStatus.ONGOING -> "正在上课"
                CourseStatus.UPCOMING -> "即将上课"
                CourseStatus.TODAY_PAST -> "今日已结束"
            }
            val statusColor = when (result.status) {
                CourseStatus.ONGOING -> MaterialTheme.colorScheme.primary
                CourseStatus.UPCOMING -> MaterialTheme.colorScheme.tertiary
                CourseStatus.TODAY_PAST -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                color = statusColor,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            // 课程名
            Text(
                text = course.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // 教室
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = buildingName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (course.classroom.isNotBlank()) {
                        Text(
                            text = "教室: ${course.classroom}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 教师
            if (course.teacher.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(28.dp))
                    Text(
                        text = course.teacher,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // 时间
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(28.dp))
                Text(
                    text = "${course.beginTime.take(5)} - ${course.endTime.take(5)}  第${course.beginSection}-${course.endSection}节",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun NavigateButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Navigation,
            contentDescription = null,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "导航去这里",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MapPickerDialog(
    context: Context,
    buildingName: String,
    classroom: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择地图导航", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("导航到：")
                Text(
                    text = buildingName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (classroom.isNotBlank()) {
                    Text(
                        text = "（教室代码: $classroom）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    openMap(context, "baidu", buildingName)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("百度地图")
            }
        },
        dismissButton = {
            Button(
                onClick = {
                    openMap(context, "amap", buildingName)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("高德地图")
            }
        }
    )
}

/** 打开浏览器/地图 App 并自动搜索教学楼位置。优先使用地图 App 的 URL Scheme
 * 直接打开对应 App（已填好目的地），未安装则回退到浏览器搜索页。 */
private fun openMap(context: Context, mapType: String, buildingName: String) {
    val fullQuery = "北京信息科技大学$buildingName"

    // 每个地图准备两个 URL：app 深层链接 + web 搜索页（回退）
    val (appUrl, webUrl) = when (mapType) {
        "baidu" -> Pair(
            "baidumap://map/geocoder?address=${Uri.encode(fullQuery)}&src=ibistu",
            "https://map.baidu.com/s?wd=${Uri.encode(fullQuery)}"
        )
        "amap" -> Pair(
            "androidamap://poi?sourceApplication=ibistu&keywords=${Uri.encode(fullQuery)}&dev=0",
            "https://uri.amap.com/search?keyword=${Uri.encode(fullQuery)}&city=${Uri.encode("北京")}&view=map"
        )
        else -> return
    }

    // 优先尝试 App 深层链接
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(appUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (_: Exception) {
        // App 未安装 → 回退到浏览器搜索页（也可能因无浏览器而失败，静默处理）
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
            // 设备没有浏览器，静默失败
        }
    }
}

// ─── 天气信息栏 ──────────────────────────────────────

/** 顶部天气信息栏：显示定位位置、当前时间、天气状况、温度（自动刷新）。 */
@Composable
private fun WeatherBar(modifier: Modifier = Modifier) {
    val location by remember { mutableStateOf("北京") }
    var currentTime by remember { mutableStateOf("") }
    var weatherText by remember { mutableStateOf<String?>(null) }
    var temperature by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    // 缓存 OkHttpClient 实例，避免每次组合都新建
    val client = remember {
        OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    // 合并时间初始化与每分钟更新
    LaunchedEffect(Unit) {
        currentTime = LocalTime.now().format(timeFormatter)
        while (true) {
            delay(60_000L)
            currentTime = LocalTime.now().format(timeFormatter)
        }
    }

    // 只请求一次天气（非登录态，纯公开数据）
    LaunchedEffect(Unit) {
        try {
            val request = Request.Builder()
                .url("https://wttr.in/Beijing?format=%C|%t&lang=zh&m")
                .build()
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { it.body?.string() }
            }
            if (response != null) {
                val parts = response.split("|")
                if (parts.size >= 2) {
                    weatherText = parts[0].trim()
                    temperature = parts[1].trim()
                }
            }
        } catch (_: Exception) {
            // 天气获取失败时静默处理，保留空值
        } finally {
            isLoading = false
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherInfoItem(text = location)
            Text("|", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            WeatherInfoItem(text = currentTime)
            Text("|", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            WeatherInfoItem(text = if (isLoading) "--" else (weatherText ?: "--"))
            Text("|", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            WeatherInfoItem(text = if (isLoading) "--" else (temperature ?: "--"))
        }
    }
}

@Composable
private fun RowScope.WeatherInfoItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f)
    )
}
