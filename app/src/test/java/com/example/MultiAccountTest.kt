package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.GhostAccounts
import com.example.data.local.GhostDatabase
import com.example.data.local.GhostMessageEntity
import com.example.data.local.GhostPreferences
import com.example.data.model.SourceConfig
import com.example.data.model.UserAccountSession
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MultiAccountTest {
    private lateinit var context: Context

    @Before fun resetAccounts() {
        context = ApplicationProvider.getApplicationContext()
        val accounts = GhostAccounts.get(context)
        accounts.accounts.value.forEach {
            context.getSharedPreferences(GhostAccounts.preferencesName(it.id), Context.MODE_PRIVATE).edit().clear().commit()
        }
        context.getSharedPreferences("ghostgram_accounts", Context.MODE_PRIVATE).edit().clear().commit()
        accounts.refresh()
        accounts.switchTo(GhostAccounts.DEFAULT_ACCOUNT)
    }

    @Test fun `existing account keeps legacy paths and switching preserves isolated sessions and settings`() {
        val registry = GhostAccounts.get(context)
        val original = GhostPreferences(context, "default")
        original.saveUserSession(UserAccountSession(isLoggedIn = true, userId = 1, phoneNumber = "111", userName = "First"))
        original.saveConfig(SourceConfig(focusKeywords = "first", showNotificationPreview = false))
        assertSame(registry, GhostAccounts.get(context.applicationContext))
        assertTrue(registry.accounts.value.single().session.isLoggedIn)
        assertEquals(1L, registry.accounts.value.single().session.userId)
        assertEquals("ghostgram_prefs", GhostAccounts.preferencesName("default"))
        assertEquals("ghostgram_database", GhostAccounts.databaseName("default"))
        assertEquals("tdlib", GhostAccounts.tdlibDirectory("default"))

        val secondId = registry.addAccount()
        val second = GhostPreferences(context, secondId)
        assertNotEquals("default", secondId)
        assertFalse(second.loadUserSession().isLoggedIn)
        second.saveUserSession(UserAccountSession(isLoggedIn = true, userId = 2, phoneNumber = "222", userName = "Second"))
        second.saveConfig(SourceConfig(focusKeywords = "second"))
        assertEquals(2, registry.accounts.value.size)
        registry.switchTo("default")
        assertEquals(1L, GhostPreferences(context).loadUserSession().userId)
        assertEquals("first", GhostPreferences(context).loadConfig().focusKeywords)
        assertFalse(GhostPreferences(context).loadConfig().showNotificationPreview)
        registry.switchTo(secondId)
        assertEquals(2L, GhostPreferences(context).loadUserSession().userId)
        assertEquals("second", GhostPreferences(context).loadConfig().focusKeywords)
        assertTrue(GhostPreferences(context).loadConfig().showNotificationPreview)
        assertEquals(secondId, context.getSharedPreferences("ghostgram_accounts", Context.MODE_PRIVATE).getString("active_id", null))
        second.logoutUser()
        assertTrue(original.loadUserSession().isLoggedIn)
        assertEquals(2L, second.loadUserSession().userId)
        assertFalse(second.loadUserSession().isLoggedIn)
        assertTrue(second.loadUserSession().blockPresencePings)
        assertTrue(original.loadUserSession().blockReadReceipts)
        assertNotEquals(secondId, registry.addAccount())
    }

    @Test fun `unfinished sign in is reused and invalid account paths are rejected`() {
        val registry = GhostAccounts.get(context)
        assertEquals("default", registry.addAccount())
        assertEquals(1, registry.accounts.value.size)
        assertFalse(GhostAccounts.isValidId("../another-account"))
        assertThrows(IllegalArgumentException::class.java) { registry.switchTo("unknown") }
        assertThrows(IllegalArgumentException::class.java) { GhostAccounts.tdlibDirectory("../another-account") }
    }

    @Test fun `identical chat and message IDs in different account databases remain separate`() {
        val firstName = GhostAccounts.databaseName("default")
        val secondName = GhostAccounts.databaseName("11111111-1111-1111-1111-111111111111")
        val first = Room.databaseBuilder(context, GhostDatabase::class.java, firstName).allowMainThreadQueries().build()
        val second = Room.databaseBuilder(context, GhostDatabase::class.java, secondName).allowMainThreadQueries().build()
        try {
            val row = GhostMessageEntity(telegramMsgId = 42, sourceId = 7, chatType = "GROUP",
                senderName = "Sender", senderHandle = "", text = "First account", timestamp = 1000)
            first.ghostMessageDao().insert(row)
            second.ghostMessageDao().insert(row.copy(text = "Second account"))
            assertEquals("First account", first.ghostMessageDao().find(7, 42)!!.text)
            assertEquals("Second account", second.ghostMessageDao().find(7, 42)!!.text)
            first.ghostMessageDao().updateBookmark(7, 42, true)
            assertFalse(second.ghostMessageDao().find(7, 42)!!.isBookmarked)
        } finally { first.close(); second.close() }
    }
}
