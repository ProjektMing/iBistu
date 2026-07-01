package edu.bistu.cs4029.ibistu.schedule

import android.util.Log
import edu.bistu.cs4029.ibistu.login.AppDatabase
import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.schedule.model.ExamCacheEntity
import edu.bistu.cs4029.ibistu.schedule.model.XxHash32
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "CachedExamRepository"

/**
 * 缓存优先的考试仓储。
 *
 * 职责：
 * 1. [loadCached] — 从 Room 读取缓存，反序列化为 [Exam] 列表
 * 2. [fetchAndCache] — 从网络获取新考试数据，哈希比对，按需持久化
 * 3. 外部调用方决定何时使用缓存、何时网络请求
 */
class CachedExamRepository(private val db: AppDatabase) {

    private val dao = db.examDao()

    // ── 读取缓存 ────────────────────────────────────────────

    /** 从 Room 加载缓存的考试数据，无缓存时返回 null。 */
    suspend fun loadCached(): List<Exam>? = withContext(Dispatchers.IO) {
        val entity = dao.load() ?: return@withContext null

        val exams = deserializeExams(entity.examsJson)
        Log.i(TAG, "📦 loadCached: ${exams.size} exams, hash=${entity.jsonHash}")

        exams
    }

    /** 读取缓存的哈希值，用于比对。 */
    suspend fun loadCachedHash(): String? = withContext(Dispatchers.IO) {
        dao.load()?.jsonHash
    }

    // ── 网络获取 + 按需缓存 ─────────────────────────────────

    /**
     * 从网络获取考试数据，计算 xxHash32 并与缓存比对。
     * - 哈希相同：跳过持久化（避免不必要的磁盘写入）
     * - 哈希不同：持久化新考试数据
     *
     * @return 网络返回的最新 [Exam] 列表
     */
    suspend fun fetchAndCache(login: BistuLogin, termCode: String): List<Exam> = withContext(Dispatchers.IO) {
        val exams = fetchExams(login, termCode)

        val examsJson = serializeExams(exams)
        val jsonHash = XxHash32.hashStringHex(examsJson)

        val oldHash = loadCachedHash()
        if (jsonHash == oldHash) {
            Log.i(TAG, "🔒 fetchAndCache: hash 未变 ($jsonHash)，跳过持久化")
        } else {
            Log.i(TAG, "💾 fetchAndCache: hash 变化 old=$oldHash → new=$jsonHash，写入 Room")
            dao.insertOrReplace(
                ExamCacheEntity(
                    termCode = termCode,
                    jsonHash = jsonHash,
                    examsJson = examsJson
                )
            )
        }

        exams
    }

    /** 清除考试缓存。 */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        dao.clear()
        Log.d(TAG, "clearCache: exam cache cleared")
    }

    // ── JSON 序列化 / 反序列化 ───────────────────────────────

    private fun serializeExams(exams: List<Exam>): String {
        val arr = JSONArray()
        for (e in exams) {
            arr.put(
                JSONObject().apply {
                    put("courseName", e.courseName)
                    put("examDate", e.examDate)
                    put("examTime", e.examTime)
                    put("location", e.location)
                    put("seatNumber", e.seatNumber)
                    put("examType", e.examType)
                    put("campus", e.campus)
                }
            )
        }
        return arr.toString()
    }

    private fun deserializeExams(json: String): List<Exam> {
        if (json.isBlank() || json == "[]" || json == "{}") return emptyList()
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    Exam(
                        courseName = obj.optString("courseName", ""),
                        examDate = obj.optString("examDate", ""),
                        examTime = obj.optString("examTime", ""),
                        location = obj.optString("location", ""),
                        seatNumber = obj.optString("seatNumber", ""),
                        examType = obj.optString("examType", ""),
                        campus = obj.optString("campus", "")
                    )
                )
            }
        }
    }
}
