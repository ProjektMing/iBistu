package edu.bistu.cs4029.ibistu.login

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import edu.bistu.cs4029.ibistu.schedule.model.ScheduleCacheEntity
import edu.bistu.cs4029.ibistu.schedule.model.ScheduleDao
import edu.bistu.cs4029.ibistu.schedule.model.ExamCacheEntity
import edu.bistu.cs4029.ibistu.schedule.model.ExamDao

@Database(
    entities = [CookieEntity::class, ScheduleCacheEntity::class, ExamCacheEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cookieDao(): CookieDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun examDao(): ExamDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Migration 1→2：新增 schedule_cache 表。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_cache` (
                        `id` INTEGER NOT NULL,
                        `term_name` TEXT NOT NULL,
                        `term_code` TEXT NOT NULL,
                        `json_hash` TEXT NOT NULL,
                        `courses_json` TEXT NOT NULL,
                        `term_weeks_json` TEXT NOT NULL DEFAULT '{}',
                        `cached_at` INTEGER NOT NULL DEFAULT 0,
                        `week_range_end` INTEGER NOT NULL DEFAULT 20,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /** Migration 2→3：新增 exam_cache 表。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `exam_cache` (
                        `id` INTEGER NOT NULL,
                        `term_code` TEXT NOT NULL,
                        `json_hash` TEXT NOT NULL,
                        `exams_json` TEXT NOT NULL,
                        `cached_at` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ibistu.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
