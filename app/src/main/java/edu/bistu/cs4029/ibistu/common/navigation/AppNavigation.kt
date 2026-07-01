package edu.bistu.cs4029.ibistu.common.navigation

import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import edu.bistu.cs4029.ibistu.R
import edu.bistu.cs4029.ibistu.common.state.AppState
import edu.bistu.cs4029.ibistu.favorites.FavoritesPage
import edu.bistu.cs4029.ibistu.profile.ProfilePage
import edu.bistu.cs4029.ibistu.schedule.HomePage
import edu.bistu.cs4029.ibistu.schedule.fetchSchedule
import edu.bistu.cs4029.ibistu.text.SplashScreen

private const val TAG = "AppNavigation"

/** 应用根节点：先展示语录，再进入主界面。 */
@Composable
fun IBistuRoot() {
    var showGreeting by rememberSaveable { mutableStateOf(true) }

    if (showGreeting) {
        SplashScreen(onTimeout = { showGreeting = false })
    } else {
        IBistuApp()
    }
}

/** 主界面导航容器。 */
@Composable
fun IBistuApp() {
    val context = LocalContext.current
    val state = remember { AppState(context) }
    val scope = rememberCoroutineScope()
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.HOME) }

    LaunchedEffect(state) {
        restoreSession(state)
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestination.entries.forEach { destination ->
                item(
                    icon = {
                        Icon(
                            painter = painterResource(destination.iconRes),
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
            AppDestination.HOME -> HomePage(state)
            AppDestination.FAVORITES -> FavoritesPage()
            AppDestination.PROFILE -> ProfilePage(state, scope)
        }
    }
}

private suspend fun restoreSession(state: AppState) {
    try {
        state.login.restoreCookies()
        if (state.login.getAllCookies().isNotEmpty()) {
            val schedule = fetchSchedule(state.login)
            state.applySchedule(schedule)
            Log.d(TAG, "Restored ${schedule.courses.size} courses")
        }
    } catch (exception: Exception) {
        Log.w(TAG, "Session restore failed", exception)
        state.login.clearAllCookies()
    } finally {
        state.isRestoring = false
    }
}

private enum class AppDestination(
    val label: String,
    @param:DrawableRes val iconRes: Int
) {
    HOME("课表", R.drawable.ic_home),
    FAVORITES("设置", android.R.drawable.ic_menu_preferences),
    PROFILE("登录", R.drawable.ic_account_box)
}
