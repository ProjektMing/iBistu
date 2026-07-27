package edu.bistu.cs4029.ibistu

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import edu.bistu.cs4029.ibistu.common.preferences.AppPreferences
import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.TermWeek
import edu.bistu.cs4029.ibistu.settings.ClassReminderScheduler
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ClassReminderInstrumentedTest {
    private lateinit var context: Context
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = AppPreferences(context)
        prefs.isClassReminderEnabled = false
        ClassReminderScheduler.cancelAll(context)
    }

    @After
    fun tearDown() {
        prefs.isClassReminderEnabled = false
        ClassReminderScheduler.cancelAll(context)
    }

    @Test
    fun schedulePersistsRestorableSnapshotAndLeadTime() {
        ClassReminderScheduler.schedule(
            context = context,
            courses = listOf(testCourse()),
            termWeeks = mapOf(
                1 to TermWeek(1, "2099-01-05 00:00:00", "2099-01-11 23:59:59")
            ),
            leadMinutes = 10
        )

        val snapshot = prefs.classReminderScheduleSnapshot
        assertNotNull(snapshot)
        assertEquals(1, JSONObject(snapshot!!).getJSONArray("courses").length())
        assertEquals(10, prefs.classReminderLeadMinutes)
    }

    @Test
    fun cancelAllClearsReminderSnapshotWithoutTouchingAutoMuteSnapshot() {
        prefs.classReminderScheduleSnapshot = """{"courses":[],"termWeeks":{}}"""
        prefs.scheduleSnapshot = """{"autoMute":"kept"}"""

        ClassReminderScheduler.cancelAll(context)

        assertNull(prefs.classReminderScheduleSnapshot)
        assertEquals("""{"autoMute":"kept"}""", prefs.scheduleSnapshot)
        prefs.clearScheduleSnapshot()
    }

    private fun testCourse() = Course(
        name = "移动应用开发",
        code = "CS4029",
        credit = "2",
        teacher = "张老师",
        classroom = "教三-301",
        campus = "沙河校区",
        week = "1周",
        dayOfWeek = 3,
        beginSection = 3,
        endSection = 4,
        beginTime = "10:00",
        endTime = "11:35"
    )
}
