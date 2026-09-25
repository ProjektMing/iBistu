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
 * 2. [fetchAndCache] — 从网络获取新课表，比对整学期快照后按需持久化
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

    /** 读取缓存中课程 JSON 的哈希值，用于诊断。 */
    suspend fun loadCachedHash(): String? = withContext(Dispatchers.IO) {
        dao.load()?.jsonHash
    }

    // ── 网络获取 + 按需缓存 ─────────────────────────────────

    /**
     * 从网络获取课表，比较学期、课程内容与教学周日期。
     * - 内容相同：跳过持久化（避免不必要的磁盘写入）
     * - 内容变化：持久化新课表
     *
     * @param termCode 可选，指定学期代码；null 时获取当前学期
     *
     * @return 网络返回的最新 [ScheduleData]
     */
    suspend fun fetchAndCache(login: BistuLogin, termCode: String? = null): ScheduleData = withContext(Dispatchers.IO) {
        val schedule = fetchSchedule(login, termCode)
        val cached = dao.load()
        val cachedCourses = cached?.let { runCatching { deserializeCourses(it.coursesJson) }.getOrNull() }
        val cachedTermWeeks = cached?.let { runCatching { deserializeTermWeeks(it.termWeeksJson) }.getOrNull() }
        // 同一学期的教学周接口暂时不可用时，保留已经缓存的日期。
        val termWeeksToCache = if (schedule.termWeeks.isEmpty() && cached?.termCode == schedule.termCode) {
            cachedTermWeeks ?: emptyMap()
        } else {
            schedule.termWeeks
        }
        val coursesUnchanged = cachedCourses?.groupingBy { it }?.eachCount() ==
            schedule.courses.groupingBy { it }.eachCount()
        val scheduleUnchanged = cached != null &&
            cached.termCode == schedule.termCode &&
            cached.termName == schedule.termName &&
            coursesUnchanged &&
            cachedTermWeeks == termWeeksToCache

        if (scheduleUnchanged) {
            Log.i(TAG, "🔒 fetchAndCache: 学期课表未变化，跳过持久化")
        } else {
            val coursesJson = serializeCourses(schedule.courses)
            val termWeeksJson = serializeTermWeeks(termWeeksToCache)
            val jsonHash = XxHash32.hashStringHex(coursesJson)
            Log.i(TAG, "💾 fetchAndCache: 学期课表有变化，写入 Room (hash=$jsonHash)")
            dao.insertOrReplace(
                ScheduleCacheEntity(
                    termName = schedule.termName,
                    termCode = schedule.termCode, // 学期代码
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

    /** 从网络获取所有可选的学期列表。 */
    suspend fun fetchTermList(login: BistuLogin): List<TermOption> = withContext(Dispatchers.IO) {
        return@withContext edu.bistu.cs4029.ibistu.schedule.fetchTermList(login)
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
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    Course(
                        name = obj.getString("name"),
                        code = obj.getString("code"),
                        credit = obj.getString("credit"),
                        teacher = obj.getString("teacher"),
                        classroom = obj.getString("classroom"),
                        campus = obj.getString("campus"),
                        week = obj.getString("week"),
                        dayOfWeek = obj.getInt("dayOfWeek"),
                        beginSection = obj.getInt("beginSection"),
                        endSection = obj.getInt("endSection"),
                        beginTime = obj.getString("beginTime"),
                        endTime = obj.getString("endTime")
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
        if (json.isBlank() || json == "{}") return emptyMap()
        val arr = JSONArray(json)
        return buildMap {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val tw = TermWeek(
                    weekNumber = obj.getInt("weekNumber"),
                    startDate = obj.getString("startDate"),
                    endDate = obj.getString("endDate")
                )
                put(tw.weekNumber, tw)
            }
        }
    }
}
