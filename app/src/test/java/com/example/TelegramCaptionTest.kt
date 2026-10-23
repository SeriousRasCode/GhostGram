package com.example

import android.app.Application
import com.example.data.remote.TelegramMessageText
import org.drinkless.tdlib.TdApi
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TelegramCaptionTest {
    @Test fun `audio and voice captions preserve text and UTF16 entity offsets`() {
        val caption = TdApi.FormattedText("🎵 Read this", arrayOf(
            TdApi.TextEntity(3, 4, TdApi.TextEntityTypeBold()),
            TdApi.TextEntity(8, 4, TdApi.TextEntityTypeTextUrl("https://telegram.org"))
        ))
        val audio = TdApi.MessageAudio().apply { this.caption = caption }
        val voice = TdApi.MessageVoiceNote().apply { this.caption = caption }
        val document = TdApi.MessageDocument().apply { this.caption = caption }
        listOf(audio, voice, document).forEach { content ->
            val formatted = TelegramMessageText.formattedText(content)!!
            assertEquals("🎵 Read this", formatted.text)
            val entities = JSONArray(TelegramMessageText.encodeEntities(formatted))
            assertEquals(3, entities.getJSONObject(0).getInt("offset"))
            assertEquals("Bold", entities.getJSONObject(0).getString("type"))
            assertEquals("https://telegram.org", entities.getJSONObject(1).getString("url"))
        }
    }

    @Test fun `uncaptioned media keeps an empty caption rather than placeholder text`() {
        val photo = TdApi.MessagePhoto().apply { caption = TdApi.FormattedText("", emptyArray()) }
        assertEquals("", TelegramMessageText.formattedText(photo)!!.text)
    }
}
