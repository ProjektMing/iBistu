package edu.bistu.cs4029.ibistu.schedule

import android.util.Log
import edu.bistu.cs4029.ibistu.login.BistuLogin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ScheduleRepository"

/** 从教务系统获取当前学期课表（或指定 termCode 的课表）。 */
suspend fun fetchSchedule(login: BistuLogin, termCode: String? = null): ScheduleData = withContext(Dispatchers.IO) {
    val actualTermCode: String
    val actualTermName: String

    if (termCode != null) {
        // 指定了学期代码：用 xnxq.do 查找学期名称，再用 termCode 直接获取课表
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
        // 未指定：用原有的 cxmrxnxq.do 获取当前学期
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

    val termWeeks = runCatching {
        val weeksJson = login.post(
            "https://jwxt.bistu.edu.cn/jwapp/sys/kbbpapp/api/schoolCalendar/getTermWeeks.do",
            mapOf("XNXQDM" to actualTermCode)
        )
        val weeks = JSONObject(weeksJson)
            .getJSONObject("datas")
            .getJSONArray("getTermWeeks")

        buildMap {
            for (index in 0 until weeks.length()) {
                val week = weeks.getJSONObject(index)
                val weekNumber = week.optInt("serialNumber", 0)
                if (weekNumber > 0) {
                    put(
                        weekNumber,
                        TermWeek(
                            weekNumber = weekNumber,
                            startDate = week.optString("startDate", ""),
                            endDate = week.optString("endDate", "")
                        )
                    )
                }
            }
        }
    }.onFailure { error ->
        Log.w(TAG, "Unable to load term week dates", error)
    }.getOrDefault(emptyMap())

    val scheduleJson = login.post(
        "https://jwxt.bistu.edu.cn/jwapp/sys/kbapp/api/wdkbcx/getMyScheduleDetail.do",
        mapOf("XNXQDM" to actualTermCode, "XQDM" to "10")
    )
    val arrangedList = JSONObject(scheduleJson)
        .getJSONObject("datas")
        .getJSONObject("getMyScheduleDetail")
        .getJSONArray("arrangedList")

    val courses = buildList {
        for (index in 0 until arrangedList.length()) {
            val course = arrangedList.getJSONObject(index)
            add(
                Course(
                    name = course.getString("courseName"),
                    code = course.getString("courseCode"),
                    credit = course.getString("credit"),
                    teacher = course.optString("weeksAndTeachers", ""),
                    classroom = course.optString("placeName", ""),
                    campus = course.optString("campusName", ""),
                    week = course.optString("week", ""),
                    dayOfWeek = course.optInt("dayOfWeek", 0),
                    beginSection = course.optInt("beginSection", 0),
                    endSection = course.optInt("endSection", 0),
                    beginTime = course.optString("beginTime", ""),
                    endTime = course.optString("endTime", "")
                )
            )
        }
    }
    Log.d(TAG, "Loaded ${courses.size} courses for $actualTermName ($actualTermCode)")
    ScheduleData(termCode = actualTermCode, termName = actualTermName, courses = courses, termWeeks = termWeeks)
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
    } // 不改顺序，API 已按最新在前返回
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
