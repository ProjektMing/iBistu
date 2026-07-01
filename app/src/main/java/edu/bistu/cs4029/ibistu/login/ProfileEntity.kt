package edu.bistu.cs4029.ibistu.login

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room 实体：用户个人资料，以学号为唯一键。
 * 登录后自动创建/加载，昵称/真实姓名/班级/头像可编辑。
 *
 * avatarUri 存储头像文件的 content:// URI 字符串（文件位于应用内部 avatars/ 目录）。
 */
@Entity(
    tableName = "profiles",
    indices = [Index(value = ["student_id"], unique = true)]
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "student_id") val studentId: String,
    @ColumnInfo(name = "nickname") val nickname: String = "",
    @ColumnInfo(name = "real_name") val realName: String = "",
    @ColumnInfo(name = "class_name") val className: String = "",
    @ColumnInfo(name = "avatar_style") val avatarStyle: Int = 0,
    @ColumnInfo(name = "avatar_uri") val avatarUri: String = "",
    @ColumnInfo(name = "gender") val gender: Int = 0
)
