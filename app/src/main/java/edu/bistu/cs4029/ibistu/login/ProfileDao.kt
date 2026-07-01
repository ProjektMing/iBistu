package edu.bistu.cs4029.ibistu.login

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProfileDao {

    /** 按学号获取个人资料，不存在时返回 null。 */
    @Query("SELECT * FROM profiles WHERE student_id = :studentId LIMIT 1")
    suspend fun getByStudentId(studentId: String): ProfileEntity?

    /** 插入或替换个人资料（按 student_id 唯一索引 upsert）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity)

    /** 删除指定学号的个人资料。 */
    @Query("DELETE FROM profiles WHERE student_id = :studentId")
    suspend fun deleteByStudentId(studentId: String)
}
