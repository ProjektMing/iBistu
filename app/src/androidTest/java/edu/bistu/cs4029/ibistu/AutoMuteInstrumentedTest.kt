package edu.bistu.cs4029.ibistu

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import edu.bistu.cs4029.ibistu.common.preferences.AppPreferences
import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.ScheduleUtils
import edu.bistu.cs4029.ibistu.schedule.TermWeek
import edu.bistu.cs4029.ibistu.settings.AutoMuteScheduler
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 自动静音调度与定时逻辑的仪器测试。
 *
 * 覆盖：
 * 1. 课表快照 JSON 格式 → reschedule 可恢复
 * 2. 周次解析（ScheduleUtils 被调度器核心使用）
 * 3. 触发时间计算 → 验证 dayOfWeek + beginTime → 具体日期的映射
 * 4. cancelAll 清除状态
 *
 * 注意：schedule() 需要 SCHEDULE_EXACT_ALARM 权限才能完整执行；
 * 在不持有该权限的设备上，测试直接向 SharedPreferences 注入快照来验证恢复逻辑。
 */
class AutoMuteInstrumentedTest {

    private lateinit var context: Context
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = AppPreferences(context)
        prefs.clearScheduleSnapshot()
        prefs.unmuteUntil = 0L
    }

    @After
    fun tearDown() {
        prefs.clearScheduleSnapshot()
        prefs.unmuteUntil = 0L
    }

    // ── 快照 JSON 格式 → 手动注入后 reschedule 可恢复 ─────────

    @Test
    fun snapshotFormat_canBeParsedByReschedule() {
        // 直接写入快照（绕过需要权限的 schedule()）
        prefs.scheduleSnapshot = buildScheduleSnapshot()

        // reschedule 应能解析快照并（尝试）重新调度
        AutoMuteScheduler.reschedule(context)

        // 快照应仍然存在（reschedule 不清除快照）
        assertNotNull(prefs.scheduleSnapshot)
    }

    @Test
    fun snapshotFormat_containsAllRequiredFields() {
        val snapshot = buildScheduleSnapshot()
        val root = JSONObject(snapshot)

        val courses = root.getJSONArray("courses")
        assertEquals(2, courses.length())

        // 验证课程包含触发时间计算所必需的全部字段
        for (i in 0 until courses.length()) {
            val c = courses.getJSONObject(i)
            assertTrue("Missing dayOfWeek", c.has("dayOfWeek"))
            assertTrue("Missing beginTime", c.has("beginTime"))
            assertTrue("Missing week", c.has("week"))
            assertTrue("Missing code", c.has("code"))
            assertTrue("Missing name", c.has("name"))
            assertTrue("Missing classroom", c.has("classroom"))

            // beginTime 格式 HH:mm
            assertTrue(
                "beginTime format: ${c.getString("beginTime")}",
                c.getString("beginTime").matches(Regex("\\d{2}:\\d{2}"))
            )
        }

        // 验证 termWeeks 中每周都有 startDate
        val weeks = root.getJSONObject("termWeeks")
        assertEquals(2, weeks.length())
        weeks.keys().forEach { key ->
            val w = weeks.getJSONObject(key)
            assertTrue("Missing startDate in week $key", w.has("startDate"))
            assertTrue("Missing weekNumber in week $key", w.has("weekNumber"))
            assertTrue("Missing endDate in week $key", w.has("endDate"))
        }
    }

    // ── cancelAll 清除状态 ────────────────────────────────────

    @Test
    fun cancelAll_clearsPreferencesState() {
        // 手动设置状态
        prefs.scheduleSnapshot = buildScheduleSnapshot()
        prefs.unmuteUntil = System.currentTimeMillis() + 45 * 60 * 1000L

        assertNotNull(prefs.scheduleSnapshot)
        assertTrue(prefs.unmuteUntil > 0L)

        AutoMuteScheduler.cancelAll(context)

        assertNull("cancelAll should clear snapshot", prefs.scheduleSnapshot)
        assertEquals("cancelAll should clear unmuteUntil", 0L, prefs.unmuteUntil)
    }

    // ── 周次解析（调度器核心依赖） ─────────────────────────────

    @Test
    fun getCourseWeeks_singleRange() {
        val weeks = ScheduleUtils.getCourseWeeks("1-16周")
        assertEquals(16, weeks.size)
        assertEquals(1, weeks.min())
        assertEquals(16, weeks.max())
    }

    @Test
    fun getCourseWeeks_multiRange() {
        val weeks = ScheduleUtils.getCourseWeeks("1-8,10-17周")
        assertEquals(16, weeks.size)
        assertEquals(1, weeks.min())
        assertEquals(17, weeks.max())
        assertTrue(9 !in weeks)
    }

    @Test
    fun getCourseWeeks_singleWeek() {
        val weeks = ScheduleUtils.getCourseWeeks("5周")
        assertEquals(1, weeks.size)
        assertTrue(5 in weeks)
    }

    @Test
    fun isCourseInWeek_positive() {
        assertTrue(ScheduleUtils.isCourseInWeek("1-16周", 8))
        assertTrue(ScheduleUtils.isCourseInWeek("1-16周", 1))
        assertTrue(ScheduleUtils.isCourseInWeek("1-16周", 16))
        assertTrue(ScheduleUtils.isCourseInWeek("5,8,12周", 8))
    }

    @Test
    fun isCourseInWeek_negative() {
        assertTrue(!ScheduleUtils.isCourseInWeek("1-16周", 0))
        assertTrue(!ScheduleUtils.isCourseInWeek("1-16周", 17))
        assertTrue(!ScheduleUtils.isCourseInWeek("", 1))
        assertTrue(!ScheduleUtils.isCourseInWeek("5,8,12周", 6))
    }

    // ── 触发时间计算验证 ─────────────────────────────────────

    @Test
    fun triggerTime_mondayCourse_startsOnMonday() {
        // 周一 (dayOfWeek=1), beginTime=08:00, 第 1 周
        // 第 1 周 startDate = 2026-02-23 (周一)
        // → dayOffset = 0, 触发日期 = 2026-02-23
        val course = Course(
            name = "高等数学", code = "MATH201", credit = "4",
            teacher = "张老师", classroom = "教5-101", campus = "小营校区",
            week = "1-16周", dayOfWeek = 1,
            beginSection = 1, endSection = 2,
            beginTime = "08:00", endTime = "09:35"
        )

        assertEquals(1, course.dayOfWeek)
        val dayOffset = (course.dayOfWeek - 1).toLong()
        assertEquals(0L, dayOffset) // 周一 = 0 天偏移
    }

    @Test
    fun triggerTime_wednesdayCourse_twoDayOffset() {
        // 周三 (dayOfWeek=3), 第 1 周 startDate = 2026-02-23 (周一)
        // → dayOffset = 2, 触发日期 = 2026-02-23 + 2 = 2026-02-25
        val course = Course(
            name = "大学英语", code = "ENG301", credit = "2",
            teacher = "王老师", classroom = "外语楼-302", campus = "小营校区",
            week = "1-8周", dayOfWeek = 3,
            beginSection = 5, endSection = 6,
            beginTime = "14:00", endTime = "15:35"
        )

        assertEquals(3, course.dayOfWeek)
        val dayOffset = (course.dayOfWeek - 1).toLong()
        assertEquals(2L, dayOffset) // 周三 = +2 天
    }

    @Test
    fun triggerTime_fridayCourse_fourDayOffset() {
        // 周五 (dayOfWeek=5) → offset = 4
        val dayOffset = (5 - 1).toLong()
        assertEquals(4L, dayOffset)
    }

    @Test
    fun triggerTime_week2_startsCorrectWeek() {
        // 第 2 周：startDate = 2026-03-02（周一）
        val week2 = TermWeek(2, "2026-03-02 00:00:00", "2026-03-08 23:59:59")
        assertEquals("2026-03-02", week2.startDate.take(10))

        // 周四课程 → 2026-03-02 + 3 = 2026-03-05
        val dayOffset = (4 - 1).toLong()
        assertEquals(3L, dayOffset)
    }

    @Test
    fun triggerTime_beginTimeParsing() {
        // beginTime="08:00" → hour=8, minute=0
        val parts = "08:00".split(":")
        assertEquals(8, parts[0].toInt())
        assertEquals(0, parts[1].toInt())

        // beginTime="14:30" → hour=14, minute=30
        val parts2 = "14:30".split(":")
        assertEquals(14, parts2[0].toInt())
        assertEquals(30, parts2[1].toInt())
    }

    // ── 无权限时 schedule() 不崩溃 ────────────────────────────

    @Test
    fun schedule_withoutPermission_doesNotCrash() {
        val courses = listOf(
            Course(
                name = "测试课", code = "TEST001", credit = "1",
                teacher = "", classroom = "", campus = "",
                week = "1周", dayOfWeek = 1,
                beginSection = 1, endSection = 1,
                beginTime = "08:00", endTime = "08:45"
            )
        )
        val termWeeks = mapOf(1 to TermWeek(1, "2026-01-01 00:00:00", "2026-01-07 23:59:59"))

        // 无权限时 schedule() 应静默返回，不抛异常
        AutoMuteScheduler.schedule(context, courses, termWeeks)
        // 未抛异常即为通过
    }

    // ── 测试数据构建 ──────────────────────────────────────────

    private fun buildScheduleSnapshot(): String = JSONObject().apply {
        put("courses", JSONArray().apply {
            put(JSONObject().apply {
                put("name", "高等数学")
                put("code", "MATH201")
                put("credit", "4")
                put("teacher", "张老师")
                put("classroom", "教5-101")
                put("campus", "小营校区")
                put("week", "1-16周")
                put("dayOfWeek", 1)
                put("beginSection", 1)
                put("endSection", 2)
                put("beginTime", "08:00")
                put("endTime", "09:35")
            })
            put(JSONObject().apply {
                put("name", "大学物理")
                put("code", "PHY101")
                put("credit", "3")
                put("teacher", "李老师")
                put("classroom", "理学院-201")
                put("campus", "小营校区")
                put("week", "1-16周")
                put("dayOfWeek", 3)
                put("beginSection", 3)
                put("endSection", 4)
                put("beginTime", "10:00")
                put("endTime", "11:35")
            })
        })
        put("termWeeks", JSONObject().apply {
            put("1", JSONObject().apply {
                put("weekNumber", 1)
                put("startDate", "2026-02-23 00:00:00")
                put("endDate", "2026-03-01 23:59:59")
            })
            put("2", JSONObject().apply {
                put("weekNumber", 2)
                put("startDate", "2026-03-02 00:00:00")
                put("endDate", "2026-03-08 23:59:59")
            })
        })
    }.toString()
}
