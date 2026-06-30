# BroadcastReceiver 组件规范

## 概述

BroadcastReceiver 是 Android 四大组件之一，用于接收系统或应用发出的广播事件。支持两种注册方式：

| 注册方式 | 说明 | 适用场景 |
|---------|------|---------|
| 静态注册 | 在 AndroidManifest.xml 中声明 | 需要在应用未启动时接收广播 |
| 动态注册 | 在代码中 registerReceiver() | 仅需要在 Activity/Service 活跃时接收 |

## 静态注册（AndroidManifest）

### 创建 Receiver

```kotlin
package edu.bistu.cs4029.ibistu.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScheduleUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 处理广播（主线程，不要执行超过 10 秒的操作）
        val action = intent.action
    }
}
```

### Manifest 注册

```xml
<receiver
    android:name=".schedule.ScheduleUpdateReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.example.UPDATE_SCHEDULE" />
    </intent-filter>
</receiver>
```

| 属性 | 说明 |
|------|------|
| `android:name` | Receiver 完整类名（相对于 manifest package） |
| `android:exported` | 是否允许其他应用发送广播到此 Receiver |
| `android:directBootAware` | 是否在 Direct Boot 模式下可用 |

> **Android 13+**: 静态注册的广播接收器会受到限制，大多数隐式广播不再支持静态注册。建议改用动态注册或 `registerReceiverForAllUsers()`。

## 动态注册（代码方式）

### 注册与注销

```kotlin
// 创建 Receiver 实例
val receiver = ScheduleUpdateReceiver()

// 创建 IntentFilter
val filter = IntentFilter().apply {
    addAction(Intent.ACTION_TIME_TICK)
    addAction(Intent.ACTION_BATTERY_LOW)
    addAction("com.example.UPDATE_SCHEDULE")
}

// 注册（在 onStart/onResume 中）
registerReceiver(
    receiver,
    filter,
    Context.RECEIVER_NOT_EXPORTED  // Android 13+ 必须指定
)

// 注销（在 onStop/onPause 中）
unregisterReceiver(receiver)
```

### Android 13+ 导出标志

| 标志 | 说明 | 适用场景 |
|------|------|---------|
| `RECEIVER_EXPORTED` | 允许其他应用发送广播到此 Receiver | 需要接收系统或其他应用的广播 |
| `RECEIVER_NOT_EXPORTED` | 仅接收本应用的广播 | 仅用于应用内部通信 |

```kotlin
// 接收系统广播（如时间变化）
registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)

// 仅接收本应用广播
registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
```

## 发送广播

```kotlin
import android.content.Intent

// 普通广播
val intent = Intent("com.example.UPDATE_SCHEDULE").apply {
    putExtra("data", "payload")
}
sendBroadcast(intent)

// 有序广播（可被接收器截断或修改）
sendOrderedBroadcast(intent, null)

// 仅发送给本应用
// 使用动态注册 + RECEIVER_NOT_EXPORTED 实现
```

## 常用系统广播

| Action | 说明 | 所需权限 | 静态注册 |
|--------|------|---------|:--------:|
| `ACTION_BOOT_COMPLETED` | 开机完成 | `RECEIVE_BOOT_COMPLETED` | ✅ |
| `ACTION_AIRPLANE_MODE_CHANGED` | 飞行模式变化 | — | ✅ |
| `CONNECTIVITY_CHANGE` | 网络连接变化 | `ACCESS_NETWORK_STATE` | ✅ |
| `ACTION_BATTERY_LOW` | 电量低 | — | ✅ |
| `ACTION_BATTERY_OKAY` | 电量恢复正常 | — | ✅ |
| `ACTION_TIME_TICK` | 每分钟时间变化 | — | ❌（仅动态） |
| `ACTION_PACKAGE_ADDED` | 应用安装 | — | ✅ |
| `ACTION_PACKAGE_REMOVED` | 应用卸载 | — | ✅ |
| `ACTION_SCREEN_ON` | 屏幕亮起 | — | ❌（仅动态） |
| `ACTION_SCREEN_OFF` | 屏幕关闭 | — | ❌（仅动态） |

## Manifest 注册规范

```xml
<!-- 静态注册 Receiver -->
<receiver
    android:name=".module.ClassNameReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

## 注意事项

1. **ANR 风险** — `onReceive()` 运行在主线程，**不要执行超过 10 秒的耗时操作**
2. **耗时任务** — 需要在 Receiver 中执行耗时操作时，应启动 `Service` 配合完成
3. **Android 13+ 限制** — 静态注册对隐式广播的限制加强，优先使用动态注册
4. **内存泄漏** — 动态注册的 Receiver 必须在 `onPause()`/`onStop()` 中注销
5. **本地广播** — 仅需应用内通信时，使用动态注册 + `RECEIVER_NOT_EXPORTED` 更安全
