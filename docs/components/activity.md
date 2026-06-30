# Activity 组件规范

## 概述

Activity 是 Android 四大组件之一，负责管理用户界面。本项目统一使用 `BaseActivity` 基类 + Jetpack Compose。

## 基类 BaseActivity

所有 Activity **必须**继承 `BaseActivity` 而非直接继承 `ComponentActivity`。

```kotlin
package edu.bistu.cs4029.ibistu.common.base

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable

/**
 * 项目 Activity 基类。
 *
 * 统一处理 Edge-to-Edge、Compose Content 设置。
 * 所有 Activity 应继承此类而非直接继承 ComponentActivity。
 */
abstract class BaseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Content()
        }
    }

    /**
     * 子类实现此方法提供 Composable 内容。
     * 不需要再调用 setContent 或 enableEdgeToEdge。
     */
    @Composable
    protected abstract fun Content()
}
```

## 创建新 Activity 的步骤

### 1. 在对应模块包下创建 Activity

```kotlin
package edu.bistu.cs4029.ibistu.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import edu.bistu.cs4029.ibistu.common.base.BaseActivity

class ScheduleActivity : BaseActivity() {

    @Composable
    override fun Content() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("课表页面")
        }
    }
}
```

### 2. 注册到 AndroidManifest.xml

```xml
<activity
    android:name=".schedule.ScheduleActivity"
    android:exported="false"
    android:theme="@style/Theme.IBistu"
    android:windowSoftInputMode="adjustResize" />
```

| 属性 | 说明 | 推荐值 |
|------|------|--------|
| `android:name` | Activity 完整类名（相对于 manifest package） | `.module.ClassNameActivity` |
| `android:exported` | 是否暴露给其他应用 | 入口 Activity 为 `true`，其余为 `false` |
| `android:theme` | 主题 | `@style/Theme.IBistu` |
| `android:windowSoftInputMode` | 软键盘行为 | `adjustResize` |

## Navigation Compose 导航

使用 Jetpack Navigation Compose 管理页面导航。

### 路由定义

在 `common/navigation/AppNavigation.kt` 中定义路由常量：

```kotlin
package edu.bistu.cs4029.ibistu.common.navigation

object AppRoutes {
    const val HOME = "home"
    const val SCHEDULE = "schedule"
    const val LOGIN = "login"
    const val SETTINGS = "settings"
}
```

### NavHost 设置

```kotlin
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = AppRoutes.HOME) {
        composable(AppRoutes.HOME) {
            // HomeScreen()
        }
        composable(AppRoutes.SCHEDULE) {
            // ScheduleScreen()
        }
    }
}
```

## 生命周期管理

在 Compose 中使用以下方式替代传统的生命周期回调：

| 传统方法 | Compose 替代 |
|---------|-------------|
| `onStart()` | `LaunchedEffect(Unit) { ... }` |
| `onResume()` | `DisposableEffect(Unit) { onDispose { ... } }` |
| `onPause()` | 通过 `LifecycleEventEffect` |
| `onStop()` | 通过 `LifecycleEventEffect` |
| `onDestroy()` | `DisposableEffect` 的 `onDispose` |

## 最佳实践

1. **Activity 保持轻薄** — 仅做生命周期管理和导航，业务逻辑放在 ViewModel 中
2. **命名规范** — Activity 类名以功能开头 + Activity 结尾（如 `LoginActivity`、`ScheduleActivity`）
3. **Intent Extra** — 使用 `Intent.getXxxExtra()` 获取参数，提前定义常量 KEY
4. **入口 Activity** — 唯一一个 `exported="true"` 的 Activity，其他均为 `false`
5. **主题统一** — 所有 Activity 使用 `@style/Theme.IBistu`
