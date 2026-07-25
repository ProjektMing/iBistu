package edu.bistu.cs4029.ibistu.common.navigation

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import edu.bistu.cs4029.ibistu.R
import edu.bistu.cs4029.ibistu.common.state.AppState
import edu.bistu.cs4029.ibistu.food.EatWhatPage
import edu.bistu.cs4029.ibistu.navigate.NavigationPage
import edu.bistu.cs4029.ibistu.profile.ProfilePage
import edu.bistu.cs4029.ibistu.schedule.HomePage
import edu.bistu.cs4029.ibistu.schedule.ExamPage
import edu.bistu.cs4029.ibistu.settings.SettingsPage
import edu.bistu.cs4029.ibistu.text.SplashScreen
import edu.bistu.cs4029.ibistu.today.TodayCampusPage
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
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.TODAY) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestination.entries.filter { it.showInNavigation }.forEach { destination ->
                item(
                    icon = {
                        destination.icon?.let { icon ->
                            Icon(imageVector = icon, contentDescription = destination.label)
                        } ?: Icon(
                            painter = painterResource(destination.iconRes!!),
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
            AppDestination.TODAY -> TodayCampusPage(
                state = state,
                onOpenSchedule = {
                    state.showExamPage = false
                    currentDestination = AppDestination.SCHEDULE
                },
                onOpenExams = {
                    state.showExamPage = true
                    currentDestination = AppDestination.SCHEDULE
                },
                onOpenFood = { currentDestination = AppDestination.FOOD }
            )
            AppDestination.SCHEDULE -> {
                if (state.showExamPage) ExamPage(state)
                else HomePage(state)
            }
            AppDestination.NAVIGATION -> NavigationPage(state)
            AppDestination.FOOD -> EatWhatPage(
                showThursdayReminder = state.showCrazyThursdayReminder
            )
            AppDestination.SETTINGS -> SettingsPage(state)
            AppDestination.PROFILE -> ProfilePage(state, scope)
        }
    }
}

/**
 * 缓存优先的会话恢复流程：
 * 1. 恢复 Cookie
 * 2. 从 Room 加载缓存 → 立刻显示课表和考试
 * 3. 后台静默网络请求 → xxHash32 比对 → 有变更则刷新 UI
 */
private suspend fun restoreSession(state: AppState) {
    try {
        state.login.restoreCookies()
        val allCookies = state.login.getAllCookies()
        if (allCookies.isEmpty()) {
            Log.i(TAG, "═══ RESTORE: no cookies ═══")
            state.isLoggedIn = false
            state.isRestoring = false
            return
        }

        // STEP1: 验证 TGC
        val tgcValid = runCatching { state.login.verifySession() }.getOrDefault(false)
        Log.i(TAG, "═══ RESTORE: cookies=${allCookies.size} tgcValid=$tgcValid ═══")
        if (!tgcValid) {
            state.login.clearAllCookies()
            state.isLoggedIn = false
            Log.w(TAG, "⚠️ TGC 已失效，已清除 cookie")
            state.isRestoring = false
            return
        }
        state.isLoggedIn = true

        // STEP2: 建立各系统 session（网络失败不阻止继续）
        val endpoints = edu.bistu.cs4029.ibistu.login.BistuLogin.casEndpoints
        for (ep in endpoints) {
            runCatching { state.login.casLogin(ep) }.onFailure {
                Log.w(TAG, "⚠️ casLogin ${ep.name} 失败（网络可能不通）: ${it.message}")
            }
        }

        val cached = state.scheduleRepo.loadCached()
        if (cached != null) {
                // 有缓存：立刻显示，后台静默刷新
                state.applySchedule(cached)
                state.isRestoring = false
                Log.i(TAG, "✅ 缓存命中：${cached.courses.size} 门课立刻显示 | hash=${cached.termName}")
                
                // 加载缓存的考试数据（如果有的话）
                val cachedExams = state.examRepo.loadCached()
                if (cachedExams != null) {
                    state.exams = cachedExams
                    Log.i(TAG, "✅ 缓存考试：${cachedExams.size} 场考试立刻显示")
                }
                
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val fresh = state.scheduleRepo.fetchAndCache(state.login, cached.termCode)
                        withContext(Dispatchers.Main) {
                            state.applySchedule(fresh)
                        }
                        if (fresh.courses.size != cached.courses.size) {
                            Log.i(TAG, "🔄 后台刷新：课程数变化 ${cached.courses.size}→${fresh.courses.size}")
                        } else {
                            Log.i(TAG, "✅ 后台刷新：数据已更新（课程数未变）")
                        }
                        
                        // 后台刷新考试数据
                        val freshExams = state.examRepo.fetchAndCache(state.login, fresh.termCode)
                        withContext(Dispatchers.Main) {
                            state.exams = freshExams
                        }
                        if (freshExams.size != (cachedExams?.size ?: 0)) {
                            Log.i(TAG, "🔄 后台刷新：考试数变化 ${cachedExams?.size ?: 0}→${freshExams.size}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ 后台刷新失败（网络可能不通）: ${e.message}")
                    }
                }
            } else {
                // 无缓存：等待网络请求
                Log.i(TAG, "⏳ 无缓存，开始网络请求...")
                try {
                    val fresh = state.scheduleRepo.fetchAndCache(state.login)
                    state.applySchedule(fresh)
                    Log.i(TAG, "✅ 网络获取成功：${fresh.courses.size} 门课已缓存")
                    
                    // 加载考试数据
                    val freshExams = state.examRepo.fetchAndCache(state.login, fresh.termCode)
                    state.exams = freshExams
                    Log.i(TAG, "✅ 网络获取考试成功：${freshExams.size} 场考试已缓存")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 无缓存且网络获取失败: ${e.message}")
                }
            }
    } catch (exception: Exception) {
        Log.w(TAG, "❌ 会话恢复失败（保留 Cookie 以便重试）", exception)
        // 仅在认证相关异常时清除 Cookie，避免网络/解析错误导致误退出
        if (exception is edu.bistu.cs4029.ibistu.login.AuthException) {
            state.login.clearAllCookies()
            state.isLoggedIn = false
        }
    } finally {
        state.isRestoring = false
    }
}

private enum class AppDestination(
    val label: String,
    val icon: ImageVector? = null,
    val iconRes: Int? = null,
    val showInNavigation: Boolean = true
) {
    TODAY("今日", Icons.Filled.Today),
    SCHEDULE("课表", Icons.Filled.CalendarMonth),
    NAVIGATION("导航", Icons.Filled.Place),
    FOOD("吃啥", iconRes = R.drawable.ic_chicken_leg, showInNavigation = false),
    SETTINGS("设置", Icons.Filled.Settings),
    PROFILE("登录", Icons.Filled.Person)
}
