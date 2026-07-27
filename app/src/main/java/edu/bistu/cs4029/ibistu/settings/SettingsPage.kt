package edu.bistu.cs4029.ibistu.settings

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import edu.bistu.cs4029.ibistu.R
import edu.bistu.cs4029.ibistu.common.state.AppState

/** 设置页面：自动静音开关 + 勿扰模式权限引导。 */
@Composable
fun SettingsPage(
    state: AppState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val nm = context.getSystemService(NotificationManager::class.java)
    val hasDndPermission = nm.isNotificationPolicyAccessGranted
    val alarm = context.getSystemService(AlarmManager::class.java)
    var hasExactAlarmPermission by remember(context) {
        mutableStateOf(alarm?.canScheduleExactAlarms() ?: false)
    }
    var hasNotificationPermission by remember(context) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }
    val exactAlarmPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasExactAlarmPermission = alarm?.canScheduleExactAlarms() ?: false
        if (hasExactAlarmPermission) state.refreshCourseAutomation()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ── 启动名人名言开关 ─────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.splash_greeting_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.splash_greeting_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = state.showSplashGreeting,
                onCheckedChange = { enabled ->
                    state.toggleSplashGreeting(enabled)
                }
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "疯狂星期四提醒",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "每周四在“吃啥”页面提醒今天可以疯狂星期四",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = state.showCrazyThursdayReminder,
                onCheckedChange = state::toggleCrazyThursdayReminder
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.class_reminder_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.class_reminder_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = state.classReminderEnabled,
                onCheckedChange = state::toggleClassReminder
            )
        }

        if (state.classReminderEnabled) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.class_reminder_lead_time),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5, 10, 15, 30).forEach { minutes ->
                    FilterChip(
                        selected = state.classReminderLeadMinutes == minutes,
                        onClick = { state.updateClassReminderLeadMinutes(minutes) },
                        label = {
                            Text(stringResource(R.string.class_reminder_minutes, minutes))
                        }
                    )
                }
            }
        }

        if (state.classReminderEnabled && !hasNotificationPermission) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.class_reminder_permission_required),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.class_reminder_permission_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            ) {
                Text(stringResource(R.string.class_reminder_grant_permission))
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        // ── 自动静音开关 ─────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.auto_mute_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.auto_mute_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = state.autoMuteEnabled,
                onCheckedChange = { enabled ->
                    state.toggleAutoMute(enabled)
                }
            )
        }

        // ── DND 权限提示 ─────────────────────────────────
        if (state.autoMuteEnabled && !hasDndPermission) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.auto_mute_permission_required),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.auto_mute_permission_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { openDndSettings(context) }
            ) {
                Text(stringResource(R.string.auto_mute_grant_permission))
            }
        }

        // ── 闹钟权限提示 ─────────────────────────────────
        if ((state.autoMuteEnabled || state.classReminderEnabled) && !hasExactAlarmPermission) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.course_alarm_permission_required),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.course_alarm_permission_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    exactAlarmPermissionLauncher.launch(exactAlarmSettingsIntent(context))
                }
            ) {
                Text(stringResource(R.string.course_alarm_grant_permission))
            }
        }

        if (state.classReminderEnabled && state.courses.isNotEmpty()) {
            val reminderReady = hasNotificationPermission && hasExactAlarmPermission
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text(
                if (reminderReady) stringResource(R.string.class_reminder_status_ready)
                else stringResource(R.string.class_reminder_status_not_ready),
                style = MaterialTheme.typography.bodyMedium,
                color = if (reminderReady) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }

        // ── 状态信息 ─────────────────────────────────
        if (state.autoMuteEnabled && state.courses.isNotEmpty()) {
            val allReady = hasDndPermission && hasExactAlarmPermission
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text(
                if (allReady) stringResource(R.string.auto_mute_status_enabled)
                else stringResource(R.string.auto_mute_status_not_ready),
                style = MaterialTheme.typography.bodyMedium,
                color = if (allReady) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
            )
            Text(
                stringResource(R.string.auto_mute_course_count, state.courses.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 打开系统勿扰模式权限设置页。 */
private fun openDndSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    context.startActivity(intent)
}

/** 打开应用精确闹钟权限设置页。 */
private fun exactAlarmSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:${context.packageName}")
    }
