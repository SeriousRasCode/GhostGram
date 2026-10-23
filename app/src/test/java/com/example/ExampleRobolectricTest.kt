package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ChatSourceType
import com.example.data.model.FocusSource
import com.example.data.model.GhostMessage
import com.example.data.model.MainTab
import com.example.data.model.SourceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ExampleRobolectricTest {

  @Test
  fun `read string from context matches app name`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("GhostGram", appName)
  }

  @Test
  fun `stealth source configuration defaults isolate chats`() {
    val config = SourceConfig()
    assertTrue(config.silenceMode)
    assertTrue(config.focusKeywords.contains("urgent"))
  }

  @Test
  fun `four main tabs defined for focus experience`() {
    val tabs = MainTab.values()
    assertEquals(4, tabs.size)
    assertEquals(MainTab.FEED, tabs[0])
    assertEquals(MainTab.GROUPS, tabs[1])
    assertEquals(MainTab.CHANNELS, tabs[2])
  }

  @Test
  fun `focus source domain model attributes`() {
    val source = FocusSource(
      id = 10,
      type = ChatSourceType.CHANNEL,
      title = "Durov Alerts",
      handleOrId = "durov",
      description = "Official announcements"
    )
    assertEquals("Durov Alerts", source.title)
    assertEquals(ChatSourceType.CHANNEL, source.type)
  }

  @Test
  fun `ghost message urgent flag identification`() {
    val msg = GhostMessage(
      id = 1,
      sourceId = 1,
      sourceTitle = "Engineering Pulse",
      chatType = ChatSourceType.CHANNEL,
      telegramMsgId = 1,
      senderName = "Pulse",
      senderHandle = "@pulse",
      text = "🚨 #urgent Architecture Directive",
      timestamp = System.currentTimeMillis(),
      isUrgent = true
    )
    assertTrue(msg.isUrgent)
  }

  @Test
  fun `user account session defaults have ghost mode active`() {
    val session = com.example.data.model.UserAccountSession(
      isLoggedIn = true,
      phoneNumber = "+1 555 123 4567"
    )
    assertTrue(session.isGhostModeActive)
    assertTrue(session.blockReadReceipts)
    assertTrue(session.blockPresencePings)
  }

  @Test
  fun `read built_by author attribution matches requirement`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val builtBy = context.getString(R.string.built_by)
    assertEquals("Built by Tedros Teshome | Software Engineer", builtBy)
  }
}
