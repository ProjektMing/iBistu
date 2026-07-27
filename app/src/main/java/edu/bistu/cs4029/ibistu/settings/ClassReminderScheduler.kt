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
        plans.forEach { plan ->
            val identity = plan.alarmIdentity
            val requestCode = reminderRequestCode(identity)
            val intent = Intent(context, ClassReminderReceiver::class.java).apply {
                action = ClassReminderReceiver.ACTION_REMIND
                data = reminderIntentData(identity)
                putExtra(ClassReminderReceiver.EXTRA_NOTIFICATION_ID, requestCode)
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
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                plan.triggersAt.atZone(zoneId).toInstant().toEpochMilli(),
                pendingIntent
            )
        }
        Log.d(TAG, "Scheduled ${plans.size} class reminders")
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
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextRun.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            pendingIntent
        )
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

    private fun reminderIntentData(identity: String): Uri = Uri.Builder()
        .scheme("ibistu")
        .authority("class-reminder")
        .appendPath(identity)
        .build()

    private fun createSnapshot(
        courses: List<Course>,
        termWeeks: Map<Int, TermWeek>
    ): String = JSONObject().apply {
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

    private fun parseSnapshot(json: String): Pair<List<Course>, Map<Int, TermWeek>> {
        val root = JSONObject(json)
        val courses = buildList {
            val array = root.getJSONArray("courses")
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    Course(
                        name = item.getString("name"),
                        code = item.getString("code"),
                        credit = item.optString("credit", ""),
                        teacher = item.optString("teacher", ""),
                        classroom = item.optString("classroom", ""),
                        campus = item.optString("campus", ""),
                        week = item.optString("week", ""),
                        dayOfWeek = item.optInt("dayOfWeek", 0),
                        beginSection = item.optInt("beginSection", 0),
                        endSection = item.optInt("endSection", 0),
                        beginTime = item.optString("beginTime", ""),
                        endTime = item.optString("endTime", "")
                    )
                )
            }
        }
        val termWeeks = buildMap {
            val weeks = root.getJSONObject("termWeeks")
            weeks.keys().forEach { key ->
                val item = weeks.getJSONObject(key)
                val weekNumber = item.optInt("weekNumber", key.toIntOrNull() ?: 0)
                put(
                    weekNumber,
                    TermWeek(
                        weekNumber = weekNumber,
                        startDate = item.optString("startDate", ""),
                        endDate = item.optString("endDate", "")
                    )
                )
            }
        }
        return courses to termWeeks
    }
}
