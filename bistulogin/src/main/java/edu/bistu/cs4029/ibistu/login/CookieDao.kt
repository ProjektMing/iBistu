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

    /** 按 domain 获取 Cookie（用于加载某域下的所有 cookie） */
    @Query("SELECT * FROM cookies WHERE domain = :domain")
    suspend fun getCookiesByDomain(domain: String): List<CookieEntity>

    /** 插入或替换 Cookie（按 name+domain+path 唯一索引 upsert） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cookie: CookieEntity)

    /** 批量插入 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cookies: List<CookieEntity>)

    /** 删除指定 Cookie */
    @Query("DELETE FROM cookies WHERE name = :name AND domain = :domain AND path = :path")
    suspend fun delete(name: String, domain: String, path: String)

    /** 清空所有 Cookie */
    @Query("DELETE FROM cookies")
    suspend fun clearAll()
}
