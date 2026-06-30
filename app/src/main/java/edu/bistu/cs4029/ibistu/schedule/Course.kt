package edu.bistu.cs4029.ibistu.schedule

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

/** 当前学期及其课程集合。 */
data class ScheduleData(
    val termName: String,
    val courses: List<Course>
)
