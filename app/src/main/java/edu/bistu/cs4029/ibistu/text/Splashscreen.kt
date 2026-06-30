package edu.bistu.cs4029.ibistu.text

import android.database.Cursor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.bistu.cs4029.ibistu.common.ui.theme.IBistuTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * 轮播启动屏。
 *
 * 优先通过 ContentProvider 查询轮播文本，若不可用则回退到 SplashConfig 直接解析 XML。
 */
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val context = LocalContext.current
    var displayText: String by remember { mutableStateOf("") }
    var displayAuthor: String by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val quotes = loadSplashItems(context)
        if (quotes.isNotEmpty()) {
            val (content, author) = quotes.random()
            displayText = content
            displayAuthor = author
        }
        delay(1500.milliseconds)
        onTimeout()
    }

    IBistuTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                if (displayAuthor.isNotBlank()) {
                    Text(
                        text = "—— $displayAuthor",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }
}

/**
 * 从 ContentProvider 加载轮播文本列表。
 *
 * 若 Provider 不可用，回退到直接解析 splash_config.xml 配置文件。
 */
private fun loadSplashItems(context: android.content.Context): List<Pair<String, String>> {
    val cursor: Cursor? = try {
        context.contentResolver.query(
            SplashProvider.CONTENT_URI,
            arrayOf(SplashProvider.COL_CONTENT, SplashProvider.COL_AUTHOR),
            null, null, null
        )
    } catch (_: Exception) {
        null
    }

    return cursor?.use { c ->
        val items = mutableListOf<Pair<String, String>>()
        val contentIdx = c.getColumnIndex(SplashProvider.COL_CONTENT)
        val authorIdx = c.getColumnIndex(SplashProvider.COL_AUTHOR)
        while (c.moveToNext()) {
            val content = if (contentIdx >= 0) c.getString(contentIdx) else ""
            val author = if (authorIdx >= 0) c.getString(authorIdx) else ""
            items.add(content to author)
        }
        items
    } ?: SplashConfig.load(context)
}
