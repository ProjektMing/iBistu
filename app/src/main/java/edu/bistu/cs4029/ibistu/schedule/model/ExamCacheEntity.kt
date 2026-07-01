package edu.bistu.cs4029.ibistu.schedule.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 考试缓存实体。
 * 保存从教务系统获取的考试安排快照，用于启动时优先显示。
 *
 * 缓存策略：
 * - [jsonHash]：原始 JSON 的 xxHash32 值（8 位十六进制字符串）
 * - 下次获取时静默比对哈希，相同则不更新
 * - 只有一个学期考试数据，因此表中只有一条记录
 */
@Entity(tableName = "exam_cache")
data class ExamCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 0,

    /** 学期代码，如 "2025-2026-2-2" */
    @ColumnInfo(name = "term_code")
    val termCode: String,

    /** 原始 JSON 的 xxHash32 值（8 位十六进制字符串） */
    @ColumnInfo(name = "json_hash")
    val jsonHash: String,

    /** 考试数据 JSON（Exam 列表的 JSON 数组） */
    @ColumnInfo(name = "exams_json")
    val examsJson: String,

    /** 缓存写入时间戳（毫秒） */
    @ColumnInfo(name = "cached_at")
    val cachedAt: Long = System.currentTimeMillis(),
)
