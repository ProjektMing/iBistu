package edu.bistu.cs4029.ibistu.schedule

import edu.bistu.cs4029.ibistu.login.AppDatabase
import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.schedule.model.ExamCacheEntity
import edu.bistu.cs4029.ibistu.schedule.model.ExamDao
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

class CachedExamRepositoryTest {

    private val db = mockk<AppDatabase>()
    private val dao = mockk<ExamDao>()
    private val login = mockk<BistuLogin>()

    private lateinit var repository: CachedExamRepository

    @Before
    fun setUp() {
        every { db.examDao() } returns dao
        repository = CachedExamRepository(db)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun loadCached_returnsDeserializedExams() = runTest {
        val expectedExams = listOf(sampleExam())
        coEvery { dao.load() } returns ExamCacheEntity(
            termCode = "2025-2026-2-2",
            jsonHash = "deadbeef",
            examsJson = examsJson(expectedExams)
        )

        val result = repository.loadCached()

        assertNotNull(result)
        assertEquals(expectedExams, result)
    }

    @Test
    fun fetchAndCache_insertsNewSnapshotWhenHashChanges() = runTest {
        val termCode = "2025-2026-2-2"
        val exams = listOf(sampleExam())
        val expectedJson = examsJson(exams)
        val expectedHash = XxHash32.hashStringHex(expectedJson)
        mockkStatic("edu.bistu.cs4029.ibistu.schedule.ExamRepositoryKt")
        coEvery { fetchExams(login, termCode) } returns exams
        coEvery { dao.load() } returns null
        coEvery { dao.insertOrReplace(any()) } just Runs

        val result = repository.fetchAndCache(login, termCode)

        assertEquals(exams, result)
        coVerify(exactly = 1) {
            dao.insertOrReplace(
                match {
                    it.termCode == termCode &&
                            it.jsonHash == expectedHash &&
                            it.examsJson == expectedJson
                }
            )
        }
    }

    @Test
    fun fetchAndCache_skipsInsertWhenHashMatchesCache() = runTest {
        val termCode = "2025-2026-2-2"
        val exams = listOf(sampleExam())
        val hash = XxHash32.hashStringHex(examsJson(exams))
        mockkStatic("edu.bistu.cs4029.ibistu.schedule.ExamRepositoryKt")
        coEvery { fetchExams(login, termCode) } returns exams
        coEvery {
            dao.load()
        } returns ExamCacheEntity(
            termCode = termCode,
            jsonHash = hash,
            examsJson = ""
        )

        val result = repository.fetchAndCache(login, termCode)

        assertEquals(exams, result)
        coVerify(exactly = 0) { dao.insertOrReplace(any()) }
    }

    @Test
    fun clearCache_deletesStoredSnapshot() = runTest {
        coEvery { dao.clear() } just Runs

        repository.clearCache()

        coVerify(exactly = 1) { dao.clear() }
    }

    private fun sampleExam() = Exam(
        courseName = "高等数学",
        examDate = "2026-06-30",
        examTime = "09:00-11:00",
        location = "北6号楼101",
        seatNumber = "12",
        examType = "期末考试",
        campus = "本部"
    )

    private fun examsJson(exams: List<Exam>): String {
        val arr = JSONArray()
        exams.forEach { exam ->
            arr.put(
                JSONObject().apply {
                    put("courseName", exam.courseName)
                    put("examDate", exam.examDate)
                    put("examTime", exam.examTime)
                    put("location", exam.location)
                    put("seatNumber", exam.seatNumber)
                    put("examType", exam.examType)
                    put("campus", exam.campus)
                }
            )
        }
        return arr.toString()
    }
}
