package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [GhostMessageEntity::class, FocusSourceEntity::class],
    version = 11,
    exportSchema = false
)
abstract class GhostDatabase : RoomDatabase() {
    abstract fun ghostMessageDao(): GhostMessageDao
    abstract fun focusSourceDao(): FocusSourceDao

    companion object {
        private val instances = mutableMapOf<String, GhostDatabase>()

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

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE ghost_messages ADD COLUMN telegramChatId INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE ghost_messages ADD COLUMN photoFileId INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE ghost_messages ADD COLUMN formattedEntities TEXT")
                database.execSQL("ALTER TABLE ghost_messages ADD COLUMN commentCount INTEGER NOT NULL DEFAULT -1")
                val columns = mutableListOf<String>()
                val definitions = mutableListOf<String>()
                database.query("PRAGMA table_info(ghost_messages)").use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(1)
                        columns.add("`$name`")
                        definitions.add("`$name` ${cursor.getString(2)}" +
                            if (cursor.getInt(3) != 0) " NOT NULL" else "")
                    }
                }
                database.execSQL("CREATE TABLE ghost_messages_new (${definitions.joinToString()}, PRIMARY KEY(sourceId, telegramMsgId))")
                database.execSQL("INSERT INTO ghost_messages_new (${columns.joinToString()}) SELECT ${columns.joinToString()} FROM ghost_messages")
                database.execSQL("DROP TABLE ghost_messages")
                database.execSQL("ALTER TABLE ghost_messages_new RENAME TO ghost_messages")
            }
        }

        fun getDatabase(context: Context, accountId: String = GhostAccounts.get(context).activeId.value): GhostDatabase {
            return synchronized(this) {
                instances[accountId]?.let { return@synchronized it }
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GhostDatabase::class.java,
                    GhostAccounts.databaseName(accountId)
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .fallbackToDestructiveMigration()
                    .build()
                instances[accountId] = instance
                instance
            }
        }
    }
}
