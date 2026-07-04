package edu.bistu.cs4029.ibistu

import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.login.LoginLogger
import edu.bistu.cs4029.ibistu.schedule.CampusCodes
import edu.bistu.cs4029.ibistu.schedule.EmptyClassroom
import edu.bistu.cs4029.ibistu.schedule.EmptyClassroomQuery
import edu.bistu.cs4029.ibistu.schedule.fetchEmptyClassrooms
import edu.bistu.cs4029.ibistu.schedule.sectionDisplayName
import edu.bistu.cs4029.ibistu.testing.MockServerTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 空闲教室查询的仪器测试。
 *
 * 使用 MockWebServer 模拟教务系统 jsjy 模块，验证 fetchEmptyClassrooms 的完整流程。
 */
class EmptyClassroomRepositoryInstrumentedTest {

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

    // ── mock JSON ─────────────────────────────────────────────

    /** jsjy 模块首页（HTML 即可，只需 200 OK） */
    private val JSJY_INDEX = "<html><body>jsjy</body></html>"

    /** 空闲教室查询响应（3 间教室） */
    private val CLASSROOM_RESPONSE = """
    {
        "code": "0",
        "datas": {
            "cxkxjs": {
                "totalSize": 3,
                "pageNumber": 1,
                "pageSize": 200,
                "rows": [
                    {
                        "JASMC": "WLA-106",
                        "JXLDM": "501",
                        "JXLDM_DISPLAY": "文理楼A座 沙河校区",
                        "XXXQDM": "10",
                        "XXXQDM_DISPLAY": "沙河校区",
                        "LC": 1.0,
                        "SKZWS": 40.0,
                        "KSZWS": 20.0,
                        "JASLXDM": "02",
                        "JASLXDM_DISPLAY": "多媒体",
                        "DWDM": "17",
                        "DWDM_DISPLAY": "教务处",
                        "JASDM": "050101",
                        "WID": "a74cd090-5f01-464b-9f28-44f440431448",
                        "BZ": null,
                        "SFYXPK": "1",
                        "SFYXJY": "1",
                        "SFYXKS": "1",
                        "ZT": "1"
                    },
                    {
                        "JASMC": "XXB-301",
                        "JXLDM": "503",
                        "JXLDM_DISPLAY": "信息楼B座 沙河校区",
                        "XXXQDM": "10",
                        "XXXQDM_DISPLAY": "沙河校区",
                        "LC": 3.0,
                        "SKZWS": 60.0,
                        "KSZWS": 45.0,
                        "JASLXDM": "01",
                        "JASLXDM_DISPLAY": "普通",
                        "DWDM": "17",
                        "DWDM_DISPLAY": "教务处",
                        "JASDM": "050301",
                        "WID": "b85cd191-6f12-575c-0g39-55g551542559",
                        "BZ": "智慧教室",
                        "SFYXPK": "1",
                        "SFYXJY": "0",
                        "SFYXKS": "1",
                        "ZT": "1"
                    },
                    {
                        "JASMC": "WLA-513",
                        "JXLDM": "501",
                        "JXLDM_DISPLAY": "文理楼A座 沙河校区",
                        "XXXQDM": "10",
                        "XXXQDM_DISPLAY": "沙河校区",
                        "LC": 5.0,
                        "SKZWS": 80.0,
                        "KSZWS": 40.0,
                        "JASLXDM": "02",
                        "JASLXDM_DISPLAY": "多媒体",
                        "DWDM": "17",
                        "DWDM_DISPLAY": "教务处",
                        "JASDM": "050113",
                        "WID": "9acc0e4a-3483-4a39-a410-150efe0ab721",
                        "BZ": null,
                        "SFYXPK": "1",
                        "SFYXJY": "1",
                        "SFYXKS": "1",
                        "ZT": "1"
                    }
                ]
            }
        }
    }
    """.trimIndent()

    // ── 成功路径 ──────────────────────────────────────────────

    @Test
    fun fetchEmptyClassrooms_parsesAllFields() = runTest {
        val login = createLogin()
        server.enqueueJson(JSJY_INDEX)         // jsjy 首页
        server.enqueueJson(CLASSROOM_RESPONSE)  // cxkxjs.do

        val query = EmptyClassroomQuery(
            campusCode = "10",
            campusName = "沙河校区",
            startDate = "2026-07-06",
            endDate = "2026-07-06",
            startSection = 3,
            endSection = 3
        )
        val rooms = fetchEmptyClassrooms(login, query)

        assertEquals(3, rooms.size)

        // 第一间教室
        val room1 = rooms[0]
        assertEquals("WLA-106", room1.name)
        assertEquals("501", room1.buildingCode)
        assertEquals("文理楼A座 沙河校区", room1.buildingName)
        assertEquals("10", room1.campusCode)
        assertEquals("沙河校区", room1.campusName)
        assertEquals(1, room1.floor)
        assertEquals(40, room1.classSeats)
        assertEquals(20, room1.examSeats)
        assertEquals("02", room1.typeCode)
        assertEquals("多媒体", room1.typeName)
        assertEquals("050101", room1.roomCode)
        assertTrue(room1.allowSchedule)
        assertTrue(room1.allowBorrow)
        assertTrue(room1.allowExam)
        assertEquals("", room1.note)

        // 第二间教室（有备注、不可借用）
        val room2 = rooms[1]
        assertEquals("XXB-301", room2.name)
        assertEquals("智慧教室", room2.note)
        assertEquals("信息楼B座 沙河校区", room2.buildingName)
        assertEquals(3, room2.floor)
        assertEquals(60, room2.classSeats)
        assertEquals("普通", room2.typeName)
        assertFalse(room2.allowBorrow)

        // 第三间教室
        val room3 = rooms[2]
        assertEquals("WLA-513", room3.name)
        assertEquals(5, room3.floor)
        assertEquals(80, room3.classSeats)
    }

    // ── 空响应 ────────────────────────────────────────────────

    @Test
    fun fetchEmptyClassrooms_emptyResult() = runTest {
        val login = createLogin()
        server.enqueueJson(JSJY_INDEX)
        server.enqueueJson("""{"code":"0","datas":{"cxkxjs":{"totalSize":0,"pageNumber":1,"pageSize":200,"rows":[]}}}""")

        val query = EmptyClassroomQuery(
            campusCode = "10",
            campusName = "沙河校区",
            startDate = "2026-07-06",
            endDate = "2026-07-06",
            startSection = 9,
            endSection = 9
        )
        val rooms = fetchEmptyClassrooms(login, query)

        assertTrue("应为空列表", rooms.isEmpty())
    }

    // ── API 返回错误码 ────────────────────────────────────────

    @Test
    fun fetchEmptyClassrooms_apiError() = runTest {
        val login = createLogin()
        server.enqueueJson(JSJY_INDEX)
        server.enqueueJson("""{"code":"1","msg":"参数错误"}""")

        val query = EmptyClassroomQuery(
            campusCode = "10",
            campusName = "沙河校区",
            startDate = "2026-07-06",
            endDate = "2026-07-06",
            startSection = 3,
            endSection = 3
        )
        val rooms = fetchEmptyClassrooms(login, query)

        assertTrue("API 返回错误码时应返回空列表", rooms.isEmpty())
    }

    // ── 工具函数 ──────────────────────────────────────────────

    @Test
    fun campusCodes_mapsCorrectly() {
        assertEquals("10", CampusCodes.codeOf("沙河校区"))
        assertEquals("20", CampusCodes.codeOf("小营校区"))
        assertEquals(null, CampusCodes.codeOf("未知校区"))
    }

    @Test
    fun sectionDisplayName_generatesCorrectly() {
        assertEquals("第1节", sectionDisplayName(1))
        assertEquals("第3节", sectionDisplayName(3))
        assertEquals("第12节", sectionDisplayName(12))
    }
}
