package edu.bistu.cs4029.ibistu.login

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CookieEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LoginDatabase : RoomDatabase() {

    abstract fun cookieDao(): CookieDao

    companion object {
        @Volatile
        private var INSTANCE: LoginDatabase? = null

        fun getInstance(context: Context): LoginDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LoginDatabase::class.java,
                    "bistulogin.db"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
