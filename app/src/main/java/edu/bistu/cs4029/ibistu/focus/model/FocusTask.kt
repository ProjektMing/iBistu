package edu.bistu.cs4029.ibistu.focus.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 待办任务实体。
 * 每个任务在创建时选定计时模式（倒计时/正向计时），
 * 后续的专注会话都绑定到此任务。
 */
@Entity(tableName = "focus_tasks")
data class FocusTask(
    /** 自增主键 */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    /** 任务名称，如 "数学"、"复习高数" */
    @ColumnInfo(name = "name")
    val name: String,

    /** 计时模式："countdown" 或 "stopwatch" */
    @ColumnInfo(name = "mode")
    val mode: String = "countdown",

    /** 目标时长（秒），仅倒计时模式使用，默认 25 分钟 */
    @ColumnInfo(name = "target_seconds")
    val targetSeconds: Int = 1500,

    /** 排序序号，预留给后续拖拽排序 */
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    /** 创建时间戳（epoch millis） */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
