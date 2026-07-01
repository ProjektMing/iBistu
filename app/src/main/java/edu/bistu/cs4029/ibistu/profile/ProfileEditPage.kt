package edu.bistu.cs4029.ibistu.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import edu.bistu.cs4029.ibistu.common.state.AppState
import kotlinx.coroutines.launch
import java.io.File

/**
 * 编辑个人资料页面。
 *
 * 头像交互流程：点击头像（或"点击更换头像"文本）→ 请求相册权限 →
 * 打开相册选择图片 → 系统裁剪 → 实时刷新显示。
 */
@Composable
fun ProfileEditPage(
    state: AppState,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── 本地编辑状态 ──
    var editNickname by remember { mutableStateOf(state.nickname) }
    var editRealName by remember { mutableStateOf(state.realName) }
    var editClassName by remember { mutableStateOf(state.className) }
    var editGender by remember { mutableIntStateOf(state.gender) }
    var editAvatarStyle by remember { mutableIntStateOf(state.avatarStyle) }
    var editAvatarPath by remember { mutableStateOf(state.avatarUri) }

    // 头像刷新计数器：每次裁剪后递增，强制 AvatarDisplay 重新加载图片
    var avatarVersion by remember { mutableIntStateOf(0) }

    // 头像选取裁剪状态
    var showCropper by remember { mutableStateOf(false) }
    var cropperImageUri by remember { mutableStateOf<Uri?>(null) }

    // ── 图片选择器 ──
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            cropperImageUri = uri
            showCropper = true
        }
    }

    // ── 相册权限请求器 ──
    var showPermissionDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            imagePickerLauncher.launch("image/*")
        } else {
            showPermissionDialog = true
        }
    }

    // ── 统一启动头像更换流程 ──
    val startAvatarPickup: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用 READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
                == PackageManager.PERMISSION_GRANTED
            ) {
                imagePickerLauncher.launch("image/*")
            } else {
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // 低版本直接打开
            imagePickerLauncher.launch("image/*")
        }
    }

    // ── 权限拒绝弹窗 ──
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要相册权限") },
            text = { Text("更换头像需要访问您的相册，请前往系统设置开启相册权限。") },
            confirmButton = {
                Button(onClick = {
                    showPermissionDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }) {
                    Text("前往设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // ── 圆形头像选取器 ──
    if (showCropper && cropperImageUri != null) {
        AvatarCropper(
            context = context,
            imageUri = cropperImageUri!!,
            studentId = state.studentId,
            onConfirmed = { path ->
                editAvatarPath = path
                avatarVersion++
                showCropper = false
                cropperImageUri = null
            },
            onCancel = {
                showCropper = false
                cropperImageUri = null
            }
        )
        return
    }

    // ── ── ── ── ── ── ── ── ── ── ── ── ── ──
    // 编辑表单页面
    // ── ── ── ── ── ── ── ── ── ── ── ── ── ──
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── 下推间距 ──
        Spacer(Modifier.height(40.dp))

        // ── 顶栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                text = "编辑资料",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── 头像区域（点击进入预览） ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AvatarDisplay(
                    avatarPath = editAvatarPath,
                    avatarStyle = editAvatarStyle,
                    version = avatarVersion,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .clickable { startAvatarPickup() }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "点击更换头像",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { startAvatarPickup() }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 表单区域 ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "基本信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                // ── 学号（只读） ──
                Text(
                    text = "学号",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.studentId,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                // ── 昵称 ──
                Text(
                    text = "昵称",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = editNickname,
                    onValueChange = { editNickname = it },
                    singleLine = true,
                    placeholder = { Text("输入昵称") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                // ── 真实姓名 ──
                Text(
                    text = "真实姓名",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = editRealName,
                    onValueChange = { editRealName = it },
                    singleLine = true,
                    placeholder = { Text("输入真实姓名") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                // ── 性别 ──
                Text(
                    text = "性别",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                GenderSelector(
                    selected = editGender,
                    onSelect = { editGender = it }
                )
                Spacer(Modifier.height(12.dp))

                // ── 所在班级 ──
                Text(
                    text = "所在班级",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = editClassName,
                    onValueChange = { editClassName = it },
                    singleLine = true,
                    placeholder = { Text("输入班级，如 计科2401") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── 保存按钮 ──
        Button(
            onClick = {
                state.nickname = editNickname
                state.realName = editRealName
                state.className = editClassName
                state.gender = editGender
                state.avatarStyle = editAvatarStyle
                state.avatarUri = editAvatarPath
                state.avatarVersion = avatarVersion
                scope.launch {
                    state.saveProfile()
                    onNavigateBack()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text("保存")
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── 性别选择器 ────────────────────────────────────────────────

@Composable
private fun GenderSelector(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        listOf(0 to "未设置", 1 to "男", 2 to "女").forEach { (value, label) ->
            val isSelected = value == selected
            OutlinedButton(
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent
                )
            ) {
                Text(
                    text = label,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ── 头像显示组件 ──────────────────────────────────────────────

@Composable
private fun AvatarDisplay(
    avatarPath: String,
    avatarStyle: Int,
    version: Int,  // 递增时强制刷新
    modifier: Modifier = Modifier
) {
    // 加载本地图片（version 变化时重新加载）
    val bitmap = remember(avatarPath, version) {
        if (avatarPath.isNotBlank()) {
            try {
                val file = File(avatarPath)
                if (file.exists()) {
                    decodeSampledBitmap(avatarPath, 400)
                } else null
            } catch (e: Exception) { null }
        } else null
    }

    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "头像",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        // 回退：彩色 Person 图标
        Surface(
            modifier = modifier,
            shape = CircleShape,
            color = avatarEditBackgrounds[avatarStyle % avatarEditBackgrounds.size]
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "头像",
                    modifier = Modifier.size(if (avatarPath.isNotBlank()) 60.dp else 48.dp),
                    tint = Color.White
                )
            }
        }
    }
}

// ── 圆形头像选取器 ──────────────────────────────────────────────

@Composable
private fun AvatarCropper(
    context: android.content.Context,
    imageUri: Uri,
    studentId: String,
    onConfirmed: (String) -> Unit,
    onCancel: () -> Unit
) {
    // 加载选取的图片为 Bitmap（最大 1024 宽高）
    val sourceBitmap = remember(imageUri) {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, opts)
            }
            val scale = maxOf(opts.outWidth, opts.outHeight) / 1024
            val sampleSize = Integer.highestOneBit(maxOf(scale, 1))
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOpts)
            }
        } catch (e: Exception) { null }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        // ── 可缩放/拖动的图片 ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        val maxT = (scale - 1f) * 500f
                        offsetX = (offsetX + pan.x).coerceIn(-maxT, maxT)
                        offsetY = (offsetY + pan.y).coerceIn(-maxT, maxT)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (sourceBitmap != null) {
                Image(
                    bitmap = sourceBitmap.asImageBitmap(),
                    contentDescription = "选取头像",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        },
                    contentScale = ContentScale.Fit
                )
            }
        }

        // ── 圆形遮罩（半透明背景 + 圆形透明区域 + 白色边框） ──
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.99f) // BlendMode.Clear 需要
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = minOf(cx, cy) - 16.dp.toPx()

            // 半透明背景
            drawRect(Color.Black.copy(alpha = 0.55f), size = size)
            // 圆形透明挖空
            drawCircle(Color.Transparent, radius, Offset(cx, cy), blendMode = BlendMode.Clear)
            // 白色边框
            drawCircle(Color.White, radius, Offset(cx, cy), style = Stroke(width = 3.dp.toPx()))
        }

        // ── 底部操作栏 ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 确认按钮
            Button(
                onClick = {
                    if (sourceBitmap != null) {
                        val viewSize = minOf(
                            sourceBitmap.width.toFloat(),
                            sourceBitmap.height.toFloat()
                        )
                        val cx = viewSize / 2f
                        val cy = viewSize / 2f
                        val radius = viewSize / 2f

                        val imgCx = (cx - offsetX) / scale
                        val imgCy = (cy - offsetY) / scale
                        val imgR = radius / scale

                        val left = (imgCx - imgR).toInt().coerceAtLeast(0)
                        val top = (imgCy - imgR).toInt().coerceAtLeast(0)
                        val side = (imgR * 2).toInt()
                            .coerceAtMost(sourceBitmap.width - left)
                            .coerceAtMost(sourceBitmap.height - top)

                        if (side > 0) {
                            val cropped = Bitmap.createBitmap(sourceBitmap, left, top, side, side)
                            val destFile = File(context.filesDir, "avatars/avatar_${studentId}.jpg")
                            destFile.parentFile?.mkdirs()
                            cropped.compress(Bitmap.CompressFormat.JPEG, 90, destFile.outputStream())
                            onConfirmed(destFile.absolutePath)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("确认头像")
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onCancel) {
                Text("取消", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

// ── 辅助函数 ──────────────────────────────────────────────────

/** 将外部 URI 的图片复制到应用内部 avatars/ 目录，返回绝对路径。 */
private fun copyImageToInternal(
    context: android.content.Context,
    sourceUri: Uri,
    studentId: String
): String {
    val dir = File(context.filesDir, "avatars")
    dir.mkdirs()
    val destFile = File(dir, "avatar_${studentId}.jpg")
    context.contentResolver.openInputStream(sourceUri)?.use { input ->
        destFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    return destFile.absolutePath
}

/** 按最大尺寸解码本地图片，避免 OOM。 */
private fun decodeSampledBitmap(path: String, maxSize: Int): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        val scale = maxOf(options.outWidth, options.outHeight) / maxSize
        val sampleSize = Integer.highestOneBit(maxOf(scale, 1))
        BitmapFactory.Options().apply { inSampleSize = sampleSize }
            .let { opts -> BitmapFactory.decodeFile(path, opts) }
    } catch (e: Exception) { null }
}

/** 编辑页用头像背景色。 */
private val avatarEditBackgrounds = listOf(
    Color(0xFFE53935), Color(0xFFEC407A), Color(0xFFAB47BC),
    Color(0xFF42A5F5), Color(0xFF26C6DA), Color(0xFF66BB6A),
    Color(0xFFFFA726), Color(0xFF8D6E63),
)
