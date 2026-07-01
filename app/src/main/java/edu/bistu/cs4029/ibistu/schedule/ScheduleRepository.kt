package edu.bistu.cs4029.ibistu.schedule

import android.util.Log
import edu.bistu.cs4029.ibistu.login.BistuLogin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "ScheduleRepository"

/** 从教务系统读取当前学期课表。 */
suspend fun fetchSchedule(login: BistuLogin): ScheduleData = withContext(Dispatchers.IO) {
    val termJson = login.post(
        "https://jwxt.bistu.edu.cn/jwapp/sys/jwpubapp/modules/gg/cxmrxnxq.do",
        mapOf("CSDM" to "SYS", "ZCSDM" to "DQXNXQDM", "SFSY" to "1")
    )
    val termRows = JSONObject(termJson)
        .getJSONObject("datas")
        .getJSONObject("cxmrxnxq")
        .getJSONArray("rows")
    val term = termRows.getJSONObject(0)
    val termCode = term.getString("XNXQDM")
    val termName = term.getString("XNXQMC")

    val termWeeks = runCatching {
        val weeksJson = login.post(
            "https://jwxt.bistu.edu.cn/jwapp/sys/kbbpapp/api/schoolCalendar/getTermWeeks.do",
            mapOf("XNXQDM" to termCode)
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
        mapOf("XNXQDM" to termCode, "XQDM" to "10")
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
    Log.d(TAG, "Loaded ${courses.size} courses for $termName")
    ScheduleData(termName = termName, courses = courses, termWeeks = termWeeks)
}
