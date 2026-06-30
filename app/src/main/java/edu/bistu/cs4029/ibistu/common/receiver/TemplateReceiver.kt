package edu.bistu.cs4029.ibistu.common.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver 参考模板。
 *
 * 提供常见的系统广播 Action 常量和接收处理示例。
 * 实际使用时根据需求创建具体 Receiver 类。
 */
class TemplateReceiver : BroadcastReceiver() {

    companion object {
        /** 常用系统广播 Action */
        object Actions {
            const val BOOT_COMPLETED = Intent.ACTION_BOOT_COMPLETED
            const val AIRPLANE_MODE = Intent.ACTION_AIRPLANE_MODE_CHANGED
            const val BATTERY_LOW = Intent.ACTION_BATTERY_LOW
            const val CONNECTIVITY = "android.net.conn.CONNECTIVITY_CHANGE"
            const val PACKAGE_ADDED = Intent.ACTION_PACKAGE_ADDED
            const val PACKAGE_REMOVED = Intent.ACTION_PACKAGE_REMOVED
            const val TIME_TICK = Intent.ACTION_TIME_TICK
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Actions.BOOT_COMPLETED -> {
                // 开机完成 — 需 RECEIVE_BOOT_COMPLETED 权限
            }
            Actions.AIRPLANE_MODE -> {
                val isEnabled = intent.getBooleanExtra("state", false)
                // 飞行模式变化
            }
            Actions.CONNECTIVITY -> {
                // 网络连接变化 — 需 ACCESS_NETWORK_STATE 权限
            }
            Actions.BATTERY_LOW -> {
                // 电量低
            }
            Actions.TIME_TICK -> {
                // 每分钟触发（仅动态注册有效）
            }
            else -> {
                // 自定义 Action 处理
                val data = intent.getStringExtra("data")
            }
        }
    }
}

/**
 * 动态注册辅助函数。
 *
 * 在 Activity 或 Service 中调用：
 * ```
 * val receiver = TemplateReceiver()
 * val filter = IntentFilter(TemplateReceiver.Actions.TIME_TICK)
 * registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
 * // ...
 * unregisterReceiver(receiver)
 * ```
 */
