package edu.bistu.cs4029.ibistu.settings

import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.ScheduleUtils
import edu.bistu.cs4029.ibistu.schedule.TermWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class ClassReminderPlan(
    val courseName: String,
    val courseCode: String,
    val teacher: String,
    val classroom: String,
    val campus: String,
    val startsAt: LocalDateTime,
    val triggersAt: LocalDateTime,
    val leadMinutes: Int,
    val weekNumber: Int
)

/**
 * Converts timetable data into future reminder occurrences without depending on Android APIs.
 */
object ClassReminderPlanner {
    private const val DEFAULT_HORIZON_DAYS = 30L

    fun plan(
        courses: List<Course>,
        termWeeks: Map<Int, TermWeek>,
        leadMinutes: Int,
        now: LocalDateTime = LocalDateTime.now(),
        horizonDays: Long = DEFAULT_HORIZON_DAYS
    ): List<ClassReminderPlan> {
        if (leadMinutes <= 0 || horizonDays <= 0) return emptyList()

        val horizon = now.plusDays(horizonDays)
        return courses
            .asSequence()
            .flatMap { course ->
                val beginTime = course.beginTime.toLocalTimeOrNull()
                    ?: return@flatMap emptySequence()
                if (course.dayOfWeek !in 1..7) return@flatMap emptySequence()

                ScheduleUtils.getCourseWeeks(course.week)
                    .asSequence()
                    .mapNotNull { weekNumber ->
                        val weekStart = termWeeks[weekNumber]
                            ?.startDate
                            ?.toLocalDateOrNull()
                            ?: return@mapNotNull null
                        val startsAt = LocalDateTime.of(
                            weekStart.plusDays((course.dayOfWeek - 1).toLong()),
                            beginTime
                        )
                        val triggersAt = startsAt.minusMinutes(leadMinutes.toLong())
                        if (!triggersAt.isAfter(now) || triggersAt.isAfter(horizon)) {
                            return@mapNotNull null
                        }
                        ClassReminderPlan(
                            courseName = course.name,
                            courseCode = course.code,
                            teacher = course.teacher,
                            classroom = course.classroom,
                            campus = course.campus,
                            startsAt = startsAt,
                            triggersAt = triggersAt,
                            leadMinutes = leadMinutes,
                            weekNumber = weekNumber
                        )
                    }
            }
            .distinctBy { "${it.courseCode}|${it.courseName}|${it.startsAt}" }
            .sortedBy { it.triggersAt }
            .toList()
    }

    private fun String.toLocalTimeOrNull(): LocalTime? {
        val parts = trim().split(":")
        if (parts.size !in 2..3) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        val second = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return runCatching { LocalTime.of(hour, minute, second) }.getOrNull()
    }

    private fun String.toLocalDateOrNull(): LocalDate? {
        if (length < 10) return null
        return try {
            LocalDate.parse(take(10), DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
