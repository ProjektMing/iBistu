package edu.bistu.cs4029.ibistu.schedule

import android.util.Log
import edu.bistu.cs4029.ibistu.login.BistuLogin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.Request

private const val TAG = "ExamRepository"

// 从教务系统读取考试安排。
//
// 端点推断逻辑（对照课表 API 的模式）：
//
//   课表 API（已确认可用）:
//     POST /jwapp/sys/kbapp/api/wdkbcx/getMyScheduleDetail.do
//     参数: XNXQDM, XQDM=10
//     响应: { datas: { getMyScheduleDetail: { arrangedList: [...] } } }
//
//   考试页面 URL:
//     /jwapp/sys/wdkwapp/default/index.do?forceApp=wdkwapp#/wdks
//     - app = wdkwapp（我的考务app）
//     - Amis 路由 = #/wdks（我的考试）
//
// 返回值：
//   - 命中端点且有考试数据 → 返回非空列表
//   - 命中端点但无考试数据 → 返回空列表（不是错误）
//   - 所有端点都没命中 → 抛出异常
suspend fun fetchExams(login: BistuLogin, termCode: String): List<Exam> = withContext(Dispatchers.IO) {
    require(termCode.isNotBlank()) { "termCode 不能为空（用于请求学期考试安排）" }
    val basePath = "https://jwxt.bistu.edu.cn/jwapp/sys/wdkwapp"
    // 用户提供的明确端点，优先尝试
    val explicitEndpoints = listOf(
        "https://jwxt.bistu.edu.cn/jwapp/sys/wdkwapp/api/wdks/queryMyExamArrangeMent.do",
    )

    // submodule 候选（对应课表的 "wdkbcx" = 我的课表查询）
    val submodules = listOf(
        "wdkscx",    // 我的考试查询（与 wdkbcx 完全对仗）
        "wdks",      // 我的考试（对应 Amis 路由 #/wdks）
        "wdkwcx",    // 我的考务查询（与 app 名 wdkwapp 对齐）
        "kscx",      // 考试查询
        "exam",      // 英文
    )

    // action 候选（对应课表的 "getMyScheduleDetail"）
    val actions = listOf(
        "queryMyExamArrangeMent",
        "getMyExamSchedule",
        "getKsksList",
        "getMyExam",
        "getExamList",
        "queryExam",
    )

    val apiEndpoints = submodules.flatMap { sub ->
        actions.map { act -> "$basePath/api/$sub/$act.do" }
    }

    val moduleActions = listOf(
        "cxKsksList", "cxKsxx", "getKsxx", "getKsksList", "wdks",
    )
    val moduleEndpoints = submodules.flatMap { sub ->
        moduleActions.map { act -> "$basePath/modules/$sub/$act.do" }
    }

    val rootEndpoints = actions.map { "$basePath/api/$it.do" }
    val allEndpoints = explicitEndpoints + apiEndpoints + moduleEndpoints + rootEndpoints

    Log.d(TAG, "Will probe ${allEndpoints.size} endpoints for termCode=$termCode")

    // 先访问考试页面，确保服务器下发所有必要的 Cookie（如 _WEU）
    try {
        val primeUrl = "https://jwxt.bistu.edu.cn/jwapp/sys/wdkwapp/*default/index.do" +
                "?THEME=indigo&EMAP_LANG=zh&forceApp=wdkwapp"
        login.redirectClient.newCall(
            okhttp3.Request.Builder().url(primeUrl).get().build()
        ).execute().close()
        Log.d(TAG, "Primed wdkwapp page, cookies should be ready")
    } catch (e: Exception) {
        Log.w(TAG, "Failed to prime wdkwapp page: ${e.message}")
    }

    val paramSets = listOf(
        mapOf("XNXQDM" to termCode),
        mapOf("XNXQDM" to termCode, "pageNumber" to "1", "pageSize" to "100"),
        mapOf("XNXQDM" to termCode, "XQDM" to "10"),
        emptyMap(),
    )

    var lastError: Exception? = null

    for ((idx, endpoint) in allEndpoints.withIndex()) {
        for (params in paramSets) {
            try {
                Log.v(TAG, "[${idx + 1}/${allEndpoints.size}] Trying POST $endpoint params=$params")
                val json = login.post(endpoint, params)

                val trimmed = json.trimStart()
                // 非 JSON 响应（HTML 错误页等），跳过
                if (trimmed.isEmpty() || !(trimmed.startsWith("{") || trimmed.startsWith("["))) {
                    Log.v(TAG, "  -> non-JSON response (${trimmed.take(120)})")
                    continue
                }

                when (val result = parseExamResponse(json)) {
                    is ParseResult.Hit -> {
                        if (result.exams.isNotEmpty()) {
                            Log.d(TAG, "✅ Loaded ${result.exams.size} exams from $endpoint")
                        } else {
                            Log.d(TAG, "✅ Endpoint $endpoint hit, but exam list is empty (no exams this term)")
                        }
                        return@withContext result.exams
                    }
                    is ParseResult.Miss -> {
                        Log.v(TAG, "  -> valid JSON but doesn't look like exam data. Root keys: ${result.rootKeys}")
                    }
                }
            } catch (e: Exception) {
                Log.v(TAG, "  -> request failed: ${e.message}")
                lastError = e
            }
        }
    }

    // 所有端点都没有命中考试 API 的特征结构
    Log.e(TAG, "All ${allEndpoints.size} endpoints failed. Last error: ${lastError?.message}")
    throw Exception(
        "无法获取考试安排（已尝试 ${allEndpoints.size} 个端点）。" +
        "请在浏览器中登录教务系统 → 考试安排页面 → DevTools Network 面板，" +
        "找到 XHR 请求的 URL，然后更新 ExamRepository.kt 中的端点列表。"
    )
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
// - 返回 Miss            = JSON 结构不匹配 → 不是考试 API，继续探测下一个端点
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
