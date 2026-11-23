package com.alan.musicplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [GroupEntity::class, GroupSongEntity::class, PlayStatEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun groupDao(): GroupDao

    abstract fun playStatDao(): PlayStatDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 -> v2：新增播放统计表，保留用户已有分组数据。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `play_stats` (" +
                        "`songId` INTEGER NOT NULL, " +
                        "`playCount` INTEGER NOT NULL, " +
                        "`lastPlayedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`songId`))",
                )
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "local_music.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
