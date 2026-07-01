package edu.bistu.cs4029.ibistu.common.navigation

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import edu.bistu.cs4029.ibistu.common.state.AppState
import edu.bistu.cs4029.ibistu.profile.ProfilePage
import edu.bistu.cs4029.ibistu.schedule.HomePage
import edu.bistu.cs4029.ibistu.schedule.ExamPage
import edu.bistu.cs4029.ibistu.settings.SettingsPage
import edu.bistu.cs4029.ibistu.text.SplashScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AppNavigation"

/** 应用根节点：闪屏淡出时内容同步淡入（交叉淡化）。闪屏期间后台恢复会话。 */
@Composable
fun IBistuRoot() {
    val context = LocalContext.current
    var contentVisible by remember { mutableStateOf(false) }
    val state = remember { AppState(context) }
    var showSplash by rememberSaveable { mutableStateOf(state.showSplashGreeting) }


    LaunchedEffect(state) {
        restoreSession(state)
        // 如果禁用了闪屏，立即显示内容
        if (!state.showSplashGreeting) {
            contentVisible = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 底层：内容在闪屏开始淡出时交叉淡入（或直接显示）
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(
                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
            ) + slideInVertically(
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 20 }
            )
        ) {
                if (state.isRestoring) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "加载中...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    IBistuApp(state = state)
                }
            }

        // 顶层：透明背景闪屏（只在启用时显示）
        if (showSplash && state.showSplashGreeting) {
            SplashScreen(
                onFadeStart = { contentVisible = true },
                onTimeout = { showSplash = false }
            )
        }
    }
}

/** 主界面导航容器。 */
@Composable
fun IBistuApp(state: AppState) {
    val scope = rememberCoroutineScope()
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestination.entries.forEach { destination ->
                item(
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination }
                )
            }
        }
    ) {
        when (currentDestination) {
            AppDestination.HOME -> {
                if (state.showExamPage) ExamPage(state)
                else HomePage(state)
            }
            AppDestination.SETTINGS -> SettingsPage(state)
            AppDestination.PROFILE -> ProfilePage(state, scope)
        }
    }
}

/**
 * 缓存优先的会话恢复流程：
 * 1. 恢复 Cookie
 * 2. 从 Room 加载缓存 → 立刻显示课表
 * 3. 后台静默网络请求 → xxHash32 比对 → 有变更则刷新 UI
 */
private suspend fun restoreSession(state: AppState) {
    try {
        state.login.restoreCookies()
        val cookieCount = state.login.getAllCookies().size
        Log.i(TAG, "═══ RESTORE START: cookies=$cookieCount ═══")
        if (cookieCount > 0) {
            val cached = state.scheduleRepo.loadCached()
            if (cached != null) {
                // 有缓存：立刻显示，后台静默刷新
                state.applySchedule(cached)
                state.isRestoring = false
                Log.i(TAG, "✅ 缓存命中：${cached.courses.size} 门课立刻显示 | hash=${cached.termName}")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val fresh = state.scheduleRepo.fetchAndCache(state.login)
                        if (fresh.courses.size != cached.courses.size) {
                            withContext(Dispatchers.Main) {
                                state.applySchedule(fresh)
                            }
                            Log.i(TAG, "🔄 后台刷新：课程数变化 ${cached.courses.size}→${fresh.courses.size}")
                        } else {
                            Log.i(TAG, "✅ 后台刷新：hash 未变，跳过更新")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ 后台刷新失败（网络可能不通）: ${e.message}")
                    }
                }
            } else {
                // 无缓存：等待网络请求
                Log.i(TAG, "⏳ 无缓存，开始网络请求...")
                val fresh = state.scheduleRepo.fetchAndCache(state.login)
                state.applySchedule(fresh)
                Log.i(TAG, "✅ 网络获取成功：${fresh.courses.size} 门课已缓存")
            }
        } else {
            Log.i(TAG, "⚠️ 无 Cookie，跳过会话恢复（需先登录）")
        }
    } catch (exception: Exception) {
        Log.w(TAG, "❌ 会话恢复失败", exception)
        state.login.clearAllCookies()
    } finally {
        state.isRestoring = false
    }
}

private enum class AppDestination(
    val label: String,
    val icon: ImageVector
) {
    HOME("课表", Icons.Filled.Home),
    SETTINGS("设置", Icons.Filled.Settings),
    PROFILE("登录", Icons.Filled.Person)
}
