package edu.bistu.cs4029.ibistu

import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.login.LoginLogger
import edu.bistu.cs4029.ibistu.schedule.Course
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
        // fetchSchedule 发送 4 个请求
        server.enqueueJson(MockResponses.CURRENT_TERM_RESPONSE)  // cxmrxnxq.do
        server.enqueueJson(MockResponses.TERM_WEEKS_RESPONSE)    // getTermWeeks.do
        server.enqueueJson(MockResponses.SCHEDULE_CAMPUSES_RESPONSE)
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
        assertEquals("4.0", math.credit)
        assertEquals("张老师", math.teacher)
        assertEquals("教5-101", math.classroom)
        assertEquals("小营校区", math.campus)
        assertEquals("1", math.week)
        assertEquals(1, math.dayOfWeek)
        assertEquals(1, math.beginSection)
        assertEquals(2, math.endSection)
        assertEquals("08:00", math.beginTime)
        assertEquals("09:35", math.endTime)

        // 验证第二门课程
        val physics = schedule.courses[1]
        assertEquals("大学物理", physics.name)
        assertEquals("李老师", physics.teacher)
        assertEquals("1", physics.week)

        // 验证第三门课程
        val english = schedule.courses[2]
        assertEquals("大学英语", english.name)
        assertEquals("王老师", english.teacher)
        assertEquals("1", english.week)

        // 周次
        assertEquals(1, schedule.termWeeks.size)
        val week1 = schedule.termWeeks[1]
        assertNotNull(week1)
        assertEquals(1, week1!!.weekNumber)
        assertEquals("2026-02-23", week1.startDate)
        assertEquals("2026-03-01", week1.endDate)
    }

    @Test
    fun fetchSchedule_requestsWholeTermForEveryCampus() = runTest {
        server.enqueueJson(MockResponses.CURRENT_TERM_RESPONSE)
        server.enqueueJson("""{"datas":{"getTermWeeks":[
            {"serialNumber":1,"startDate":"2026-02-23","endDate":"2026-03-01"},
            {"serialNumber":20,"startDate":"2026-07-06","endDate":"2026-07-12"}
        ]}}""")
        server.enqueueJson("""{"code":"0","datas":[{"id":"10"},{"id":"20"}]}""")
        server.enqueueJson(MockResponses.SCHEDULE_RESPONSE.replace("\"week\": \"1\"", "\"week\": \"001111\""))
        server.enqueueJson(MockResponses.SCHEDULE_RESPONSE_2024_2.replace("\"week\": \"1\"", "\"week\": \"00001001\""))

        val schedule = fetchSchedule(createLogin())

        assertEquals(4, schedule.courses.size)
        assertEquals("3-6", schedule.courses.first().week)
        assertEquals("5,8", schedule.courses.last().week)
        assertEquals(5, server.mockWebServer.requestCount)
        val requests = (1..5).map { server.mockWebServer.takeRequest() }
        assertTrue(requests[2].url.toString().contains("getMyScheduledCampus.do?termCode=2025-2026-2"))
        assertEquals("XNXQDM=2025-2026-2&XQDM=10", requests[3].body!!.utf8())
        assertEquals("XNXQDM=2025-2026-2&XQDM=20", requests[4].body!!.utf8())
    }

    @Test(expected = IllegalStateException::class)
    fun fetchSchedule_propagatesBusinessFailure() = runTest {
        server.enqueueJson(MockResponses.CURRENT_TERM_RESPONSE)
        server.enqueueJson(MockResponses.TERM_WEEKS_RESPONSE)
        server.enqueueJson(MockResponses.SCHEDULE_CAMPUSES_RESPONSE)
        server.enqueueJson("""{"code":"-1","msg":"session expired"}""")
        fetchSchedule(createLogin())
        assertTrue("Expected a business failure", false)
    }

    @Test
    fun fetchSchedule_emptyArrangedListIsSuccessful() = runTest {
        server.enqueueJson(MockResponses.CURRENT_TERM_RESPONSE)
        server.enqueueJson(MockResponses.TERM_WEEKS_RESPONSE)
        server.enqueueJson(MockResponses.SCHEDULE_CAMPUSES_RESPONSE)
        server.enqueueJson("""{"code":"0","datas":{"getMyScheduleDetail":{"arrangedList":[]}}}""")
        assertTrue(fetchSchedule(createLogin()).courses.isEmpty())
    }

    @Test
    fun fetchSchedule_zeroBitmapDoesNotFallBackToTeacherWeeks() = runTest {
        server.enqueueJson(MockResponses.CURRENT_TERM_RESPONSE)
        server.enqueueJson(MockResponses.TERM_WEEKS_RESPONSE)
        server.enqueueJson(MockResponses.SCHEDULE_CAMPUSES_RESPONSE)
        server.enqueueJson(MockResponses.SCHEDULE_RESPONSE.replace("\"week\": \"1\"", "\"week\": \"000\""))
        assertTrue(fetchSchedule(createLogin()).courses.all { it.week.isEmpty() })
    }

    // ── 课表解析辅助方法 ──────────────────────────────────────

    @Test
    fun scheduleUtils_getWeekRange_fromFetchedCourses() {
        // 直接测试 getWeekRange 工具函数，不依赖网络
        val courses = listOf(
            Course(
                "高等数学",
                "MATH201",
                "4.0",
                "张老师",
                "教5-101",
                "小营校区",
                "1-16",
                1,
                1,
                2,
                "08:00",
                "09:35"
            ),
            Course(
                "大学物理",
                "PHY101",
                "3.0",
                "李老师",
                "理学院-201",
                "小营校区",
                "1",
                2,
                3,
                4,
                "10:00",
                "11:35"
            ),
            Course(
                "大学英语",
                "ENG301",
                "2.0",
                "王老师",
                "外语楼-302",
                "小营校区",
                "1",
                3,
                5,
                6,
                "14:00",
                "15:35"
            )
        )

        val weekRange = ScheduleUtils.getWeekRange(courses)
        assertEquals(1..17, weekRange) // max week = 16 + 1
    }

    // ── 课表无周次数据 ────────────────────────────────────────

    @Test
    fun fetchSchedule_emptyTermWeeks() = runTest {
        val login = createLogin()
        server.enqueueJson(MockResponses.CURRENT_TERM_RESPONSE)
        // getTermWeeks 返回空列表 → termWeeks 应为空
        server.enqueueJson("""{"datas":{"getTermWeeks":[]}}""")
        server.enqueueJson(MockResponses.SCHEDULE_CAMPUSES_RESPONSE)
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
        // 指定学期：学期名称 → 教学周 → 校区 → 整学期课表
        server.enqueueJson(MockResponses.XNXQ_LIST_RESPONSE)
        server.enqueueJson(MockResponses.TERM_WEEKS_RESPONSE)
        server.enqueueJson(MockResponses.SCHEDULE_CAMPUSES_RESPONSE)
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
