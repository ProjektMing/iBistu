package edu.bistu.cs4029.ibistu.schedule.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** 考试缓存 DAO。 */
@Dao
interface ExamDao {

    /** 加载缓存的考试数据（唯一记录）。 */
    @Query("SELECT * FROM exam_cache WHERE id = 0")
    suspend fun load(): ExamCacheEntity?

    /** 写入/替换考试缓存（使用 REPLACE 策略）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(cache: ExamCacheEntity)

    /** 删除缓存。 */
    @Query("DELETE FROM exam_cache")
    suspend fun clear()
}
