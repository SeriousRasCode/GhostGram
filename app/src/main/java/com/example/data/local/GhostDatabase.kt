package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [GhostMessageEntity::class, FocusSourceEntity::class],
    version = 10,
    exportSchema = false
)
abstract class GhostDatabase : RoomDatabase() {
    abstract fun ghostMessageDao(): GhostMessageDao
    abstract fun focusSourceDao(): FocusSourceDao

    companion object {
        @Volatile
        private var INSTANCE: GhostDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE ghost_messages ADD COLUMN views TEXT")
                } catch (e: Exception) { }
                try {
                    database.execSQL("ALTER TABLE ghost_messages ADD COLUMN reactions TEXT")
                } catch (e: Exception) { }
                try {
                    database.execSQL("ALTER TABLE focus_sources ADD COLUMN avatarPath TEXT")
                } catch (e: Exception) { }
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE ghost_messages ADD COLUMN sourceAvatarPath TEXT")
                } catch (e: Exception) { }
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE ghost_messages ADD COLUMN senderId INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) { }
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE ghost_messages ADD COLUMN animationMimeType TEXT")
                database.execSQL("ALTER TABLE ghost_messages ADD COLUMN replyToMessageId INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE ghost_messages ADD COLUMN replyToSenderName TEXT")
                database.execSQL("ALTER TABLE ghost_messages ADD COLUMN replyToText TEXT")
            }
        }

        fun getDatabase(context: Context): GhostDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GhostDatabase::class.java,
                    "ghostgram_database"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
