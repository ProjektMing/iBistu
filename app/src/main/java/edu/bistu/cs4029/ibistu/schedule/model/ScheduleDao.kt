package edu.bistu.cs4029.ibistu.schedule.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** 课表缓存 DAO。 */
@Dao
interface ScheduleDao {

    /** 加载缓存的课表（唯一记录）。 */
    @Query("SELECT * FROM schedule_cache WHERE id = 0")
    suspend fun load(): ScheduleCacheEntity?

    /** 写入/替换课表缓存（使用 REPLACE 策略）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(cache: ScheduleCacheEntity)

    /** 删除缓存。 */
    @Query("DELETE FROM schedule_cache")
    suspend fun clear()
}
