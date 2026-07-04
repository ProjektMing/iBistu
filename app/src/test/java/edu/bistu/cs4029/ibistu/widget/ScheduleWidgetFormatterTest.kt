package edu.bistu.cs4029.ibistu.widget

import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.ScheduleData
import edu.bistu.cs4029.ibistu.schedule.TermWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
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

        assertEquals("正在上课 · 高等数学", model.status)
        assertTrue(model.courses.single().highlighted)
        assertEquals("教5-101", model.courses.single().room)
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

        assertEquals("下一节 · 10:40 大学物理", model.status)
        assertEquals(listOf(false, true), model.courses.map { it.highlighted })
    }

    private fun schedule(courses: List<Course>) = ScheduleData(
        termCode = "2025-2026-3",
        termName = "2025-2026学年 小学期",
        courses = courses,
        termWeeks = mapOf(
            1 to TermWeek(1, "2026-07-06 00:00:00", "2026-07-12 23:59:59")
        )
    )

    private fun course(name: String, start: String, end: String) = Course(
        name = name,
        code = name,
        credit = "2",
        teacher = "老师",
        classroom = "教5-101",
        campus = "小营校区",
        week = "1周",
        dayOfWeek = 1,
        beginSection = 1,
        endSection = 2,
        beginTime = start,
        endTime = end
    )
}
