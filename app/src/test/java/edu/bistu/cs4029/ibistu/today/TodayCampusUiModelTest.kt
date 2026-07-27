package edu.bistu.cs4029.ibistu.today

import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.Exam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** 今日校园首页展示模型测试。 */
class TodayCampusUiModelTest {

    @Test
    fun buildTodayCampusUiModel_highlightsCurrentCourse() {
        val model = buildTodayCampusUiModel(
            courses = listOf(
                course("高等数学", day = 1, begin = "08:00", end = "09:35"),
                course("大学物理", day = 1, begin = "10:00", end = "11:35"),
                course("周二课程", day = 2, begin = "08:00", end = "09:35")
            ),
            exams = emptyList(),
            currentWeek = 3,
            now = LocalDateTime.of(2026, 7, 27, 9, 20)
        )

        assertEquals(listOf("高等数学", "大学物理"), model.todayCourses.map { it.name })
        assertEquals("高等数学", model.highlightedCourse?.name)
        assertEquals("正在上课", model.highlightedLabel)
        assertEquals("15 分钟后下课", model.highlightedStatus)
        assertTrue(model.highlightedProgress > 0.8f)
    }

    @Test
    fun buildTodayCampusUiModel_highlightsNextCourseAndUpcomingExam() {
        val model = buildTodayCampusUiModel(
            courses = listOf(
                course("高等数学", day = 1, begin = "08:00", end = "09:35"),
                course("大学物理", day = 1, begin = "10:00", end = "11:35")
            ),
            exams = listOf(
                exam("大学物理", "2026-07-30"),
                exam("高等数学", "2026-07-29")
            ),
            currentWeek = 3,
            now = LocalDateTime.of(2026, 7, 27, 9, 40)
        )

        assertEquals("大学物理", model.highlightedCourse?.name)
        assertEquals("下一节", model.highlightedLabel)
        assertEquals("20 分钟后开始", model.highlightedStatus)
        assertEquals("高等数学", model.nextExam?.courseName)
        assertEquals(2L, model.nextExamDays)
    }

    @Test
    fun buildTodayCampusUiModel_ignoresCoursesOutsideCurrentWeek() {
        val model = buildTodayCampusUiModel(
            courses = listOf(
                course("本周课程", day = 1, begin = "08:00", end = "09:35", week = "3周"),
                course("下周课程", day = 1, begin = "10:00", end = "11:35", week = "4周")
            ),
            exams = emptyList(),
            currentWeek = 3,
            now = LocalDateTime.of(2026, 7, 27, 12, 0)
        )

        assertEquals(listOf("本周课程"), model.todayCourses.map { it.name })
        assertNull(model.highlightedCourse)
        assertEquals("今日课程已完成", model.highlightedLabel)
    }

    private fun course(
        name: String,
        day: Int,
        begin: String,
        end: String,
        week: String = "1-16周"
    ) = Course(
        name = name,
        code = "TEST",
        credit = "2",
        teacher = "张老师",
        classroom = "WLA-106",
        campus = "沙河校区",
        week = week,
        dayOfWeek = day,
        beginSection = 1,
        endSection = 2,
        beginTime = begin,
        endTime = end
    )

    private fun exam(name: String, date: String) = Exam(
        courseName = name,
        examDate = date,
        examTime = "09:00-11:00",
        location = "WLA-106",
        seatNumber = "12",
        examType = "期末考试",
        campus = "沙河校区"
    )
}
