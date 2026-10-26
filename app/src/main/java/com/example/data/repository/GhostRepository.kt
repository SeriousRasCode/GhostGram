package com.example.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.data.local.FocusSourceEntity
import com.example.data.local.GhostDatabase
import com.example.data.local.GhostMessageEntity
import com.example.data.local.GhostPreferences
import com.example.data.local.MusicTrackEntity
import com.example.data.model.ChatSourceType
import com.example.data.model.DiscoveredChat
import com.example.data.model.FocusSource
import com.example.data.model.ForumTopicSummary
import com.example.data.model.TelegramUserProfile
import com.example.data.model.GhostMessage
import com.example.data.model.SourceConfig
import com.example.data.model.UserAccountSession
import com.example.data.model.ChatHistoryDirection
import com.example.data.remote.TelegramGhostClient
import com.example.data.remote.TelegramUserClient
import com.example.data.remote.TelegramHistoryRequests
import com.example.data.remote.TelegramHistoryLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.collect
import java.util.concurrent.atomic.AtomicInteger
import org.drinkless.tdlib.TdApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.supervisorScope

class GhostRepository(
    private val database: GhostDatabase,
    private val preferences: GhostPreferences,
    private val remoteClient: TelegramGhostClient,
    private val userClient: TelegramUserClient
) {
    companion object {
        private val instances = mutableMapOf<String, GhostRepository>()
        fun getInstance(context: android.content.Context, accountId: String): GhostRepository = synchronized(this) {
            instances.getOrPut(accountId) {
                GhostRepository(GhostDatabase.getDatabase(context.applicationContext, accountId),
                    GhostPreferences(context.applicationContext, accountId), TelegramGhostClient(),
                    TelegramUserClient.getInstance(context.applicationContext, accountId))
            }
        }
    }
    private val messageDao = database.ghostMessageDao()
    private val sourceDao = database.focusSourceDao()
    private val musicTrackDao = database.musicTrackDao()
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    fun close() = updateScope.cancel()

    private val reactionRefreshGate = Semaphore(2)
    private val reactionRefreshTimes = android.util.LruCache<String, Long>(500)

    /** Empty-filter search reaches Telegram without sending read acknowledgements. */
    suspend fun refreshMessageReactions(message: GhostMessage) = reactionRefreshGate.withPermit {
        if (message.chatType != ChatSourceType.CHANNEL || !getUserSession().isLoggedIn) return@withPermit
        val key = "${message.sourceId}:${message.telegramMsgId}"
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(reactionRefreshTimes) {
            val previous = reactionRefreshTimes.get(key)
            if (previous != null && now - previous < 60_000) return@withPermit
            reactionRefreshTimes.put(key, now)
        }
        try {
            val chatId = message.telegramChatId.takeIf { it != 0L } ?: return@withPermit
            val result = userClient.execute(TdApi.SearchChatMessages(chatId, null, "", null,
                message.telegramMsgId, 0, 1, TdApi.SearchMessagesFilterEmpty()))
            result.messages.firstOrNull { it.id == message.telegramMsgId }?.let { fresh ->
                val info = fresh.interactionInfo ?: return@let
                userClient.rememberInteractions(fresh)
                messageDao.updateInteractions(message.sourceId, fresh.id,
                    userClient.formatReactions(info.reactions), info.viewCount.takeIf { it > 0 }?.toString(),
                    info.replyInfo?.replyCount ?: -1)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            synchronized(reactionRefreshTimes) { reactionRefreshTimes.remove(key) }
            throw cancelled
        } catch (error: Exception) {
            Log.w("GhostRepository", "Reaction refresh unavailable", error)
        }
    }

    suspend fun getReactionUsers(message: GhostMessage, reactionKey: String) = userClient.getReactionUsers(message, reactionKey)

    init {
        updateScope.launch {
            val chatIds = sourceDao.getAllSources().map { sources ->
                sources.mapNotNull { source -> source.telegramChatId.takeIf { it != 0L }
                    ?: source.handleOrId.toLongOrNull() }.toSet()
            }.distinctUntilChanged()
            combine(chatIds, userClient.authStateFlow, userClient.connectionStateFlow) { ids, auth, connection ->
                Triple(ids, auth is TdApi.AuthorizationStateReady, connection is TdApi.ConnectionStateReady)
            }.distinctUntilChanged().collectLatest { (ids, authorized, connected) ->
                if (!authorized) userClient.resetOpenChats()
                else if (connected) {
                    while (true) {
                        try { userClient.setOpenChats("feed", ids); break }
                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (error: Exception) { Log.w("GhostRepository", "Chat subscriptions will retry", error) }
                        delay(5_000)
                    }
                }
            }
        }
        updateScope.launch {
            userClient.updates.collect { update ->
                try {
                    when (update) {
                        is TdApi.UpdateNewMessage -> persistLiveMessage(update.message)
                        // Closed channels may only announce a new last message.
                        is TdApi.UpdateChatLastMessage -> update.lastMessage?.let { persistLiveMessage(it) }
                        is TdApi.UpdateMessageContent -> reloadMessage(update.chatId, update.messageId)
                        is TdApi.UpdateMessageInteractionInfo -> sourceDao.findByTelegramChatId(update.chatId)?.let { sourceId ->
                            val info = update.interactionInfo
                            messageDao.updateInteractions(sourceId, update.messageId,
                                userClient.formatReactions(info?.reactions, resolveCustomEmoji = false), info?.viewCount?.takeIf { it > 0 }?.toString(),
                                info?.replyInfo?.replyCount ?: -1)
                        }
                        is TdApi.UpdateDeleteMessages -> {
                            if (update.isPermanent) sourceDao.findByTelegramChatId(update.chatId)?.let {
                                messageDao.deleteMessages(it, update.messageIds.toList())
                            }
                        }
                        is TdApi.UpdateFile -> if (update.file.local.isDownloadingCompleted) {
                            messageDao.updatePhoto(update.file.id, update.file.local.path)
                            messageDao.updateAttachment(update.file.id, update.file.local.path)
                            messageDao.updateSticker(update.file.id, update.file.local.path)
                            messageDao.updateStickerThumbnail(update.file.id, update.file.local.path)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("GhostRepository", "Could not apply Telegram update", e)
                }
            }
        }
        updateScope.launch {
            // Recover on startup, reconnect and returning to the foreground, even with alerts off.
            combine(userClient.authStateFlow, userClient.connectionStateFlow, userClient.recoveryVersion) { auth, connection, recovery ->
                (auth is TdApi.AuthorizationStateReady && connection is TdApi.ConnectionStateReady) to recovery
            }.distinctUntilChanged().collectLatest { (ready, _) ->
                if (ready) {
                    var retryMs = 1_000L
                    while (true) {
                        try {
                            refreshAllSources()
                            break
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (error: Exception) { Log.w("GhostRepository", "Catch-up will retry", error) }
                        delay(retryMs)
                        retryMs = (retryMs * 2).coerceAtMost(30_000)
                    }
                }
            }
        }
        updateScope.launch {
            // Public channel previews have no TDLib update stream. Poll them while
            // signed out so new posts appear without manual refresh.
            while (true) {
                delay(30_000)
                if (!userClient.isAuthorized()) {
                    try {
                        sourceDao.getAllSources().first().map { it.toDomain() }
                            .filter { it.type == ChatSourceType.CHANNEL }
                            .forEach { refreshSource(it, 0).getOrThrow() }
                    }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (error: Exception) { Log.w("GhostRepository", "Public source polling will retry", error) }
                }
            }
        }
    }

    private suspend fun reloadMessage(chatId: Long, messageId: Long) {
        if (sourceDao.findByTelegramChatId(chatId) != null) {
            persistLiveMessage(userClient.execute(TdApi.GetMessageLocally(chatId, messageId)))
        }
    }

    private suspend fun persistLiveMessage(message: TdApi.Message) {
        val source = sourceDao.findSourceByTelegramChatId(message.chatId) ?: return
        val previous = messageDao.find(source.id, message.id)
        val mapped = userClient.mapToGhostMessage(message, source.id, source.title, getUserSession().userId)
        messageDao.upsertPreservingBookmark(GhostMessageEntity.fromDomain(mapped.copy(
            isBookmarked = previous?.isBookmarked ?: false,
            sourceAvatarPath = mapped.sourceAvatarPath ?: source.avatarPath
        )))
        sourceDao.updateLastMessage(source.id, mapped.text.take(60), mapped.timestamp)
    }

    suspend fun getComments(chatId: Long, messageId: Long, fromMessageId: Long = 0): List<GhostMessage> =
        userClient.getComments(chatId, messageId, fromMessageId)

    suspend fun findNotificationSource(chatId: Long): FocusSource? = withContext(Dispatchers.IO) {
        sourceDao.findSourceByTelegramChatId(chatId)?.toDomain()
            ?: sourceDao.getAllSources().first().firstOrNull { it.handleOrId.toLongOrNull() == chatId }?.toDomain()
    }

    suspend fun loadHistoryWindow(source: FocusSource, direction: ChatHistoryDirection, anchorId: Long = 0): List<GhostMessage> = withContext(Dispatchers.IO) {
        val chatId = if (source.id == -1L) getUserSession().userId else source.telegramChatId.takeIf { it != 0L }
            ?: source.handleOrId.toLongOrNull()
            ?: userClient.execute(TdApi.SearchPublicChat(source.handleOrId.removePrefix("@"))).id
        require(chatId != 0L) { "This chat isn't available" }
        if (source.id != -1L && source.telegramChatId != chatId) sourceDao.updateTelegramChatId(source.id, chatId)
        var target: TdApi.Message? = null
        val query = when (direction) {
            ChatHistoryDirection.FIRST -> {
                val first = userClient.execute(TelegramHistoryRequests.first(chatId)).messages.filterNotNull().minByOrNull { it.id }
                    ?: throw IllegalStateException("No accessible messages in this chat")
                target = first
                TelegramHistoryRequests.around(chatId, first.id)
            }
            ChatHistoryDirection.AROUND -> {
                target = userClient.execute(TdApi.GetMessage(chatId, anchorId))
                TelegramHistoryRequests.around(chatId, anchorId)
            }
            ChatHistoryDirection.LATEST -> TelegramHistoryRequests.latest(chatId)
            ChatHistoryDirection.OLDER -> TelegramHistoryRequests.older(chatId, anchorId)
            ChatHistoryDirection.NEWER -> TelegramHistoryRequests.newer(chatId, anchorId)
        }
        val raw = if (direction == ChatHistoryDirection.LATEST || direction == ChatHistoryDirection.OLDER) {
            val messages = mutableListOf<TdApi.Message>()
            TelegramHistoryLoader.load(chatId, if (direction == ChatHistoryDirection.OLDER) anchorId else 0,
                minimumMessages = 50, fetch = { userClient.execute(it) }, publish = { messages.addAll(it) })
            messages
        } else userClient.execute(query).messages.filterNotNull() + listOfNotNull(target)
        val mapped = raw.distinctBy { it.id }.sortedByDescending { it.id }.map {
            userClient.mapToGhostMessage(it, source.id, source.title, getUserSession().userId)
        }
        messageDao.upsertAllPreservingBookmarks(mapped.map { message ->
            GhostMessageEntity.fromDomain(message.copy(
                isBookmarked = messageDao.find(source.id, message.id)?.isBookmarked ?: message.isBookmarked,
                sourceAvatarPath = message.sourceAvatarPath ?: source.avatarPath))
        })
        mapped
    }

    suspend fun setChatOpen(source: FocusSource, open: Boolean) {
        val id = if (source.id == -1L) getUserSession().userId else source.telegramChatId.takeIf { it != 0L }
            ?: source.handleOrId.toLongOrNull() ?: return
        if (id == 0L || !userClient.isAuthorized()) return
        try {
            userClient.setOpenChats("detail:$id", if (open) setOf(id) else emptySet())
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Log.w("GhostRepository", "Could not update open chat", e) }
    }

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
        val phone = phoneNumber.filter(Char::isDigit)
        val existing = preferences.accountRegistry.accounts.value.firstOrNull {
            it.id != preferences.accountId && it.session.phoneNumber.filter(Char::isDigit) == phone && it.session.userId != 0L
        }
        if (existing != null) return Result.failure(IllegalArgumentException("This account is already added. Switch to it from the account menu."))
        val current = preferences.loadUserSession()
        if (current.userId != 0L && current.phoneNumber.filter(Char::isDigit) != phone) {
            return Result.failure(IllegalArgumentException("Use Add account to sign in with another phone number."))
        }
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

    suspend fun logoutUser(): Result<Unit> {
        val result = userClient.logout()
        if (result.isSuccess) preferences.logoutUser()
        return result
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

    suspend fun getUserProfile(userId: Long): Result<TelegramUserProfile> = userClient.getUserProfile(userId)

    suspend fun getPrivateChatUserId(chatId: Long): Result<Long> = userClient.getPrivateChatUserId(chatId)

    suspend fun getForumTopics(source: FocusSource): List<ForumTopicSummary> = withContext(Dispatchers.IO) {
        val chatId = source.telegramChatId.takeIf { it != 0L }
            ?: source.handleOrId.toLongOrNull()
            ?: userClient.execute(TdApi.SearchPublicChat(source.handleOrId.removePrefix("@"))).id
        val topics = userClient.execute(TdApi.GetForumTopics(chatId, "", 0, 0, 0, 100)).topics
        val myUserId = getUserSession().userId
        topics.mapNotNull { topic ->
            val info = topic.info ?: return@mapNotNull null
            val preview = topic.lastMessage?.let {
                userClient.mapToGhostMessage(it, source.id, source.title, myUserId).text
            }
            ForumTopicSummary(info.forumTopicId, info.name, topic.unreadCount, preview, info.isGeneral)
        }
    }

    suspend fun getForumTopicMessages(source: FocusSource, topicId: Int): List<GhostMessage> = withContext(Dispatchers.IO) {
        val chatId = source.telegramChatId.takeIf { it != 0L }
            ?: source.handleOrId.toLongOrNull()
            ?: userClient.execute(TdApi.SearchPublicChat(source.handleOrId.removePrefix("@"))).id
        val myUserId = getUserSession().userId
        userClient.execute(TdApi.GetForumTopicHistory(chatId, topicId, 0, 0, 100)).messages
            .filterNotNull()
            .map { userClient.mapToGhostMessage(it, source.id, source.title, myUserId) }
            .sortedByDescending { it.telegramMsgId }
    }

    suspend fun importedChatIds(): Set<Long> = withContext(Dispatchers.IO) {
        sourceDao.getAllSources().first().map { it.telegramChatId }.filter { it != 0L }.toSet()
    }

    suspend fun isChatImported(chatId: Long): Boolean = withContext(Dispatchers.IO) {
        sourceDao.findByTelegramChatId(chatId) != null
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
        sourceDao.getSourceById(sourceId).first()?.telegramChatId?.takeIf { it != 0L }?.let { musicTrackDao.deleteForSource(it) }
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
        }.flowOn(Dispatchers.Default)
    }

    fun getPersonalMessages(): Flow<List<GhostMessage>> {
        return messageDao.getPersonalMessages().map { entities ->
            entities.map { it.toDomain() }
        }.flowOn(Dispatchers.Default)
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
        }.flowOn(Dispatchers.Default)
    }

    suspend fun refreshSource(source: FocusSource, fromMessageId: Long = 0): Result<Unit> = withContext(Dispatchers.IO) {
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
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                Log.w("GhostRepository", "Could not resolve handle ${source.handleOrId}: ${e.message}")
                throw e
            }
        }

        if (isLoggedIn) {
            check(userClient.isAuthorized()) { "Telegram is still connecting" }
            val chatId = if (source.id == -1L || source.type == ChatSourceType.SAVED_MESSAGES) getUserSession().userId else telegramChatId
            require(chatId != 0L) { "This chat isn't available" }
            val previousNewest = if (fromMessageId == 0L) messageDao.newestMessageId(source.id) ?: 0L else 0L
            TelegramHistoryLoader.load(chatId, fromMessageId, stopAtMessageId = previousNewest,
                fetch = { userClient.execute(it) }, publish = { page ->
                    // Publish small batches so one slow lookup cannot hide an entire source.
                    page.chunked(10).forEach { batch ->
                        val messages = coroutineScope {
                            batch.map { raw -> async { userClient.mapToGhostMessage(raw, source.id, source.title, getUserSession().userId) } }.awaitAll()
                        }
                        messageDao.upsertAllPreservingBookmarks(messages.map { msg -> GhostMessageEntity.fromDomain(msg.copy(
                            isUrgent = msg.isUrgent || keywords.any { msg.text.contains(it, ignoreCase = true) },
                            sourceAvatarPath = msg.sourceAvatarPath ?: source.avatarPath)) })
                        messages.maxByOrNull { it.timestamp }?.let { newest ->
                            sourceDao.updateLastMessage(source.id, newest.text.take(60), newest.timestamp)
                        }
                    }
                })
            updateScope.launch {
                try {
                    val file = userClient.execute(TdApi.GetChat(chatId)).photo?.small ?: return@launch
                    val downloaded = if (file.local.isDownloadingCompleted) file
                        else userClient.execute(TdApi.DownloadFile(file.id, 1, 0, 0, true))
                    downloaded.local.path.takeIf { downloaded.local.isDownloadingCompleted && it.isNotBlank() }
                        ?.let { sourceDao.updateAvatar(source.id, it) }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Exception) { Log.w("GhostRepository", "Source avatar unavailable", error) }
            }
            return@withContext Result.success(Unit)
        }

        // Public previews are a signed-out fallback, never a replacement for an authenticated error.
        val messages = when {
            fromMessageId != 0L -> emptyList()
            source.type == ChatSourceType.CHANNEL -> remoteClient.fetchPublicChannel(source.handleOrId, source.id, source.title).getOrThrow()
            config.botToken.isNotBlank() -> remoteClient.fetchBotGroupUpdates(config.botToken, source.handleOrId, source.id, source.title).getOrThrow()
            else -> emptyList()
        }
        messageDao.upsertAllPreservingBookmarks(messages.map { msg -> GhostMessageEntity.fromDomain(msg.copy(
            sourceId = source.id, sourceTitle = source.title,
            isUrgent = msg.isUrgent || keywords.any { msg.text.contains(it, ignoreCase = true) },
            sourceAvatarPath = msg.sourceAvatarPath ?: source.avatarPath)) })
        messages.maxByOrNull { it.timestamp }?.let { sourceDao.updateLastMessage(source.id, it.text.take(60), it.timestamp) }
        Result.success(Unit)
    }
    suspend fun refreshAllSources() = refreshMutex.withLock {
        val sources = sourceDao.getAllSources().first().map { it.toDomain() }
        val gate = Semaphore(4)
        val failures = supervisorScope {
            sources.map { source -> async {
                gate.withPermit {
                    try { refreshSource(source, 0); null }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (error: Exception) { error }
                }
            } }.awaitAll()
        }
        failures.filterNotNull().firstOrNull()?.let { throw it }
        // Legacy repair is low priority and must not hold up the latest messages.
        updateScope.launch { repairLegacyStickers() }
        Unit
    }

    private suspend fun repairLegacyStickers() = withContext(Dispatchers.IO) {
        if (!userClient.isAuthorized()) return@withContext
        val sources = sourceDao.getAllSources().first().associateBy { it.id }
        messageDao.legacyStickerMessages().forEach { cached ->
            if (!userClient.isAuthorized()) return@withContext
            val source = sources[cached.sourceId] ?: return@forEach
            val chatId = source.telegramChatId.takeIf { it != 0L } ?: source.handleOrId.toLongOrNull() ?: return@forEach
            try {
                val original = userClient.execute(TdApi.GetMessage(chatId, cached.telegramMsgId))
                val sticker = (original.content as? TdApi.MessageSticker)?.sticker ?: return@forEach
                val format = when (sticker.format) {
                    is TdApi.StickerFormatTgs -> "tgs"
                    is TdApi.StickerFormatWebm -> "webm"
                    else -> "webp"
                }
                messageDao.updateStickerMetadata(cached.sourceId, cached.telegramMsgId, sticker.sticker.id, format, sticker.emoji,
                    sticker.sticker.local.path.takeIf { sticker.sticker.local.isDownloadingCompleted },
                    sticker.thumbnail?.file?.id ?: 0,
                    sticker.thumbnail?.file?.local?.takeIf { it.isDownloadingCompleted }?.path)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { Log.w("GhostRepository", "Could not recover cached sticker metadata", e) }
        }
    }

    suspend fun searchChatMessages(
        chatId: Long,
        query: String,
        filter: TdApi.SearchMessagesFilter = TdApi.SearchMessagesFilterEmpty(),
        fromMessageId: Long = 0,
        limit: Int = 50
    ): List<GhostMessage> = withContext(Dispatchers.IO) {
        val source = sourceDao.findSourceByTelegramChatId(chatId)
        userClient.searchChatMessages(chatId, query, filter, fromMessageId, limit).map { message ->
            message.copy(sourceId = source?.id ?: message.sourceId,
                sourceTitle = source?.title ?: message.sourceTitle)
        }
    }

    suspend fun getImportedMusicSources(): List<FocusSource> = sourceDao.getAllSources().first()
        .map { it.toDomain() }
        .filter { it.type in listOf(ChatSourceType.GROUP, ChatSourceType.CHANNEL) && it.telegramChatId != 0L }

    suspend fun getMusicLibrary(sourceChatId: Long, onProgress: (completed: Int, total: Int) -> Unit): List<GhostMessage> = coroutineScope {
        val importedChats = sourceDao.getAllSources().first()
            .filter { it.telegramChatId != 0L }
            .filter { it.type in listOf(ChatSourceType.GROUP.name, ChatSourceType.CHANNEL.name) }
            .filter { it.telegramChatId == sourceChatId }
            .distinctBy { it.telegramChatId }
        if (importedChats.isEmpty()) {
            onProgress(0, 0)
            return@coroutineScope emptyList()
        }

        val completedChats = AtomicInteger(0)
        val gate = Semaphore(3)
        importedChats.map { source -> async(Dispatchers.IO) {
            gate.withPermit {
                try {
                    val tracks = mutableListOf<GhostMessage>()
                    var cursor = 0L
                    while (true) {
                        val page = searchChatMessages(
                            chatId = source.telegramChatId,
                            query = "",
                            filter = TdApi.SearchMessagesFilterAudio(),
                            fromMessageId = cursor,
                            limit = 100
                        )
                        val olderMessages = page.filter { cursor == 0L || it.telegramMsgId < cursor }
                        if (olderMessages.isEmpty()) break
                        tracks += olderMessages
                        cursor = olderMessages.minOf { it.telegramMsgId }
                    }
                    tracks
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("GhostRepository", "Could not scan music in ${source.title}", error)
                    throw error
                } finally {
                    onProgress(completedChats.incrementAndGet(), importedChats.size)
                }
            }
        } }.awaitAll().flatten()
            .filter { it.fileAttachment?.mimeType?.startsWith("audio/") == true }
            .filterNot { it.fileAttachment?.fileName?.startsWith("VoiceNote_", ignoreCase = true) == true }
            .distinctBy { it.telegramChatId to it.telegramMsgId }
            .sortedByDescending { it.timestamp }
    }

    suspend fun getCachedMusicLibrary(sourceChatId: Long): List<GhostMessage> = withContext(Dispatchers.IO) {
        musicTrackDao.getForSource(sourceChatId).map { it.toDomain() }
            .filterNot { it.fileAttachment?.fileName?.startsWith("VoiceNote_", ignoreCase = true) == true }
    }

    fun observeMusicLibrary(sourceChatId: Long): Flow<List<GhostMessage>> =
        musicTrackDao.observeForSource(sourceChatId).map { tracks ->
            tracks.map { it.toDomain() }
                .filterNot { it.fileAttachment?.fileName?.startsWith("VoiceNote_", ignoreCase = true) == true }
        }

    suspend fun setMusicFavorite(track: GhostMessage, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        musicTrackDao.setFavorite(track.telegramChatId, track.telegramMsgId, isFavorite)
    }

    suspend fun recordMusicListeningTime(chatId: Long, messageId: Long, listenedMs: Long) = withContext(Dispatchers.IO) {
        if (listenedMs > 0L) musicTrackDao.recordListeningTime(chatId, messageId, listenedMs, System.currentTimeMillis())
    }

    suspend fun saveMusicLibrary(sourceChatId: Long, tracks: List<GhostMessage>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val existing = musicTrackDao.getForSource(sourceChatId)
                .associateBy { it.telegramChatId to it.telegramMsgId }
            musicTrackDao.deleteForSource(sourceChatId)
            if (tracks.isNotEmpty()) {
                musicTrackDao.insertAll(tracks.map { track ->
                    val fresh = MusicTrackEntity.fromDomain(sourceChatId, track)
                    val previous = existing[track.telegramChatId to track.telegramMsgId]
                    if (previous == null) fresh else fresh.copy(
                        isFavorite = previous.isFavorite,
                        playCount = previous.playCount,
                        lastPlayedAt = previous.lastPlayedAt,
                        addedAt = previous.addedAt,
                        listenedMs = previous.listenedMs
                    )
                })
            }
        }
    }

    suspend fun searchFeed(query: String, previous: List<GhostMessage> = emptyList()): List<GhostMessage> = coroutineScope {
        val config = getConfig()
        val gate = Semaphore(4)
        sourceDao.getAllSources().first().filter {
            it.type in listOf(ChatSourceType.GROUP.name, ChatSourceType.CHANNEL.name) || config.includePersonalInFeed
        }.map { source -> async {
            gate.withPermit {
                val chatId = source.telegramChatId.takeIf { it != 0L } ?: source.handleOrId.toLongOrNull() ?: return@withPermit emptyList()
                val cursor = previous.filter { it.sourceId == source.id }.minOfOrNull { it.telegramMsgId } ?: 0L
                try { searchChatMessages(chatId, query, fromMessageId = cursor) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { emptyList() }
            }
        } }.awaitAll().flatten().sortedByDescending { it.timestamp }
    }

    suspend fun getChatMedia(chatId: Long, filter: org.drinkless.tdlib.TdApi.SearchMessagesFilter): List<GhostMessage> {
        return userClient.searchChatMessages(chatId, "", filter)
    }

    suspend fun toggleBookmark(message: GhostMessage) = withContext(Dispatchers.IO) {
        val existing = messageDao.find(message.sourceId, message.telegramMsgId)
        if (existing == null) messageDao.insert(GhostMessageEntity.fromDomain(message.copy(isBookmarked = !message.isBookmarked)))
        else messageDao.updateBookmark(message.sourceId, message.telegramMsgId, !message.isBookmarked)
    }

    suspend fun downloadFile(fileId: Int, onProgress: (Float) -> Unit = {}): Result<String> {
        val result = userClient.downloadFile(fileId, onProgress)
        result.getOrNull()?.let { path -> withContext(Dispatchers.IO) {
            messageDao.updatePhoto(fileId, path)
            messageDao.updateAttachment(fileId, path)
            messageDao.updateSticker(fileId, path)
            messageDao.updateStickerThumbnail(fileId, path)
        } }
        return result
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
