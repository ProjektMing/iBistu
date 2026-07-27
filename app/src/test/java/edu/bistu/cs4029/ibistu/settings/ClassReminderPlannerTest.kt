package edu.bistu.cs4029.ibistu.settings

import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.TermWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ClassReminderPlannerTest {

    @Test
    fun plansReminderAtConfiguredLeadTimeWithCourseDetails() {
        val reminders = ClassReminderPlanner.plan(
            courses = listOf(course()),
            termWeeks = mapOf(
                1 to TermWeek(
                    weekNumber = 1,
                    startDate = "2026-08-31 00:00:00",
                    endDate = "2026-09-06 23:59:59"
                )
            ),
            leadMinutes = 15,
            now = LocalDateTime.of(2026, 8, 31, 9, 0)
        )

        assertEquals(1, reminders.size)
        with(reminders.single()) {
            assertEquals("移动应用开发", courseName)
            assertEquals("教三-301", classroom)
            assertEquals("沙河校区", campus)
            assertEquals(LocalDateTime.of(2026, 9, 2, 10, 0), startsAt)
            assertEquals(LocalDateTime.of(2026, 9, 2, 9, 45), triggersAt)
            assertEquals(15, leadMinutes)
        }
    }

    @Test
    fun skipsPastOutOfWindowAndUnscheduledWeeks() {
        val reminders = ClassReminderPlanner.plan(
            courses = listOf(
                course(code = "PAST", week = "1周"),
                course(code = "FUTURE", week = "7周"),
                course(code = "MISSING", week = "2周")
            ),
            termWeeks = mapOf(
                1 to termWeek(1, "2026-08-31"),
                7 to termWeek(7, "2026-10-12")
            ),
            leadMinutes = 15,
            now = LocalDateTime.of(2026, 9, 3, 9, 0),
            horizonDays = 30
        )

        assertTrue(reminders.isEmpty())
    }

    @Test
    fun deduplicatesIdenticalCourseOccurrencesAndRejectsInvalidLeadTime() {
        val duplicated = course()
        val weeks = mapOf(1 to termWeek(1, "2026-08-31"))

        val reminders = ClassReminderPlanner.plan(
            courses = listOf(duplicated, duplicated),
            termWeeks = weeks,
            leadMinutes = 10,
            now = LocalDateTime.of(2026, 8, 31, 9, 0)
        )

        assertEquals(1, reminders.size)
        assertTrue(
            ClassReminderPlanner.plan(
                courses = listOf(duplicated),
                termWeeks = weeks,
                leadMinutes = 0,
                now = LocalDateTime.of(2026, 8, 31, 9, 0)
            ).isEmpty()
        )
    }

    @Test
    fun acceptsServerTimeThatIncludesSeconds() {
        val reminders = ClassReminderPlanner.plan(
            courses = listOf(course().copy(beginTime = "10:00:00")),
            termWeeks = mapOf(1 to termWeek(1, "2026-08-31")),
            leadMinutes = 15,
            now = LocalDateTime.of(2026, 8, 31, 9, 0)
        )

        assertEquals(LocalDateTime.of(2026, 9, 2, 10, 0), reminders.single().startsAt)
    }

    private fun course(
        code: String = "CS4029",
        week: String = "1周"
    ) = Course(
        name = "移动应用开发",
        code = code,
        credit = "2",
        teacher = "张老师",
        classroom = "教三-301",
        campus = "沙河校区",
        week = week,
        dayOfWeek = 3,
        beginSection = 3,
        endSection = 4,
        beginTime = "10:00",
        endTime = "11:35"
    )

    private fun termWeek(number: Int, startDate: String) = TermWeek(
        weekNumber = number,
        startDate = "$startDate 00:00:00",
        endDate = "$startDate 23:59:59"
    )
}
