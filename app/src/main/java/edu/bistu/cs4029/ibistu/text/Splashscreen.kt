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
import edu.bistu.cs4029.ibistu.common.ui.theme.IBistuTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    // 直接使用配置数据，无需 Context
    val texts = remember { SplashConfig.splashTexts }
    val randomIndex = remember { texts.indices.random() }
    val displayText = texts[randomIndex]

    IBistuTheme {
        LaunchedEffect(Unit) {
            delay(1500.milliseconds)
            onTimeout()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}