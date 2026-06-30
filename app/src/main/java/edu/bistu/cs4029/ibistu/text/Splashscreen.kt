package edu.bistu.cs4029.ibistu.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import edu.bistu.cs4029.ibistu.ui.theme.IBistuTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val text = listOf(
        "孩子们 我回来了————科比布莱恩特",
        "你跑不过我你信吗————张雪峰"
    )

    val randomIndex = remember { (text.indices).random() }
    val displayText = text[randomIndex]
    // 使用应用主题，保持一致
    IBistuTheme {
        // 1秒后自动跳转
        LaunchedEffect(Unit) {
            delay(1500.milliseconds)
            onTimeout()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background), // 使用主题背景色
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground // 使用主题文字颜色
            )
        }
    }
}