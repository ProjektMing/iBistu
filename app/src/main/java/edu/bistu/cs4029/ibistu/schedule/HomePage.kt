package edu.bistu.cs4029.ibistu.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bistu.cs4029.ibistu.common.state.AppState

/** 按周展示的七列课程表首页。 */
@Composable
fun HomePage(state: AppState, modifier: Modifier = Modifier) {
    if (state.courses.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请先在 Profile 中登录", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.termName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            TextButton(onClick = {
                state.isLoadingExams = true
                state.showExamPage = true
            }) {
                Text("考试安排", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(4.dp))
        WeekNavigator(
            currentWeek = state.currentWeek,
            weekRange = state.weekRange,
            onPrevious = {
                if (state.currentWeek > state.weekRange.first) state.currentWeek--
            },
            onNext = {
                if (state.currentWeek < state.weekRange.last) state.currentWeek++
            }
        )
        state.termWeeks[state.currentWeek]?.let { week ->
            if (week.startDate.isNotBlank() || week.endDate.isNotBlank()) {
                Text(
                    text = formatDateRange(week.startDate, week.endDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        WeeklyTimeTable(courses = state.courses, currentWeek = state.currentWeek)
    }
}

private fun formatDateRange(start: String, end: String): String {
    fun format(value: String): String {
        val parts = value.substringBefore(' ').split('-')
        if (parts.size < 3) return value
        val month = parts[1].trimStart('0').ifBlank { "0" }
        val day = parts[2].trimStart('0').ifBlank { "0" }
        return "${month}月${day}日"
    }

    return listOf(start, end)
        .filter(String::isNotBlank)
        .joinToString(" — ", transform = ::format)
}

@Composable
private fun WeekNavigator(
    currentWeek: Int,
    weekRange: IntRange,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, enabled = currentWeek > weekRange.first) {
            Text("<", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = "第 $currentWeek 周",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        IconButton(onClick = onNext, enabled = currentWeek < weekRange.last) {
            Text(">", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun WeeklyTimeTable(courses: List<Course>, currentWeek: Int) {
    val rowHeight = 52.dp
    val sectionLabelWidth = 38.dp
    val weekCourses = remember(courses, currentWeek) {
        courses.filter { ScheduleUtils.isCourseInWeek(it.week, currentWeek) }
    }

    if (weekCourses.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("本周无课", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    Column {
        WeekdayHeader(sectionLabelWidth)
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val dayWidth = (maxWidth - sectionLabelWidth) / DAYS_PER_WEEK
            TimetableGrid(rowHeight = rowHeight, sectionLabelWidth = sectionLabelWidth)

            weekCourses.forEach { course ->
                val topOffset = (rowHeight + DIVIDER_SIZE) * (course.beginSection - 1).toFloat()
                val sectionSpan = course.endSection - course.beginSection + 1
                val cardHeight = (rowHeight + DIVIDER_SIZE) * sectionSpan.toFloat() - 2.dp
                val leftOffset = sectionLabelWidth + dayWidth * (course.dayOfWeek - 1).toFloat()

                Box(
                    modifier = Modifier
                        .offset(x = leftOffset + CELL_PADDING, y = topOffset + CELL_PADDING)
                        .width(dayWidth - CELL_PADDING * 2)
                        .height(cardHeight)
                ) {
                    TimeTableCell(course)
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader(sectionLabelWidth: androidx.compose.ui.unit.Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(vertical = 6.dp)
    ) {
        Spacer(Modifier.width(sectionLabelWidth))
        for (day in 1..DAYS_PER_WEEK) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = ScheduleUtils.dayLabels[day] ?: "?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TimetableGrid(
    rowHeight: androidx.compose.ui.unit.Dp,
    sectionLabelWidth: androidx.compose.ui.unit.Dp
) {
    Column(Modifier.fillMaxWidth()) {
        for (section in 1..SECTION_COUNT) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
            ) {
                SectionLabel(section = section, width = sectionLabelWidth)
                repeat(DAYS_PER_WEEK) { dayIndex ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (dayIndex < DAYS_PER_WEEK - 1) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(DIVIDER_SIZE)
                                    .fillMaxHeight()
                                    .background(GRID_COLOR)
                            )
                        }
                    }
                }
            }
            if (section < SECTION_COUNT) {
                HorizontalDivider(thickness = DIVIDER_SIZE, color = GRID_COLOR)
            }
        }
    }
}

@Composable
private fun SectionLabel(section: Int, width: androidx.compose.ui.unit.Dp) {
    val startTime = sectionStartTimes.getOrNull(section - 1)
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = section.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            startTime?.let {
                Text(
                    text = it,
                    fontSize = 7.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun TimeTableCell(course: Course, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = courseColor(course.name.hashCode())),
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

private fun courseColor(hash: Int): Color = courseColors[(hash and Int.MAX_VALUE) % courseColors.size]

private const val DAYS_PER_WEEK = 7
private const val SECTION_COUNT = 12
private val DIVIDER_SIZE = 0.5.dp
private val CELL_PADDING = 1.5.dp
private val GRID_COLOR = Color(0xFFE0E0E0)

private val sectionStartTimes = listOf(
    "08:00", "08:50", "09:50", "10:40", "11:30", "13:30", "14:20",
    "15:20", "16:10", "18:30", "19:20", "20:10"
)

private val courseColors = listOf(
    Color(0xFFE3F2FD),
    Color(0xFFE8F5E9),
    Color(0xFFFFF3E0),
    Color(0xFFF3E5F5),
    Color(0xFFE0F7FA),
    Color(0xFFFFEBEE),
    Color(0xFFF1F8E9),
    Color(0xFFFFF8E1),
    Color(0xFFE8EAF6),
    Color(0xFFFCE4EC)
)
