package com.example.data.remote

import com.example.data.model.ChatSourceType
import com.example.data.model.GhostMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class TelegramGhostClient {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches real live public posts from a Telegram channel via the public web preview endpoint.
     * ZERO personal authentication = ZERO presence pings = Last seen status untouched.
     */
    suspend fun fetchPublicChannel(
        channelHandle: String,
        sourceId: Long = 0,
        sourceTitle: String = ""
    ): Result<List<GhostMessage>> = withContext(Dispatchers.IO) {
        val cleanHandle = channelHandle.trim().removePrefix("@")
        if (cleanHandle.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        try {
            val url = "https://t.me/s/$cleanHandle"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile")
                .header("Accept", "text/html,application/xhtml+xml")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.success(emptyList())
            }

            val html = response.body?.string() ?: ""
            val parsedPosts = parseTelegramChannelHtml(html, cleanHandle, sourceId, sourceTitle)

            Result.success(parsedPosts)
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    /**
     * Parses messages from Telegram web preview HTML.
     */
    private fun parseTelegramChannelHtml(
        html: String,
        channelHandle: String,
        sourceId: Long,
        sourceTitle: String
    ): List<GhostMessage> {
        val messages = mutableListOf<GhostMessage>()

        // Split HTML by message blocks
        val messageChunks = html.split("tgme_widget_message_wrap")
        if (messageChunks.size <= 1) return emptyList()

        val displaySourceTitle = if (sourceTitle.isNotBlank()) sourceTitle else "@$channelHandle"

        for (i in 1 until messageChunks.size) {
            val chunk = messageChunks[i]

            // Extract message ID
            val idMatcher = Pattern.compile("data-post=\"[^\"]+/(\\d+)\"").matcher(chunk)
            val msgId = if (idMatcher.find()) idMatcher.group(1)?.toLongOrNull() ?: System.currentTimeMillis() else System.currentTimeMillis()

            // Extract message text
            val textMatcher = Pattern.compile("<div class=\"tgme_widget_message_text[^\"]*\"[^>]*>(.*?)</div>", Pattern.DOTALL).matcher(chunk)
            val rawText = if (textMatcher.find()) textMatcher.group(1) ?: "" else ""
            val cleanText = sanitizeHtml(rawText)

            if (cleanText.isBlank()) continue

            // Extract views
            val viewsMatcher = Pattern.compile("<span class=\"tgme_widget_message_views\">([^<]+)</span>").matcher(chunk)
            val views = if (viewsMatcher.find()) viewsMatcher.group(1) else "1.2K"

            // Extract photo url
            val photoMatcher = Pattern.compile("background-image:url\\('([^']+)'\\)").matcher(chunk)
            val photoUrl = if (photoMatcher.find()) photoMatcher.group(1) else null

            // Check pinned
            val isPinned = chunk.contains("tgme_widget_message_pinned")

            val message = GhostMessage(
                id = msgId,
                sourceId = sourceId,
                sourceTitle = displaySourceTitle,
                chatType = ChatSourceType.CHANNEL,
                telegramMsgId = msgId,
                senderName = displaySourceTitle,
                senderHandle = "@$channelHandle",
                text = cleanText,
                timestamp = System.currentTimeMillis() - ((messageChunks.size - i) * 1800000L),
                imageUrl = photoUrl,
                isPinned = isPinned,
                views = views,
                isBookmarked = false,
                isUrgent = cleanText.contains("urgent", ignoreCase = true) ||
                        cleanText.contains("critical", ignoreCase = true) ||
                        cleanText.contains("alert", ignoreCase = true) ||
                        cleanText.contains("deadline", ignoreCase = true)
            )
            messages.add(message)
        }

        return messages.reversed()
    }

    private fun sanitizeHtml(html: String): String {
        return html
            .replace(Regex("<br/?>"), "\n")
            .replace(Regex("<a [^>]+>([^<]+)</a>"), "$1")
            .replace(Regex("<b[^>]*>([^<]+)</b>"), "$1")
            .replace(Regex("<i[^>]*>([^<]+)</i>"), "$1")
            .replace(Regex("<code[^>]*>([^<]+)</code>"), "$1")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }

    /**
     * Fetches updates from Telegram Bot API for group.
     */
    suspend fun fetchBotGroupUpdates(
        botToken: String,
        groupChatId: String?,
        sourceId: Long = 0,
        sourceTitle: String = ""
    ): Result<List<GhostMessage>> = withContext(Dispatchers.IO) {
        if (botToken.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        try {
            val url = "https://api.telegram.org/bot$botToken/getUpdates?limit=50&allowed_updates=[\"message\"]"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.success(emptyList())
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            if (!json.optBoolean("ok", false)) {
                return@withContext Result.success(emptyList())
            }

            val resultArray = json.optJSONArray("result") ?: return@withContext Result.success(emptyList())
            val messages = mutableListOf<GhostMessage>()

            val displayGroupTitle = if (sourceTitle.isNotBlank()) sourceTitle else "Core Group"

            for (i in 0 until resultArray.length()) {
                val updateObj = resultArray.getJSONObject(i)
                val msgObj = updateObj.optJSONObject("message") ?: continue
                val chatObj = msgObj.optJSONObject("chat")
                val chatId = chatObj?.optLong("id")?.toString() ?: ""

                if (!groupChatId.isNullOrBlank() && chatId != groupChatId.trim()) {
                    continue
                }

                val msgId = msgObj.optLong("message_id")
                val text = msgObj.optString("text", "")
                if (text.isBlank()) continue

                val fromObj = msgObj.optJSONObject("from")
                val senderFirst = fromObj?.optString("first_name", "Member") ?: "Member"
                val senderLast = fromObj?.optString("last_name", "") ?: ""
                val senderUser = fromObj?.optString("username", "") ?: ""
                val fullName = if (senderLast.isNotBlank()) "$senderFirst $senderLast" else senderFirst

                val dateSec = msgObj.optLong("date", System.currentTimeMillis() / 1000)

                messages.add(
                    GhostMessage(
                        id = msgId,
                        sourceId = sourceId,
                        sourceTitle = displayGroupTitle,
                        chatType = ChatSourceType.GROUP,
                        telegramMsgId = msgId,
                        senderName = fullName,
                        senderHandle = if (senderUser.isNotBlank()) "@$senderUser" else fullName,
                        text = text,
                        timestamp = dateSec * 1000,
                        isPinned = false,
                        views = null,
                        isBookmarked = false,
                        isUrgent = text.contains("urgent", ignoreCase = true) ||
                                text.contains("deadline", ignoreCase = true) ||
                                text.contains("alert", ignoreCase = true)
                    )
                )
            }

            Result.success(messages.reversed())
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    /**
     * Tests a Telegram Bot Token by calling getMe.
     */
    suspend fun testBotConnection(botToken: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim()
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Bot Token cannot be blank"))
        }

        try {
            val url = "https://api.telegram.org/bot$cleanToken/getMe"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)

            if (json.optBoolean("ok", false)) {
                val resultObj = json.optJSONObject("result")
                val botUser = resultObj?.optString("username", "bot") ?: "bot"
                val botName = resultObj?.optString("first_name", "Telegram Bot") ?: "Telegram Bot"
                Result.success("$botName (@$botUser)")
            } else {
                val desc = json.optString("description", "Invalid Bot Token")
                Result.failure(Exception(desc))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends a stealth response to the group via Bot API without revealing the user's personal presence.
     */
    suspend fun sendStealthGroupMessage(botToken: String, chatId: String, text: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Bot Token and Group Chat ID are required for live sending."))
        }

        try {
            val jsonBody = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
            }.toString()

            val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("https://api.telegram.org/bot$botToken/sendMessage")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val json = JSONObject(responseBody)
            val ok = json.optBoolean("ok", false)

            if (ok) {
                Result.success(true)
            } else {
                Result.failure(Exception(json.optString("description", "Failed to send message")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCuratedChannelPosts(sourceId: Long = 1, sourceTitle: String = "Engineering Pulse"): List<GhostMessage> {
        val now = System.currentTimeMillis()
        val title = if (sourceTitle.isNotBlank()) sourceTitle else "Engineering Pulse"
        return listOf(
            GhostMessage(
                id = 101 + sourceId * 10,
                sourceId = sourceId,
                sourceTitle = title,
                chatType = ChatSourceType.CHANNEL,
                telegramMsgId = 101,
                senderName = title,
                senderHandle = "@$title".lowercase().replace(" ", "_"),
                text = "🚨 #urgent Architecture Directive: v3.4 Migration Window is set for Saturday 02:00 UTC. Zero downtime expected. Please verify all staging builds by Friday 18:00.",
                timestamp = now - 15 * 60 * 1000,
                isPinned = true,
                views = "2.4K",
                isBookmarked = false,
                isUrgent = true
            ),
            GhostMessage(
                id = 102 + sourceId * 10,
                sourceId = sourceId,
                sourceTitle = title,
                chatType = ChatSourceType.CHANNEL,
                telegramMsgId = 102,
                senderName = title,
                senderHandle = "@$title".lowercase().replace(" ", "_"),
                text = "📌 Core Repository Access policy has been updated. Two-factor authentication hardware keys are now mandatory for all production branches.",
                timestamp = now - 2 * 3600 * 1000,
                isPinned = true,
                views = "3.1K",
                isBookmarked = false,
                isUrgent = false
            ),
            GhostMessage(
                id = 103 + sourceId * 10,
                sourceId = sourceId,
                sourceTitle = title,
                chatType = ChatSourceType.CHANNEL,
                telegramMsgId = 103,
                senderName = title,
                senderHandle = "@$title".lowercase().replace(" ", "_"),
                text = "Weekly Digest: 14 PRs merged into master. Test coverage increased to 89.4%. Benchmark latency reduced by 22ms on mobile clients.",
                timestamp = now - 14 * 3600 * 1000,
                isPinned = false,
                views = "1.8K",
                isBookmarked = false,
                isUrgent = false
            ),
            GhostMessage(
                id = 104 + sourceId * 10,
                sourceId = sourceId,
                sourceTitle = title,
                chatType = ChatSourceType.CHANNEL,
                telegramMsgId = 104,
                senderName = title,
                senderHandle = "@$title".lowercase().replace(" ", "_"),
                text = "⚠️ Notice: Cloud SQL replica sync latency spike resolved. All read-replicas are operating within normal SLO tolerances (<5ms).",
                timestamp = now - 28 * 3600 * 1000,
                isPinned = false,
                views = "2.9K",
                isBookmarked = false,
                isUrgent = false
            )
        )
    }

    fun getCuratedGroupPosts(sourceId: Long = 2, sourceTitle: String = "Core Dev Squad"): List<GhostMessage> {
        val now = System.currentTimeMillis()
        val title = if (sourceTitle.isNotBlank()) sourceTitle else "Core Dev Squad"
        return listOf(
            GhostMessage(
                id = 201 + sourceId * 10,
                sourceId = sourceId,
                sourceTitle = title,
                chatType = ChatSourceType.GROUP,
                telegramMsgId = 201,
                senderName = "Elena Vance (Tech Lead)",
                senderHandle = "@elena_v",
                text = "Team reminder: Keep discussion focused today. Focus sprint in progress. If you need urgent input on the database schema, ping me with #urgent.",
                timestamp = now - 8 * 60 * 1000,
                isPinned = true,
                views = null,
                isBookmarked = false,
                isUrgent = false
            ),
            GhostMessage(
                id = 202 + sourceId * 10,
                sourceId = sourceId,
                sourceTitle = title,
                chatType = ChatSourceType.GROUP,
                telegramMsgId = 202,
                senderName = "Marcus Rivera",
                senderHandle = "@mrivera",
                text = "🚨 #urgent The staging auth endpoints are throwing 401s for token exchange. Looking into the OAuth callback secret rotation right now.",
                timestamp = now - 35 * 60 * 1000,
                isPinned = false,
                views = null,
                isBookmarked = false,
                isUrgent = true
            ),
            GhostMessage(
                id = 203 + sourceId * 10,
                sourceId = sourceId,
                sourceTitle = title,
                chatType = ChatSourceType.GROUP,
                telegramMsgId = 203,
                senderName = "Sophia Lin",
                senderHandle = "@sophialin",
                text = "Auth issue resolved, it was an expired staging cert. Pull latest commit `b8f41` and rebuild.",
                timestamp = now - 22 * 60 * 1000,
                isPinned = false,
                views = null,
                isBookmarked = false,
                isUrgent = false
            ),
            GhostMessage(
                id = 204 + sourceId * 10,
                sourceId = sourceId,
                sourceTitle = title,
                chatType = ChatSourceType.GROUP,
                telegramMsgId = 204,
                senderName = "David Kim",
                senderHandle = "@dkim",
                text = "Heads up: Friday release deadline is strict at 16:00 CET. All code freezes go into effect Thursday midnight.",
                timestamp = now - 4 * 3600 * 1000,
                isPinned = false,
                views = null,
                isBookmarked = false,
                isUrgent = true
            )
        )
    }
}
