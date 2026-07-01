package edu.bistu.cs4029.ibistu

import org.junit.Assert.*
import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.ScheduleUtils
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun getWeekRange_usesActualMaximumWeek() {
        val courses = listOf(
            Course(
                name = "高数",
                code = "MATH101",
                credit = "4",
                teacher = "张老师",
                classroom = "A101",
                campus = "本部",
                week = "1-16周",
                dayOfWeek = 1,
                beginSection = 1,
                endSection = 2,
                beginTime = "08:00",
                endTime = "09:35"
            )
        )

        assertEquals(1..16, ScheduleUtils.getWeekRange(courses))
    }
}