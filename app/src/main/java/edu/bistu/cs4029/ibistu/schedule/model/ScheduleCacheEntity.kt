package edu.bistu.cs4029.ibistu.schedule.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 课表缓存实体。
 * 保存从教务系统获取的完整课表快照，用于启动时优先显示。
 *
 * 缓存策略：
 * - [jsonHash]：原始 JSON 的 xxHash32 值（8 位十六进制字符串）
 * - 下次获取时静默比对哈希，相同则不更新
 * - 只有一个学期课表，因此表中只有一条记录
 */
@Entity(tableName = "schedule_cache")
data class ScheduleCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 0,

    /** 学期名称，如 "2025-2026-2" */
    @ColumnInfo(name = "term_name")
    val termName: String,

    /** 学期代码，如 "2025-2026-2-2" */
    @ColumnInfo(name = "term_code")
    val termCode: String,

    /** 原始 JSON 的 xxHash32 值（8 位十六进制字符串） */
    @ColumnInfo(name = "json_hash")
    val jsonHash: String,

    /** 课表 JSON 原始字符串（教务系统 API 返回的 schedule JSON） */
    @ColumnInfo(name = "courses_json")
    val coursesJson: String,

    /** 教学周 JSON（TermWeek 列表的 JSON 数组） */
    @ColumnInfo(name = "term_weeks_json")
    val termWeeksJson: String,

    /** 缓存写入时间戳（毫秒） */
    @ColumnInfo(name = "cached_at")
    val cachedAt: Long = System.currentTimeMillis(),

    /** 学期最后教学周（如 20），用于计算周范围 */
    @ColumnInfo(name = "week_range_end")
    val weekRangeEnd: Int = 20,
)
