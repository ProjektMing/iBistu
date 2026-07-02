package edu.bistu.cs4029.ibistu.schedule

/**
 * 解析教务系统 API 的 `weeksAndTeachers` 字段。
 *
 * 该字段同时包含周次范围和教师姓名，典型格式：
 * - "1-16周 张老师"           → weeks="1-16", teacher="张老师"
 * - "张三[1-16周]"             → weeks="1-16", teacher="张三"
 * - "1-8周 李老师, 9-16周 王老师" → weeks="1-16", teacher="李老师, 王老师"
 * - "1-16周(单) 赵六"          → weeks="1-16(单)", teacher="赵六"
 * - "1-16周(双)"               → weeks="1-16(双)", teacher=""
 * - "1-16周" (无教师)          → weeks="1-16", teacher=""
 */
object WeeksAndTeachersParser {

    /**
     * 解析结果。
     * @param weekText  提取的纯周次字符串，可直接传给 [ScheduleUtils.getCourseWeeks]
     * @param teacher   提取的教师姓名（可能为空）
     */
    data class Parsed(
        val weekText: String,
        val teacher: String
    )

    /**
     * 从 `weeksAndTeachers` 原始字符串中提取周次和教师。
     * 若解析失败，返回 [fallbackWeek] 作为 weekText，teacher 为空。
     */
    fun parse(raw: String, fallbackWeek: String = ""): Parsed {
        if (raw.isBlank()) return Parsed(fallbackWeek, "")

        // ── 模式 1: "张三[1-16周]" / "张三[1-16周(单)]" ──
        val bracketPattern = Regex("""^(.+?)\[([^]]+)]$""")
        bracketPattern.matchEntire(raw.trim())?.let { match ->
            val teacher = match.groupValues[1].trim()
            val weekPart = match.groupValues[2].trim()
            return Parsed(weekPart, teacher)
        }

        // ── 模式 2: 逗号分隔的多段（如 "1-8周 李老师, 9-16周 王老师"）──
        if (raw.contains(",") && (raw.contains("周") || raw.contains("单") || raw.contains("双"))) {
            val segments = raw.split(",")
            val weekParts = mutableListOf<String>()
            val teacherParts = mutableListOf<String>()
            for (seg in segments) {
                val part = parseSingleSegment(seg.trim())
                if (part.weekText.isNotBlank()) weekParts.add(part.weekText)
                if (part.teacher.isNotBlank()) teacherParts.add(part.teacher)
            }
            if (weekParts.isNotEmpty()) {
                return Parsed(
                    weekText = weekParts.joinToString(","),
                    teacher = teacherParts.distinct().joinToString(", ")
                )
            }
        }

        // ── 模式 3: 单段 "1-16周 张老师" 或 "1-16周(单) 张老师" ──
        return parseSingleSegment(raw.trim())
    }

    /**
     * 解析单段文本（不含逗号的单个教师+周次组合）。
     * 策略：从右向左找到"周"字（含可能的(单)/(双)修饰），
     * 其左边为周次部分，右边为教师部分。
     */
    private fun parseSingleSegment(text: String): Parsed {
        // 先找周次模式的结束位置（"周"字，可能紧跟 (单)/(双) 等修饰）
        val weekEndPattern = Regex("""周[）)""]?$|周[（(](单|双)周?[）)]$""")
        val weekPattern = Regex("""(\d+[-]\d+周([（(](单|双)周?[）)])?|\d+周([（(](单|双)周?[）)])?|\d+([，,-]\d+)*周([（(](单|双)周?[）)])?)""")

        val weekMatch = weekPattern.find(text)
        if (weekMatch != null) {
            val weekRange = weekMatch.range
            val weekText = text.substring(weekRange.first, weekRange.last + 1)
            // 教师是剩余部分的拼接（去掉周次部分前后的空格和分隔符）
            val before = text.substring(0, weekRange.first).trim()
            val after = text.substring(weekRange.last + 1).trim()
            val teacher = listOf(before, after).filter { it.isNotBlank() }
                .joinToString(" ")
                .trim()
            return Parsed(cleanWeekText(weekText), teacher)
        }

        // 无"周"字时的回退：尝试整体作为周次解析（如纯 "1-16"）
        if (text.matches(Regex("""^[\d,\-，\s]+$"""))) {
            return Parsed(text.replace("，", ",").replace(" ", ""), "")
        }

        // 最后的回退：原样返回
        return Parsed(text, "")
    }

    /** 清洗周次文本中的 "周" 字 → 交由 ScheduleUtils 统一处理 */
    private fun cleanWeekText(text: String): String = text
}
