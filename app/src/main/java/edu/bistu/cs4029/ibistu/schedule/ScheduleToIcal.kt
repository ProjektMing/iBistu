package edu.bistu.cs4029.ibistu.schedule

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val TAG = "ScheduleToIcal"

/**
 * 课表 iCal 导出工具。
 *
 * 将 [ScheduleData] 转换为标准 .ics 格式，支持：
 * - 每周连续课程 → FREQ=WEEKLY RRULE
 * - 非连续周课程 → 逐个生成 VEVENT
 * - 导入系统日历或分享文件
 */
object ScheduleToIcal {

    private const val ICS_PRODID = "-//iBistu//Schedule//CN"
    private const val ICS_VERSION = "2.0"
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    // ── 公开 API ──────────────────────────────────────────

    /**
     * 生成 .ics 文件内容。
     * @param data 课表数据（含 courses 和 termWeeks）
     * @return 标准 iCal 格式的字符串
     */
    fun generateIcs(data: ScheduleData): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:$ICS_VERSION")
        sb.appendLine("PRODID:$ICS_PRODID")
        sb.appendLine("CALSCALE:GREGORIAN")
        sb.appendLine("X-WR-CALNAME:${data.termName}（iBistu）")
        sb.appendLine("X-WR-TIMEZONE:Asia/Shanghai")

        for (course in data.courses) {
            appendEvents(sb, course, data.termWeeks)
        }

        sb.append("END:VCALENDAR")
        return sb.toString()
    }

    /**
     * 将 .ics 内容写入临时文件并通过分享 Intent 发送。
     * @return true 表示成功启动分享
     */
    fun shareIcs(context: Context, data: ScheduleData): Boolean {
        return try {
            val icsContent = generateIcs(data)
            val file = File(context.cacheDir, "iBistu_schedule.ics")
            file.writeText(icsContent, Charsets.UTF_8)

            Log.d(TAG, "shareIcs: wrote ${icsContent.length} bytes, ${data.courses.size} courses")

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/calendar"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "导出课表到")
            context.startActivity(chooser)
            Toast.makeText(context, "正在导出课表...", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Log.e(TAG, "shareIcs failed", e)
            Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    // ── VEVENT 生成 ────────────────────────────────────────

    private fun appendEvents(
        sb: StringBuilder,
        course: Course,
        termWeeks: Map<Int, TermWeek>
    ) {
        val weeks = ScheduleUtils.getCourseWeeks(course.week)
        if (weeks.isEmpty()) return

        val sortedWeeks = weeks.sorted()
        val dayOfWeek = course.dayOfWeek // 1=Monday ... 7=Sunday
        val icalDay = toIcalDay(dayOfWeek)

        // 判断是否为连续周（1-16 形式）
        val isContinuous = weeks.size == (sortedWeeks.last() - sortedWeeks.first() + 1)

        if (isContinuous && sortedWeeks.size > 1) {
            // 连续周 → 一个 VEVENT + RRULE
            appendRecurringEvent(sb, course, sortedWeeks, icalDay, termWeeks)
        } else {
            // 非连续周 → 每个周单独一个 VEVENT
            for (weekNum in sortedWeeks) {
                appendSingleEvent(sb, course, weekNum, termWeeks)
            }
        }
    }

    /** 连续周课程：一个 VEVENT + RRULE */
    private fun appendRecurringEvent(
        sb: StringBuilder,
        course: Course,
        sortedWeeks: List<Int>,
        icalDay: String,
        termWeeks: Map<Int, TermWeek>
    ) {
        val firstWeek = sortedWeeks.first()
        val lastWeek = sortedWeeks.last()
        val count = sortedWeeks.size

        val dtStart = computeDtStart(course, firstWeek, termWeeks) ?: return
        val dtEnd = computeDtEnd(dtStart, course.beginTime, course.endTime) ?: return
        val dtUntil = computeDtUntil(course, lastWeek, termWeeks) ?: return

        sb.appendLine("BEGIN:VEVENT")
        sb.appendLine("DTSTART;TZID=Asia/Shanghai:${dtStart.format(TIME_FORMATTER)}")
        sb.appendLine("DTEND;TZID=Asia/Shanghai:${dtEnd.format(TIME_FORMATTER)}")
        sb.appendLine("RRULE:FREQ=WEEKLY;BYDAY=$icalDay;COUNT=$count")
        sb.appendLine("SUMMARY:${escapeIcal(course.name)}")
        sb.appendLine("LOCATION:${escapeIcal(course.classroom)}")
        val desc = buildList {
            if (course.teacher.isNotBlank()) add("教师：${course.teacher}")
            if (course.credit.isNotBlank()) add("学分：${course.credit}")
            if (course.code.isNotBlank()) add("课程码：${course.code}")
            add("上课周次：${course.week}")
        }.joinToString("\\n")
        if (desc.isNotBlank()) sb.appendLine("DESCRIPTION:$desc")
        sb.appendLine("END:VEVENT")
    }

    /** 单次课程事件（用于非连续周） */
    private fun appendSingleEvent(
        sb: StringBuilder,
        course: Course,
        weekNum: Int,
        termWeeks: Map<Int, TermWeek>
    ) {
        val dtStart = computeDtStart(course, weekNum, termWeeks) ?: return
        val dtEnd = computeDtEnd(dtStart, course.beginTime, course.endTime) ?: return

        sb.appendLine("BEGIN:VEVENT")
        sb.appendLine("DTSTART;TZID=Asia/Shanghai:${dtStart.format(TIME_FORMATTER)}")
        sb.appendLine("DTEND;TZID=Asia/Shanghai:${dtEnd.format(TIME_FORMATTER)}")
        sb.appendLine("SUMMARY:${escapeIcal(course.name)}")
        sb.appendLine("LOCATION:${escapeIcal(course.classroom)}")
        val desc = buildList {
            if (course.teacher.isNotBlank()) add("教师：${course.teacher}")
            if (course.credit.isNotBlank()) add("学分：${course.credit}")
            if (course.code.isNotBlank()) add("课程码：${course.code}")
            add("第 ${weekNum} 周")
        }.joinToString("\\n")
        if (desc.isNotBlank()) sb.appendLine("DESCRIPTION:$desc")
        sb.appendLine("END:VEVENT")
    }

    // ── 日期计算 ──────────────────────────────────────────

    /**
     * 计算课程的 DTSTART（不含时间偏移）。
     * 根据 termWeeks[startWeek].startDate + (dayOfWeek - 1) days 计算。
     */
    private fun computeDtStart(
        course: Course,
        weekNum: Int,
        termWeeks: Map<Int, TermWeek>
    ): LocalDateTime? {
        val weekInfo = termWeeks[weekNum]
        val weekStart = weekInfo?.startDate ?: return null

        // 处理 "2026-06-29 00:00:00" 格式（带时间部分）或 "2026-06-29" 格式
        val datePart = weekStart.substringBefore(' ').substringBefore('T')
        val weekStartDate = LocalDate.parse(datePart, DateTimeFormatter.ISO_LOCAL_DATE)
        // dayOfWeek: 1=Monday ... 7=Sunday
        val dayOffset = course.dayOfWeek - 1
        val courseDate = weekStartDate.plusDays(dayOffset.toLong())

        val beginTime = parseTime(course.beginTime) ?: LocalTime.of(8, 0)

        return LocalDateTime.of(courseDate, beginTime)
    }

    /** 计算 DTEND = DTSTART 的日期 + endTime */
    private fun computeDtEnd(
        dtStart: LocalDateTime,
        beginTimeStr: String,
        endTimeStr: String
    ): LocalDateTime? {
        val endTime = parseTime(endTimeStr)
            ?: return dtStart.plusMinutes(45)
        return LocalDateTime.of(dtStart.toLocalDate(), endTime)
    }

    /** 计算 RRULE 的 UNTIL（基于最后一周的日期 + endTime） */
    private fun computeDtUntil(
        course: Course,
        lastWeek: Int,
        termWeeks: Map<Int, TermWeek>
    ): LocalDateTime? {
        val weekInfo = termWeeks[lastWeek]
        val weekStart = weekInfo?.startDate ?: return null
        val datePart = weekStart.substringBefore(' ').substringBefore('T')
        val weekStartDate = LocalDate.parse(datePart, DateTimeFormatter.ISO_LOCAL_DATE)
        val courseDate = weekStartDate.plusDays((course.dayOfWeek - 1).toLong())
        val endTime = parseTime(course.endTime) ?: LocalTime.of(8, 45)
        return LocalDateTime.of(courseDate, endTime)
    }

    // ── 工具函数 ──────────────────────────────────────────

    /** 解析 "HH:mm" 格式的时间字符串 */
    private fun parseTime(time: String): LocalTime? {
        return try {
            LocalTime.parse(time, DateTimeFormatter.ofPattern("H:mm"))
        } catch (_: Exception) {
            try {
                LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"))
            } catch (_: Exception) {
                null
            }
        }
    }

    /** 将 dayOfWeek（1=周一）映射到 iCal 格式（MO, TU, ...） */
    private fun toIcalDay(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "MO"
        2 -> "TU"
        3 -> "WE"
        4 -> "TH"
        5 -> "FR"
        6 -> "SA"
        7 -> "SU"
        else -> "MO"
    }

    /** 转义 iCal 文本中的特殊字符 */
    private fun escapeIcal(text: String): String = text
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
}
