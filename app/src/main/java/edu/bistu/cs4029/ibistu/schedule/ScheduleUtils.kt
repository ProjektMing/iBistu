package edu.bistu.cs4029.ibistu.schedule

/** 课表周次解析与课程筛选工具。 */
object ScheduleUtils {

    // 匹配 "(单)"、"(单周)"、"(双)"、"(双周)" 等修饰
    private val ODD_EVEN_REGEX = Regex("""[（(]\s*(单周?|双周?)\s*[）)]""")
    // 匹配前导 "第" 和后缀 "周"
    private val PREFIX_SUFFIX_REGEX = Regex("""^第|周$""")

    /**
     * 判断课程是否在指定周次上课。
     * 支持格式：
     * - "1-16" / "1-16周" / "第1-16周"              → 连续范围
     * - "1,3,5,7"                                  → 离散周次
     * - "1-16(单)" / "1-16周(单周)"                  → 单周：1,3,5,...,15
     * - "2-16(双)" / "2-16周(双)"                    → 双周：2,4,6,...,16
     */
    fun isCourseInWeek(weekText: String, weekNumber: Int): Boolean {
        val weeks = getCourseWeeks(weekText)
        return weekNumber in weeks
    }

    /** 解析课程涉及的全部周次。 */
    fun getCourseWeeks(weekText: String): Set<Int> {
        if (weekText.isBlank()) return emptySet()

        // 1. 检测并记录单/双周修饰，然后移除修饰符
        val oddEvenMatch = ODD_EVEN_REGEX.find(weekText)
        val isOddOnly = oddEvenMatch?.value?.contains("单") == true
        val isEvenOnly = oddEvenMatch?.value?.contains("双") == true
        var cleaned = ODD_EVEN_REGEX.replace(weekText, "")

        // 2. 移除中文前后缀：前导 "第"、后缀 "周"
        cleaned = PREFIX_SUFFIX_REGEX.replace(cleaned, "")
        // 也处理残留的"周"字（出现在中间的情况，如 "1-8周,9-16周"）
        cleaned = cleaned.replace("周", "")
        cleaned = cleaned.trim()
        if (cleaned.isBlank()) return emptySet()

        // 3. 先按逗号分割，解析每个段落的原始周次集合
        val rawWeeks = mutableSetOf<Int>()
        cleaned.split(",").forEach { segment ->
            val value = segment.trim()
            if (value.contains("-")) {
                val range = value.split("-")
                if (range.size == 2) {
                    val start = range[0].trim().toIntOrNull()
                    val end = range[1].trim().toIntOrNull()
                    if (start != null && end != null && start <= end) {
                        rawWeeks.addAll(start..end)
                    }
                }
            } else {
                value.toIntOrNull()?.let { rawWeeks.add(it) }
            }
        }

        // 4. 根据单/双周修饰过滤
        return when {
            isOddOnly -> rawWeeks.filter { it % 2 == 1 }.toSet()
            isEvenOnly -> rawWeeks.filter { it % 2 == 0 }.toSet()
            else -> rawWeeks
        }
    }

    /** 计算全部课程覆盖的周次范围。 */
    fun getWeekRange(courses: List<Course>): IntRange {
        val weeks = courses.flatMap { getCourseWeeks(it.week) }
        return if (weeks.isEmpty()) DEFAULT_WEEK_RANGE else weeks.min()..(weeks.max() + 1)
    }

    /** 星期数字对应的中文标签。 */
    val dayLabels = mapOf(
        1 to "一",
        2 to "二",
        3 to "三",
        4 to "四",
        5 to "五",
        6 to "六",
        7 to "日"
    )

    private val DEFAULT_WEEK_RANGE = 1..20
}
