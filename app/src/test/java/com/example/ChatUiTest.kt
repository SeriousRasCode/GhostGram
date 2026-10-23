package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.screens.SourceDetailTopBar
import com.example.ui.theme.GhostGramTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatUiTest {
    @get:Rule val compose = createComposeRule()
    private val session = UserAccountSession(userId = 99, userName = "You")
    private fun message(id: Long = 1, type: ChatSourceType = ChatSourceType.GROUP, text: String = "The updated plans are ready for review.") =
        GhostMessage(id, sourceId = 5, sourceTitle = "Design collective", chatType = type, telegramMsgId = id,
            telegramChatId = -1005, senderId = 12, senderName = "Maya Chen", senderHandle = "", text = text,
            timestamp = 1_793_000_000_000L + id * 30_000)

    private fun render(dark: Boolean = false, fontScale: Float = 1f, width: Dp = 390.dp, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GhostGramTheme(darkTheme = dark) {
                    Surface(Modifier.requiredWidth(width).requiredHeight(760.dp).testTag("preview"),
                        color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }

    private fun screenshot(name: String) {
        val file = File("build/reports/chat-previews/$name.png")
        file.parentFile.mkdirs()
        file.outputStream().use { compose.onNodeWithTag("preview").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable private fun Header(type: ChatSourceType = ChatSourceType.GROUP, title: String = "Design collective") {
        SourceDetailTopBar(FocusSource(5, type, title, "design"), "", null,
            {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun `message menu is labeled large enough and performs bookmark action`() {
        var bookmarked = false
        render { Column(Modifier.padding(12.dp)) {
            ChatMessageCard(message(), session, { bookmarked = true }, {}, { null }, null)
        } }
        compose.onNodeWithContentDescription("Message options").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Bookmark", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertTrue(bookmarked) }
    }

    @Test fun `group and personal conversation layouts render in light mode`() {
        render {
            Column {
                Header()
                Column(Modifier.padding(horizontal = 12.dp)) {
                    MessageDateSeparator(message().timestamp)
                    ChatMessageCard(message(), session, {}, {}, { null }, null, endsGroup = false)
                    ChatMessageCard(message(2, text = "Please check the spacing on the smaller screens too."), session, {}, {}, { null }, null, startsGroup = false)
                    ChatMessageCard(message(3, ChatSourceType.PERSONAL, "I'll review those this afternoon.").copy(senderId = 99, senderName = "You"),
                        session, {}, {}, { null }, null)
                }
            }
        }
        compose.onNodeWithText("Maya Chen").assertIsDisplayed()
        compose.onNodeWithText("I'll review those this afternoon.").assertIsDisplayed()
        screenshot("conversation-light")
    }

    @Test fun `channel layout renders in dark mode with accessible discussion target`() {
        render(dark = true) {
            Column {
                Header(ChatSourceType.CHANNEL, "Studio journal")
                Column(Modifier.padding(12.dp)) {
                    MessageDateSeparator(message().timestamp)
                    ChatMessageCard(message(type = ChatSourceType.CHANNEL, text = "A calmer space for the conversations that matter.\n\nOur October update brings clearer reading, thoughtful details and more room for your content.")
                        .copy(sourceTitle = "Studio journal", views = "1.2K", commentCount = 12, reactions = "👍 24 · ❤ 8", isPinned = true),
                        session, {}, {}, { null }, null)
                }
            }
        }
        compose.onNodeWithText("12 comments").assertHasClickAction().assertHeightIsAtLeast(48.dp)
        screenshot("channel-dark")
    }

    @Test fun `large text and long names keep chat actions reachable on narrow screens`() {
        render(fontScale = 2f, width = 320.dp) {
            Column {
                Header(ChatSourceType.PERSONAL, "Alexandra Montgomery Williams")
                Column(Modifier.padding(12.dp)) {
                    ChatMessageCard(message(type = ChatSourceType.PERSONAL, text = "Meet at 10?"), session, {}, {}, { null }, null)
                }
            }
        }
        compose.onNodeWithContentDescription("Search messages").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Message options").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        screenshot("chat-large-text")
    }

    @Test fun `discussion has accessible close refresh and history actions`() {
        var dismissed = false
        var more = false
        render {
            CommentsSheetContent(message(type = ChatSourceType.CHANNEL).copy(commentCount = 18),
                listOf(message(2, text = "The new spacing looks much easier to read."), message(1)), session,
                false, null, false, rememberLazyListState(), 1f, { null }, {}, {}, {}, { more = true }, { dismissed = true },
                modifier = Modifier.fillMaxSize().padding(top = 16.dp))
        }
        compose.onNodeWithContentDescription("Close comments").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Load earlier comments").performClick()
        compose.runOnIdle { assertTrue(more) }
        screenshot("discussion-light")
        compose.onNodeWithContentDescription("Close comments").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun `discussion failure offers a working retry without a blank panel`() {
        var retried = false
        render(fontScale = 2f, width = 320.dp) {
            CommentsSheetContent(message(type = ChatSourceType.CHANNEL), emptyList(), session,
                false, "Check your connection and try again.", false, rememberLazyListState(), 1f,
                { null }, {}, {}, { retried = true }, {}, {}, modifier = Modifier.fillMaxSize())
        }
        compose.onNodeWithText("Try again").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertTrue(retried) }
        screenshot("discussion-error-large-text")
    }

    @Test fun `hidden spoiler text is excluded from accessible text until revealed`() {
        render { Column(Modifier.padding(20.dp)) {
            RichMessageText("Public secret", "[{\"offset\":7,\"length\":6,\"type\":\"Spoiler\"}]")
        } }
        compose.onAllNodesWithText("secret", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }
}
