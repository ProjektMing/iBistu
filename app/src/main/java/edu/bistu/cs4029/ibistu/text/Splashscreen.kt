package edu.bistu.cs4029.ibistu.text

import android.database.Cursor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.bistu.cs4029.ibistu.common.ui.theme.IBistuTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** 淡出动画时长（毫秒）。 */
private const val FADE_OUT_DURATION = 400

/**
 * 语录屏 —— 冷启动后的第一屏。
 *
 * 黑白极简：只有句子和作者，无装饰线、无动画、无色块。
 * 位置偏上约 38% 处，利用留白制造呼吸感。
 * 结束后 400ms 淡出，再进入主界面。
 * 深色模式下自动反色。
 */
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val context = LocalContext.current
    var displayText: String by remember { mutableStateOf("") }
    var displayAuthor: String by remember { mutableStateOf("") }
    var fadingOut by remember { mutableStateOf(false) }

    // 淡出动画：1 → 0
    val alpha by animateFloatAsState(
        targetValue = if (fadingOut) 0f else 1f,
        animationSpec = tween(FADE_OUT_DURATION),
        label = "greeting-fade-out"
    )

    LaunchedEffect(Unit) {
        loadSplashItems(context).randomOrNull()?.let { (content, author) ->
            displayText = content
            displayAuthor = author
        }
        delay(1800.milliseconds)
        fadingOut = true
        delay(FADE_OUT_DURATION.toLong().milliseconds)
        onTimeout()
    }

    IBistuTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .alpha(alpha)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 36.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Top
            ) {
                // 上方留白：内容约在屏幕 38% 高度处，不是正中心
                Spacer(modifier = Modifier.weight(0.62f))

                // 句子
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                // 作者（与句子之间有明显的纵向呼吸）
                if (displayAuthor.isNotBlank()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = displayAuthor,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                        textAlign = TextAlign.Start
                    )
                }

                // 下方留白
                Spacer(modifier = Modifier.weight(0.38f))
            }
        }
    }
}

/** 优先查询 ContentProvider，不可用时直接读取 XML 配置。 */
private fun loadSplashItems(context: android.content.Context): List<Pair<String, String>> {
    val cursor: Cursor? = try {
        context.contentResolver.query(
            SplashProvider.CONTENT_URI,
            arrayOf(SplashProvider.COL_CONTENT, SplashProvider.COL_AUTHOR),
            null,
            null,
            null
        )
    } catch (_: Exception) {
        null
    }

    return cursor?.use { result ->
        val contentIndex = result.getColumnIndex(SplashProvider.COL_CONTENT)
        val authorIndex = result.getColumnIndex(SplashProvider.COL_AUTHOR)
        buildList {
            while (result.moveToNext()) {
                val content = if (contentIndex >= 0) result.getString(contentIndex) else ""
                val author = if (authorIndex >= 0) result.getString(authorIndex) else ""
                add(content to author)
            }
        }
    } ?: SplashConfig.load(context)
}
