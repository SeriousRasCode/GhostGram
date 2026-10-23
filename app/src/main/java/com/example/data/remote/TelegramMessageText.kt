package com.example.data.remote

import org.drinkless.tdlib.TdApi
import org.json.JSONArray
import org.json.JSONObject

/** Keeps Telegram's original UTF-16 offsets; captions must never have filenames prepended. */
internal object TelegramMessageText {
    fun formattedText(content: TdApi.MessageContent): TdApi.FormattedText? = when (content) {
        is TdApi.MessageText -> content.text
        is TdApi.MessagePhoto -> content.caption
        is TdApi.MessageVideo -> content.caption
        is TdApi.MessageAnimation -> content.caption
        is TdApi.MessageAudio -> content.caption
        is TdApi.MessageVoiceNote -> content.caption
        is TdApi.MessageDocument -> content.caption
        else -> null
    }

    fun encodeEntities(text: TdApi.FormattedText): String = JSONArray().apply {
        text.entities.forEach { entity ->
            put(JSONObject().apply {
                put("offset", entity.offset)
                put("length", entity.length)
                put("type", entity.type.javaClass.simpleName.removePrefix("TextEntityType"))
                when (val type = entity.type) {
                    is TdApi.TextEntityTypeTextUrl -> put("url", type.url)
                    is TdApi.TextEntityTypeMentionName -> put("url", "tg://user?id=${type.userId}")
                    else -> Unit
                }
            })
        }
    }.toString()
}
