package edu.bistu.cs4029.ibistu.focus

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import edu.bistu.cs4029.ibistu.focus.model.FocusSession
import edu.bistu.cs4029.ibistu.focus.model.FocusTask

/** 专注会话 & 待办任务 DAO。 */
@Dao
interface FocusDao {

    // ── 待办任务 CRUD ──

    /** 插入一条待办任务，返回自增 id。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: FocusTask): Long

    /** 获取所有待办任务，按 sort_order 升序排列。 */
    @Query("SELECT * FROM focus_tasks ORDER BY sort_order ASC, created_at DESC")
    suspend fun getAllTasks(): List<FocusTask>

    /** 按 id 获取单个待办任务。 */
    @Query("SELECT * FROM focus_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Int): FocusTask?

    /** 更新待办任务的计时模式。 */
    @Query("UPDATE focus_tasks SET mode = :mode WHERE id = :taskId")
    suspend fun updateTaskMode(taskId: Int, mode: String)

    /** 更新待办任务的目标时长。 */
    @Query("UPDATE focus_tasks SET target_seconds = :seconds WHERE id = :taskId")
    suspend fun updateTaskTarget(taskId: Int, seconds: Int)

    /** 更新待办任务名称。 */
    @Query("UPDATE focus_tasks SET name = :name WHERE id = :taskId")
    suspend fun updateTaskName(taskId: Int, name: String)

    /** 删除待办任务。不级联删除关联会话（taskId 变为孤儿）。 */
    @Query("DELETE FROM focus_tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: Int)

    // ── 专注会话 CRUD ──

    /** 插入一条专注会话记录。返回新记录的自增 id。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: FocusSession): Long

    /** 获取所有会话记录，按开始时间降序排列。 */
    @Query("SELECT * FROM focus_sessions ORDER BY start_time DESC")
    suspend fun getAllSessions(): List<FocusSession>

    /** 按日期范围查询会话记录，按开始时间降序排列。 */
    @Query("SELECT * FROM focus_sessions WHERE start_time BETWEEN :from AND :to ORDER BY start_time DESC")
    suspend fun getSessionsInRange(from: Long, to: Long): List<FocusSession>

    /** 统计日期范围内的专注总次数。 */
    @Query("SELECT COUNT(*) FROM focus_sessions WHERE start_time BETWEEN :from AND :to")
    suspend fun getSessionCountInRange(from: Long, to: Long): Int

    /** 统计日期范围内的专注总时长（秒）。 */
    @Query("SELECT COALESCE(SUM(duration_seconds), 0) FROM focus_sessions WHERE start_time BETWEEN :from AND :to")
    suspend fun getTotalDurationInRange(from: Long, to: Long): Long

    /** 按小时统计指定日期范围内的专注次数（时段分布）。 */
    @Query("""
        SELECT CAST(((start_time / 3600000) % 24) AS INTEGER) AS hour,
               COUNT(*) AS count
        FROM focus_sessions
        WHERE start_time BETWEEN :from AND :to
        GROUP BY hour
        ORDER BY hour
    """)
    suspend fun getHourlyDistribution(from: Long, to: Long): List<HourlyDistribution>

    /** 按天统计指定日期范围内的专注总时长（秒）。 */
    @Query("""
        SELECT CAST(start_time / 86400000 AS INTEGER) AS day_key,
               SUM(duration_seconds) AS total_seconds
        FROM focus_sessions
        WHERE start_time BETWEEN :from AND :to
        GROUP BY day_key
        ORDER BY day_key
    """)
    suspend fun getDailyDuration(from: Long, to: Long): List<DailyDuration>

    /** 获取所有有记录的日期（去重），按天降序排列。用于计算连续打卡天数。 */
    @Query("""
        SELECT DISTINCT CAST(start_time / 86400000 AS INTEGER) AS day_key
        FROM focus_sessions
        ORDER BY day_key DESC
    """)
    suspend fun getAllActiveDays(): List<Long>

    // ── 按待办任务查询 ──

    /** 按任务 id 和日期范围查询专注会话。 */
    @Query("SELECT * FROM focus_sessions WHERE task_id = :taskId AND start_time BETWEEN :from AND :to ORDER BY start_time DESC")
    suspend fun getSessionsByTask(taskId: Int, from: Long, to: Long): List<FocusSession>

    /** 统计某任务的专注总时长（秒）。 */
    @Query("SELECT COALESCE(SUM(duration_seconds), 0) FROM focus_sessions WHERE task_id = :taskId AND start_time BETWEEN :from AND :to")
    suspend fun getTotalDurationByTask(taskId: Int, from: Long, to: Long): Long

    /** 所有待办任务的时长汇总（按日期范围）。 */
    @Query("""
        SELECT fs.task_id AS task_id,
               COALESCE(ft.name, '未分类') AS task_name,
               COALESCE(SUM(fs.duration_seconds), 0) AS total_seconds
        FROM focus_sessions fs
        LEFT JOIN focus_tasks ft ON fs.task_id = ft.id
        WHERE fs.start_time BETWEEN :from AND :to
        GROUP BY fs.task_id
        ORDER BY total_seconds DESC
    """)
    suspend fun getTaskDurationBreakdown(from: Long, to: Long): List<TaskDuration>
}

/** 时段分布结果。 */
data class HourlyDistribution(
    val hour: Int,
    val count: Int
)

/** 每日专注时长结果。 */
data class DailyDuration(
    val day_key: Long,
    val total_seconds: Long
)

/** 待办任务专注时长汇总结果。 */
data class TaskDuration(
    val task_id: Int,
    val task_name: String,
    val total_seconds: Long
)
