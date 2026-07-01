package edu.bistu.cs4029.ibistu.schedule

import edu.bistu.cs4029.ibistu.login.AppDatabase
import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.schedule.model.ScheduleCacheEntity
import edu.bistu.cs4029.ibistu.schedule.model.ScheduleDao
import edu.bistu.cs4029.ibistu.schedule.model.XxHash32
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class CachedScheduleRepositoryTest {

    private val db = mockk<AppDatabase>()
    private val dao = mockk<ScheduleDao>()
    private val login = mockk<BistuLogin>()

    private lateinit var repository: CachedScheduleRepository

    @Before
    fun setUp() {
        every { db.scheduleDao() } returns dao
        repository = CachedScheduleRepository(db)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun loadCached_returnsDeserializedSchedule() = runTest {
        val expectedCourses = listOf(sampleCourse())
        val expectedTermWeeks = mapOf(1 to sampleTermWeek())
        coEvery { dao.load() } returns ScheduleCacheEntity(
            termName = "2025-2026-2",
            termCode = "",
            jsonHash = "deadbeef",
            coursesJson = coursesJson(expectedCourses),
            termWeeksJson = termWeeksJson(expectedTermWeeks)
        )

        val result = repository.loadCached()

        assertNotNull(result)
        assertEquals("", result?.termCode)
        assertEquals("2025-2026-2", result?.termName)
        assertEquals(expectedCourses, result?.courses)
        assertEquals(expectedTermWeeks, result?.termWeeks)
    }

    @Test
    fun fetchAndCache_insertsNewSnapshotWhenHashChanges() = runTest {
        val schedule = ScheduleData(
            termCode = "2025-2026-2-2",
            termName = "2025-2026-2",
            courses = listOf(sampleCourse()),
            termWeeks = mapOf(1 to sampleTermWeek())
        )
        val expectedCoursesJson = coursesJson(schedule.courses)
        val expectedTermWeeksJson = termWeeksJson(schedule.termWeeks)
        val expectedHash = XxHash32.hashStringHex(expectedCoursesJson)
        mockkStatic("edu.bistu.cs4029.ibistu.schedule.ScheduleRepositoryKt")
        coEvery { fetchSchedule(login) } returns schedule
        coEvery { dao.load() } returns null
        coEvery { dao.insertOrReplace(any()) } just Runs

        val result = repository.fetchAndCache(login)

        assertEquals(schedule, result)
        coVerify(exactly = 1) {
            dao.insertOrReplace(
                match {
                    it.termName == schedule.termName &&
                            it.termCode == schedule.termCode &&
                            it.jsonHash == expectedHash &&
                            it.coursesJson == expectedCoursesJson &&
                            it.termWeeksJson == expectedTermWeeksJson &&
                            it.weekRangeEnd == 16
                }
            )
        }
    }

    @Test
    fun fetchAndCache_skipsInsertWhenHashMatchesCache() = runTest {
        val schedule = ScheduleData(
            termCode = "2025-2026-2-2",
            termName = "2025-2026-2",
            courses = listOf(sampleCourse()),
            termWeeks = mapOf(1 to sampleTermWeek())
        )
        val hash = XxHash32.hashStringHex(coursesJson(schedule.courses))
        mockkStatic("edu.bistu.cs4029.ibistu.schedule.ScheduleRepositoryKt")
        coEvery { fetchSchedule(login) } returns schedule
        coEvery {
            dao.load()
        } returns ScheduleCacheEntity(
            termName = schedule.termName,
            termCode = "",
            jsonHash = hash,
            coursesJson = "",
            termWeeksJson = ""
        )

        val result = repository.fetchAndCache(login)

        assertEquals(schedule, result)
        coVerify(exactly = 0) { dao.insertOrReplace(any()) }
    }

    @Test
    fun clearCache_deletesStoredSnapshot() = runTest {
        coEvery { dao.clear() } just Runs

        repository.clearCache()

        coVerify(exactly = 1) { dao.clear() }
    }

    private fun sampleCourse() = Course(
        name = "高等数学",
        code = "MATH101",
        credit = "4.0",
        teacher = "张老师",
        classroom = "北6号楼101",
        campus = "本部",
        week = "1-16周",
        dayOfWeek = 1,
        beginSection = 1,
        endSection = 2,
        beginTime = "08:00",
        endTime = "09:35"
    )

    private fun sampleTermWeek() = TermWeek(
        weekNumber = 1,
        startDate = "2026-02-23 00:00:00",
        endDate = "2026-03-01 23:59:59"
    )

    private fun coursesJson(courses: List<Course>): String {
        val arr = JSONArray()
        courses.forEach { course ->
            arr.put(
                JSONObject().apply {
                    put("name", course.name)
                    put("code", course.code)
                    put("credit", course.credit)
                    put("teacher", course.teacher)
                    put("classroom", course.classroom)
                    put("campus", course.campus)
                    put("week", course.week)
                    put("dayOfWeek", course.dayOfWeek)
                    put("beginSection", course.beginSection)
                    put("endSection", course.endSection)
                    put("beginTime", course.beginTime)
                    put("endTime", course.endTime)
                }
            )
        }
        return arr.toString()
    }

    private fun termWeeksJson(termWeeks: Map<Int, TermWeek>): String {
        val arr = JSONArray()
        termWeeks.values.forEach { termWeek ->
            arr.put(
                JSONObject().apply {
                    put("weekNumber", termWeek.weekNumber)
                    put("startDate", termWeek.startDate)
                    put("endDate", termWeek.endDate)
                }
            )
        }
        return arr.toString()
    }
}
