package edu.bistu.cs4029.ibistu

import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.login.LoginLogger
import edu.bistu.cs4029.ibistu.schedule.ScheduleUtils
import edu.bistu.cs4029.ibistu.schedule.fetchSchedule
import edu.bistu.cs4029.ibistu.schedule.fetchTermList
import edu.bistu.cs4029.ibistu.testing.MockResponses
import edu.bistu.cs4029.ibistu.testing.MockServerTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 课表获取的仪器测试。
 *
 * 使用 MockWebServer 模拟教务系统 API，验证 fetchSchedule 的完整流程和解析正确性。
 */
class ScheduleRepositoryInstrumentedTest {

    @get:Rule
    val server = MockServerTestRule()

    private val testLogger = object : LoginLogger {
        override fun debug(msg: String) { println("[TEST] $msg") }
        override fun info(msg: String) { println("[TEST] $msg") }
        override fun warn(msg: String) { println("[TEST] $msg") }
        override fun error(msg: String) { println("[TEST] $msg") }
    }

    private fun createLogin() = BistuLogin(
        logger = testLogger,
        injectedClient = server.newClient()
    )

    // ── 完整课表获取 ──────────────────────────────────────────

    @Test
    fun fetchSchedule_parsesAllFields() = runTest {
        val login = createLogin()
        // fetchSchedule 发送 3 个 POST 请求
        server.enqueueJson(MockResponses.CURRENT_TERM_RESPONSE)  // cxmrxnxq.do
        server.enqueueJson(MockResponses.TERM_WEEKS_RESPONSE)    // getTermWeeks.do
        server.enqueueJson(MockResponses.SCHEDULE_RESPONSE)      // getMyScheduleDetail.do

        val schedule = fetchSchedule(login)

        // 基本字段
        assertEquals("2025-2026-2", schedule.termCode)
        assertEquals("2025-2026学年第2学期", schedule.termName)

        // 课程列表
        assertEquals(3, schedule.courses.size)

        val math = schedule.courses[0]
        assertEquals("高等数学", math.name)
        assertEquals("MATH201", math.code)
        assertEquals("4", math.credit)
        assertEquals("1-16周 张老师", math.teacher)
        assertEquals("教5-101", math.classroom)
        assertEquals("小营校区", math.campus)
        assertEquals("1-16", math.week)
        assertEquals(1, math.dayOfWeek)
        assertEquals(1, math.beginSection)
        assertEquals(2, math.endSection)
        assertEquals("08:00", math.beginTime)
        assertEquals("09:35", math.endTime)

        // 周次
        assertEquals(4, schedule.termWeeks.size)
        val week1 = schedule.termWeeks[1]
        assertNotNull(week1)
        assertEquals(1, week1!!.weekNumber)
        assertEquals("2026-02-23", week1.startDate)
        assertEquals("2026-03-01", week1.endDate)
    }

    // ── 课表解析辅助方法 ──────────────────────────────────────

    @Test
    fun scheduleUtils_getWeekRange_fromFetchedCourses() = runTest {
        val login = createLogin()
        server.enqueueJson(MockResponses.CURRENT_TERM_RESPONSE)
        server.enqueueJson(MockResponses.TERM_WEEKS_RESPONSE)
        server.enqueueJson(MockResponses.SCHEDULE_RESPONSE)

        val schedule = fetchSchedule(login)

        val weekRange = ScheduleUtils.getWeekRange(schedule.courses)
        assertEquals(1..17, weekRange) // max week = 16 + 1
    }

    // ── 课表无周次数据 ────────────────────────────────────────

    @Test
    fun fetchSchedule_emptyTermWeeks() = runTest {
        val login = createLogin()
        server.enqueueJson(MockResponses.CURRENT_TERM_RESPONSE)
        // getTermWeeks 返回错误（非 JSON → 触发 onFailure → 回退空 map）
        server.enqueueJson("""{"datas":{"getTermWeeks":[]}}""")
        server.enqueueJson(MockResponses.SCHEDULE_RESPONSE)

        val schedule = fetchSchedule(login)

        assertTrue("termWeeks should be empty when API fails", schedule.termWeeks.isEmpty())
        assertEquals(3, schedule.courses.size)
    }

    // ── 学期列表 ──────────────────────────────────────────────

    @Test
    fun fetchTermList_parsesAllTerms() = runTest {
        val login = createLogin()
        server.enqueueJson(MockResponses.XNXQ_LIST_RESPONSE)

        val terms = fetchTermList(login)

        assertEquals(5, terms.size)
        // 列表倒序排列（最新在前），所以第一个是 2025-2026-3
        val first = terms[0]
        assertEquals("2025-2026-3", first.termCode)
        assertEquals("2025-2026学年 小学期", first.termName)

        val last = terms[4]
        assertEquals("2024-2025-1", last.termCode)
        assertEquals("2024-2025学年 第一学期", last.termName)
    }

    // ── 指定学期获取课表 ──────────────────────────────────────

    @Test
    fun fetchSchedule_withSpecifiedTermCode() = runTest {
        val login = createLogin()
        // 当指定 termCode 时：GET xnxq.do → POST getTermWeeks → POST getMyScheduleDetail
        server.enqueueJson(MockResponses.XNXQ_LIST_RESPONSE)
        server.enqueueJson(MockResponses.TERM_WEEKS_RESPONSE)
        server.enqueueJson(MockResponses.SCHEDULE_RESPONSE_2024_2)

        val schedule = fetchSchedule(login, "2024-2025-2")

        assertEquals("2024-2025-2", schedule.termCode)
        assertEquals("2024-2025学年 第二学期", schedule.termName)
        assertEquals(1, schedule.courses.size)

        val course = schedule.courses[0]
        assertEquals("数据结构", course.name)
        assertEquals("CS201", course.code)
    }
}
