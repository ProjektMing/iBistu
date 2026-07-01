package edu.bistu.cs4029.ibistu.settings

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    val hasExactAlarmPermission = alarm?.canScheduleExactAlarms() ?: false

    Column(
        modifier = modifier
            .fillMaxSize()
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
                    "启动时显示名人名言",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "关闭后，下次启动会跳过第二个文字启动页",
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
        if (state.autoMuteEnabled && !hasExactAlarmPermission) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                "需要闹钟权限",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "自动静音需要「闹钟和提醒」权限才能在上课前准时触发。\n请点击下方按钮前往系统设置授权。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { openExactAlarmSettings(context) }
            ) {
                Text("前往开启闹钟权限")
            }
        }

        // ── 状态信息 ─────────────────────────────────
        if (state.autoMuteEnabled && state.courses.isNotEmpty()) {
            val allReady = hasDndPermission && hasExactAlarmPermission
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text(
                if (allReady) stringResource(R.string.auto_mute_status_enabled) else "状态：权限未就绪",
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
private fun openExactAlarmSettings(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}
