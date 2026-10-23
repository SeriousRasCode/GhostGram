package com.example

import android.app.Application
import androidx.room.Room
import androidx.room.util.TableInfo
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.GhostDatabase
import com.example.data.local.GhostMessageEntity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MessageStorageTest {
    private fun message(source: Long) = GhostMessageEntity(
        telegramMsgId = 42, sourceId = source, chatType = "CHANNEL", senderName = "Channel",
        senderHandle = "", text = "A caption", timestamp = 1000,
        telegramChatId = source, photoFileId = 7, commentCount = 4,
        formattedEntities = "[{\"offset\":0,\"length\":1,\"type\":\"Bold\"}]"
    )

    @Test fun `messages with equal IDs from different sources coexist and bookmark independently`() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GhostDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.ghostMessageDao()
            dao.insertAll(listOf(message(100), message(200)))
            assertEquals(2, dao.countAll())
            dao.updateBookmark(100, 42, true)
            assertTrue(dao.find(100, 42)!!.isBookmarked)
            assertFalse(dao.find(200, 42)!!.isBookmarked)
            dao.updatePhoto(7, "/downloaded/photo.jpg")
            val loaded = dao.find(100, 42)!!.toDomain()
            assertEquals("/downloaded/photo.jpg", loaded.imageUrl)
            assertEquals(7, loaded.photoFileId)
            assertEquals(4, loaded.commentCount)
            assertEquals(message(100).formattedEntities, loaded.formattedEntities)
            assertEquals("A caption", loaded.text)
            dao.deleteMessages(100, listOf(42))
            assertNotNull(dao.find(200, 42))
        } finally { db.close() }
    }

    @Test fun `v10 migration preserves messages and produces the current Room schema`() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GhostDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val sql = db.openHelper.writableDatabase
            val expected = TableInfo.read(sql, "ghost_messages")
            sql.execSQL("DROP TABLE ghost_messages")
            sql.execSQL("""CREATE TABLE ghost_messages (
                telegramMsgId INTEGER NOT NULL PRIMARY KEY, sourceId INTEGER NOT NULL,
                sourceTitle TEXT NOT NULL, sourceAvatarPath TEXT, chatType TEXT NOT NULL,
                senderId INTEGER NOT NULL, senderName TEXT NOT NULL, senderHandle TEXT NOT NULL,
                text TEXT NOT NULL, timestamp INTEGER NOT NULL, imageUrl TEXT, stickerPath TEXT,
                videoPath TEXT, audioPath TEXT, voicePath TEXT, attachmentName TEXT, attachmentSize TEXT,
                attachmentMime TEXT, attachmentUrl TEXT, attachmentLocalPath TEXT,
                attachmentFileId INTEGER NOT NULL, animationMimeType TEXT,
                replyToMessageId INTEGER NOT NULL, replyToSenderName TEXT, replyToText TEXT,
                isPinned INTEGER NOT NULL, views TEXT, reactions TEXT,
                isBookmarked INTEGER NOT NULL, isUrgent INTEGER NOT NULL)""")
            sql.execSQL("""INSERT INTO ghost_messages
                (telegramMsgId, sourceId, sourceTitle, chatType, senderId, senderName, senderHandle,
                text, timestamp, attachmentFileId, replyToMessageId, isPinned, isBookmarked, isUrgent)
                VALUES (42, 100, 'Channel', 'CHANNEL', 0, 'Author', '', 'Keep me', 1000, 0, 0, 0, 1, 0)""")
            GhostDatabase.MIGRATION_10_11.migrate(sql)
            assertEquals(expected, TableInfo.read(sql, "ghost_messages"))
            val migrated = db.ghostMessageDao().find(100, 42)!!
            assertEquals("Keep me", migrated.text)
            assertTrue(migrated.isBookmarked)
            db.ghostMessageDao().insert(message(200))
            assertEquals(2, db.ghostMessageDao().countAll())
        } finally { db.close() }
    }
}
