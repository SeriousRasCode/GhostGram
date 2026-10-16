package com.example.data.repository

import android.util.Log
import com.example.data.local.FocusSourceEntity
import com.example.data.local.GhostDatabase
import com.example.data.local.GhostMessageEntity
import com.example.data.local.GhostPreferences
import com.example.data.model.ChatSourceType
import com.example.data.model.DiscoveredChat
import com.example.data.model.FocusSource
import com.example.data.model.GhostMessage
import com.example.data.model.SourceConfig
import com.example.data.model.UserAccountSession
import com.example.data.remote.TelegramGhostClient
import com.example.data.remote.TelegramUserClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class GhostRepository(
    private val database: GhostDatabase,
    private val preferences: GhostPreferences,
    private val remoteClient: TelegramGhostClient,
    private val userClient: TelegramUserClient
) {
    private val messageDao = database.ghostMessageDao()
    private val sourceDao = database.focusSourceDao()

    val configFlow: Flow<SourceConfig> = preferences.configFlow
    val userSessionFlow: Flow<UserAccountSession> = preferences.userSessionFlow
    val authStateFlow = userClient.authStateFlow

    fun getConfig(): SourceConfig = preferences.loadConfig()

    fun getUserSession(): UserAccountSession = preferences.loadUserSession()

    fun saveConfig(config: SourceConfig) {
        preferences.saveConfig(config)
    }

    // Personal Telegram Account Authentication
    suspend fun sendLoginCode(phoneNumber: String): Result<String> {
        return userClient.sendLoginCode(phoneNumber)
    }

    suspend fun signInWithCode(
        phoneNumber: String,
        code: String,
        displayName: String = "",
        username: String = ""
    ): Result<UserAccountSession> {
        val result = userClient.signIn(phoneNumber, code, displayName, username)
        if (result.isSuccess) {
            val session = result.getOrNull()!!
            preferences.saveUserSession(session)
        }
        return result
    }

    suspend fun check2FAPassword(password: String, phoneNumber: String): Result<UserAccountSession> {
        val result = userClient.check2FAPassword(password, phoneNumber)
        if (result.isSuccess) {
            val session = result.getOrNull()!!
            preferences.saveUserSession(session)
        }
        return result
    }

    suspend fun refreshUserSession(): Result<UserAccountSession> {
        val result = userClient.fetchMe()
        if (result.isSuccess) {
            preferences.saveUserSession(result.getOrNull()!!)
        }
        return result
    }

    suspend fun logoutUser() {
        preferences.logoutUser()
        userClient.logout()
    }

    suspend fun resendCode(): Result<Unit> {
        return userClient.resendCode()
    }

    suspend fun fetchAccountDialogs(): Result<List<DiscoveredChat>> {
        return userClient.fetchAccountDialogs()
    }

    suspend fun searchAccountChats(query: String): Result<List<DiscoveredChat>> {
        return userClient.searchAccountChats(query)
    }

    // Source Management (User Added Groups and Channels)
    fun getAllSources(): Flow<List<FocusSource>> {
        return sourceDao.getAllSources().map { list -> list.map { it.toDomain() } }
    }

    fun getGroups(): Flow<List<FocusSource>> {
        return sourceDao.getSourcesByType(ChatSourceType.GROUP.name).map { list -> list.map { it.toDomain() } }
    }

    fun getChannels(): Flow<List<FocusSource>> {
        return sourceDao.getSourcesByType(ChatSourceType.CHANNEL.name).map { list -> list.map { it.toDomain() } }
    }

    fun getPersonalSources(): Flow<List<FocusSource>> {
        return sourceDao.getSourcesByType(ChatSourceType.PERSONAL.name).map { list -> list.map { it.toDomain() } }
    }

    suspend fun addSource(source: FocusSource): Long = withContext(Dispatchers.IO) {
        val entity = FocusSourceEntity.fromDomain(source)
        val id = sourceDao.insertSource(entity)
        val savedSource = source.copy(id = id)
        refreshSource(savedSource)
        id
    }

    suspend fun removeSource(sourceId: Long) = withContext(Dispatchers.IO) {
        sourceDao.deleteSource(sourceId)
        messageDao.clearBySource(sourceId)
    }

    // Unified Feed (Aggregates new things across all added sources!)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getAllFeedMessages(): Flow<List<GhostMessage>> {
        return preferences.configFlow.flatMapLatest { config: com.example.data.model.SourceConfig ->
            val flow = if (config.includePersonalInFeed) {
                messageDao.getUniversalFeedMessages()
            } else {
                messageDao.getFeedMessages()
            }
            
            flow.map { entities ->
                val keywords = parseKeywords(config.focusKeywords)
                entities.map { entity ->
                    val domain = entity.toDomain()
                    val hasKeyword = keywords.any { kw -> domain.text.contains(kw, ignoreCase = true) }
                    domain.copy(isUrgent = domain.isUrgent || hasKeyword)
                }
            }
        }
    }

    fun getPersonalMessages(): Flow<List<GhostMessage>> {
        return messageDao.getPersonalMessages().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    // Messages for a single specific selected group or channel
    fun getMessagesForSource(sourceId: Long): Flow<List<GhostMessage>> {
        return messageDao.getMessagesBySource(sourceId).map { entities ->
            val keywords = parseKeywords(preferences.loadConfig().focusKeywords)
            entities.map { entity ->
                val domain = entity.toDomain()
                val hasKeyword = keywords.any { kw -> domain.text.contains(kw, ignoreCase = true) }
                domain.copy(isUrgent = domain.isUrgent || hasKeyword)
            }
        }
    }

    suspend fun refreshSource(source: FocusSource, fromMessageId: Long = 0): Result<Unit> {
        val config = preferences.loadConfig()
        val keywords = parseKeywords(config.focusKeywords)
        val isLoggedIn = preferences.loadUserSession().isLoggedIn
        
        var telegramChatId = if (source.telegramChatId != 0L) {
            source.telegramChatId
        } else {
            source.handleOrId.toLongOrNull() ?: 0L
        }

        // If we still have no ID but we have a handle and are logged in, try to resolve it
        if (telegramChatId == 0L && source.handleOrId.isNotBlank() && isLoggedIn) {
            try {
                val chat = userClient.execute(org.drinkless.tdlib.TdApi.SearchPublicChat(source.handleOrId))
                telegramChatId = chat.id
                // Update the source in DB with the resolved ID for future use
                sourceDao.updateTelegramChatId(source.id, telegramChatId)
            } catch (e: Exception) {
                Log.w("GhostRepository", "Could not resolve handle ${source.handleOrId}: ${e.message}")
            }
        }

        // Ensure chat is loaded in TDLib cache if we have an ID
        if (telegramChatId != 0L && isLoggedIn) {
            try {
                userClient.execute(org.drinkless.tdlib.TdApi.GetChat(telegramChatId))
            } catch (e: Exception) {
                Log.w("GhostRepository", "Could not load chat info for $telegramChatId: ${e.message}")
            }
        }

        val msgs = if (source.id == -1L || source.type == ChatSourceType.SAVED_MESSAGES) {
            if (isLoggedIn) {
                userClient.getStealthClassGroupMessages(-1L, "Saved Messages", fromMessageId, limit = 300)
            } else {
                emptyList()
            }
        } else if (source.type == ChatSourceType.CHANNEL) {
            var results = if (isLoggedIn && telegramChatId != 0L) {
                userClient.getStealthChannelMessages(telegramChatId, source.title, fromMessageId)
            } else {
                emptyList()
            }

            if (results.isEmpty() && fromMessageId == 0L) {
                val publicResult = remoteClient.fetchPublicChannel(source.handleOrId, source.id, source.title)
                results = if (publicResult.isSuccess) publicResult.getOrNull() ?: emptyList() else emptyList()
            }
            results
        } else {
            var results = if (isLoggedIn && telegramChatId != 0L) {
                userClient.getStealthClassGroupMessages(telegramChatId, source.title, fromMessageId)
            } else {
                emptyList()
            }

            if (results.isEmpty() && fromMessageId == 0L && config.botToken.isNotBlank()) {
                val botRes = remoteClient.fetchBotGroupUpdates(config.botToken, source.handleOrId, source.id, source.title)
                results = if (botRes.isSuccess) botRes.getOrNull() ?: emptyList() else emptyList()
            }
            results
        }

        if (msgs.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val entities = msgs.map { msg ->
                    val hasKeyword = keywords.any { kw -> msg.text.contains(kw, ignoreCase = true) }
                    GhostMessageEntity.fromDomain(msg.copy(
                        isUrgent = msg.isUrgent || hasKeyword, 
                        sourceId = source.id, 
                        sourceTitle = source.title,
                        sourceAvatarPath = msg.sourceAvatarPath ?: source.avatarPath
                    ))
                }
                messageDao.insertAll(entities)
                sourceDao.updateLastMessage(source.id, msgs.first().text.take(60), msgs.first().timestamp)
                
                // Refresh Avatar if missing or as part of periodic refresh
                if (isLoggedIn && telegramChatId != 0L) {
                    try {
                        val chat = userClient.execute(org.drinkless.tdlib.TdApi.GetChat(telegramChatId))
                        chat.photo?.small?.let { file ->
                            if (file.local.canBeDownloaded && !file.local.isDownloadingCompleted) {
                                userClient.execute(org.drinkless.tdlib.TdApi.DownloadFile(file.id, 1, 0, 0, true))
                            }
                            // Re-fetch to get local path
                            val updatedFile = userClient.execute(org.drinkless.tdlib.TdApi.GetFile(file.id))
                            if (updatedFile.local.isDownloadingCompleted && updatedFile.local.path.isNotBlank()) {
                                sourceDao.updateAvatar(source.id, updatedFile.local.path)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GhostRepository", "Failed to refresh avatar for source ${source.id}: ${e.message}")
                    }
                }
            }
        }
        return Result.success(Unit)
    }

    suspend fun refreshAllSources() {
        val sources = sourceDao.getAllSources().first().map { it.toDomain() }
        sources.forEach { source ->
            refreshSource(source, 0)
        }
    }

    suspend fun searchChatMessages(chatId: Long, query: String): List<GhostMessage> {
        return userClient.searchChatMessages(chatId, query)
    }

    suspend fun getChatMedia(chatId: Long, filter: org.drinkless.tdlib.TdApi.SearchMessagesFilter): List<GhostMessage> {
        return userClient.searchChatMessages(chatId, "", filter)
    }

    suspend fun toggleBookmark(telegramMsgId: Long, isBookmarked: Boolean) = withContext(Dispatchers.IO) {
        messageDao.updateBookmark(telegramMsgId, !isBookmarked)
    }

    suspend fun downloadFile(fileId: Int): Result<String> {
        return userClient.downloadFile(fileId)
    }

    suspend fun sendStealthGroupMessage(source: FocusSource, text: String): Result<Boolean> {
        return Result.failure(Exception("Messaging disabled to protect presence stealth"))
    }
    suspend fun testBotConnection(token: String): Result<String> {
        return Result.success("Stealth Mode: Remote bot connections disabled")
    }

    fun getBookmarkedMessages(): Flow<List<GhostMessage>> {
        return messageDao.getBookmarkedMessages().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun saveNoteToSavedMessages(text: String): Long = 0L

    suspend fun seedInitialDataIfNeeded() {
        // Production: Do not seed mock data. Users will add their own real chats via the Picker.
    }

    private fun parseKeywords(raw: String): List<String> {
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}
