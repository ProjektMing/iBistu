package edu.bistu.cs4029.ibistu.today

import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.Exam
import edu.bistu.cs4029.ibistu.schedule.ScheduleUtils
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 今日校园首页所需的稳定展示数据。 */
data class TodayCampusUiModel(
    val greeting: String,
    val dateLabel: String,
    val weekLabel: String,
    val todayCourses: List<Course>,
    val highlightedCourse: Course?,
    val highlightedLabel: String,
    val highlightedStatus: String,
    val highlightedProgress: Float,
    val nextExam: Exam?,
    val nextExamDays: Long?
)

/**
 * 从已有课表与考试数据构建今日校园首页模型。
 *
 * @param courses 当前缓存的课程。
 * @param exams 当前缓存的考试。
 * @param currentWeek 当前教学周。
 * @param now 构建模型使用的时间，测试可传入固定值。
 */
fun buildTodayCampusUiModel(
    courses: List<Course>,
    exams: List<Exam>,
    currentWeek: Int,
    now: LocalDateTime = LocalDateTime.now()
): TodayCampusUiModel {
    val todayCourses = courses
        .filter { course ->
            course.dayOfWeek == now.dayOfWeek.value &&
                ScheduleUtils.isCourseInWeek(course.week, currentWeek)
        }
        .sortedWith(compareBy<Course> { it.beginTime }.thenBy { it.beginSection })

    val currentTime = now.toLocalTime()
    val currentCourse = todayCourses.firstOrNull { course ->
        val begin = course.beginTime.asCourseTime()
        val end = course.endTime.asCourseTime()
        begin != null && end != null && currentTime >= begin && currentTime < end
    }
    val nextCourse = currentCourse ?: todayCourses.firstOrNull { course ->
        course.beginTime.asCourseTime()?.isAfter(currentTime) == true
    }

    val highlightedLabel: String
    val highlightedStatus: String
    val highlightedProgress: Float
    when {
        currentCourse != null -> {
            val begin = requireNotNull(currentCourse.beginTime.asCourseTime())
            val end = requireNotNull(currentCourse.endTime.asCourseTime())
            val remainingMinutes = Duration.between(currentTime, end).toMinutes().coerceAtLeast(1)
            val totalMinutes = Duration.between(begin, end).toMinutes().coerceAtLeast(1)
            val elapsedMinutes = Duration.between(begin, currentTime).toMinutes().coerceAtLeast(0)
            highlightedLabel = "正在上课"
            highlightedStatus = "$remainingMinutes 分钟后下课"
            highlightedProgress = (elapsedMinutes.toFloat() / totalMinutes).coerceIn(0f, 1f)
        }

        nextCourse != null -> {
            val begin = requireNotNull(nextCourse.beginTime.asCourseTime())
            val minutesUntilStart = Duration.between(currentTime, begin).toMinutes().coerceAtLeast(1)
            highlightedLabel = "下一节"
            highlightedStatus = if (minutesUntilStart < 60) {
                "$minutesUntilStart 分钟后开始"
            } else {
                "${nextCourse.beginTime} 开始"
            }
            highlightedProgress = 0f
        }

        todayCourses.isEmpty() -> {
            highlightedLabel = "今天没有课程"
            highlightedStatus = "给自己安排一点专注时间吧"
            highlightedProgress = 0f
        }

        else -> {
            highlightedLabel = "今日课程已完成"
            highlightedStatus = "辛苦啦，记得整理今天的笔记"
            highlightedProgress = 1f
        }
    }

    val today = now.toLocalDate()
    val nextExamWithDate = exams
        .mapNotNull { exam -> exam.examDate.asExamDate()?.let { date -> exam to date } }
        .filter { (_, date) -> !date.isBefore(today) }
        .minByOrNull { (_, date) -> date }

    return TodayCampusUiModel(
        greeting = greetingForHour(now.hour),
        dateLabel = now.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
        weekLabel = "第 $currentWeek 周",
        todayCourses = todayCourses,
        highlightedCourse = nextCourse,
        highlightedLabel = highlightedLabel,
        highlightedStatus = highlightedStatus,
        highlightedProgress = highlightedProgress,
        nextExam = nextExamWithDate?.first,
        nextExamDays = nextExamWithDate?.second?.let { ChronoUnit.DAYS.between(today, it) }
    )
}

private val CourseTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val ExamDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun String.asCourseTime(): LocalTime? =
    runCatching { LocalTime.parse(trim(), CourseTimeFormatter) }.getOrNull()

private fun String.asExamDate(): LocalDate? =
    runCatching { LocalDate.parse(substringBefore(' ').trim(), ExamDateFormatter) }.getOrNull()

private fun greetingForHour(hour: Int): String = when (hour) {
    in 5..10 -> "早上好"
    in 11..13 -> "中午好"
    in 14..17 -> "下午好"
    else -> "晚上好"
}
