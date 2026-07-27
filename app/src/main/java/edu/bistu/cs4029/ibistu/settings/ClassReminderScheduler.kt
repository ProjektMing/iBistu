package edu.bistu.cs4029.ibistu.settings

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import edu.bistu.cs4029.ibistu.common.preferences.AppPreferences
import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.ScheduleUtils
import edu.bistu.cs4029.ibistu.schedule.TermWeek
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Persists timetable data and schedules exact alarms for upcoming class notifications.
 */
object ClassReminderScheduler {
    private const val TAG = "ClassReminderScheduler"
    private const val SNAPSHOT_SCHEMA_VERSION = 1
    private const val LEGACY_SNAPSHOT_SCHEMA_VERSION = 0
    private const val REMINDER_REQUEST_CODE_OFFSET = 50_000
    private const val DAILY_RESCHEDULE_REQUEST_CODE = 49_999

    /**
     * Replaces old reminder alarms, persists the latest timetable snapshot and schedules the
     * next reminder window when exact-alarm access is available.
     */
    fun schedule(
        context: Context,
        courses: List<Course>,
        termWeeks: Map<Int, TermWeek>,
        leadMinutes: Int
    ) {
        val prefs = AppPreferences(context)
        prefs.classReminderScheduleSnapshot?.let { oldSnapshot ->
            runCatching { parseSnapshot(oldSnapshot) }
                .onSuccess { (oldCourses, oldWeeks) ->
                    cancelReminderAlarms(context, oldCourses, oldWeeks)
                    if (isLegacySnapshot(oldSnapshot)) {
                        cancelLegacyReminderAlarms(context, oldCourses, oldWeeks)
                    }
                }
                .onFailure { Log.w(TAG, "Unable to cancel the previous reminder snapshot", it) }
        }
        prefs.classReminderScheduleSnapshot = createSnapshot(courses, termWeeks)
        prefs.classReminderLeadMinutes = leadMinutes

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        if (!alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Reminder snapshot saved; exact alarm access is not granted")
            return
        }

        scheduleReminderAlarms(context, courses, termWeeks, prefs.classReminderLeadMinutes)
        scheduleDailyRescheduleAlarm(context)
    }

    /** Restores alarms from the persisted snapshot when reminders and exact alarms are enabled. */
    fun reschedule(context: Context) {
        val prefs = AppPreferences(context)
        if (!prefs.isClassReminderEnabled) return
        val snapshot = prefs.classReminderScheduleSnapshot ?: run {
            Log.w(TAG, "No class reminder snapshot found")
            return
        }
        val (courses, termWeeks) = runCatching { parseSnapshot(snapshot) }
            .getOrElse {
                Log.e(TAG, "Invalid class reminder snapshot", it)
                return
            }
        if (isLegacySnapshot(snapshot)) {
            cancelLegacyReminderAlarms(context, courses, termWeeks)
            prefs.classReminderScheduleSnapshot = createSnapshot(courses, termWeeks)
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        if (!alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot restore class reminders without exact alarm access")
            return
        }
        scheduleReminderAlarms(
            context,
            courses,
            termWeeks,
            prefs.classReminderLeadMinutes
        )
        scheduleDailyRescheduleAlarm(context)
    }

    /** Cancels every reminder and daily refresh alarm, then removes the persisted snapshot. */
    fun cancelAll(context: Context) {
        val prefs = AppPreferences(context)
        prefs.classReminderScheduleSnapshot?.let { snapshot ->
            runCatching { parseSnapshot(snapshot) }
                .onSuccess { (courses, termWeeks) ->
                    cancelReminderAlarms(context, courses, termWeeks)
                    if (isLegacySnapshot(snapshot)) {
                        cancelLegacyReminderAlarms(context, courses, termWeeks)
                    }
                }
                .onFailure { Log.w(TAG, "Unable to parse reminder snapshot while cancelling", it) }
        }

        context.getSystemService(AlarmManager::class.java)?.let { alarmManager ->
            reminderReschedulePendingIntent(
                context,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )?.let { pendingIntent ->
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        prefs.clearClassReminderScheduleSnapshot()
    }

    private fun scheduleReminderAlarms(
        context: Context,
        courses: List<Course>,
        termWeeks: Map<Int, TermWeek>,
        leadMinutes: Int
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val zoneId = ZoneId.systemDefault()
        val plans = ClassReminderPlanner.plan(
            courses = courses,
            termWeeks = termWeeks,
            leadMinutes = leadMinutes
        )
        val notificationIds = allocateClassReminderNotificationIds(
            plans.map(ClassReminderPlan::alarmIdentity)
        )
        var scheduledCount = 0
        plans.forEach { plan ->
            val identity = plan.alarmIdentity
            val requestCode = reminderRequestCode(identity)
            val intent = Intent(context, ClassReminderReceiver::class.java).apply {
                action = ClassReminderReceiver.ACTION_REMIND
                data = reminderIntentData(identity)
                putExtra(
                    ClassReminderReceiver.EXTRA_NOTIFICATION_ID,
                    notificationIds.getValue(identity)
                )
                putExtra(ClassReminderReceiver.EXTRA_COURSE_NAME, plan.courseName)
                putExtra(ClassReminderReceiver.EXTRA_CLASSROOM, plan.classroom)
                putExtra(ClassReminderReceiver.EXTRA_CAMPUS, plan.campus)
                putExtra(ClassReminderReceiver.EXTRA_START_TIME, plan.startsAt.toLocalTime().toString())
                putExtra(ClassReminderReceiver.EXTRA_LEAD_MINUTES, plan.leadMinutes)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    plan.triggersAt.atZone(zoneId).toInstant().toEpochMilli(),
                    pendingIntent
                )
                scheduledCount++
            } catch (error: SecurityException) {
                Log.w(
                    TAG,
                    "Exact-alarm access changed while scheduling ${plan.alarmIdentity}",
                    error
                )
            }
        }
        Log.d(TAG, "Scheduled $scheduledCount of ${plans.size} class reminders")
    }

    private fun cancelReminderAlarms(
        context: Context,
        courses: List<Course>,
        termWeeks: Map<Int, TermWeek>
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        courses.forEach { course ->
            ScheduleUtils.getCourseWeeks(course.week).forEach { weekNumber ->
                if (termWeeks[weekNumber] == null) return@forEach
                val identity = classReminderAlarmIdentity(
                    courseCode = course.code,
                    courseName = course.name,
                    weekNumber = weekNumber,
                    dayOfWeek = course.dayOfWeek,
                    beginTime = course.beginTime
                )
                val requestCode = reminderRequestCode(identity)
                val intent = Intent(context, ClassReminderReceiver::class.java).apply {
                    action = ClassReminderReceiver.ACTION_REMIND
                    data = reminderIntentData(identity)
                }
                PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
                )?.let { pendingIntent ->
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }
        }
    }

    private fun cancelLegacyReminderAlarms(
        context: Context,
        courses: List<Course>,
        termWeeks: Map<Int, TermWeek>
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        courses.forEach { course ->
            ScheduleUtils.getCourseWeeks(course.week).forEach { weekNumber ->
                if (termWeeks[weekNumber] == null) return@forEach
                val intent = Intent(context, ClassReminderReceiver::class.java).apply {
                    action = ClassReminderReceiver.ACTION_REMIND
                }
                PendingIntent.getBroadcast(
                    context,
                    legacyReminderRequestCode(course.code, course.name, weekNumber),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
                )?.let { pendingIntent ->
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }
        }
    }

    private fun scheduleDailyRescheduleAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        if (!alarmManager.canScheduleExactAlarms()) return
        val pendingIntent = reminderReschedulePendingIntent(
            context,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        ) ?: return
        val now = LocalDateTime.now()
        var nextRun = now.toLocalDate().atTime(LocalTime.of(0, 10))
        if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextRun.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                pendingIntent
            )
        } catch (error: SecurityException) {
            Log.w(TAG, "Exact-alarm access changed while scheduling the daily refresh", error)
        }
    }

    private fun reminderReschedulePendingIntent(context: Context, flags: Int): PendingIntent? {
        val intent = Intent(context, ClassReminderReceiver::class.java).apply {
            action = ClassReminderReceiver.ACTION_RESCHEDULE
        }
        return PendingIntent.getBroadcast(
            context,
            DAILY_RESCHEDULE_REQUEST_CODE,
            intent,
            flags
        )
    }

    private fun reminderRequestCode(identity: String): Int =
        REMINDER_REQUEST_CODE_OFFSET + identity.hashCode().and(0x000F_FFFF)

    private fun legacyReminderRequestCode(code: String, name: String, weekNumber: Int): Int =
        REMINDER_REQUEST_CODE_OFFSET +
            "$code|$name|$weekNumber".hashCode().and(0x000F_FFFF)

    private fun reminderIntentData(identity: String): Uri = Uri.Builder()
        .scheme("ibistu")
        .authority("class-reminder")
        .appendPath(identity)
        .build()

    private fun createSnapshot(
        courses: List<Course>,
        termWeeks: Map<Int, TermWeek>
    ): String = JSONObject().apply {
        put("schemaVersion", SNAPSHOT_SCHEMA_VERSION)
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
            termWeeks.forEach { (weekNumber, termWeek) ->
                put(weekNumber.toString(), JSONObject().apply {
                    put("weekNumber", termWeek.weekNumber)
                    put("startDate", termWeek.startDate)
                    put("endDate", termWeek.endDate)
                })
            }
        })
    }.toString()

    internal fun parseSnapshot(json: String): Pair<List<Course>, Map<Int, TermWeek>> {
        val root = JSONObject(json)
        val schemaVersion = root.optInt(
            "schemaVersion",
            LEGACY_SNAPSHOT_SCHEMA_VERSION
        )
        require(
            schemaVersion == LEGACY_SNAPSHOT_SCHEMA_VERSION ||
                schemaVersion == SNAPSHOT_SCHEMA_VERSION
        ) {
            "Unsupported class reminder snapshot schema: $schemaVersion"
        }
        val courses = buildList {
            val array = root.getJSONArray("courses")
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val dayOfWeek = item.getInt("dayOfWeek")
                val beginSection = item.getInt("beginSection")
                val endSection = item.getInt("endSection")
                val beginTime = item.getString("beginTime")
                val endTime = item.getString("endTime")
                val week = item.getString("week")
                require(dayOfWeek in 1..7) { "Invalid course dayOfWeek: $dayOfWeek" }
                require(beginSection > 0 && endSection >= beginSection) {
                    "Invalid course section range: $beginSection-$endSection"
                }
                require(week.isNotBlank()) { "Missing course week expression" }
                require(beginTime.toClassReminderLocalTimeOrNull() != null) {
                    "Invalid course beginTime: $beginTime"
                }
                require(endTime.toClassReminderLocalTimeOrNull() != null) {
                    "Invalid course endTime: $endTime"
                }
                add(
                    Course(
                        name = item.getString("name"),
                        code = item.getString("code"),
                        credit = item.optString("credit", ""),
                        teacher = item.optString("teacher", ""),
                        classroom = item.optString("classroom", ""),
                        campus = item.optString("campus", ""),
                        week = week,
                        dayOfWeek = dayOfWeek,
                        beginSection = beginSection,
                        endSection = endSection,
                        beginTime = beginTime,
                        endTime = endTime
                    )
                )
            }
        }
        val termWeeks = buildMap {
            val weeks = root.getJSONObject("termWeeks")
            weeks.keys().forEach { key ->
                val item = weeks.getJSONObject(key)
                val weekNumber = item.getInt("weekNumber")
                val startDate = item.getString("startDate")
                val endDate = item.getString("endDate")
                require(weekNumber > 0 && key.toIntOrNull() == weekNumber) {
                    "Invalid term week key: $key"
                }
                require(startDate.isNotBlank() && endDate.isNotBlank()) {
                    "Missing date range for week $weekNumber"
                }
                put(
                    weekNumber,
                    TermWeek(
                        weekNumber = weekNumber,
                        startDate = startDate,
                        endDate = endDate
                    )
                )
            }
        }
        return courses to termWeeks
    }

    private fun isLegacySnapshot(json: String): Boolean =
        JSONObject(json).optInt("schemaVersion", LEGACY_SNAPSHOT_SCHEMA_VERSION) ==
            LEGACY_SNAPSHOT_SCHEMA_VERSION
}
