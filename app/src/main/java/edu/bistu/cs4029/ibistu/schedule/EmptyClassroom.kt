package edu.bistu.cs4029.ibistu.schedule

/** 空教室查询结果中的单条教室记录，对应 cxkxjs.do 响应 rows 中的一项。 */
data class EmptyClassroom(
    val name: String,            // JASMC — 教室名称，如 "WLA-106"
    val buildingCode: String,    // JXLDM
    val buildingName: String,    // JXLDM_DISPLAY — 如 "文理楼A座 沙河校区"
    val campusCode: String,      // XXXQDM
    val campusName: String,      // XXXQDM_DISPLAY — 如 "沙河校区"
    val floor: Int,              // LC — 楼层
    val classSeats: Int,         // SKZWS — 上课座位数
    val examSeats: Int,          // KSZWS — 考试座位数
    val typeCode: String,        // JASLXDM
    val typeName: String,        // JASLXDM_DISPLAY — 如 "多媒体"
    val roomCode: String,        // JASDM — 教室代码
    val id: String,              // WID
    val note: String,            // BZ — 备注
    val allowSchedule: Boolean,  // SFYXPK
    val allowBorrow: Boolean,    // SFYXJY
    val allowExam: Boolean       // SFYXKS
)

/** 空教室查询参数（由长按课表自动生成）。 */
data class EmptyClassroomQuery(
    val campusCode: String,      // 校区代码
    val campusName: String,      // 校区名称（用于 querySetting value_display）
    val startDate: String,       // KSRQ — yyyy-MM-dd
    val endDate: String,         // JSRQ
    val startSection: Int,       // KSJC — 开始节次
    val endSection: Int,         // JSJC — 结束节次
    val minSeats: Int = 1,       // SKZWS 最小值
    val buildingCode: String? = null,  // JXLDM（可选）
    val roomName: String? = null       // JASMC（可选，来源于长按课程所在教室）
)

/** 校区名称 → 代码映射（已知校区）。 */
object CampusCodes {
    private val map = mapOf(
        "沙河校区" to "10",
        "小营校区" to "20",
    )

    fun codeOf(name: String): String? = map[name]
    fun nameOf(code: String): String? = map.entries.firstOrNull { it.value == code }?.key
}

/** 节次编号 → 显示名（如 "第1节"）。 */
fun sectionDisplayName(section: Int): String = "第${section}节"

/**
 * 计算长按课表单元格时的空教室查询节次。
 *
 * 有课程时必须覆盖整段课程，避免只查询用户按下的单节而返回实际不可用的教室；
 * 空白单元格则只查询用户选中的节次。
 */
fun querySectionRange(course: Course?, selectedSection: Int): IntRange =
    course?.let { it.beginSection..it.endSection } ?: selectedSection..selectedSection
