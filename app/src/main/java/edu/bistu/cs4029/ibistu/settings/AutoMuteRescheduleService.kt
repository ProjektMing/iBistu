package edu.bistu.cs4029.ibistu.settings

import edu.bistu.cs4029.ibistu.common.service.BaseWorkerService

/**
 * 自动静音闹钟重调度服务。
 *
 * 用于开机后的短时后台恢复任务；完成重调度后立即停止，避免长期占用后台资源。
 */
class AutoMuteRescheduleService : BaseWorkerService() {

    override fun onWork() {
        try {
            AutoMuteScheduler.reschedule(this)
        } finally {
            stopSelf()
        }
    }

    override fun getStartMode(): Int = START_NOT_STICKY
}
