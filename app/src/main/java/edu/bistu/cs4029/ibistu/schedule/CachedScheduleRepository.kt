package edu.bistu.cs4029.ibistu.schedule

import android.util.Log
import edu.bistu.cs4029.ibistu.login.AppDatabase
import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.schedule.model.ScheduleCacheEntity
import edu.bistu.cs4029.ibistu.schedule.model.XxHash32
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "CachedScheduleRepository"

/**
 * 缓存优先的课表仓储。
 *
 * 职责：
 * 1. [loadCached] — 从 Room 读取缓存，反序列化为 [ScheduleData]
 * 2. [fetchAndCache] — 从网络获取新课表，哈希比对，按需持久化
 * 3. 外部调用方决定何时使用缓存、何时网络请求
 */
class CachedScheduleRepository(private val db: AppDatabase) {

    private val dao = db.scheduleDao()

    // ── 读取缓存 ────────────────────────────────────────────

    /** 从 Room 加载缓存的课表，无缓存时返回 null。 */
    suspend fun loadCached(): ScheduleData? = withContext(Dispatchers.IO) {
        val entity = dao.load() ?: return@withContext null

        val courses = deserializeCourses(entity.coursesJson)
        val termWeeks = deserializeTermWeeks(entity.termWeeksJson)
        Log.i(TAG, "📦 loadCached: ${courses.size} courses, hash=${entity.jsonHash}")

        ScheduleData(
            termName = entity.termName,
            courses = courses,
            termWeeks = termWeeks,
            termCode = entity.termCode
        )
    }

    /** 读取缓存的哈希值，用于比对。 */
    suspend fun loadCachedHash(): String? = withContext(Dispatchers.IO) {
        dao.load()?.jsonHash
    }

    // ── 网络获取 + 按需缓存 ─────────────────────────────────

    /**
     * 从网络获取课表，计算 xxHash32 并与缓存比对。
     * - 哈希相同：跳过持久化（避免不必要的磁盘写入）
     * - 哈希不同：持久化新课表
     *
     * @return 网络返回的最新 [ScheduleData]
     */
    suspend fun fetchAndCache(login: BistuLogin): ScheduleData = withContext(Dispatchers.IO) {
        val schedule = fetchSchedule(login)

        val coursesJson = serializeCourses(schedule.courses)
        val termWeeksJson = serializeTermWeeks(schedule.termWeeks)
        val jsonHash = XxHash32.hashStringHex("$coursesJson\n$termWeeksJson")

        val oldHash = loadCachedHash()
        if (jsonHash == oldHash) {
            Log.i(TAG, "🔒 fetchAndCache: hash 未变 ($jsonHash)，跳过持久化")
        } else {
            Log.i(TAG, "💾 fetchAndCache: hash 变化 old=$oldHash → new=$jsonHash，写入 Room")
            dao.insertOrReplace(
                ScheduleCacheEntity(
                    termName = schedule.termName,
                    termCode = schedule.termCode,
                    jsonHash = jsonHash,
                    coursesJson = coursesJson,
                    termWeeksJson = termWeeksJson,
                    weekRangeEnd = schedule.courses.maxOfOrNull {
                        ScheduleUtils.getCourseWeeks(it.week).maxOrNull() ?: 20
                    } ?: 20
                )
            )
        }

        schedule
    }

    /** 清除课表缓存。 */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        dao.clear()
        Log.d(TAG, "clearCache: schedule cache cleared")
    }

    // ── JSON 序列化 / 反序列化 ───────────────────────────────

    private fun serializeCourses(courses: List<Course>): String {
        val arr = JSONArray()
        for (c in courses) {
            arr.put(
                JSONObject().apply {
                    put("name", c.name)
                    put("code", c.code)
                    put("credit", c.credit)
                    put("teacher", c.teacher)
                    put("classroom", c.classroom)
                    put("campus", c.campus)
                    put("week", c.week)
                    put("dayOfWeek", c.dayOfWeek)
                    put("beginSection", c.beginSection)
                    put("endSection", c.endSection)
                    put("beginTime", c.beginTime)
                    put("endTime", c.endTime)
                }
            )
        }
        return arr.toString()
    }

    private fun deserializeCourses(json: String): List<Course> {
        if (json.isBlank() || json == "[]" || json == "{}") return emptyList()
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    Course(
                        name = obj.optString("name", ""),
                        code = obj.optString("code", ""),
                        credit = obj.optString("credit", ""),
                        teacher = obj.optString("teacher", ""),
                        classroom = obj.optString("classroom", ""),
                        campus = obj.optString("campus", ""),
                        week = obj.optString("week", ""),
                        dayOfWeek = obj.optInt("dayOfWeek", 0),
                        beginSection = obj.optInt("beginSection", 0),
                        endSection = obj.optInt("endSection", 0),
                        beginTime = obj.optString("beginTime", ""),
                        endTime = obj.optString("endTime", "")
                    )
                )
            }
        }
    }

    private fun serializeTermWeeks(weeks: Map<Int, TermWeek>): String {
        val arr = JSONArray()
        for ((_, tw) in weeks) {
            arr.put(
                JSONObject().apply {
                    put("weekNumber", tw.weekNumber)
                    put("startDate", tw.startDate)
                    put("endDate", tw.endDate)
                }
            )
        }
        return arr.toString()
    }

    private fun deserializeTermWeeks(json: String): Map<Int, TermWeek> {
        if (json.isBlank() || json == "[]" || json == "{}") return emptyMap()
        val arr = JSONArray(json)
        return buildMap {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val tw = TermWeek(
                    weekNumber = obj.optInt("weekNumber", 0),
                    startDate = obj.optString("startDate", ""),
                    endDate = obj.optString("endDate", "")
                )
                if (tw.weekNumber > 0) {
                    put(tw.weekNumber, tw)
                }
            }
        }
    }
}
