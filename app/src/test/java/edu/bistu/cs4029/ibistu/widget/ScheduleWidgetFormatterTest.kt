package edu.bistu.cs4029.ibistu.widget

import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.ScheduleData
import edu.bistu.cs4029.ibistu.schedule.TermWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleWidgetFormatterTest {
    @Test
    fun noCache_promptsUserToOpenApp() {
        val model = ScheduleWidgetFormatter.build(null, LocalDate.of(2026, 7, 6), LocalTime.NOON)

        assertEquals("打开应用登录并加载课表", model.status)
        assertTrue(model.courses.isEmpty())
    }

    @Test
    fun currentCourse_isHighlighted() {
        val model = ScheduleWidgetFormatter.build(
            schedule(courses = listOf(course("高等数学", "08:00", "09:35"))),
            LocalDate.of(2026, 7, 6),
            LocalTime.of(8, 30)
        )

        assertEquals("正在上课 · 高等数学 · 还有 65 分钟", model.status)
        assertTrue(model.courses.single().highlighted)
        assertEquals("教5-101", model.courses.single().room)
        assertEquals(LocalDateTime.of(2026, 7, 6, 9, 35), model.nextRefreshAt)
    }

    @Test
    fun beforeClass_highlightsNextCourse() {
        val model = ScheduleWidgetFormatter.build(
            schedule(courses = listOf(
                course("高等数学", "08:00", "09:35"),
                course("大学物理", "10:40", "11:25")
            )),
            LocalDate.of(2026, 7, 6),
            LocalTime.of(9, 50)
        )

        assertEquals("50 分钟后上课 · 大学物理", model.status)
        assertEquals(listOf("大学物理"), model.courses.map { it.name })
        assertTrue(model.courses.single().highlighted)
    }

    @Test
    fun countdownRoundsUpPartialMinute() {
        val model = ScheduleWidgetFormatter.build(
            schedule(courses = listOf(course("高等数学", "08:00", "09:35"))),
            LocalDate.of(2026, 7, 6),
            LocalTime.of(8, 30, 30)
        )

        assertEquals("正在上课 · 高等数学 · 还有 65 分钟", model.status)
    }

    @Test
    fun afterLastClass_showsTomorrowFirstCourseInsteadOfDeadEnd() {
        val model = ScheduleWidgetFormatter.build(
            schedule(
                courses = listOf(
                    course("高等数学", "08:00", "09:35"),
                    course("大学英语", "10:40", "11:25", dayOfWeek = 2)
                )
            ),
            LocalDate.of(2026, 7, 6),
            LocalTime.of(18, 0)
        )

        assertEquals("明天 10:40 上课 · 大学英语", model.status)
        assertEquals("明天 10:40", model.courses.single().time)
        assertTrue(model.courses.single().highlighted)
        assertEquals(LocalDateTime.of(2026, 7, 7, 10, 40), model.nextRefreshAt)
    }

    @Test
    fun responsiveLayout_keepsCompactWidgetsReadable() {
        assertEquals(0, ScheduleWidgetLayoutPolicy.visibleCourseLimit(100))
        assertEquals(1, ScheduleWidgetLayoutPolicy.visibleCourseLimit(140))
        assertEquals(2, ScheduleWidgetLayoutPolicy.visibleCourseLimit(220))
        assertEquals(4, ScheduleWidgetLayoutPolicy.visibleCourseLimit(280))
    }

    @Test
    fun freeDayUsesAccurateEmptyState() {
        val model = ScheduleWidgetFormatter.build(
            schedule(courses = listOf(course("高等数学", "08:00", "09:35"))),
            LocalDate.of(2026, 7, 7),
            LocalTime.NOON
        )

        assertEquals("今天没有课，未来 7 天没有安排", model.status)
    }

    private fun schedule(courses: List<Course>) = ScheduleData(
        termCode = "2025-2026-3",
        termName = "2025-2026学年 小学期",
        courses = courses,
        termWeeks = mapOf(
            1 to TermWeek(1, "2026-07-06 00:00:00", "2026-07-12 23:59:59")
        )
    )

    private fun course(
        name: String,
        start: String,
        end: String,
        dayOfWeek: Int = 1
    ) = Course(
        name = name,
        code = name,
        credit = "2",
        teacher = "老师",
        classroom = "教5-101",
        campus = "小营校区",
        week = "1周",
        dayOfWeek = dayOfWeek,
        beginSection = 1,
        endSection = 2,
        beginTime = start,
        endTime = end
    )
}
