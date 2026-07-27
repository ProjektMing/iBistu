package edu.bistu.cs4029.ibistu.settings

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import edu.bistu.cs4029.ibistu.MainActivity
import edu.bistu.cs4029.ibistu.R
import edu.bistu.cs4029.ibistu.common.preferences.AppPreferences

/**
 * Posts class notifications for [ACTION_REMIND] and restores alarms for [ACTION_RESCHEDULE].
 */
class ClassReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = AppPreferences(context)
        when (intent.action) {
            ACTION_REMIND -> {
                if (prefs.isClassReminderEnabled) showReminder(context, intent)
            }

            ACTION_RESCHEDULE -> {
                if (prefs.isClassReminderEnabled) ClassReminderScheduler.reschedule(context)
            }
        }
    }

    private fun showReminder(context: Context, intent: Intent) {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME).orEmpty()
        if (courseName.isBlank()) return
        val classroom = intent.getStringExtra(EXTRA_CLASSROOM).orEmpty()
        val campus = intent.getStringExtra(EXTRA_CAMPUS).orEmpty()
        val startTime = intent.getStringExtra(EXTRA_START_TIME).orEmpty()
        val leadMinutes = intent.getIntExtra(
            EXTRA_LEAD_MINUTES,
            AppPreferences.DEFAULT_CLASS_REMINDER_LEAD_MINUTES
        )
        val notificationId = intent.getIntExtra(
            EXTRA_NOTIFICATION_ID,
            courseName.hashCode().and(Int.MAX_VALUE)
        )

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.class_reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.class_reminder_channel_description)
            }
        )

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val location = listOf(classroom, campus)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")
        val details = buildString {
            append(context.getString(R.string.class_reminder_starts_in, leadMinutes))
            if (startTime.isNotBlank()) append(" · ").append(startTime)
            if (location.isNotBlank()) append(" · ").append(location)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_class_reminder)
            .setContentTitle(courseName)
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .build()
        manager.notify(notificationId, notification)
    }

    companion object {
        const val ACTION_REMIND = "edu.bistu.cs4029.ibistu.ACTION_CLASS_REMINDER"
        const val ACTION_RESCHEDULE =
            "edu.bistu.cs4029.ibistu.ACTION_CLASS_REMINDER_RESCHEDULE"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_COURSE_NAME = "course_name"
        const val EXTRA_CLASSROOM = "classroom"
        const val EXTRA_CAMPUS = "campus"
        const val EXTRA_START_TIME = "start_time"
        const val EXTRA_LEAD_MINUTES = "lead_minutes"
        private const val CHANNEL_ID = "class_reminders"
    }
}
