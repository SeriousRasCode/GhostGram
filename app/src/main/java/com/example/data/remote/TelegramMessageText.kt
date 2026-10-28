package com.example.data.remote

import org.drinkless.tdlib.TdApi
import org.json.JSONArray
import org.json.JSONObject

/** Keeps Telegram's original UTF-16 offsets; captions must never have filenames prepended. */
object TelegramMessageText {
    /** Text shown in feed cards, chat lists, replies, and notification previews. */
    fun preview(content: TdApi.MessageContent): String = when (content) {
        is TdApi.MessageText -> content.text.text
        is TdApi.MessagePhoto -> content.caption.text.ifBlank { "[Photo]" }
        is TdApi.MessageVideo -> content.caption.text.ifBlank { "[Video]" }
        is TdApi.MessageAnimation -> content.caption.text.ifBlank { "[GIF]" }
        is TdApi.MessageAudio -> content.caption.text.ifBlank {
            listOf(content.audio.performer, content.audio.title).filter { it.isNotBlank() }
                .joinToString(" — ").ifBlank { "[Audio]" }
        }
        is TdApi.MessageVoiceNote -> content.caption.text.ifBlank { "[Voice message]" }
        is TdApi.MessageDocument -> content.caption.text.ifBlank {
            "[File: ${content.document.fileName.ifBlank { "document" }}]"
        }
        is TdApi.MessageSticker -> content.sticker.emoji.takeIf { it.isNotBlank() }?.let { "Sticker $it" } ?: "[Sticker]"
        is TdApi.MessageAnimatedEmoji -> content.emoji
        is TdApi.MessageDice -> "[Dice: ${content.emoji}]"
        is TdApi.MessageLocation -> "[Location]"
        is TdApi.MessageVenue -> "[Venue: ${content.venue.title}]"
        is TdApi.MessageContact -> "[Contact: ${content.contact.firstName}]"
        is TdApi.MessageVideoNote -> "[Video message]"
        is TdApi.MessagePoll -> "[Poll: ${content.poll.question.text}]"
        is TdApi.MessageGame -> "[Game: ${content.game.title}]"
        is TdApi.MessageInvoice -> "[Invoice: ${content.title}]"
        is TdApi.MessageCall -> "[Call]"
        is TdApi.MessageStory -> "[Story]"
        is TdApi.MessagePaidMedia -> content.caption.text.ifBlank { "[Paid media]" }
        is TdApi.MessageGiveaway -> "[Giveaway]"
        is TdApi.MessageGiveawayWinners -> "[Giveaway winners]"
        is TdApi.MessageExpiredPhoto -> "[Expired photo]"
        is TdApi.MessageExpiredVideo -> "[Expired video]"
        else -> "[${content.javaClass.simpleName.removePrefix("Message").replace(Regex("([a-z])([A-Z])")) { match -> "${match.groupValues[1]} ${match.groupValues[2]}" }}]"
    }

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
