package edu.bistu.cs4029.ibistu.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.bistu.cs4029.ibistu.common.state.AppState
import edu.bistu.cs4029.ibistu.focus.model.FocusSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale

/**
 * 日期范围筛选模式。
 */
private enum class DateFilter {
    DAY, WEEK, MONTH, CUSTOM
}

/**
 * 专注数据统计页面。
 * 展示摘要卡片、日期筛选、柱状图、时段分布、近期会话列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusStatsPage(
    state: AppState,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }
    val fullDateFormatter = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    var dateFilter by rememberSaveable { mutableStateOf(DateFilter.WEEK) }
    var customStartMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var customEndMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var showStartDatePicker by rememberSaveable { mutableStateOf(false) }
    var showEndDatePicker by rememberSaveable { mutableStateOf(false) }

    // 计算日期范围
    val dateRange = remember(dateFilter, customStartMillis, customEndMillis) {
        val now = LocalDate.now()
        when (dateFilter) {
            DateFilter.DAY -> {
                val start = now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val end = now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                start to end
            }
            DateFilter.WEEK -> {
                val monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val sunday = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                val start = monday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val end = sunday.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                start to end
            }
            DateFilter.MONTH -> {
                val firstDay = now.withDayOfMonth(1)
                val lastDay = now.with(TemporalAdjusters.lastDayOfMonth())
                val start = firstDay.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val end = lastDay.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                start to end
            }
            DateFilter.CUSTOM -> {
                val start = customStartMillis ?: now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val end = customEndMillis ?: now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 86400000L - 1
                start to end
            }
        }
    }

    // 数据状态
    var sessions by remember { mutableStateOf<List<FocusSession>>(emptyList()) }
    var totalCount by remember { mutableStateOf(0) }
    var totalDuration by remember { mutableStateOf(0L) }
    var dailyDuration by remember { mutableStateOf<List<DailyDuration>>(emptyList()) }
    var hourlyData by remember { mutableStateOf<List<HourlyDistribution>>(emptyList()) }
    var consecutiveDays by remember { mutableIntStateOf(0) }
    var taskDurationBreakdown by remember { mutableStateOf<List<TaskDuration>>(emptyList()) }

    // 计算连续打卡天数
    fun calcConsecutiveDays(activeDayKeys: List<Long>): Int {
        if (activeDayKeys.isEmpty()) return 0
        val todayKey = LocalDate.now().toEpochDay()
        val sorted = activeDayKeys.sortedDescending()
        // 检查今天是否有记录
        var count = 0
        var expectedDay = todayKey
        for (dayKey in sorted) {
            if (dayKey == expectedDay) {
                count++
                expectedDay--
            } else if (dayKey < expectedDay) {
                break
            }
        }
        return count
    }

    // 加载数据
    LaunchedEffect(dateRange) {
        withContext(Dispatchers.IO) {
            val (from, to) = dateRange
            sessions = state.focusDao.getSessionsInRange(from, to)
            totalCount = state.focusDao.getSessionCountInRange(from, to)
            totalDuration = state.focusDao.getTotalDurationInRange(from, to)
            dailyDuration = state.focusDao.getDailyDuration(from, to)
            hourlyData = state.focusDao.getHourlyDistribution(from, to)
            val allDays = state.focusDao.getAllActiveDays()
            consecutiveDays = calcConsecutiveDays(allDays)
            taskDurationBreakdown = state.focusDao.getTaskDurationBreakdown(from, to)
        }
    }

    // 计算日均
    val daysInRange = remember(dateRange) {
        val (from, to) = dateRange
        val millisInDay = 24 * 60 * 60 * 1000L
        ((to - from) / millisInDay + 1).coerceAtLeast(1)
    }
    val dailyAvgSeconds = if (daysInRange > 0) totalDuration / daysInRange else 0L

    // 日分布数据转换为图表数据
    val chartData = remember(dailyDuration, dateFormatter) {
        dailyDuration.map { dd ->
            val date = Date(dd.day_key * 86400000L)
            dateFormatter.format(date) to (dd.total_seconds / 60).toInt()
        }
    }

    // 时段数据
    val hourlyPairs = remember(hourlyData) {
        hourlyData.map { it.hour to it.count }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 日期筛选器
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DateFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = dateFilter == filter,
                        onClick = { dateFilter = filter },
                        label = {
                            Text(
                                when (filter) {
                                    DateFilter.DAY -> "日"
                                    DateFilter.WEEK -> "周"
                                    DateFilter.MONTH -> "月"
                                    DateFilter.CUSTOM -> "自定义"
                                }
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }

        // 自定义日期选择
        if (dateFilter == DateFilter.CUSTOM) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }
                    TextButton(onClick = { showStartDatePicker = true }) {
                        Text(text = if (customStartMillis != null) sdf.format(java.util.Date(customStartMillis!!))
                            else "开始日期")
                    }
                    Text("—")
                    TextButton(onClick = { showEndDatePicker = true }) {
                        Text(text = if (customEndMillis != null) sdf.format(java.util.Date(customEndMillis!!))
                            else "截止日期")
                    }
                }
            }
        }

        // 摘要卡片行
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FocusSummaryCard(
                    title = "专注次数",
                    value = "$totalCount",
                    modifier = Modifier.weight(1f)
                )
                FocusSummaryCard(
                    title = "总时长",
                    value = formatDuration(totalDuration),
                    modifier = Modifier.weight(1f)
                )
                FocusSummaryCard(
                    title = "连续打卡",
                    value = "${consecutiveDays}天",
                    modifier = Modifier.weight(1f)
                )
                FocusSummaryCard(
                    title = "日均时长",
                    value = formatDuration(dailyAvgSeconds),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 代办时长分布
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "代办时长分布",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    if (taskDurationBreakdown.isEmpty()) {
                        Text(
                            text = "暂无数据",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        val maxDuration = taskDurationBreakdown.maxOf { it.total_seconds }.coerceAtLeast(1)
                        taskDurationBreakdown.forEach { td ->
                            val min = td.total_seconds / 60
                            val label = if (min > 0) "${min}分钟" else "${td.total_seconds}秒"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = td.task_name,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(80.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                LinearProgressIndicator(
                                    progress = { (td.total_seconds.toFloat() / maxDuration).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(12.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 专注时长分布柱状图
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "专注时长分布",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    DurationBarChart(
                        data = chartData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            }
        }

        // 时段分布图
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "时段分布",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    HourlyDistributionChart(
                        data = hourlyPairs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }
        }

        // 近期会话列表标题
        item {
            Text(
                text = "近期记录",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // 会话列表
        if (sessions.isEmpty()) {
            item {
                Text(
                    text = "暂无专注记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            items(sessions) { session ->
                SessionListItem(session, fullDateFormatter)
            }
        }

        // 底部留白
        item { Spacer(Modifier.height(16.dp)) }
    }

    // DatePicker 对话框
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = customStartMillis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    customStartMillis = datePickerState.selectedDateMillis
                    showStartDatePicker = false
                    dateFilter = DateFilter.CUSTOM
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = customEndMillis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    customEndMillis = datePickerState.selectedDateMillis
                    showEndDatePicker = false
                    dateFilter = DateFilter.CUSTOM
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * 格式化秒数为可读时长字符串。
 */
private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h${minutes}m"
    } else {
        "${minutes}m"
    }
}

/**
 * 会话列表项。
 */
@Composable
private fun SessionListItem(
    session: FocusSession,
    formatter: SimpleDateFormat
) {
    val dateText = remember(session.startTime) {
        formatter.format(Date(session.startTime))
    }
    val durationMin = session.durationSeconds / 60
    val durationSec = session.durationSeconds % 60
    val durationText = if (durationMin > 0) "${durationMin}分${durationSec}秒" else "${durationSec}秒"
    val modeText = when (session.mode) {
        "countdown" -> "倒计时"
        "stopwatch" -> "正向计时"
        else -> session.mode
    }
    val taskLabel = when {
        session.taskId > 0 -> "任务 #${session.taskId}"
        else -> ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "[$modeText]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (session.label.isNotBlank()) {
                Text(
                    text = session.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (taskLabel.isNotBlank()) {
                Text(
                    text = taskLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (session.interruptionType.isNotBlank()) {
                Text(
                    text = "中断: ${session.interruptionType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
