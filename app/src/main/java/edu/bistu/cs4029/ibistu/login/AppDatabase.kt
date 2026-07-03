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
import edu.bistu.cs4029.ibistu.focus.FocusDao
import edu.bistu.cs4029.ibistu.focus.model.FocusSession
import edu.bistu.cs4029.ibistu.focus.model.FocusTask
import edu.bistu.cs4029.ibistu.schedule.model.ExamDao

@Database(
    entities = [ScheduleCacheEntity::class, ExamCacheEntity::class, FocusSession::class, FocusTask::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scheduleDao(): ScheduleDao
    abstract fun examDao(): ExamDao
    abstract fun focusDao(): FocusDao

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

        /** Migration 3→4：将 Cookie 存储迁移至独立的 bistulogin 模块（bistulogin.db）。
         *  此处仅删除旧 cookies 表；已保存的 Cookie 不予迁移，用户升级后需重新登录。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `cookies`")
            }
        }

        /** Migration 4→5：新增 focus_sessions 表。 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `focus_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `start_time` INTEGER NOT NULL,
                        `end_time` INTEGER NOT NULL,
                        `duration_seconds` INTEGER NOT NULL,
                        `target_duration_seconds` INTEGER NOT NULL DEFAULT 0,
                        `mode` TEXT NOT NULL DEFAULT 'countdown',
                        `label` TEXT NOT NULL DEFAULT '',
                        `created_at` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        /** Migration 5→6：focus_sessions 表新增 interruption_type 列。 */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `interruption_type` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Migration 6→7：新增 focus_tasks 表；focus_sessions 表新增 task_id 列。 */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `focus_tasks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `mode` TEXT NOT NULL DEFAULT 'countdown',
                        `target_seconds` INTEGER NOT NULL DEFAULT 1500,
                        `sort_order` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `task_id` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ibistu.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

