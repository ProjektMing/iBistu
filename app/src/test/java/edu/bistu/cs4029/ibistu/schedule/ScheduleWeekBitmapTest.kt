package edu.bistu.cs4029.ibistu.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

/** 文档中的周次位图与应用周次格式之间的契约。 */
class ScheduleWeekBitmapTest {
    @Test
    fun preservesLeadingZerosAndCompactsConsecutiveWeeks() {
        mapOf(
            "001111" to "3-6",
            "00001001" to "5,8",
            "000000001101" to "9-10,12",
            "11110110111" to "1-4,6-7,9-11",
            "1" to "1",
            "000" to "",
            "" to ""
        ).forEach { (bitmap, expected) ->
            assertEquals(expected, scheduleWeekText(bitmap))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonBinaryWeekValues() {
        scheduleWeekText("1-16")
    }

    @Test
    fun remainsCompatibleWithCourseFiltering() {
        assertEquals(listOf(5, 8), ScheduleUtils.getCourseWeeks(scheduleWeekText("00001001")).toList())
    }
}
