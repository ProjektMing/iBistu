package edu.bistu.cs4029.ibistu.settings

import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.ScheduleUtils
import edu.bistu.cs4029.ibistu.schedule.TermWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * One local-time course reminder occurrence.
 *
 * [startsAt] is the class start, while [triggersAt] is the notification alarm time after
 * subtracting [leadMinutes]. [dayOfWeek] and [beginTime] preserve the timetable slot identity.
 */
data class ClassReminderPlan(
    val courseName: String,
    val courseCode: String,
    val teacher: String,
    val classroom: String,
    val campus: String,
    val startsAt: LocalDateTime,
    val triggersAt: LocalDateTime,
    val leadMinutes: Int,
    val weekNumber: Int,
    val dayOfWeek: Int,
    val beginTime: String
)

/** Stable identity shared by alarm creation and cancellation for one course occurrence. */
internal val ClassReminderPlan.alarmIdentity: String
    get() = classReminderAlarmIdentity(
        courseCode = courseCode,
        courseName = courseName,
        weekNumber = weekNumber,
        dayOfWeek = dayOfWeek,
        beginTime = beginTime
    )

internal fun classReminderAlarmIdentity(
    courseCode: String,
    courseName: String,
    weekNumber: Int,
    dayOfWeek: Int,
    beginTime: String
): String {
    val normalizedBeginTime = beginTime.toClassReminderLocalTimeOrNull()?.toString()
        ?: beginTime.trim()
    return "$courseCode|$courseName|$weekNumber|$dayOfWeek|$normalizedBeginTime"
}

/**
 * Assigns deterministic notification identifiers and resolves Java string-hash collisions.
 */
internal fun allocateClassReminderNotificationIds(
    identities: Collection<String>
): Map<String, Int> {
    val usedIds = mutableSetOf<Int>()
    return buildMap {
        identities.distinct().sorted().forEach { identity ->
            var candidate = identity.hashCode().and(Int.MAX_VALUE).coerceAtLeast(1)
            while (!usedIds.add(candidate)) {
                candidate = if (candidate == Int.MAX_VALUE) 1 else candidate + 1
            }
            put(identity, candidate)
        }
    }
}

/**
 * Converts timetable data into future reminder occurrences without depending on Android APIs.
 */
object ClassReminderPlanner {
    private const val DEFAULT_HORIZON_DAYS = 30L

    /**
     * Plans reminder occurrences strictly after [now] and within [horizonDays].
     *
     * Invalid lead times, invalid timetable values, past occurrences, missing teaching weeks and
     * duplicate occurrences are omitted. The returned list is ordered by trigger time.
     */
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
                val beginTime = course.beginTime.toClassReminderLocalTimeOrNull()
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
                            weekNumber = weekNumber,
                            dayOfWeek = course.dayOfWeek,
                            beginTime = beginTime.toString()
                        )
                    }
            }
            .distinctBy { "${it.courseCode}|${it.courseName}|${it.startsAt}" }
            .sortedBy { it.triggersAt }
            .toList()
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

internal fun String.toClassReminderLocalTimeOrNull(): LocalTime? {
    val parts = trim().split(":")
    if (parts.size !in 2..3) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    val second = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return runCatching { LocalTime.of(hour, minute, second) }.getOrNull()
}
