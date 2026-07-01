package edu.bistu.cs4029.ibistu.settings

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import edu.bistu.cs4029.ibistu.common.preferences.AppPreferences
import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.ScheduleUtils
import edu.bistu.cs4029.ibistu.schedule.TermWeek
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 自动静音闹钟调度器。
 *
 * 根据课表数据计算每节课的上课时间，通过 AlarmManager 安排精确闹钟。
 * 同时保存课表副本到 SharedPreferences，以便 BroadcastReceiver 独立重调度。
 */
object AutoMuteScheduler {
    private const val TAG = "AutoMuteScheduler"
    private const val MUTE_DURATION_MINUTES = 45L
    private const val SCHEDULE_WINDOW_DAYS = 30L
    private const val REQUEST_CODE_OFFSET = 1000

    /** 日期时间格式（与教务系统返回格式一致）。 */
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * 开启自动静音后调用：保存课表并安排所有闹钟。
     */
    fun schedule(context: Context, courses: List<Course>, termWeeks: Map<Int, TermWeek>) {
        saveScheduleSnapshot(context, courses, termWeeks)
        scheduleAlarms(context, courses, termWeeks)
        scheduleDailyRescheduleAlarm(context)
        Log.d(TAG, "Scheduled auto-mute with ${courses.size} courses")
    }

    /**
     * 关闭自动静音后调用：取消重调度闹钟和解除闹钟，
     * 并清除持久化状态。已安排的 MUTE 闹钟会被 Receiver 自动忽略。
     */
    fun cancelAll(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return

        // 取消每日重调度闹钟（阻止继续安排新闹钟）
        val rescheduleIntent = Intent(context, AutoMuteReceiver::class.java).apply {
            action = AutoMuteReceiver.ACTION_RESCHEDULE
        }
        PendingIntent.getBroadcast(
            context, 0, rescheduleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )?.cancel()

        // 取消待解除静音的闹钟
        AutoMuteReceiver.cancelUnmuteAlarm(context)

        // 清除持久化状态
        val prefs = AppPreferences(context)
        prefs.clearScheduleSnapshot()
        prefs.unmuteUntil = 0L

        Log.d(TAG, "Auto-mute cancelled, reschedule alarm removed")
    }

    /**
     * 从 SharedPreferences 中读取课表快照并重新调度。
     * 由每日重调度闹钟或开机广播触发。
     */
    fun reschedule(context: Context) {
        val prefs = AppPreferences(context)
        val snapshot = prefs.scheduleSnapshot ?: run {
            Log.w(TAG, "No schedule snapshot found, cannot reschedule")
            return
        }
        val (courses, termWeeks) = parseScheduleSnapshot(snapshot)
        scheduleAlarms(context, courses, termWeeks)
    }

    // ── 内部实现 ────────────────────────────────────────────

    /** 安排未来 SCHEDULE_WINDOW_DAYS 天内的所有课程 MUTE 闹钟。 */
    private fun scheduleAlarms(
        context: Context,
        courses: List<Course>,
        termWeeks: Map<Int, TermWeek>
    ) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        if (!alarm.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule exact alarms: permission not granted")
            return
        }
        val now = System.currentTimeMillis()
        val nowDate = LocalDate.now()
        val endDate = nowDate.plusDays(SCHEDULE_WINDOW_DAYS)
        val zone = ZoneId.systemDefault()

        var scheduledCount = 0

        for (course in courses) {
            val courseWeeks = ScheduleUtils.getCourseWeeks(course.week)
            val parts = course.beginTime.split(":")
            if (parts.size < 2) continue
            val beginHour = parts[0].toIntOrNull() ?: continue
            val beginMinute = parts[1].toIntOrNull() ?: continue

            for (weekNumber in courseWeeks) {
                val termWeek = termWeeks[weekNumber] ?: continue
                val baseDate = try {
                    LocalDateTime.parse(termWeek.startDate, dateFormatter).toLocalDate()
                } catch (_: Exception) { continue }

                // 该课程本周的上课日期 = 周一 + (dayOfWeek - 1) 天
                val courseDate = baseDate.plusDays((course.dayOfWeek - 1).toLong())

                // 只安排未来 SCHEDULE_WINDOW_DAYS 天内的课程
                if (courseDate.isBefore(nowDate) || courseDate.isAfter(endDate)) continue

                val courseDateTime = LocalDateTime.of(courseDate, LocalTime.of(beginHour, beginMinute))
                val triggerMillis = courseDateTime.atZone(zone).toInstant().toEpochMilli()

                // 跳过已过去的时间
                if (triggerMillis <= now) continue

                val intent = Intent(context, AutoMuteReceiver::class.java).apply {
                    action = AutoMuteReceiver.ACTION_MUTE
                }
                val requestCode = REQUEST_CODE_OFFSET +
                        (course.code + weekNumber).hashCode().and(0x7FFF)
                val pending = PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                alarm.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerMillis, pending
                )
                scheduledCount++
            }
        }

        Log.d(TAG, "Scheduled $scheduledCount mute alarms for next $SCHEDULE_WINDOW_DAYS days")
    }

    /** 安排每日凌晨 00:05 的重调度闹钟。 */
    private fun scheduleDailyRescheduleAlarm(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        if (!alarm.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule daily reschedule: permission not granted")
            return
        }
        val intent = Intent(context, AutoMuteReceiver::class.java).apply {
            action = AutoMuteReceiver.ACTION_RESCHEDULE
        }
        val pending = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 计算下一个 00:05 的时间戳
        val now = LocalDateTime.now()
        var nextRun = now.toLocalDate().atTime(LocalTime.of(0, 5))
        if (nextRun.isBefore(now)) {
            nextRun = nextRun.plusDays(1)
        }
        val triggerMillis = nextRun.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
        Log.d(TAG, "Daily reschedule alarm set for ${nextRun}")
    }

    // ── 课表快照（用于 Receiver 独立恢复） ──────────────────

    private fun saveScheduleSnapshot(
        context: Context,
        courses: List<Course>,
        termWeeks: Map<Int, TermWeek>
    ) {
        val json = JSONObject().apply {
            put("courses", JSONArray().apply {
                courses.forEach { course ->
                    put(JSONObject().apply {
                        put("name", course.name)
                        put("code", course.code)
                        put("credit", course.credit)
                        put("teacher", course.teacher)
                        put("classroom", course.classroom)
                        put("campus", course.campus)
                        put("week", course.week)
                        put("dayOfWeek", course.dayOfWeek)
                        put("beginSection", course.beginSection)
                        put("endSection", course.endSection)
                        put("beginTime", course.beginTime)
                        put("endTime", course.endTime)
                    })
                }
            })
            put("termWeeks", JSONObject().apply {
                termWeeks.forEach { (weekNum, tw) ->
                    put(weekNum.toString(), JSONObject().apply {
                        put("weekNumber", tw.weekNumber)
                        put("startDate", tw.startDate)
                        put("endDate", tw.endDate)
                    })
                }
            })
        }
        AppPreferences(context).scheduleSnapshot = json.toString()
    }

    private fun parseScheduleSnapshot(json: String): Pair<List<Course>, Map<Int, TermWeek>> {
        val root = JSONObject(json)
        val courses = buildList {
            val arr = root.getJSONArray("courses")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(Course(
                    name = obj.getString("name"),
                    code = obj.getString("code"),
                    credit = obj.optString("credit", ""),
                    teacher = obj.optString("teacher", ""),
                    classroom = obj.optString("classroom", ""),
                    campus = obj.optString("campus", ""),
                    week = obj.optString("week", ""),
                    dayOfWeek = obj.optInt("dayOfWeek", 0),
                    beginSection = obj.optInt("beginSection", 0),
                    endSection = obj.optInt("endSection", 0),
                    beginTime = obj.optString("beginTime", ""),
                    endTime = obj.optString("endTime", "")
                ))
            }
        }
        val termWeeks = buildMap {
            val twObj = root.getJSONObject("termWeeks")
            twObj.keys().forEach { key ->
                val obj = twObj.getJSONObject(key)
                val weekNumber = obj.optInt("weekNumber", key.toIntOrNull() ?: 0)
                put(weekNumber, TermWeek(
                    weekNumber = weekNumber,
                    startDate = obj.optString("startDate", ""),
                    endDate = obj.optString("endDate", "")
                ))
            }
        }
        return courses to termWeeks
    }
}
