package edu.bistu.cs4029.ibistu.widget

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
import java.time.LocalDate
import java.time.LocalTime

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
                ids.forEach { manager.updateAppWidget(it, contentViews(context, it, model)) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun loadingViews(context: Context) = RemoteViews(context.packageName, R.layout.widget_schedule).apply {
        setTextViewText(R.id.widget_status, "正在读取课表…")
        setViewVisibility(R.id.widget_courses, View.GONE)
        bindActions(context, this)
    }

    private fun contentViews(context: Context, widgetId: Int, model: ScheduleWidgetModel) =
        RemoteViews(context.packageName, R.layout.widget_schedule).apply {
            setTextViewText(R.id.widget_title, model.title)
            setTextViewText(R.id.widget_term, model.term)
            setTextViewText(R.id.widget_status, model.status)
            removeAllViews(R.id.widget_courses)
            setViewVisibility(R.id.widget_courses, if (model.courses.isEmpty()) View.GONE else View.VISIBLE)
            model.courses.take(MAX_VISIBLE_COURSES).forEach { course ->
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

    companion object {
        private const val TAG = "ScheduleWidget"
        const val ACTION_REFRESH = "edu.bistu.cs4029.ibistu.widget.REFRESH"
        private const val MAX_VISIBLE_COURSES = 4

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
    val courses: List<ScheduleWidgetCourse>
)

internal data class ScheduleWidgetCourse(
    val time: String,
    val name: String,
    val room: String,
    val highlighted: Boolean
)

internal object ScheduleWidgetFormatter {
    fun build(schedule: ScheduleData?, date: LocalDate, time: LocalTime): ScheduleWidgetModel {
        if (schedule == null) {
            return ScheduleWidgetModel("今日课表", "iBistu", "打开应用登录并加载课表", emptyList())
        }
        val currentWeek = schedule.termWeeks.values.firstOrNull { week ->
            val start = parseDate(week.startDate)
            val end = parseDate(week.endDate)
            start != null && end != null && !date.isBefore(start) && !date.isAfter(end)
        }?.weekNumber

        if (currentWeek == null) {
            return ScheduleWidgetModel("今日课表", schedule.termName, "当前不在教学周内", emptyList())
        }

        val today = schedule.courses
            .filter { it.dayOfWeek == date.dayOfWeek.value }
            .filter { ScheduleUtils.isCourseInWeek(it.week, currentWeek) }
            .sortedBy { it.beginTime }

        if (today.isEmpty()) {
            return ScheduleWidgetModel("今日课表 · 第${currentWeek}周", schedule.termName, "今天没有课，好好休息", emptyList())
        }

        val activeIndex = today.indexOfFirst { course ->
            val start = parseTime(course.beginTime)
            val end = parseTime(course.endTime)
            start != null && end != null && !time.isBefore(start) && time.isBefore(end)
        }
        val nextIndex = today.indexOfFirst { course ->
            parseTime(course.beginTime)?.isAfter(time) == true
        }
        val highlightIndex = if (activeIndex >= 0) activeIndex else nextIndex
        val status = when {
            activeIndex >= 0 -> "正在上课 · ${today[activeIndex].name}"
            nextIndex >= 0 -> "下一节 · ${today[nextIndex].beginTime} ${today[nextIndex].name}"
            else -> "今日课程已结束"
        }

        return ScheduleWidgetModel(
            title = "今日课表 · 第${currentWeek}周",
            term = schedule.termName,
            status = status,
            courses = today.mapIndexed { index, course -> course.toWidgetCourse(index == highlightIndex) }
        )
    }

    private fun Course.toWidgetCourse(highlighted: Boolean) = ScheduleWidgetCourse(
        time = "$beginTime–$endTime",
        name = name,
        room = classroom.ifBlank { campus.ifBlank { "地点待定" } },
        highlighted = highlighted
    )

    private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    private fun parseTime(raw: String): LocalTime? = runCatching { LocalTime.parse(raw) }.getOrNull()
}
