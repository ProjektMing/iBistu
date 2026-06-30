package edu.bistu.cs4029.ibistu.common.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

/**
 * 应用路由定义。
 */
object AppRoutes {
    const val HOME = "home"
    const val PROFILE = "profile"
    const val FAVORITES = "favorites"
    const val SCHEDULE = "schedule"
    const val LOGIN = "login"
    const val SETTINGS = "settings"
}

/**
 * 应用导航图。
 *
 * @param navController NavHostController 实例
 * @param startDestination 起始路由，默认 HOME
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = AppRoutes.HOME
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(AppRoutes.HOME) {
            // TODO: 后续 Phase 移入 HomeScreen
        }
        composable(AppRoutes.PROFILE) {
            // TODO: 后续 Phase 移入 ProfileScreen
        }
        composable(AppRoutes.FAVORITES) {
            // TODO: 后续 Phase 移入 FavoritesScreen
        }
    }
}
