package edu.bistu.cs4029.ibistu.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import edu.bistu.cs4029.ibistu.common.state.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 登录与账户页面。 */
@Composable
fun ProfilePage(
    state: AppState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val result = state.loginResult
    var debugTaps by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.isRestoring) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Spacer(Modifier.height(8.dp))
            Text("恢复登录中...", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        if ((result != null && result.isSuccess) || state.login.getAllCookies().isNotEmpty()) {
            LoggedInContent(state)
            return@Column
        }

        LoginForm(state = state, scope = scope, debugTaps = debugTaps) {
            debugTaps = it
        }
    }
}

@Composable
private fun LoggedInContent(state: AppState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "登录成功",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "欢迎回来，今天也要好好上课",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
        }
    }

    Spacer(Modifier.height(20.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProfileStatCard(
            icon = Icons.Filled.DateRange,
            value = state.courses.size.toString(),
            label = "本学期课程",
            modifier = Modifier.weight(1f)
        )
        ProfileStatCard(
            icon = Icons.Filled.CheckCircle,
            value = state.exams.size.toString(),
            label = "考试安排",
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(Modifier.height(12.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("当前学期", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                state.selectedTermName.ifBlank { state.termName.ifBlank { "课表加载中" } },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    Spacer(Modifier.height(20.dp))
    OutlinedButton(
        onClick = state::clearSession,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("退出登录")
    }
}

@Composable
private fun ProfileStatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(18.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LoginForm(
    state: AppState,
    scope: CoroutineScope,
    debugTaps: Int,
    onDebugTapsChange: (Int) -> Unit
) {
    Text(
        "iBistu 登录",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.clickable {
            val updatedTaps = debugTaps + 1
            if (updatedTaps >= DEBUG_TAP_COUNT) {
                state.showDebug = !state.showDebug
                onDebugTapsChange(0)
            } else {
                onDebugTapsChange(updatedTaps)
            }
        }
    )
    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = state.studentId,
        onValueChange = {
            state.studentId = it
            state.errorMessage = ""
        },
        label = { Text("学号") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = state.password,
        onValueChange = {
            state.password = it
            state.errorMessage = ""
        },
        label = { Text("密码") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))

    Button(
        onClick = { login(state, scope) },
        enabled = !state.isLoggingIn,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.isLoggingIn) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(if (state.isLoggingIn) "登录中..." else "登录")
    }

    if (state.errorMessage.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Text(state.errorMessage, color = MaterialTheme.colorScheme.error)
    }

    if (state.showDebug) {
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = { dumpDatabase(state, scope) }) {
            Text("📋 Dump DB")
        }
    }
}

private fun login(state: AppState, scope: CoroutineScope) {
    if (state.studentId.isBlank() || state.password.isBlank()) {
        state.errorMessage = "请输入学号和密码"
        return
    }

    state.isLoggingIn = true
    state.errorMessage = ""
    scope.launch {
        try {
            val result = state.login.fullLogin(state.studentId, state.password)
            state.loginResult = result
            if (result.isSuccess) {
                val schedule = state.scheduleRepo.fetchAndCache(state.login)
                state.applySchedule(schedule)
                val exams = state.examRepo.fetchAndCache(state.login, schedule.termCode)
                state.exams = exams
            } else {
                state.errorMessage = result.message.ifBlank { "登录失败: code=${result.code}" }
            }
        } catch (exception: Exception) {
            state.errorMessage = "网络错误: ${exception.message}"
        } finally {
            state.isLoggingIn = false
        }
    }
}

private fun dumpDatabase(state: AppState, scope: CoroutineScope) {
    scope.launch {
        try {
            state.login.dumpToLog()
            state.errorMessage = "DB dumped to logcat"
        } catch (exception: Exception) {
            state.errorMessage = "dump failed: ${exception.message}"
        }
    }
}

private const val DEBUG_TAP_COUNT = 5
