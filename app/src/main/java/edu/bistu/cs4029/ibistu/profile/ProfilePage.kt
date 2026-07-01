package edu.bistu.cs4029.ibistu.profile

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import edu.bistu.cs4029.ibistu.common.state.AppState
import edu.bistu.cs4029.ibistu.schedule.fetchSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/** 登录与账户页面。已登录时展示个人资料，未登录时展示登录表单。 */
@Composable
fun ProfilePage(
    state: AppState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val result = state.loginResult
    var debugTaps by remember { mutableIntStateOf(0) }
    var showEditPage by remember { mutableStateOf(false) }
    var showAvatarViewer by remember { mutableStateOf(false) }

    if (state.isRestoring) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(8.dp))
                Text("恢复登录中...", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    // ── 全屏头像查看器（覆盖在最上层） ──
    if (showAvatarViewer) {
        AvatarViewer(
            state = state,
            onClose = { showAvatarViewer = false }
        )
        return
    }

    if ((result != null && result.isSuccess) || state.courses.isNotEmpty()) {
        if (showEditPage) {
            ProfileEditPage(
                state = state,
                onNavigateBack = { showEditPage = false }
            )
        } else {
            LoggedInView(
                state = state,
                onEditClick = { showEditPage = true },
                onAvatarClick = { showAvatarViewer = true },
                modifier = modifier
            )
        }
    } else {
        // 未登录 — 居中显示登录表单
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LoginForm(state = state, scope = scope, debugTaps = debugTaps) {
                debugTaps = it
            }
        }
    }
}

// ── 全屏头像查看器 ──────────────────────────────────────────────

@Composable
private fun AvatarViewer(
    state: AppState,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    val avatarBitmap = remember(state.avatarUri, state.avatarVersion) {
        if (state.avatarUri.isNotBlank()) {
            try {
                val file = File(state.avatarUri)
                if (file.exists()) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
                    BitmapFactory.decodeFile(state.avatarUri, opts)?.asImageBitmap()
                } else null
            } catch (e: Exception) { null }
        } else null
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val saveAvatar: () -> Unit = {
        if (state.avatarUri.isNotBlank()) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "iBistu_avatar.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                )
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        File(state.avatarUri).inputStream().use { input -> input.copyTo(output) }
                    }
                    Toast.makeText(context, "头像已保存到相册", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        // ── 可缩放头像（先声明，位于底层） ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        // 钳制偏移，防止图片拖出屏幕
                        val maxTranslate = (scale - 1f) * 600f
                        offsetX = (offsetX + pan.x).coerceIn(-maxTranslate, maxTranslate)
                        offsetY = (offsetY + pan.y).coerceIn(-maxTranslate, maxTranslate)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap,
                    contentDescription = "头像",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { showSaveDialog = true })
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        },
                    contentScale = ContentScale.Fit
                )
            } else {
                Surface(
                    modifier = Modifier
                        .size(250.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { showSaveDialog = true })
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        },
                    shape = CircleShape,
                    color = avatarBackgrounds[state.avatarStyle % avatarBackgrounds.size]
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "头像",
                            modifier = Modifier.size(120.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // ── 关闭按钮（后声明，覆盖在最顶层） ──
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 36.dp, end = 12.dp)
                .size(48.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }

    // ── 长按保存确认弹窗 ──
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存头像") },
            text = { Text("是否将当前头像保存到系统相册？") },
            confirmButton = {
                Button(onClick = {
                    showSaveDialog = false
                    saveAvatar()
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("取消") }
            }
        )
    }
}

// ── 已登录界面 ────────────────────────────────────────────────

@Composable
private fun LoggedInView(
    state: AppState,
    onEditClick: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))

        // ── 头像 & 欢迎卡片 ──
        ProfileHeaderCard(state = state, onAvatarClick = onAvatarClick)

        Spacer(Modifier.height(16.dp))

        // ── 学习概况卡片 ──
        StatsCard(state = state)

        Spacer(Modifier.height(16.dp))

        // ── 编辑资料按钮 ──
        Button(
            onClick = onEditClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("编辑资料")
        }

        Spacer(Modifier.height(16.dp))

        // ── 退出登录 ──
        OutlinedButton(
            onClick = state::clearSession,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("退出登录")
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── 头像卡片 ──────────────────────────────────────────────────

@Composable
private fun ProfileHeaderCard(
    state: AppState,
    onAvatarClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AvatarCircle(state = state, onClick = onAvatarClick)

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (state.nickname.isNotBlank()) state.nickname
                else "欢迎回来",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))

            Text(
                text = "学号：${state.studentId}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "已登录",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ── 头像圆圈组件 ──────────────────────────────────────────────

@Composable
private fun AvatarCircle(
    state: AppState,
    onClick: () -> Unit = {}
) {
    val avatarBitmap = remember(state.avatarUri, state.avatarVersion) {
        if (state.avatarUri.isNotBlank()) {
            try {
                val file = File(state.avatarUri)
                if (file.exists()) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeFile(state.avatarUri, opts)?.asImageBitmap()
                } else null
            } catch (e: Exception) { null }
        } else null
    }

    val baseModifier = Modifier.size(72.dp).clickable { onClick() }

    if (avatarBitmap != null) {
        Image(
            bitmap = avatarBitmap,
            contentDescription = "头像",
            modifier = baseModifier.clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(
            modifier = baseModifier,
            shape = CircleShape,
            color = avatarBackgrounds[state.avatarStyle % avatarBackgrounds.size]
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "头像",
                modifier = Modifier
                    .padding(16.dp)
                    .size(40.dp),
                tint = Color.White
            )
        }
    }
}

// ── 学习概况卡片 ──────────────────────────────────────────────

@Composable
private fun StatsCard(state: AppState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "📊 学习概况",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📚 课程数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${state.courses.size} 门",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (state.termName.isNotBlank()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📅 学期",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.termName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ── 登录表单（保持原有逻辑不变）────────────────────────────────

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
                state.applySchedule(fetchSchedule(state.login))
                state.onLoginSuccess()
                state.loadProfile(state.studentId)
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
            state.login.dumpDbToLog()
            state.errorMessage = "DB dumped to logcat"
        } catch (exception: Exception) {
            state.errorMessage = "dump failed: ${exception.message}"
        }
    }
}

private const val DEBUG_TAP_COUNT = 5

/** 预设 8 种头像背景色。 */
private val avatarBackgrounds = listOf(
    Color(0xFFE57373),
    Color(0xFFF06292),
    Color(0xFFBA68C8),
    Color(0xFF64B5F6),
    Color(0xFF4DD0E1),
    Color(0xFF81C784),
    Color(0xFFFFB74D),
    Color(0xFFA1887F),
)
