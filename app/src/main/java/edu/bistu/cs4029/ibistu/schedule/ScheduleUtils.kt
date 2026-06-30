package edu.bistu.cs4029.ibistu.schedule

/** 课表周次解析与课程筛选工具。 */
object ScheduleUtils {
    /** 判断课程是否在指定周次上课。 */
    fun isCourseInWeek(weekText: String, weekNumber: Int): Boolean {
        val cleaned = weekText.replace("周", "").trim()
        if (cleaned.isBlank()) return false

        return cleaned.split(",").any { segment ->
            val value = segment.trim()
            if (value.contains("-")) {
                val range = value.split("-")
                if (range.size != 2) return@any false
                val start = range[0].trim().toIntOrNull() ?: return@any false
                val end = range[1].trim().toIntOrNull() ?: return@any false
                weekNumber in start..end
            } else {
                weekNumber == value.toIntOrNull()
            }
        }
    }

    /** 解析课程涉及的全部周次。 */
    fun getCourseWeeks(weekText: String): Set<Int> {
        val cleaned = weekText.replace("周", "").trim()
        if (cleaned.isBlank()) return emptySet()

        return buildSet {
            cleaned.split(",").forEach { segment ->
                val value = segment.trim()
                if (value.contains("-")) {
                    val range = value.split("-")
                    if (range.size == 2) {
                        val start = range[0].trim().toIntOrNull()
                        val end = range[1].trim().toIntOrNull()
                        if (start != null && end != null && start <= end) {
                            addAll(start..end)
                        }
                    }
                } else {
                    value.toIntOrNull()?.let(::add)
                }
            }
        }
    }

    /** 计算全部课程覆盖的周次范围。 */
    fun getWeekRange(courses: List<Course>): IntRange {
        val weeks = courses.flatMap { getCourseWeeks(it.week) }
        return if (weeks.isEmpty()) DEFAULT_WEEK_RANGE else weeks.min()..weeks.max()
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
