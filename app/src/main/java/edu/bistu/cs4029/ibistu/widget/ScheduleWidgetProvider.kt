package edu.bistu.cs4029.ibistu.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import edu.bistu.cs4029.ibistu.MainActivity
import edu.bistu.cs4029.ibistu.R
import edu.bistu.cs4029.ibistu.login.AppDatabase
import edu.bistu.cs4029.ibistu.schedule.CachedScheduleRepository
import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.ScheduleData
import edu.bistu.cs4029.ibistu.schedule.ScheduleUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        updateAsync(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))
            updateAsync(context, manager, ids)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        updateAsync(context, manager, intArrayOf(appWidgetId))
    }

    private fun updateAsync(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        ids.forEach { manager.updateAppWidget(it, loadingViews(context)) }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = CachedScheduleRepository(AppDatabase.getInstance(context.applicationContext))
                val model = try {
                    val schedule = repository.loadCached()
                    ScheduleWidgetFormatter.build(schedule, LocalDate.now(), LocalTime.now())
                } catch (exception: Exception) {
                    Log.e(TAG, "Failed to load the cached schedule", exception)
                    ScheduleWidgetModel("今日课表", "iBistu", "加载失败，请稍后重试", emptyList())
                }
                scheduleBoundaryRefresh(context, model.nextRefreshAt)
                ids.forEach { widgetId ->
                    val height = manager.getAppWidgetOptions(widgetId).getInt(
                        AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                        DEFAULT_WIDGET_HEIGHT_DP
                    )
                    val visibleCourseLimit = ScheduleWidgetLayoutPolicy.visibleCourseLimit(height)
                    manager.updateAppWidget(
                        widgetId,
                        contentViews(context, widgetId, model, visibleCourseLimit)
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        automaticRefreshPendingIntent(
            context,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )?.let { pendingIntent ->
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun loadingViews(context: Context) = RemoteViews(context.packageName, R.layout.widget_schedule).apply {
        setTextViewText(R.id.widget_status, "正在读取课表…")
        setViewVisibility(R.id.widget_courses, View.GONE)
        bindActions(context, this)
    }

    private fun contentViews(
        context: Context,
        widgetId: Int,
        model: ScheduleWidgetModel,
        visibleCourseLimit: Int
    ) =
        RemoteViews(context.packageName, R.layout.widget_schedule).apply {
            setTextViewText(R.id.widget_title, model.title)
            setTextViewText(R.id.widget_term, model.term)
            setTextViewText(R.id.widget_status, model.status)
            removeAllViews(R.id.widget_courses)
            setViewVisibility(
                R.id.widget_courses,
                if (model.courses.isEmpty() || visibleCourseLimit == 0) View.GONE else View.VISIBLE
            )
            model.courses.take(visibleCourseLimit).forEach { course ->
                addView(R.id.widget_courses, courseRow(context, course))
            }
            bindActions(context, this, widgetId)
        }

    private fun courseRow(context: Context, course: ScheduleWidgetCourse) =
        RemoteViews(context.packageName, R.layout.widget_schedule_course).apply {
            setTextViewText(R.id.widget_course_time, course.time)
            setTextViewText(R.id.widget_course_name, course.name)
            setTextViewText(R.id.widget_course_room, course.room)
            setTextColor(
                R.id.widget_course_time,
                context.getColor(if (course.highlighted) R.color.widget_accent else R.color.widget_text_secondary)
            )
        }

    private fun bindActions(context: Context, views: RemoteViews, widgetId: Int = 0) {
        val openIntent = Intent(context, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            context, widgetId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openPending)

        val refreshIntent = Intent(context, ScheduleWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPending = PendingIntent.getBroadcast(
            context, widgetId, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)
    }

    private fun scheduleBoundaryRefresh(context: Context, nextRefreshAt: LocalDateTime?) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = automaticRefreshPendingIntent(
            context,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        ) ?: return
        if (nextRefreshAt == null) {
            alarmManager.cancel(pendingIntent)
            return
        }
        val triggerAtMillis = nextRefreshAt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli() + BOUNDARY_REFRESH_DELAY_MILLIS
        if (triggerAtMillis <= System.currentTimeMillis()) return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC,
            triggerAtMillis,
            pendingIntent
        )
    }

    private fun automaticRefreshPendingIntent(context: Context, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            AUTOMATIC_REFRESH_REQUEST_CODE,
            Intent(context, ScheduleWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            },
            flags
        )

    companion object {
        private const val TAG = "ScheduleWidget"
        const val ACTION_REFRESH = "edu.bistu.cs4029.ibistu.widget.REFRESH"
        private const val DEFAULT_WIDGET_HEIGHT_DP = 140
        private const val AUTOMATIC_REFRESH_REQUEST_CODE = 90_001
        private const val BOUNDARY_REFRESH_DELAY_MILLIS = 1_000L

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ScheduleWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            context.sendBroadcast(Intent(context, ScheduleWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            })
        }
    }
}

internal data class ScheduleWidgetModel(
    val title: String,
    val term: String,
    val status: String,
    val courses: List<ScheduleWidgetCourse>,
    val nextRefreshAt: LocalDateTime? = null
)

internal data class ScheduleWidgetCourse(
    val time: String,
    val name: String,
    val room: String,
    val highlighted: Boolean
)

internal object ScheduleWidgetLayoutPolicy {
    fun visibleCourseLimit(heightDp: Int): Int = when {
        heightDp < 120 -> 0
        heightDp < 180 -> 1
        heightDp < 260 -> 2
        else -> 4
    }
}

internal object ScheduleWidgetFormatter {
    fun build(schedule: ScheduleData?, date: LocalDate, time: LocalTime): ScheduleWidgetModel {
        if (schedule == null) {
            return ScheduleWidgetModel("今日课表", "iBistu", "打开应用登录并加载课表", emptyList())
        }
        val currentWeek = weekNumberOn(schedule, date)
        val title = currentWeek?.let { "今日课表 · 第${it}周" } ?: "近期课表"
        val subtitle = "${date.monthValue}月${date.dayOfMonth}日 ${dayLabel(date)} · ${schedule.termName}"

        if (currentWeek != null) {
            val today = coursesOn(schedule, date, currentWeek)
            val activeIndex = today.indexOfFirst { course ->
                val start = parseTime(course.beginTime)
                val end = parseTime(course.endTime)
                start != null && end != null && !time.isBefore(start) && time.isBefore(end)
            }
            val nextIndex = today.indexOfFirst { course ->
                parseTime(course.beginTime)?.isAfter(time) == true
            }
            val focusIndex = if (activeIndex >= 0) activeIndex else nextIndex

            if (focusIndex >= 0) {
                val status = if (activeIndex >= 0) {
                    val remainingMinutes = roundedUpMinutesBetween(
                        time,
                        parseTime(today[activeIndex].endTime)
                    )
                    "正在上课 · ${today[activeIndex].name} · 还有 ${remainingMinutes} 分钟"
                } else {
                    val minutesUntil = roundedUpMinutesBetween(
                        time,
                        parseTime(today[nextIndex].beginTime)
                    )
                    "${formatCountdown(minutesUntil)}上课 · ${today[nextIndex].name}"
                }
                val remainingCourses = today.mapIndexedNotNull { index, course ->
                    if (index == activeIndex || parseTime(course.beginTime)?.isAfter(time) == true) {
                        course.toWidgetCourse(highlighted = index == focusIndex)
                    } else {
                        null
                    }
                }
                val boundaryTime = parseTime(
                    if (activeIndex >= 0) {
                        today[activeIndex].endTime
                    } else {
                        today[nextIndex].beginTime
                    }
                )
                val nextRefreshAt = boundaryTime?.let { LocalDateTime.of(date, it) }
                return ScheduleWidgetModel(
                    title,
                    subtitle,
                    status,
                    remainingCourses,
                    nextRefreshAt
                )
            }
        }

        val nextDay = findNextTeachingDay(schedule, date)
        if (nextDay != null) {
            val dayPrefix = relativeDayLabel(date, nextDay.date)
            val firstCourse = nextDay.courses.first()
            return ScheduleWidgetModel(
                title = title,
                term = subtitle,
                status = "$dayPrefix ${firstCourse.beginTime} 上课 · ${firstCourse.name}",
                courses = nextDay.courses.mapIndexed { index, course ->
                    course.toWidgetCourse(
                        highlighted = index == 0,
                        dayPrefix = dayPrefix
                    )
                },
                nextRefreshAt = parseTime(firstCourse.beginTime)?.let {
                    LocalDateTime.of(nextDay.date, it)
                }
            )
        }

        val emptyStatus = if (currentWeek == null) {
            "当前不在教学周内，未来 7 天没有课程"
        } else if (coursesOn(schedule, date, currentWeek).isEmpty()) {
            "今天没有课，未来 7 天没有安排"
        } else {
            "今天课程已结束，未来 7 天没有安排"
        }
        return ScheduleWidgetModel(title, subtitle, emptyStatus, emptyList())
    }

    private data class TeachingDay(
        val date: LocalDate,
        val courses: List<Course>
    )

    private fun findNextTeachingDay(schedule: ScheduleData, date: LocalDate): TeachingDay? {
        for (offset in 1L..7L) {
            val candidateDate = date.plusDays(offset)
            val weekNumber = weekNumberOn(schedule, candidateDate) ?: continue
            val courses = coursesOn(schedule, candidateDate, weekNumber)
            if (courses.isNotEmpty()) return TeachingDay(candidateDate, courses)
        }
        return null
    }

    private fun weekNumberOn(schedule: ScheduleData, date: LocalDate): Int? =
        schedule.termWeeks.values.firstOrNull { week ->
            val start = parseDate(week.startDate)
            val end = parseDate(week.endDate)
            start != null && end != null && !date.isBefore(start) && !date.isAfter(end)
        }?.weekNumber

    private fun coursesOn(
        schedule: ScheduleData,
        date: LocalDate,
        weekNumber: Int
    ): List<Course> = schedule.courses
        .filter { it.dayOfWeek == date.dayOfWeek.value }
        .filter { ScheduleUtils.isCourseInWeek(it.week, weekNumber) }
        .filter { parseTime(it.beginTime) != null && parseTime(it.endTime) != null }
        .sortedBy { parseTime(it.beginTime) }

    private fun Course.toWidgetCourse(
        highlighted: Boolean,
        dayPrefix: String? = null
    ) = ScheduleWidgetCourse(
        time = if (dayPrefix == null) "$beginTime–$endTime" else "$dayPrefix $beginTime",
        name = name,
        room = classroom.ifBlank { campus.ifBlank { "地点待定" } },
        highlighted = highlighted
    )

    private fun formatCountdown(minutes: Long): String = when {
        minutes < 60 -> "$minutes 分钟后"
        minutes % 60 == 0L -> "${minutes / 60} 小时后"
        else -> "${minutes / 60} 小时 ${minutes % 60} 分钟后"
    }

    private fun roundedUpMinutesBetween(start: LocalTime, end: LocalTime?): Long {
        if (end == null) return 1
        val seconds = Duration.between(start, end).seconds.coerceAtLeast(1)
        return (seconds + 59) / 60
    }

    private fun relativeDayLabel(today: LocalDate, target: LocalDate): String =
        when (Duration.between(today.atStartOfDay(), target.atStartOfDay()).toDays()) {
            1L -> "明天"
            2L -> "后天"
            else -> dayLabel(target)
        }

    private fun dayLabel(date: LocalDate): String = when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }

    private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    private fun parseTime(raw: String): LocalTime? = runCatching { LocalTime.parse(raw) }.getOrNull()
}
