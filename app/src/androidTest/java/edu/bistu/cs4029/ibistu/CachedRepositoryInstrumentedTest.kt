package edu.bistu.cs4029.ibistu

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import edu.bistu.cs4029.ibistu.login.AppDatabase
import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.login.LoginLogger
import edu.bistu.cs4029.ibistu.schedule.CachedExamRepository
import edu.bistu.cs4029.ibistu.schedule.CachedScheduleRepository
import edu.bistu.cs4029.ibistu.testing.MockResponses
import edu.bistu.cs4029.ibistu.testing.MockServerTestRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 缓存层仪器测试（Room + MockWebServer 集成）。
 *
 * 使用内存数据库避免污染真机存储，MockWebServer 提供网络数据。
 */
class CachedRepositoryInstrumentedTest {

    @get:Rule
    val server = MockServerTestRule()

    private lateinit var db: AppDatabase
    private lateinit var login: BistuLogin
    private lateinit var scheduleRepo: CachedScheduleRepository
    private lateinit var examRepo: CachedExamRepository

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .fallbackToDestructiveMigration(false)
            .build()

        login = BistuLogin(
            logger = object : LoginLogger {
                override fun debug(msg: String) { println("[TEST] $msg") }
                override fun info(msg: String) { println("[TEST] $msg") }
                override fun warn(msg: String) { println("[TEST] $msg") }
                override fun error(msg: String) { println("[TEST] $msg") }
            },
            injectedClient = server.newClient(),
            injectedRedirectClient = server.newRedirectClient()
        )

        scheduleRepo = CachedScheduleRepository(db)
        examRepo = CachedExamRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── CachedScheduleRepository ──────────────────────────────

    @Test
    fun schedule_loadCached_returnsNullWhenEmpty() = runTest {
        val cached = scheduleRepo.loadCached()
        assertNull("Should be null when cache is empty", cached)
    }

    @Test
    fun schedule_fetchAndCache_persistsAndLoads() = runTest {
        // 模拟 fetchSchedule 的 3 个 API 调用
        server.enqueueJson(MockResponses.CURRENT_TERM_RESPONSE)
        server.enqueueJson(MockResponses.TERM_WEEKS_RESPONSE)
        server.enqueueJson(MockResponses.SCHEDULE_RESPONSE)

        // 初始无缓存
        assertNull(scheduleRepo.loadCached())

        // 网络获取 → 自动缓存
        val fetched = scheduleRepo.fetchAndCache(login)

        assertEquals("2025-2026-2", fetched.termCode)
        assertEquals(3, fetched.courses.size)
        assertEquals(0, fetched.termWeeks.size)

        // 再次读取缓存 → 应与网络获取结果一致
        val cached = scheduleRepo.loadCached()
        assertNotNull(cached)
        assertEquals(fetched.termName, cached!!.termName)
        assertEquals(fetched.courses.size, cached.courses.size)
        assertEquals(fetched.termWeeks.size, cached.termWeeks.size)
    }

    @Test
    fun schedule_fetchAndCache_skipsDuplicateWrites() = runTest {
        // 第一次 fetch
        server.enqueueJson(MockResponses.CURRENT_TERM_RESPONSE)
        server.enqueueJson(MockResponses.TERM_WEEKS_RESPONSE)
        server.enqueueJson(MockResponses.SCHEDULE_RESPONSE)

        val first = scheduleRepo.fetchAndCache(login)
        assertEquals(3, first.courses.size)

        // 第二次 fetch（相同数据 → enqueue 同样的响应）
        server.enqueueJson(MockResponses.CURRENT_TERM_RESPONSE)
        server.enqueueJson(MockResponses.TERM_WEEKS_RESPONSE)
        server.enqueueJson(MockResponses.SCHEDULE_RESPONSE)

        val second = scheduleRepo.fetchAndCache(login)
        // 哈希相同 → 不重复写入，但返回结果应一致
        assertEquals(first.courses.size, second.courses.size)
    }

    @Test
    fun schedule_clearCache_removesData() = runTest {
        server.enqueueJson(MockResponses.CURRENT_TERM_RESPONSE)
        server.enqueueJson(MockResponses.TERM_WEEKS_RESPONSE)
        server.enqueueJson(MockResponses.SCHEDULE_RESPONSE)

        scheduleRepo.fetchAndCache(login)
        assertNotNull(scheduleRepo.loadCached())

        scheduleRepo.clearCache()
        assertNull(scheduleRepo.loadCached())
    }

    // ── CachedExamRepository ──────────────────────────────────

    @Test
    fun exam_loadCached_returnsNullWhenEmpty() = runTest {
        val cached = examRepo.loadCached()
        assertNull("Should be null when cache is empty", cached)
    }

    @Test
    fun exam_fetchAndCache_persistsAndLoads() = runTest {
        // prime page + explicit endpoint
        server.enqueueJson("{}")
        server.enqueueJson(MockResponses.EXAM_RESPONSE)

        assertNull(examRepo.loadCached())

        val fetched = examRepo.fetchAndCache(login, "2025-2026-3")

        assertEquals(2, fetched.size)
        assertEquals("高等数学", fetched[0].courseName)

        val cached = examRepo.loadCached()
        assertNotNull(cached)
        assertEquals(2, cached!!.size)
        assertEquals("高等数学", cached[0].courseName)
        assertEquals("大学物理", cached[1].courseName)
    }

    @Test
    fun exam_fetchAndCache_handlesEmptyExams() = runTest {
        server.enqueueJson("{}")
        server.enqueueJson(MockResponses.EMPTY_EXAM_RESPONSE)

        val fetched = examRepo.fetchAndCache(login, "2025-2026-3")

        assertTrue(fetched.isEmpty())

        val cached = examRepo.loadCached()
        assertNotNull(cached)
        assertTrue(cached!!.isEmpty())
    }

    @Test
    fun exam_clearCache_removesData() = runTest {
        server.enqueueJson("{}")
        server.enqueueJson(MockResponses.EXAM_RESPONSE)

        examRepo.fetchAndCache(login, "2025-2026-3")
        assertNotNull(examRepo.loadCached())

        examRepo.clearCache()
        assertNull(examRepo.loadCached())
    }
}
