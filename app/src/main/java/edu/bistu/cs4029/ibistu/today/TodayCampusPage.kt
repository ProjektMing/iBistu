package edu.bistu.cs4029.ibistu.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bistu.cs4029.ibistu.common.state.AppState
import edu.bistu.cs4029.ibistu.common.ui.theme.IBistuTheme
import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.Exam
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** 汇总下一节课、今日课表、考试倒计时和常用入口的校园首页。 */
@Composable
fun TodayCampusPage(
    modifier: Modifier = Modifier,
    state: AppState,
    onOpenSchedule: () -> Unit,
    onOpenExams: () -> Unit,
    onOpenFood: () -> Unit
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = LocalDateTime.now()
        }
    }

    val model = remember(state.courses, state.exams, state.currentWeek, now) {
        buildTodayCampusUiModel(
            courses = state.courses,
            exams = state.exams,
            currentWeek = state.currentWeek,
            now = now
        )
    }

    TodayCampusContent(
        modifier = modifier,
        model = model,
        onOpenSchedule = onOpenSchedule,
        onOpenExams = onOpenExams,
        onOpenFood = onOpenFood
    )
}

/** 可独立预览和测试的今日校园页面内容。 */
@Composable
fun TodayCampusContent(
    modifier: Modifier = Modifier,
    model: TodayCampusUiModel,
    onOpenSchedule: () -> Unit,
    onOpenExams: () -> Unit,
    onOpenFood: () -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("today-campus-page"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TodayHeader(model)
        }
        item {
            HighlightedCourseCard(model, onOpenSchedule)
        }
        item {
            QuickActions(
                onOpenSchedule = onOpenSchedule,
                onOpenExams = onOpenExams,
                onOpenFood = onOpenFood
            )
        }
        model.nextExam?.let { exam ->
            item {
                NextExamCard(
                    exam = exam,
                    daysRemaining = model.nextExamDays,
                    onClick = onOpenExams
                )
            }
        }
        item {
            SectionTitle(
                title = "今天的安排",
                supportingText = "${model.todayCourses.size} 门课程",
                onClick = onOpenSchedule
            )
        }
        if (model.todayCourses.isEmpty()) {
            item {
                EmptyTodayCard(onOpenSchedule)
            }
        } else {
            items(model.todayCourses, key = { "${it.code}-${it.beginTime}-${it.classroom}" }) { course ->
                TodayCourseCard(
                    course = course,
                    isHighlighted = course == model.highlightedCourse
                )
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TodayHeader(model: TodayCampusUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = model.greeting,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${model.dateLabel} · ${model.weekLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.WbSunny,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun HighlightedCourseCard(
    model: TodayCampusUiModel,
    onOpenSchedule: () -> Unit
) {
    ElevatedCard(
        onClick = onOpenSchedule,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("today-highlight-card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = model.highlightedLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "打开课表",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = model.highlightedCourse?.name ?: model.highlightedStatus,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (model.highlightedCourse != null) {
                Spacer(Modifier.height(8.dp))
                CourseMetaLine(
                    icon = Icons.Filled.AccessTime,
                    text = "${model.highlightedCourse.beginTime}-${model.highlightedCourse.endTime}"
                )
                Spacer(Modifier.height(5.dp))
                CourseMetaLine(
                    icon = Icons.Filled.LocationOn,
                    text = listOf(model.highlightedCourse.classroom, model.highlightedCourse.campus)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                )
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { model.highlightedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = model.highlightedStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "点击查看完整课表",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun CourseMetaLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickActions(
    onOpenSchedule: () -> Unit,
    onOpenExams: () -> Unit,
    onOpenFood: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickActionCard(
            modifier = Modifier.weight(1f),
            label = "完整课表",
            icon = Icons.Filled.CalendarMonth,
            onClick = onOpenSchedule
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            label = "考试安排",
            icon = Icons.AutoMirrored.Filled.Assignment,
            onClick = onOpenExams
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            label = "今天吃啥",
            icon = Icons.Filled.Restaurant,
            onClick = onOpenFood
        )
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NextExamCard(
    exam: Exam,
    daysRemaining: Long?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiary
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when (daysRemaining) {
                            0L -> "今天"
                            1L -> "明天"
                            else -> "${daysRemaining ?: "--"}天"
                        },
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "最近考试",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = exam.courseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${exam.examDate} · ${exam.examTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "查看考试安排"
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    supportingText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onClick) {
            Text("查看全部")
        }
    }
}

@Composable
private fun TodayCourseCard(
    modifier: Modifier = Modifier,
    course: Course,
    isHighlighted: Boolean
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("today-course-${course.code}-${course.beginTime}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(58.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = course.beginTime,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = course.endTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .height(54.dp),
                shape = CircleShape,
                color = if (isHighlighted) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            ) {}
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOf(course.classroom, course.teacher)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isHighlighted) {
                Icon(
                    imageVector = Icons.Filled.RadioButtonChecked,
                    contentDescription = "当前课程",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun EmptyTodayCard(onOpenSchedule: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.LocalCafe,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "今天没有课程",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "可以复习、运动，或者找间空教室自习",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenSchedule) {
                Text("查看本周课表")
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun TodayCampusPagePreview() {
    IBistuTheme {
        TodayCampusContent(
            model = previewTodayCampusModel(),
            onOpenSchedule = {},
            onOpenExams = {},
            onOpenFood = {}
        )
    }
}

private fun previewTodayCampusModel() = TodayCampusUiModel(
    greeting = "早上好",
    dateLabel = "7月27日 星期一",
    weekLabel = "第 3 周",
    todayCourses = listOf(
        previewCourse("高等数学", "08:00", "09:35", "教5-101"),
        previewCourse("大学物理", "10:00", "11:35", "WLA-106"),
        previewCourse("移动应用开发", "14:00", "15:35", "信息楼-302")
    ),
    highlightedCourse = previewCourse("大学物理", "10:00", "11:35", "WLA-106"),
    highlightedLabel = "下一节",
    highlightedStatus = "20 分钟后开始",
    highlightedProgress = 0f,
    nextExam = Exam(
        courseName = "高等数学",
        examDate = "2026-07-29",
        examTime = "09:00-11:00",
        location = "教5-101",
        seatNumber = "12",
        examType = "期末考试",
        campus = "小营校区"
    ),
    nextExamDays = 2
)

private fun previewCourse(name: String, begin: String, end: String, classroom: String) = Course(
    name = name,
    code = name,
    credit = "2",
    teacher = "张老师",
    classroom = classroom,
    campus = "小营校区",
    week = "1-16周",
    dayOfWeek = 1,
    beginSection = 1,
    endSection = 2,
    beginTime = begin,
    endTime = end
)
