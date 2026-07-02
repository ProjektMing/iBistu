package edu.bistu.cs4029.ibistu.login

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CookieDao {

    /** 获取所有持久化的 Cookie */
    @Query("SELECT * FROM cookies")
    suspend fun getAllCookies(): List<CookieEntity>

    /** 插入或替换 Cookie（按 name+domain+path 唯一索引 upsert） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cookies: List<CookieEntity>)

    /** 清空所有 Cookie */
    @Query("DELETE FROM cookies")
    suspend fun clearAll()
}
