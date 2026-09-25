package edu.bistu.cs4029.ibistu.schedule

import android.util.Log
import edu.bistu.cs4029.ibistu.login.BistuLogin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ScheduleRepository"

/** 从教务系统获取当前学期课表（或指定 termCode 的课表）。
 *  按校区获取整学期安排，将 API 周次位图转换为应用周次文本。 */
suspend fun fetchSchedule(login: BistuLogin, termCode: String? = null): ScheduleData = withContext(Dispatchers.IO) {
    val actualTermCode: String
    val actualTermName: String

    if (termCode != null) {
        actualTermCode = termCode
        actualTermName = runCatching {
            val termJson = login.get(
                "https://jwxt.bistu.edu.cn/jwapp/sys/homeapp/api/home/kb/xnxq.do"
            )
            val termList = JSONObject(termJson).getJSONArray("datas")
            findTermName(termList, termCode)
        }.onFailure { if (it is CancellationException) throw it }.getOrNull() ?: run {
            Log.w(TAG, "termCode=$termCode 未在 xnxq.do 中找到或解析失败，回退到默认名称")
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
        if (error is CancellationException) throw error
        Log.w(TAG, "Unable to load term week dates", error)
    }.getOrDefault(emptyMap())

    val campusJson = login.get(
        "https://jwxt.bistu.edu.cn/jwapp/sys/homeapp/api/home/student/getMyScheduledCampus.do?termCode=$actualTermCode"
    )
    val campusRoot = JSONObject(campusJson)
    checkScheduleResponse(campusRoot)
    val campuses = campusRoot.getJSONArray("datas")
    val campusCodes = (0 until campuses.length()).map { campuses.getJSONObject(it).getString("id") }.distinct()
    val allCourses = campusCodes.flatMap { campusCode ->
        fetchTermCourses(login, actualTermCode, campusCode)
    }
    Log.d(TAG, "Loaded ${allCourses.size} courses for $actualTermName ($actualTermCode)")

    ScheduleData(termCode = actualTermCode, termName = actualTermName, courses = allCourses, termWeeks = termWeeks)
}

/** 获取一个校区的整学期安排；错误向上传递，防止用空课表覆盖缓存。 */
private suspend fun fetchTermCourses(login: BistuLogin, termCode: String, campusCode: String): List<Course> {
    val json = login.post(
        "https://jwxt.bistu.edu.cn/jwapp/sys/kbapp/api/wdkbcx/getMyScheduleDetail.do",
        mapOf("XNXQDM" to termCode, "XQDM" to campusCode)
    )
    val root = JSONObject(json)
    checkScheduleResponse(root)
    val detail = root.getJSONObject("datas").getJSONObject("getMyScheduleDetail")
    val arrangedList = detail.getJSONArray("arrangedList")
    return (0 until arrangedList.length()).map { index ->
        val course = arrangedList.getJSONObject(index)
        val rawWeeksAndTeachers = course.optString("weeksAndTeachers", "")
        Course(
            name = course.getString("courseName"),
            code = course.getString("courseCode"),
            credit = course.getString("credit"),
            teacher = extractTeacherName(rawWeeksAndTeachers),
            classroom = course.optString("placeName", ""),
            campus = course.optString("campusName", ""),
            week = scheduleWeekText(course.getString("week")),
            dayOfWeek = course.optInt("dayOfWeek", 0),
            beginSection = course.optInt("beginSection", 0),
            endSection = course.optInt("endSection", 0),
            beginTime = course.optString("beginTime", ""),
            endTime = course.optString("endTime", "")
        )
    }
}

private fun checkScheduleResponse(root: JSONObject) {
    check(root.getString("code") == "0") {
        "课表接口请求失败：${root.optString("msg", "未知错误")}"
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
