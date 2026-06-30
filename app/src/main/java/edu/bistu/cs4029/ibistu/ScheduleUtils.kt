package edu.bistu.cs4029.ibistu

/**
 * 课表工具函数：周次解析、课程筛选
 */
object ScheduleUtils {

    /**
     * 判断课程是否在指定周次上课。
     *
     * week 字段格式示例：
     *   "1-16"       → 第 1 到 16 周
     *   "1,3,5,7,9"  → 第 1、3、5、7、9 周
     *   "1-16周"      → 带中文后缀
     *   "1-8,10,12-16周" → 混合
     *
     * @param weekStr  课程原始 week 字段
     * @param weekNum  要查询的周次（1-based）
     */
    fun isCourseInWeek(weekStr: String, weekNum: Int): Boolean {
        val cleaned = weekStr.replace("周", "").trim()
        if (cleaned.isBlank()) return false

        val segments = cleaned.split(",").map { it.trim() }
        return segments.any { seg ->
            if (seg.contains("-")) {
                val parts = seg.split("-")
                if (parts.size == 2) {
                    val start = parts[0].trim().toIntOrNull() ?: return@any false
                    val end = parts[1].trim().toIntOrNull() ?: return@any false
                    weekNum in start..end
                } else false
            } else {
                weekNum == seg.toIntOrNull()
            }
        }
    }

    /**
     * 获取课程涉及的所有周次。
     */
    fun getCourseWeeks(weekStr: String): Set<Int> {
        val cleaned = weekStr.replace("周", "").trim()
        if (cleaned.isBlank()) return emptySet()

        val result = mutableSetOf<Int>()
        val segments = cleaned.split(",").map { it.trim() }
        segments.forEach { seg ->
            if (seg.contains("-")) {
                val parts = seg.split("-")
                if (parts.size == 2) {
                    val start = parts[0].trim().toIntOrNull()
                    val end = parts[1].trim().toIntOrNull()
                    if (start != null && end != null) {
                        result.addAll(start..end)
                    }
                }
            } else {
                seg.toIntOrNull()?.let { result.add(it) }
            }
        }
        return result
    }

    /**
     * 计算所有课程涉及的最大周次范围。
     * 用于周导航的上下限。
     */
    fun getWeekRange(courses: List<Course>): IntRange {
        if (courses.isEmpty()) return 1..20
        var minWeek = Int.MAX_VALUE
        var maxWeek = Int.MIN_VALUE
        courses.forEach { course ->
            val weeks = getCourseWeeks(course.week)
            if (weeks.isNotEmpty()) {
                val wMin = weeks.min()
                val wMax = weeks.max()
                if (wMin < minWeek) minWeek = wMin
                if (wMax > maxWeek) maxWeek = wMax
            }
        }
        return if (minWeek == Int.MAX_VALUE) 1..20 else minWeek..maxWeek
    }

    /** 星期几的中文标签 */
    val dayLabels = mapOf(
        1 to "一", 2 to "二", 3 to "三", 4 to "四",
        5 to "五", 6 to "六", 7 to "日"
    )
}
