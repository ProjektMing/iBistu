package edu.bistu.cs4029.ibistu.settings

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import edu.bistu.cs4029.ibistu.common.preferences.AppPreferences
import edu.bistu.cs4029.ibistu.common.state.AppState
import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.ScheduleData
import edu.bistu.cs4029.ibistu.schedule.TermWeek
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Verifies reminder snapshot persistence, cleanup isolation and schedule replacement on Android. */
class ClassReminderInstrumentedTest {
    private lateinit var context: Context
    private lateinit var prefs: AppPreferences

    /** Resets reminder state before each test to prevent alarms leaking between cases. */
    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = AppPreferences(context)
        prefs.isClassReminderEnabled = false
        ClassReminderScheduler.cancelAll(context)
    }

    /** Removes reminder state and pending alarms after each test. */
    @After
    fun tearDown() {
        prefs.isClassReminderEnabled = false
        ClassReminderScheduler.cancelAll(context)
    }

    /** Scheduling persists enough timetable data and the selected lead time for restoration. */
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

    /** Reminder cleanup does not remove the independent automatic-mute snapshot. */
    @Test
    fun cancelAllClearsReminderSnapshotWithoutTouchingAutoMuteSnapshot() {
        prefs.classReminderScheduleSnapshot = """{"courses":[],"termWeeks":{}}"""
        prefs.scheduleSnapshot = """{"autoMute":"kept"}"""

        ClassReminderScheduler.cancelAll(context)

        assertNull(prefs.classReminderScheduleSnapshot)
        assertEquals("""{"autoMute":"kept"}""", prefs.scheduleSnapshot)
        prefs.clearScheduleSnapshot()
    }

    /** Applying an empty timetable removes reminders left by the previously selected term. */
    @Test
    fun applyingEmptyScheduleCancelsPreviousReminderSnapshot() {
        prefs.isClassReminderEnabled = true
        prefs.classReminderScheduleSnapshot = """{"courses":[],"termWeeks":{}}"""
        val state = AppState(context)

        state.applySchedule(
            ScheduleData(
                termCode = "empty-term",
                termName = "无课学期",
                courses = emptyList(),
                termWeeks = emptyMap()
            )
        )

        assertNull(prefs.classReminderScheduleSnapshot)
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
