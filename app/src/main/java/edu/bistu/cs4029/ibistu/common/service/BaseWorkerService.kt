package edu.bistu.cs4029.ibistu.common.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 后台 Service 基类。
 *
 * 提供协程作用域管理和 START_STICKY 重启策略。
 * 适用于不需要前台通知的后台任务。
 *
 * 使用方式：
 * ```
 * class SyncService : BaseWorkerService() {
 *     override fun onWork() {
 *         scope.launch {
 *             // 后台任务
 *         }
 *     }
 * }
 * ```
 */
abstract class BaseWorkerService : Service() {

    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Service 被系统回收后的重启策略，子类可按任务语义覆盖。 */
    protected open fun getStartMode(): Int = START_STICKY

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        onWork()
        return getStartMode()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 子类在此方法中启动后台任务。
     * 使用 `scope.launch { ... }` 执行协程。
     */
    protected open fun onWork() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
