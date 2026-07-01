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
open class TemplateReceiver : BroadcastReceiver() {

    companion object {
        private const val EXTRA_AIRPLANE_MODE_STATE = "state"

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
            Actions.BOOT_COMPLETED -> onBootCompleted(context)
            Actions.AIRPLANE_MODE -> onAirplaneModeChanged(
                context,
                intent.getBooleanExtra(EXTRA_AIRPLANE_MODE_STATE, false)
            )
            Actions.CONNECTIVITY -> onConnectivityChanged(context)
            Actions.BATTERY_LOW -> onBatteryLow(context)
            Actions.PACKAGE_ADDED -> onPackageAdded(context, intent.data?.schemeSpecificPart)
            Actions.PACKAGE_REMOVED -> onPackageRemoved(context, intent.data?.schemeSpecificPart)
            Actions.TIME_TICK -> onTimeTick(context)
            else -> onUnhandledAction(context, intent)
        }
    }

    protected open fun onBootCompleted(context: Context) = Unit

    protected open fun onAirplaneModeChanged(context: Context, isEnabled: Boolean) = Unit

    protected open fun onConnectivityChanged(context: Context) = Unit

    protected open fun onBatteryLow(context: Context) = Unit

    protected open fun onPackageAdded(context: Context, packageName: String?) = Unit

    protected open fun onPackageRemoved(context: Context, packageName: String?) = Unit

    protected open fun onTimeTick(context: Context) = Unit

    protected open fun onUnhandledAction(context: Context, intent: Intent) = Unit
}

