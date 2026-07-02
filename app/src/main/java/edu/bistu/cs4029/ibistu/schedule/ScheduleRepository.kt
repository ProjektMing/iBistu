package edu.bistu.cs4029.ibistu.schedule

import android.util.Log
import edu.bistu.cs4029.ibistu.login.BistuLogin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ScheduleRepository"

/** 从教务系统获取当前学期课表（或指定 termCode 的课表）。
 *  会并发获取所有教学周的课程数据并合并。 */
suspend fun fetchSchedule(login: BistuLogin, termCode: String? = null): ScheduleData = withContext(Dispatchers.IO) {
    val actualTermCode: String
    val actualTermName: String

    if (termCode != null) {
        val termJson = login.get(
            "https://jwxt.bistu.edu.cn/jwapp/sys/homeapp/api/home/kb/xnxq.do"
        )
        val termList = JSONObject(termJson)
            .getJSONArray("datas")
        actualTermCode = termCode
        actualTermName = findTermName(termList, termCode)
            ?: run {
                Log.w(TAG, "termCode=$termCode 未在 xnxq.do 中找到，回退到 API 默认名称")
                "$termCode 学期"
            }
    } else {
        val termJson = login.post(
            "https://jwxt.bistu.edu.cn/jwapp/sys/jwpubapp/modules/gg/cxmrxnxq.do",
            mapOf("CSDM" to "SYS", "ZCSDM" to "DQXNXQDM", "SFSY" to "1")
        )
        val termRows = JSONObject(termJson)
            .getJSONObject("datas")
            .getJSONObject("cxmrxnxq")
            .getJSONArray("rows")
        val term = termRows.getJSONObject(0)
        actualTermCode = term.getString("XNXQDM")
        actualTermName = term.getString("XNXQMC")
    }

    // 先获取教学周日期映射
    val termWeeks = runCatching {
        val weeksJson = login.post(
            "https://jwxt.bistu.edu.cn/jwapp/sys/kbbpapp/api/schoolCalendar/getTermWeeks.do",
            mapOf("XNXQDM" to actualTermCode)
        )
        val weeks = JSONObject(weeksJson)
            .getJSONObject("datas")
            .getJSONArray("getTermWeeks")

        val list = (0 until weeks.length()).map { index ->
            val week = weeks.getJSONObject(index)
            week.optInt("serialNumber", 0) to Pair(
                week.optString("startDate", ""),
                week.optString("endDate", "")
            )
        }
        list.filter { it.first > 0 }.associate { (num, dates) ->
            num to TermWeek(weekNumber = num, startDate = dates.first, endDate = dates.second)
        }
    }.onFailure { error ->
        Log.w(TAG, "Unable to load term week dates", error)
    }.getOrDefault(emptyMap())

    // 获取课表：有 termWeeks 时先探测，若未发布则跳过逐周请求
    val allCourses = if (termWeeks.isNotEmpty()) {
        val maxWeek = termWeeks.keys.max()
        // 先探测第一周
        val probe = fetchOneWeek(login, actualTermCode, weekNumber = 1)
        if (probe.isEmpty() && maxWeek > 1) {
            // 第一周为空，再试最末一周确认是否课表未发布
            val probeLast = fetchOneWeek(login, actualTermCode, weekNumber = maxWeek)
            if (probeLast.isEmpty()) {
                Log.w(TAG, "课表可能尚未发布，跳过逐周请求 (term=$actualTermCode)")
                emptyList()
            } else {
                // 首周空但末周有数据：正常获取全部（跳过已探测的首尾周）
                probe + probeLast + fetchAllWeeks(login, actualTermCode, maxWeek - 1, startFrom = 2)
            }
        } else if (probe.isNotEmpty() && maxWeek > 1) {
            // 第一周有数据：获取剩余周
            probe + fetchAllWeeks(login, actualTermCode, maxWeek, startFrom = 2)
        } else {
            probe
        }
    } else {
        Log.w(TAG, "termWeeks 为空，退回到单次课表请求")
        fetchOneWeek(login, actualTermCode, weekNumber = null)
    }
    Log.d(TAG, "Loaded ${allCourses.size} courses for $actualTermName ($actualTermCode)")

    ScheduleData(termCode = actualTermCode, termName = actualTermName, courses = allCourses, termWeeks = termWeeks)
}

/**
 * 并发获取 startFrom..maxWeek 所有周的课程，合并返回。
 * 使用协程并发（最多 2 个并发），避免对服务器造成压力。
 */
private suspend fun fetchAllWeeks(
    login: BistuLogin,
    termCode: String,
    maxWeek: Int,
    startFrom: Int = 1
): List<Course> = coroutineScope {
    val concurrency = 2
    val allCourses = mutableListOf<Course>()

    var week = startFrom
    while (week <= maxWeek) {
        val batchEnd = minOf(week + concurrency - 1, maxWeek)
        val batch = (week..batchEnd).map { w ->
            async(Dispatchers.IO) {
                fetchOneWeek(login, termCode, w)
            }
        }
        val results = batch.awaitAll()
        results.forEach { allCourses.addAll(it) }
        week = batchEnd + 1
    }

    allCourses
}

/**
 * 获取指定周次的课程列表。
 * @param weekNumber 周次；为 null 时不带 ZC 参数（退回到原来的单次请求模式）
 * @return 课程列表；若课表未发布（code ≠ "0" 或 getMyScheduleDetail 为 null）返回空列表
 */
private suspend fun fetchOneWeek(
    login: BistuLogin,
    termCode: String,
    weekNumber: Int?
): List<Course> {
    return try {
        val params = mutableMapOf("XNXQDM" to termCode, "XQDM" to "10")
        if (weekNumber != null) {
            params["ZC"] = weekNumber.toString()
        }
        val json = login.post(
            "https://jwxt.bistu.edu.cn/jwapp/sys/kbapp/api/wdkbcx/getMyScheduleDetail.do",
            params
        )
        val root = JSONObject(json)

        // 检查业务状态码：非 "0" 表示课表未发布或请求失败
        val code = root.optString("code", "0")
        if (code != "0") {
            val msg = root.optString("msg", "")
            Log.w(TAG, "课表接口返回 code=$code${if (msg.isNotBlank()) " msg=$msg" else ""} (term=$termCode, week=$weekNumber)")
            emptyList()
        } else {
            val detail = root.optJSONObject("datas")?.optJSONObject("getMyScheduleDetail")
            if (detail == null) {
                Log.w(TAG, "getMyScheduleDetail 为 null，课表可能尚未发布 (term=$termCode, week=$weekNumber)")
                emptyList()
            } else {
                val arrangedList = detail.optJSONArray("arrangedList") ?: JSONArray()

                buildList {
                    for (i in 0 until arrangedList.length()) {
                        val course = arrangedList.getJSONObject(i)
                        val rawWeeksAndTeachers = course.optString("weeksAndTeachers", "")
                        val teacher = extractTeacherName(rawWeeksAndTeachers)
                        // weekNumber（即请求参数 ZC）为权威周次；API 返回的 week 字段不可靠（实测恒为 "1"）
                        val weekValue = weekNumber?.toString()
                            ?: course.optString("week", "").ifBlank { "" }
                        // 二次回退：从 weeksAndTeachers 解析周次（如 "1周[实验]/..." → "1"）
                        val finalWeek = if (weekValue.isBlank() && rawWeeksAndTeachers.isNotBlank()) {
                            WeeksAndTeachersParser.parse(rawWeeksAndTeachers).weekText.ifBlank { "" }
                        } else {
                            weekValue
                        }

                        add(
                            Course(
                                name = course.getString("courseName"),
                                code = course.getString("courseCode"),
                                credit = course.getString("credit"),
                                teacher = teacher.ifBlank { rawWeeksAndTeachers },
                                classroom = course.optString("placeName", ""),
                                campus = course.optString("campusName", ""),
                                week = finalWeek,
                                dayOfWeek = course.optInt("dayOfWeek", 0),
                                beginSection = course.optInt("beginSection", 0),
                                endSection = course.optInt("endSection", 0),
                                beginTime = course.optString("beginTime", ""),
                                endTime = course.optString("endTime", "")
                            )
                        )
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to fetch week $weekNumber for $termCode: ${e.message}")
        emptyList()
    }
}

/** 从教务系统获取所有可选的学期列表。 */
suspend fun fetchTermList(login: BistuLogin): List<TermOption> = withContext(Dispatchers.IO) {
    val json = login.get(
        "https://jwxt.bistu.edu.cn/jwapp/sys/homeapp/api/home/kb/xnxq.do"
    )
    val arr = JSONObject(json).getJSONArray("datas")
    buildList {
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            add(
                TermOption(
                    termCode = item.getString("itemCode"),
                    termName = item.getString("itemName")
                )
            )
        }
    }
}

/** 在学期列表中查找指定 termCode 的名称。 */
private fun findTermName(termList: JSONArray, targetCode: String): String? {
    for (i in 0 until termList.length()) {
        val item = termList.getJSONObject(i)
        if (item.getString("itemCode") == targetCode) {
            return item.getString("itemName")
        }
    }
    return null
}

/**
 * 从 weeksAndTeachers 字段提取纯教师名。
 * 真实格式示例：
 * - "1周[实验]/张翠平[主讲]" → "张翠平"
 * - "1-16周/张三[主讲]" → "张三"
 * - "1-16周 张三" → "张三"
 * - "1周[实验]/张翠平[主讲]/李四[助教]" → "张翠平, 李四"
 */
private fun extractTeacherName(raw: String): String {
    if (raw.isBlank()) return ""

    // 统一分隔符：将 "周 " 后面的教师名通过 "/" 机制处理
    // 先按 / 分割，再对每段按 "周 " 分割（如果存在）
    val slashParts = raw.split("/")

    val allSegments = slashParts.flatMap { slashPart ->
        val trimmed = slashPart.trim()
        // 找到 "周" 后面的内容（可能是教师名）
        val weekIdx = trimmed.lastIndexOf('周')
        if (weekIdx >= 0 && weekIdx < trimmed.lastIndex) {
            val afterWeek = trimmed.substring(weekIdx + 1).trim()
            // 只当后续内容看起来像教师名时才分割（排除括号、标点等）
            if (afterWeek.isNotBlank()
                && !afterWeek.startsWith("[")
                && !afterWeek.startsWith("]")
                && !afterWeek.startsWith(")")
                && afterWeek.any { it.isLetter() || it in '\u4e00'..'\u9fff' }
            ) {
                // "1-16周 张三" → ["1-16周", "张三"]
                listOf(trimmed.substring(0, weekIdx + 1), afterWeek)
            } else {
                listOf(trimmed)
            }
        } else {
            listOf(trimmed)
        }
    }

    val teacherNames = allSegments.mapNotNull { seg ->
        var name = seg.trim()
            .replace(Regex("""\[[^]]*]"""), "")   // 去除 [xxx]
            .replace(Regex("""[（(]\s*(单周?|双周?)\s*[）)]"""), "")  // 去除 (单)/(双)
            .trim()
        // 过滤纯周次段
        if (name.isNotBlank() && !name.matches(Regex("""^\d+.*周?$"""))) {
            name
        } else null
    }

    return teacherNames.joinToString(", ")
}
