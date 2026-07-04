package edu.bistu.cs4029.ibistu.schedule

import android.util.Log
import edu.bistu.cs4029.ibistu.login.BistuLogin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "EmptyClassroomRepo"

/** 从教务系统 jsjy 模块查询空闲教室。 */
suspend fun fetchEmptyClassrooms(
    login: BistuLogin,
    query: EmptyClassroomQuery,
    pageSize: Int = 200,
    pageNumber: Int = 1
): List<EmptyClassroom> = withContext(Dispatchers.IO) {
    // 1. 先访问教室借用模块首页以获取所需 Cookie
    try {
        login.get(
            "https://jwxt.bistu.edu.cn/jwapp/sys/jsjy/*default/index.do" +
                "?THEME=indigo&EMAP_LANG=zh&forceApp=jsjy"
        )
    } catch (e: Exception) {
        Log.w(TAG, "jsjy 模块首页访问失败（session 可能已过期）: ${e.message}")
    }

    // 2. 构建 querySetting JSON（顺序必须与浏览器一致：校区 → 节次 → 日期 → DZLYLX）
    val filters = mutableListOf<JSONObject>()

    // 校区
    filters.add(JSONObject().apply {
        put("name", "XXXQDM")
        put("caption", "校区")
        put("linkOpt", "AND")
        put("builderList", "cbl_List")
        put("builder", "equal")
        put("value", query.campusCode)
        put("value_display", query.campusName)
    })

    // 节次
    filters.add(JSONObject().apply {
        put("name", "KSJC")
        put("caption", "开始节次")
        put("linkOpt", "AND")
        put("builderList", "cbl_List")
        put("builder", "equal")
        put("value", query.startSection.toString())
        put("value_display", sectionDisplayName(query.startSection))
    })
    filters.add(JSONObject().apply {
        put("name", "JSJC")
        put("caption", "结束节次")
        put("linkOpt", "AND")
        put("builderList", "cbl_List")
        put("builder", "equal")
        put("value", query.endSection.toString())
        put("value_display", sectionDisplayName(query.endSection))
    })

    // 日期
    filters.add(JSONObject().apply {
        put("name", "KSRQ")
        put("caption", "开始日期")
        put("linkOpt", "AND")
        put("builderList", "cbl_String")
        put("builder", "include")
        put("value", query.startDate)
    })
    filters.add(JSONObject().apply {
        put("name", "JSRQ")
        put("caption", "结束日期")
        put("linkOpt", "AND")
        put("builderList", "cbl_String")
        put("builder", "include")
        put("value", query.endDate)
    })

    // 教学楼（可选）
    if (query.buildingCode != null) {
        filters.add(JSONObject().apply {
            put("name", "JXLDM")
            put("caption", "教学楼")
            put("linkOpt", "AND")
            put("builderList", "cbl_List")
            put("builder", "equal")
            put("value", query.buildingCode)
        })
    }

    // 教室名称模糊匹配（可选）
    if (query.roomName != null) {
        filters.add(JSONObject().apply {
            put("name", "JASMC")
            put("caption", "教室名称")
            put("linkOpt", "AND")
            put("builderList", "cbl_String")
            put("builder", "include")
            put("value", query.roomName)
        })
    }

    // 固定隐藏参数
    filters.add(JSONObject().apply {
        put("name", "DZLYLX")
        put("value", "JSJY")
    })

    val querySettingJson = JSONArray(filters).toString()

    // 3. 构建 POST 参数
    val formBody = linkedMapOf(
        "querySetting" to querySettingJson,
        "KSRQ" to query.startDate,
        "JSRQ" to query.endDate,
        "KSJC" to query.startSection.toString(),
        "JSJC" to query.endSection.toString(),
        "*order" to "+LC,+SKZWS,+WID",
        "pageSize" to pageSize.toString(),
        "pageNumber" to pageNumber.toString()
    )

    // 4. 发送请求
    val url = "https://jwxt.bistu.edu.cn/jwapp/sys/jsjy/modules/jsjysq/cxkxjs.do"
    Log.d(TAG, "querySetting: $querySettingJson")
    Log.d(TAG, "表单参数: KSRQ=${query.startDate} JSRQ=${query.endDate} KSJC=${query.startSection} JSJC=${query.endSection}")
    val json = login.post(url, formBody)

    // 5. 解析响应
    val root = JSONObject(json)
    val code = root.optString("code", "")
    if (code != "0") {
        val msg = root.optString("msg", "")
        Log.w(TAG, "cxkxjs.do 返回 code=$code${if (msg.isNotBlank()) " msg=$msg" else ""}")
        return@withContext emptyList()
    }

    val rows = root
        .optJSONObject("datas")
        ?.optJSONObject("cxkxjs")
        ?.optJSONArray("rows")
        ?: JSONArray()

    val totalSize = root
        .optJSONObject("datas")
        ?.optJSONObject("cxkxjs")
        ?.optInt("totalSize", 0) ?: 0

    Log.d(TAG, "响应: totalSize=$totalSize rows=${rows.length()}")

    val classrooms = buildList {
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            add(
                EmptyClassroom(
                    name = r.optString("JASMC", ""),
                    buildingCode = r.optString("JXLDM", ""),
                    buildingName = r.optString("JXLDM_DISPLAY", ""),
                    campusCode = r.optString("XXXQDM", ""),
                    campusName = r.optString("XXXQDM_DISPLAY", ""),
                    floor = r.optDouble("LC", 0.0).toInt(),
                    classSeats = r.optDouble("SKZWS", 0.0).toInt(),
                    examSeats = r.optDouble("KSZWS", 0.0).toInt(),
                    typeCode = r.optString("JASLXDM", ""),
                    typeName = r.optString("JASLXDM_DISPLAY", ""),
                    roomCode = r.optString("JASDM", ""),
                    id = r.optString("WID", ""),
                    note = r.optString("BZ", "").let { if (it == "null" || it.isBlank()) "" else it },
                    allowSchedule = r.optString("SFYXPK", "") == "1",
                    allowBorrow = r.optString("SFYXJY", "") == "1",
                    allowExam = r.optString("SFYXKS", "") == "1"
                )
            )
        }
    }

    classrooms
}
