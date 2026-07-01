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

/** 从配置中随机展示一条文本，并在短暂延迟后进入应用。 */
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val context = LocalContext.current
    var displayText by remember { mutableStateOf("") }
    var displayAuthor by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loadSplashItems(context).randomOrNull()?.let { (content, author) ->
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
