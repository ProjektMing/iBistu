package edu.bistu.cs4029.ibistu.schedule

/** 学期选项（代码 + 名称），来自 xnxq.do 列表。 */
data class TermOption(
    val termCode: String,
    val termName: String
)

/** 单条课程安排。 */
data class Course(
    val name: String,
    val code: String,
    val credit: String,
    val teacher: String,
    val classroom: String,
    val campus: String,
    val week: String,
    val dayOfWeek: Int,
    val beginSection: Int,
    val endSection: Int,
    val beginTime: String,
    val endTime: String
)

/** 某一教学周对应的日期范围。 */
data class TermWeek(
    val weekNumber: Int,
    val startDate: String,
    val endDate: String
)

/** 当前学期及其课程集合。 */
data class ScheduleData(
    val termCode: String,
    val termName: String,
    val courses: List<Course>,
    val termWeeks: Map<Int, TermWeek>
)

/** 单条考试安排。 */
data class Exam(
    val courseName: String,
    val examDate: String,      // "2025-01-06"
    val examTime: String,      // "09:00-11:00"
    val location: String,
    val seatNumber: String,
    val examType: String,
    val campus: String
)
