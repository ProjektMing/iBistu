package edu.bistu.cs4029.ibistu.focus.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 专注会话实体。
 * 记录每次专注练习的完整数据，用于数据统计和回顾。
 */
@Entity(tableName = "focus_sessions")
data class FocusSession(
    /** 自增主键 */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    /** 会话开始时间戳（epoch millis） */
    @ColumnInfo(name = "start_time")
    val startTime: Long,

    /** 会话结束时间戳（epoch millis） */
    @ColumnInfo(name = "end_time")
    val endTime: Long,

    /** 实际专注时长（秒） */
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int,

    /** 目标时长（秒）：倒计时模式的目标时长；正向计时与此相同或为 0 */
    @ColumnInfo(name = "target_duration_seconds")
    val targetDurationSeconds: Int = 0,

    /** 计时模式："countdown"（倒计时）或 "stopwatch"（正向计时） */
    @ColumnInfo(name = "mode")
    val mode: String = "countdown",

    /** 可选备注标签，如 "复习高数" */
    @ColumnInfo(name = "label")
    val label: String = "",

    /** 记录创建时间戳（epoch millis） */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** 中断类型：""（无中断）、"微信消息"、"电话"、"走神"、"他人打扰" 等 */
    @ColumnInfo(name = "interruption_type")
    val interruptionType: String = "",

    /** 关联的待办任务 ID，0 表示未分类 */
    @ColumnInfo(name = "task_id")
    val taskId: Int = 0,
)
