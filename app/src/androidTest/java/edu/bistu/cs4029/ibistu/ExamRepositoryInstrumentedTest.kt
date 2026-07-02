package edu.bistu.cs4029.ibistu

import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.login.LoginLogger
import edu.bistu.cs4029.ibistu.schedule.fetchExams
import edu.bistu.cs4029.ibistu.testing.MockResponses
import edu.bistu.cs4029.ibistu.testing.MockServerTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 考试安排获取的仪器测试。
 *
 * 使用 MockWebServer 模拟教务系统 API，验证 fetchExams 的「探测 → 命中 → 解析」流程。
 */
class ExamRepositoryInstrumentedTest {

    @get:Rule
    val server = MockServerTestRule()

    private val testLogger = object : LoginLogger {
        override fun debug(msg: String) {
            println("[TEST] $msg")
        }

        override fun info(msg: String) {
            println("[TEST] $msg")
        }

        override fun warn(msg: String) {
            println("[TEST] $msg")
        }

        override fun error(msg: String) {
            println("[TEST] $msg")
        }
    }

    private fun createLogin() = BistuLogin(
        logger = testLogger,
        injectedClient = server.newClient(),
        injectedRedirectClient = server.newRedirectClient()
    )

    // ── 完整考试获取 ──────────────────────────────────────────

    @Test
    fun fetchExams_parsesCorrectly() = runTest {
        val login = createLogin()
        // 1) prime wdkwapp page (redirectClient GET)
        server.enqueueJson("{}")
        // 2) explicit endpoint POST (first one hits)
        server.enqueueJson(MockResponses.EXAM_RESPONSE)

        val exams = fetchExams(login, "2025-2026-3")

        assertEquals(2, exams.size)

        val exam1 = exams[0]
        assertEquals("高等数学", exam1.courseName)
        assertEquals("2026-07-06", exam1.examDate)
        assertEquals("09:00-11:00", exam1.examTime)
        assertEquals("沙河校区文理楼A-101", exam1.location)
        assertEquals("12", exam1.seatNumber)
        assertEquals("期末考试", exam1.examType)
        assertEquals("沙河校区", exam1.campus)

        val exam2 = exams[1]
        assertEquals("大学物理", exam2.courseName)
        assertEquals("2026-07-07", exam2.examDate)
        assertEquals("14:00-16:00", exam2.examTime)
        assertEquals("沙河校区文理楼B-202", exam2.location)
        assertEquals("8", exam2.seatNumber)
        assertEquals("期末考试", exam2.examType)
        assertEquals("沙河校区", exam2.campus)
    }

    // ── 空考试列表（端点命中但无考试） ─────────────────────────

    @Test
    fun fetchExams_emptyWhenNoExams() = runTest {
        val login = createLogin()
        // prime page 失败（非 JSON 响应 → 被 catch 吞掉，继续探测）
        server.enqueueJson("not json")
        // explicit endpoint POST 返回空 rows
        server.enqueueJson(MockResponses.EMPTY_EXAM_RESPONSE)

        val exams = fetchExams(login, "2025-2026-3")
        assertTrue("Exams should be empty when API returns empty rows", exams.isEmpty())

        // ── termCode 不能为空 ─────────────────────────────────────

        @Test(expected = IllegalArgumentException::class)
        fun fetchExams_throwsOnBlankTermCode() = runTest {
            val login = createLogin()
            fetchExams(login, "   ")
        }
    }
}

