package edu.bistu.cs4029.ibistu.schedule

import android.util.Log
import edu.bistu.cs4029.ibistu.login.BistuLogin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ExamRepository"

private const val ExamEndpoint =
    "https://jwxt.bistu.edu.cn/jwapp/sys/wdkwapp/api/wdks/queryMyExamArrangeMent.do"

/**
 * 从教务系统读取指定学期的考试安排。
 *
 * @param login 已建立教务系统会话的登录客户端。
 * @param termCode 教务系统学期代码。
 * @return 该学期的考试安排；没有考试时返回空列表。
 * @throws IllegalArgumentException 当 [termCode] 为空时抛出。
 * @throws IllegalStateException 当服务器响应不包含考试安排结构时抛出。
 */
suspend fun fetchExams(login: BistuLogin, termCode: String): List<Exam> = withContext(Dispatchers.IO) {
    require(termCode.isNotBlank()) { "termCode 不能为空（用于请求学期考试安排）" }

    val response = login.post(ExamEndpoint, mapOf("XNXQDM" to termCode))
    when (val result = parseExamResponse(response)) {
        is ParseResult.Hit -> result.exams
        is ParseResult.Miss -> error("考试安排响应格式错误，根节点字段：${result.rootKeys}")
    }
}

// -- 解析结果：命中（有数据或无数据） vs 未命中（JSON 结构不匹配） --

private sealed class ParseResult {
    /** 命中了考试 API 的特征 JSON 结构。exams 可能为空（表示本学期无考试）。 */
    data class Hit(val exams: List<Exam>) : ParseResult()

    /** JSON 有效但不包含任何已知的考试数据结构。 */
    data class Miss(val rootKeys: List<String>) : ParseResult()
}

// 解析考试 API 的 JSON 响应。
//
// 关键区分：
// - 返回 Hit(emptyList()) = JSON 结构匹配（有 datas -> xxx -> rows），但列表为空 → 确实没考试
// - 返回 Miss            = JSON 结构不匹配 → 报告响应格式错误
private fun parseExamResponse(json: String): ParseResult {
    val trimmed = json.trimStart()
    if (trimmed.startsWith("[")) {
        val arr = JSONArray(trimmed)
        Log.d(TAG, "Hit at top-level array (${arr.length()} items)")
        return ParseResult.Hit(parseExamRows(arr))
    }

    val root = JSONObject(trimmed)
    val allExams = mutableListOf<Exam>()

    // 1. 标准格式: { datas: { <actionName>: { rows|arrangedList|arranged|list|notArranged: [...] } } }
    val datas = root.optJSONObject("datas")
    if (datas != null) {
        val keys = datas.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val container = datas.optJSONObject(key) ?: continue
            // 一个 container 下可能有多个数组（如 arranged + notArranged），全部收集
            val arrayKeys = listOf("arranged", "rows", "arrangedList", "list", "notArranged")
            var foundAny = false
            for (arrKey in arrayKeys) {
                val rows = container.optJSONArray(arrKey) ?: continue
                Log.d(TAG, "Hit at datas.$key.$arrKey (${rows.length()} items)")
                allExams.addAll(parseExamRows(rows))
                foundAny = true
            }
            if (foundAny) return ParseResult.Hit(allExams)
        }
    }

    // 2. datas 直接是数组: { datas: [...] }
    val directArray = root.optJSONArray("datas")
    if (directArray != null) {
        Log.d(TAG, "Hit at datas[] (${directArray.length()} items)")
        return ParseResult.Hit(parseExamRows(directArray))
    }

    // 3. 顶层就是数组: [ ... ]
    try {
        val arr = JSONArray(json)
        Log.d(TAG, "Hit at top-level array (${arr.length()} items)")
        return ParseResult.Hit(parseExamRows(arr))
    } catch (_: Exception) { }

    // 4. { code: 200, data: { rows|list: [...] } }
    val data = root.optJSONObject("data")
    if (data != null) {
        val rows = data.optJSONArray("rows")
            ?: data.optJSONArray("list")
        if (rows != null) {
            Log.d(TAG, "Hit at data.rows (${rows.length()} items)")
            return ParseResult.Hit(parseExamRows(rows))
        }
    }

    // 5. { success: true, result: [...] }
    val result = root.optJSONArray("result")
    if (result != null) {
        Log.d(TAG, "Hit at result[] (${result.length()} items)")
        return ParseResult.Hit(parseExamRows(result))
    }

    // 没有命中任何已知结构
    return ParseResult.Miss(root.keys().asSequence().toList())
}

// 将 JSONArray 解析为 Exam 列表，自动探测字段名（兼容中英文、大小写变体）。
private fun parseExamRows(rows: JSONArray): List<Exam> {
    return buildList {
        for (i in 0 until rows.length()) {
            val item = rows.getJSONObject(i)
            add(
                Exam(
                    courseName = pick(
                        item,
                        "KCM", "courseName", "KCMC", "kcmc",
                        "className", "BJMC",
                        "examName", "KSMC", "ksmc"
                    ),
                    examDate = pick(
                        item,
                        "examDate", "KSRQ", "ksrq",
                        "testDate", "RQ", "rq",
                        "date", "examTime"
                    ),
                    examTime = pick(
                        item,
                        "KSSJMS", "examTime", "KSSJ", "kssj",
                        "testTime", "SJ", "sj",
                        "time", "KS_TIME", "timeRange"
                    ),
                    location = pick(
                        item,
                        "JASMC", "placeName", "KSDD", "ksdd",
                        "place", "location", "room",
                        "examRoom", "JSM", "jsm",
                        "className", "address"
                    ),
                    seatNumber = pick(
                        item,
                        "seatNo", "ZWH", "zwh",
                        "seat", "ZW", "zw",
                        "seatNumber"
                    ),
                    examType = pick(
                        item,
                        "KSLXDM_DISPLAY", "examType", "KSLX", "kslx",
                        "type", "KSLXMC", "kslxmc",
                        "examKind", "examMethod"
                    ),
                    campus = pick(
                        item,
                        "YXDM_DISPLAY", "campusName", "XQMC", "xqmc",
                        "campus", "XQ", "xq"
                    )
                )
            )
        }
    }
}

// 从 JSONObject 中按优先级选取第一个非空字符串字段。
private fun pick(json: JSONObject, vararg keys: String): String {
    for (key in keys) {
        val value = json.optString(key, "")
        if (value.isNotBlank()) return value
    }
    return ""
}
