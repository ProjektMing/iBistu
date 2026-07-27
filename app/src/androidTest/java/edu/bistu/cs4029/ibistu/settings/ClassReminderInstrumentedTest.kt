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
import org.junit.Assert.assertTrue
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
        prefs.clearScheduleSnapshot()
    }

    /** Removes reminder state and pending alarms after each test. */
    @After
    fun tearDown() {
        prefs.isClassReminderEnabled = false
        ClassReminderScheduler.cancelAll(context)
        prefs.clearScheduleSnapshot()
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
        val root = JSONObject(snapshot!!)
        val course = root.getJSONArray("courses").getJSONObject(0)
        assertEquals(1, root.getInt("schemaVersion"))
        assertEquals(1, root.getJSONArray("courses").length())
        assertTrue(root.getJSONObject("termWeeks").length() > 0)
        assertEquals(testCourse().code, course.getString("code"))
        assertEquals(testCourse().week, course.getString("week"))
        assertEquals(testCourse().classroom, course.getString("classroom"))
        assertEquals(10, prefs.classReminderLeadMinutes)
    }

    /** Missing required timetable fields make a persisted snapshot invalid instead of silent. */
    @Test
    fun parseSnapshotRejectsMissingRequiredCourseFields() {
        val invalid = """
            {
              "schemaVersion": 1,
              "courses": [{
                "name": "移动应用开发",
                "code": "CS4029",
                "week": "1周"
              }],
              "termWeeks": {}
            }
        """.trimIndent()

        assertTrue(runCatching { ClassReminderScheduler.parseSnapshot(invalid) }.isFailure)
    }

    /** A versionless snapshot is upgraded once so legacy alarm identities can be retired. */
    @Test
    fun rescheduleMigratesLegacySnapshotToCurrentSchema() {
        ClassReminderScheduler.schedule(
            context = context,
            courses = listOf(testCourse()),
            termWeeks = mapOf(
                1 to TermWeek(1, "2099-01-05 00:00:00", "2099-01-11 23:59:59")
            ),
            leadMinutes = 10
        )
        val legacySnapshot = JSONObject(prefs.classReminderScheduleSnapshot!!)
            .apply { remove("schemaVersion") }
            .toString()
        prefs.classReminderScheduleSnapshot = legacySnapshot
        prefs.isClassReminderEnabled = true

        ClassReminderScheduler.reschedule(context)

        assertEquals(
            1,
            JSONObject(prefs.classReminderScheduleSnapshot!!).getInt("schemaVersion")
        )
    }

    /** Reminder cleanup does not remove the independent automatic-mute snapshot. */
    @Test
    fun cancelAllClearsReminderSnapshotWithoutTouchingAutoMuteSnapshot() {
        prefs.classReminderScheduleSnapshot = """{"courses":[],"termWeeks":{}}"""
        prefs.scheduleSnapshot = """{"autoMute":"kept"}"""

        ClassReminderScheduler.cancelAll(context)

        assertNull(prefs.classReminderScheduleSnapshot)
        assertEquals("""{"autoMute":"kept"}""", prefs.scheduleSnapshot)
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
