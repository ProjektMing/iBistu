# Service 组件规范

## 概述

Service 是 Android 四大组件之一，用于执行长时间后台操作。本项目提供两种 Service 基类模板：

| 类型 | 基类 | 适用场景 |
|------|------|---------|
| 后台 Service | `BaseWorkerService` | 不需要通知的短时后台任务 |
| 前台 Service | `BaseForegroundService` | 需要持久运行的任务（下载、同步等） |

## 后台 Service（BaseWorkerService）

适用于不需要用户感知的短时后台任务。

### 创建步骤

```kotlin
package edu.bistu.cs4029.ibistu.schedule

import edu.bistu.cs4029.ibistu.common.service.BaseWorkerService
import kotlinx.coroutines.launch

class ScheduleSyncService : BaseWorkerService() {

    override fun onWork() {
        scope.launch {
            // 后台同步任务
            // scope 在 onDestroy 时自动取消
        }
    }
}
```

### Manifest 注册

```xml
<service
    android:name=".schedule.ScheduleSyncService"
    android:exported="false" />
```

### 启动方式

```kotlin
val intent = Intent(this, ScheduleSyncService::class.java)
startService(intent)          // 启动（API 26+ 推荐 startForegroundService）

// 停止
stopService(intent)
// 或 Service 内部调用 stopSelf()
```

## 前台 Service（BaseForegroundService）

适用于需要持久运行且用户可见的任务。

### 创建步骤

```kotlin
package edu.bistu.cs4029.ibistu.common.service

class DownloadService : BaseForegroundService() {

    override fun getChannelId() = "download_channel"
    override fun getChannelName() = "下载服务"
    override fun getNotificationId() = 1001

    override fun onWork() {
        scope.launch {
            // 前台下载任务
        }
    }
}
```

### Manifest 注册

```xml
<service
    android:name=".common.service.DownloadService"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```

> **注意**: Android 14+ 前台 Service 必须声明 `android:foregroundServiceType` 属性。

### 启动方式（API 26+）

```kotlin
import android.content.Context
import androidx.core.content.ContextCompat

ContextCompat.startForegroundService(this, Intent(this, DownloadService::class.java))
```

### 通知渠道

前台 Service **必须**显示一个通知。通过 `getChannelId()` 自定义渠道：

```kotlin
override fun getChannelId() = "my_channel"
override fun getChannelName() = "我的服务"
```

## 绑定 Service

需要与 Activity/Fragment 交互时使用绑定模式：

```kotlin
class BoundService : Service() {
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): BoundService = this@BoundService
    }

    override fun onBind(intent: Intent?): IBinder = binder
}

// Activity 中绑定
val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val boundService = (service as BoundService.LocalBinder).getService()
    }
    override fun onServiceDisconnected(name: ComponentName?) {}
}

bindService(Intent(this, BoundService::class.java), connection, Context.BIND_AUTO_CREATE)
```

## 协程生命周期管理

两个基类都使用以下方式管理协程：

```kotlin
protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// 自动在 onDestroy 时清理
override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
}
```

> 使用 `SupervisorJob` 确保单个协程失败不会影响其他协程。

## Manifest 注册规范

```xml
<!-- 后台 Service -->
<service
    android:name=".module.ClassNameService"
    android:exported="false" />

<!-- 前台 Service（Android 14+） -->
<service
    android:name=".module.ClassNameService"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```

## 注意事项（Android 8+/14+）

1. **后台执行限制** — Android 8+ 限制了后台 Service 的运行时间，长时间任务应使用前台 Service
2. **前台 Service 类型** — Android 14+ 要求声明 `foregroundServiceType`（如 `dataSync`、`location`、`camera` 等）
3. **通知渠道** — 前台 Service 必须在 Android 8+ 上创建通知渠道
4. **权限** — 前台 Service 可能需要 `FOREGROUND_SERVICE_XXX` 权限
5. **协程作用域** — 务必在 `onDestroy()` 中取消协程，避免内存泄漏
