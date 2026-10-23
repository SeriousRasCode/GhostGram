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
    @Test fun `refresh upserts retain latest bookmark state and insert newly received messages`() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GhostDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.ghostMessageDao()
            dao.insert(message(100).copy(isBookmarked = true))
            dao.upsertAllPreservingBookmarks(listOf(message(100).copy(text = "Updated"), message(200)))
            assertTrue(dao.find(100, 42)!!.isBookmarked)
            assertEquals("Updated", dao.find(100, 42)!!.text)
            assertFalse(dao.find(200, 42)!!.isBookmarked)
            dao.updateBookmark(100, 42, false)
            dao.upsertPreservingBookmark(message(100).copy(isBookmarked = true))
            assertFalse(dao.find(100, 42)!!.isBookmarked)
        } finally { db.close() }
    }
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

    @Test fun `reaction updates affect only their source and preserve message content and bookmarks`() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GhostDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.ghostMessageDao()
            dao.insertAll(listOf(message(100).copy(isBookmarked = true), message(200)))
            dao.updateInteractions(100, 42, "👍 12 · 🔥 8", "100", 5)
            val changed = dao.find(100, 42)!!
            assertEquals("👍 12 · 🔥 8", changed.reactions)
            assertEquals("100", changed.views)
            assertEquals(5, changed.commentCount)
            assertTrue(changed.isBookmarked)
            assertEquals("A caption", changed.text)
            assertEquals(7, changed.photoFileId)
            assertNull(dao.find(200, 42)!!.reactions)
            dao.updateInteractions(100, 42, null, null, -1)
            assertNull(dao.find(100, 42)!!.reactions)
        } finally { db.close() }
    }

    @Test fun `sticker downloads and reply destinations survive storage updates`() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GhostDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.ghostMessageDao()
            dao.insert(message(100).copy(photoFileId = 0, stickerFileId = 99, stickerFormat = "tgs",
                stickerThumbnailFileId = 100, stickerEmoji = "🎉", replyToMessageId = 123L shl 20, replyToChatId = -100999L))
            dao.updateStickerMetadata(100, 42, 99, "tgs", "🎉", null, 100, null)
            dao.updateSticker(99, "/sticker.tgs")
            dao.updateStickerThumbnail(100, "/thumbnail.webp")
            val loaded = dao.find(100, 42)!!.toDomain()
            assertEquals(99, loaded.stickerFileId)
            assertEquals("tgs", loaded.stickerFormat)
            assertEquals("/sticker.tgs", loaded.stickerPath)
            assertEquals("/thumbnail.webp", loaded.stickerThumbnailPath)
            assertEquals("🎉", loaded.stickerEmoji)
            assertEquals(123L shl 20, loaded.replyToMessageId)
            assertEquals(-100999L, loaded.replyToChatId)
            assertEquals(loaded, GhostMessageEntity.fromDomain(loaded).toDomain())
            assertEquals("A caption", loaded.text)
        } finally { db.close() }
    }

    @Test fun `legacy sticker metadata repair preserves existing message state`() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GhostDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            db.focusSourceDao().insertSource(com.example.data.local.FocusSourceEntity(
                id = 100, type = "CHANNEL", title = "Channel", handleOrId = "-100999", telegramChatId = -100999))
            val dao = db.ghostMessageDao()
            dao.insert(message(100).copy(photoFileId = 0, text = "Sticker 🎉", isBookmarked = true, reactions = "👍 3"))
            assertEquals(1, dao.legacyStickerMessages().size)
            dao.updateStickerMetadata(100, 42, 99, "tgs", "🎉", null, 100, null)
            assertTrue(dao.legacyStickerMessages().isEmpty())
            val recovered = dao.find(100, 42)!!
            assertTrue(recovered.isBookmarked)
            assertEquals("👍 3", recovered.reactions)
            assertEquals("Sticker 🎉", recovered.text)
            assertEquals(99, recovered.stickerFileId)
        } finally { db.close() }
    }

    @Test fun `v10 migration preserves messages through the current Room schema`() {
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
            GhostDatabase.MIGRATION_11_12.migrate(sql)
            assertEquals(expected, TableInfo.read(sql, "ghost_messages"))
            val migrated = db.ghostMessageDao().find(100, 42)!!
            assertEquals("Keep me", migrated.text)
            assertTrue(migrated.isBookmarked)
            db.ghostMessageDao().insert(message(200))
            assertEquals(2, db.ghostMessageDao().countAll())
        } finally { db.close() }
    }
}
