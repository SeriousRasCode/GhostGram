package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.GhostAccounts
import com.example.data.model.ChatSourceType
import com.example.data.model.DiscoveredChat
import com.example.data.model.FileAttachment
import com.example.data.model.GhostMessage
import com.example.data.model.UserAccountSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.TimeUnit

class TelegramUserClient private constructor(private val context: Context, val accountId: String) {
    companion object {
        private val instances = mutableMapOf<String, TelegramUserClient>()
        fun getInstance(context: Context, accountId: String = GhostAccounts.get(context).activeId.value): TelegramUserClient {
            require(GhostAccounts.isValidId(accountId))
            return synchronized(this) {
                instances.getOrPut(accountId) { TelegramUserClient(context.applicationContext, accountId) }
            }
        }
    }

    private var client: Client? = null
    private val authState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authStateFlow = authState.asStateFlow()

    private val currentPasswordHint = MutableStateFlow<String?>(null)
    
    fun isAuthorized(): Boolean = authState.value is TdApi.AuthorizationStateReady

    private val updateBus = TelegramUpdateBus()
    val updates = updateBus.updates
    val recoveryVersion = updateBus.recoveryVersion
    private val connectionState = MutableStateFlow<TdApi.ConnectionState?>(null)
    val connectionStateFlow = connectionState.asStateFlow()
    fun requestCatchUp() = updateBus.requestRecovery()
    private val chatSubscriptions = TelegramChatSubscriptions(
        open = { execute(TdApi.OpenChat(it)); Unit },
        close = { execute(TdApi.CloseChat(it)); Unit })

    suspend fun setOpenChats(owner: String, ids: Set<Long>) = chatSubscriptions.replace(owner, ids)
    suspend fun resetOpenChats() = chatSubscriptions.reset()
    private data class InteractionSnapshot(val info: TdApi.MessageInteractionInfo?)
    private val interactions = android.util.LruCache<String, InteractionSnapshot>(3000)

    fun rememberInteractions(message: TdApi.Message) {
        message.interactionInfo?.let { interactions.put("${message.chatId}:${message.id}", InteractionSnapshot(it)) }
    }
    private val customReactionEmoji = java.util.concurrent.ConcurrentHashMap<Long, String>()

    init {
        setupClient()
    }

    private fun setupClient() {
        try {
            // Set log level to see more details if needed
            Client.execute(TdApi.SetLogVerbosityLevel(1))
            
            client = Client.create({ update ->
                when (update) {
                    is TdApi.UpdateAuthorizationState -> {
                        val state = update.authorizationState
                        Log.d("TDLib", "Auth State Change: ${state.javaClass.simpleName}")
                        authState.value = state
                        if (state is TdApi.AuthorizationStateWaitPassword) {
                            currentPasswordHint.value = state.passwordHint
                        }
                        if (state is TdApi.AuthorizationStateReady) {
                            enforceOfflineStatus()
                        }
                        handleAuthAction(state)
                    }
                    else -> {
                        if (update is TdApi.UpdateConnectionState) connectionState.value = update.state
                        if (update is TdApi.UpdateMessageInteractionInfo) {
                            interactions.put("${update.chatId}:${update.messageId}", InteractionSnapshot(update.interactionInfo))
                        }
                        updateBus.offer(update)
                    }
                }
            }, { error ->
                Log.e("TDLib", "Default Error Handler: ${error.message}")
            }, { error ->
                Log.e("TDLib", "Exception Handler: ${error.message}")
            })
        } catch (e: Throwable) {
            Log.e("TDLib", "Failed to create TDLib Client: ${e.message}")
        }
    }

    private fun handleAuthAction(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val dbDir = java.io.File(context.filesDir, GhostAccounts.tdlibDirectory(accountId))
                if (!dbDir.exists()) dbDir.mkdirs()
                val fDir = java.io.File(dbDir, "files")
                if (!fDir.exists()) fDir.mkdirs()

                val apiIdStr = BuildConfig.TELEGRAM_API_ID.trim()
                val apiId = try { apiIdStr.toInt() } catch (e: Exception) { 0 }
                val apiHash = BuildConfig.TELEGRAM_API_HASH?.trim() ?: ""

                Log.d("TDLib", "Setting TDLIB Parameters with API_ID: $apiId")
                val setParams = TdApi.SetTdlibParameters(
                    false,          // use_test_dc
                    dbDir.absolutePath,
                    fDir.absolutePath,
                    ByteArray(0),   // database_encryption_key
                    true,           // use_file_database
                    true,           // use_chat_info_database
                    true,           // use_message_database
                    false,          // use_secret_chats
                    apiId,
                    apiHash,
                    "en",
                    "Android",
                    "1.0",
                    "1.0"
                )
                send(setParams)
            }
            else -> {
                Log.d("TDLib", "Authorization State: ${state.javaClass.simpleName}")
            }
        }
    }

    private fun send(query: TdApi.Function<out TdApi.Object>, callback: (TdApi.Object) -> Unit = {}) {
        client?.send(query) { res ->
            callback(res)
        }
    }

    fun requestBackgroundDownload(fileId: Int) { send(TdApi.DownloadFile(fileId, 1, 0, 0, false)) }

    suspend fun <T : TdApi.Object> execute(query: TdApi.Function<T>): T = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<T>()
        val c = client ?: throw Exception("Telegram client not initialized (check API ID/Hash)")
        
        Log.d("TDLib", "Executing: ${query.javaClass.simpleName}")
        c.send(query) { res ->
            if (res is TdApi.Error) {
                Log.e("TDLib", "Execution Error (${query.javaClass.simpleName}): ${res.message}")
                deferred.completeExceptionally(Exception(res.message))
            } else {
                @Suppress("UNCHECKED_CAST")
                deferred.complete(res as T)
            }
        }
        
        try {
            kotlinx.coroutines.withTimeout(20000) {
                deferred.await()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e("TDLib", "Execution Timeout: ${query.javaClass.simpleName}")
            throw Exception("Telegram request timed out: ${query.javaClass.simpleName}. Check your internet or API credentials.")
        }
    }

    suspend fun sendLoginCode(phoneNumber: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiId = try { BuildConfig.TELEGRAM_API_ID.toInt() } catch (e: Exception) { 0 }
            val apiHash = BuildConfig.TELEGRAM_API_HASH ?: ""
            if (apiId == 0 || apiHash.isBlank()) {
                throw Exception("TELEGRAM_API_ID or TELEGRAM_API_HASH is missing in Secrets panel.")
            }

            Log.d("TDLib", "sendLoginCode: Checking current state...")
            val currentState = authState.value
            
            if (currentState is TdApi.AuthorizationStateWaitCode || currentState is TdApi.AuthorizationStateWaitPassword) {
                Log.d("TDLib", "sendLoginCode: Already in a later state (${currentState.javaClass.simpleName}), skipping SetAuthenticationPhoneNumber")
                return@withContext Result.success("already_sent")
            }

            Log.d("TDLib", "sendLoginCode: Waiting for WaitPhoneNumber state...")
            try {
                kotlinx.coroutines.withTimeout(15000) {
                    authState.first { it is TdApi.AuthorizationStateWaitPhoneNumber }
                }
            } catch (e: Exception) {
                val current = authState.value?.javaClass?.simpleName ?: "null"
                throw Exception("TDLib not ready for login (Current state: $current). Please check if your API ID/Hash are correct in the Secrets panel.")
            }
            
            Log.d("TDLib", "sendLoginCode: Sending phone number $phoneNumber")
            val settings = TdApi.PhoneNumberAuthenticationSettings()
            execute(TdApi.SetAuthenticationPhoneNumber(phoneNumber, settings))
            Result.success("code_sent")
        } catch (e: Exception) {
            Log.e("TDLib", "sendLoginCode Error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun resendCode(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("TDLib", "resendCode: Requesting resend...")
            execute(TdApi.ResendAuthenticationCode())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("TDLib", "resendCode Error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun signIn(
        phoneNumber: String,
        code: String,
        displayName: String = "",
        username: String = ""
    ): Result<UserAccountSession> = withContext(Dispatchers.IO) {
        try {
            Log.d("TDLib", "signIn: Waiting for WaitCode state...")
            kotlinx.coroutines.withTimeout(15000) {
                authState.first { it is TdApi.AuthorizationStateWaitCode }
            }
            execute(TdApi.CheckAuthenticationCode(code))
            
            Log.d("TDLib", "signIn: Waiting for final auth state...")
            val finalState = kotlinx.coroutines.withTimeout(15000) {
                authState.first { 
                    it is TdApi.AuthorizationStateReady || it is TdApi.AuthorizationStateWaitPassword 
                }
            }
            
            if (finalState is TdApi.AuthorizationStateWaitPassword) {
                val hint = (finalState as? TdApi.AuthorizationStateWaitPassword)?.passwordHint
                return@withContext Result.failure(Exception("SESSION_PASSWORD_NEEDED|${hint ?: ""}"))
            }

            fetchMe()
        } catch (e: Exception) {
            Log.e("TDLib", "signIn Error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun check2FAPassword(password: String, phoneNumber: String): Result<UserAccountSession> = withContext(Dispatchers.IO) {
        try {
            Log.d("TDLib", "check2FAPassword: Waiting for WaitPassword state...")
            kotlinx.coroutines.withTimeout(15000) {
                authState.first { it is TdApi.AuthorizationStateWaitPassword }
            }
            execute(TdApi.CheckAuthenticationPassword(password))
            
            Log.d("TDLib", "check2FAPassword: Waiting for Ready state...")
            kotlinx.coroutines.withTimeout(15000) {
                authState.first { it is TdApi.AuthorizationStateReady }
            }
            fetchMe()
        } catch (e: Exception) {
            Log.e("TDLib", "check2FAPassword Error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchMe(): Result<UserAccountSession> = withContext(Dispatchers.IO) {
        if (!isAuthorized()) return@withContext Result.failure(Exception("Unauthorized"))
        try {
            val user = execute(TdApi.GetMe())
            val usernames = user.usernames
            val handle = if (usernames != null && usernames.activeUsernames != null && usernames.activeUsernames.isNotEmpty()) {
                "@${usernames.activeUsernames[0]}"
            } else {
                "@user_${user.id}"
            }

            var profilePicPath: String? = null
            user.profilePhoto?.small?.let { file ->
                var currentFile = file
                if (currentFile.local.canBeDownloaded && !currentFile.local.isDownloadingCompleted) {
                    execute(TdApi.DownloadFile(currentFile.id, 1, 0, 0, true))
                    // Wait up to 5 seconds for download
                    repeat(5) {
                        delay(1000)
                        val updatedFile = execute(TdApi.GetFile(currentFile.id))
                        if (updatedFile.local.isDownloadingCompleted) {
                            currentFile = updatedFile
                            return@repeat
                        }
                    }
                }
                if (currentFile.local.isDownloadingCompleted) {
                    profilePicPath = currentFile.local.path
                }
            }

            val session = UserAccountSession(
                isLoggedIn = true,
                phoneNumber = user.phoneNumber ?: "",
                userName = "${user.firstName} ${user.lastName}".trim(),
                userHandle = handle,
                userId = user.id,
                profilePicPath = profilePicPath,
                isGhostModeActive = true,
                blockReadReceipts = true,
                blockPresencePings = true
            )
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchAccountChats(query: String): Result<List<DiscoveredChat>> = withContext(Dispatchers.IO) {
        if (!isAuthorized()) return@withContext Result.failure(Exception("Unauthorized"))
        try {
            val chatIds = linkedSetOf<Long>()
            try { chatIds.addAll(execute(TdApi.SearchChats(query, 200)).chatIds.toList()) }
            catch (e: Exception) { Log.w("TDLib", "Local chat search unavailable: ${e.message}") }
            try { chatIds.addAll(execute(TdApi.SearchChatsOnServer(query, 200)).chatIds.toList()) }
            catch (e: Exception) { Log.w("TDLib", "Server chat search unavailable: ${e.message}") }
            if (query.isNotBlank()) {
                try { chatIds.addAll(execute(TdApi.SearchPublicChats(query.removePrefix("@"))).chatIds.toList()) }
                catch (e: Exception) { Log.w("TDLib", "Public chat search unavailable: ${e.message}") }
            }
            try {
                val archived = execute(TdApi.GetChats(TdApi.ChatListArchive(), 10000))
                chatIds.addAll(archived.chatIds.filter { id ->
                    try { execute(TdApi.GetChat(id)).title.contains(query, ignoreCase = true) }
                    catch (_: Exception) { false }
                })
            } catch (e: Exception) {
                Log.w("TDLib", "Archive search unavailable: ${e.message}")
            }
            val discovered = chatIds.mapNotNull { chatId ->
                try { mapToDiscoveredChat(execute(TdApi.GetChat(chatId))) }
                catch (_: Exception) { null }
            }
            Result.success(discovered)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(fileId: Int): Result<String> = withContext(Dispatchers.IO) {
        if (!isAuthorized()) return@withContext Result.failure(Exception("Unauthorized"))
        try {
            Log.d("TDLib", "downloadFile: Requesting download for $fileId")
            val updatedFile = execute(TdApi.DownloadFile(fileId, 1, 0, 0, true))
            
            // Wait up to 30 seconds for download
            var currentFile = updatedFile
            repeat(30) {
                if (currentFile.local.isDownloadingCompleted) {
                    return@withContext Result.success(currentFile.local.path)
                }
                delay(1000)
                currentFile = execute(TdApi.GetFile(fileId))
            }
            
            if (currentFile.local.isDownloadingCompleted) {
                Result.success(currentFile.local.path)
            } else {
                Result.failure(Exception("Download timed out"))
            }
        } catch (e: Exception) {
            Log.e("TDLib", "downloadFile Error: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun mapToDiscoveredChat(chat: TdApi.Chat): DiscoveredChat {
        val lastMsg = chat.lastMessage?.let { getMessageText(it.content) } ?: "No messages"
        
        val avatarPath = chat.photo?.small?.let { file ->
            if (file.local.canBeDownloaded && !file.local.isDownloadingCompleted) {
                send(TdApi.DownloadFile(file.id, 1, 0, 0, true))
            }
            if (file.local.isDownloadingCompleted) file.local.path else null
        }
        
        var username: String? = null
        val memberCount = when (val type = chat.type) {
            is TdApi.ChatTypeSupergroup -> {
                try {
                    val group = execute(TdApi.GetSupergroup(type.supergroupId))
                    username = group.usernames?.activeUsernames?.firstOrNull()
                    group.memberCount
                } catch (e: Exception) { 0 }
            }
            is TdApi.ChatTypeBasicGroup -> {
                try {
                    val group = execute(TdApi.GetBasicGroup(type.basicGroupId))
                    group.memberCount
                } catch (e: Exception) { 0 }
            }
            else -> 0
        }

        val chatType = when (chat.type) {
            is TdApi.ChatTypeSupergroup -> {
                if ((chat.type as TdApi.ChatTypeSupergroup).isChannel) ChatSourceType.CHANNEL 
                else ChatSourceType.GROUP
            }
            is TdApi.ChatTypeBasicGroup -> ChatSourceType.GROUP
            is TdApi.ChatTypePrivate -> ChatSourceType.PERSONAL
            else -> ChatSourceType.CHANNEL
        }

        return DiscoveredChat(
            id = chat.id,
            title = chat.title,
            type = chatType,
            username = username,
            avatarPath = avatarPath,
            memberCount = memberCount,
            lastMessage = lastMsg
        )
    }

    suspend fun fetchAccountDialogs(): Result<List<DiscoveredChat>> = withContext(Dispatchers.IO) {
        if (!isAuthorized()) return@withContext Result.failure(Exception("Unauthorized"))
        try {
            val chatIds = linkedSetOf<Long>()
            for (chatList in listOf(TdApi.ChatListMain(), TdApi.ChatListArchive())) {
                // LoadChats can return fewer than requested; keep asking until TDLib reports the end.
                for (page in 0 until 100) {
                    try {
                        execute(TdApi.LoadChats(chatList, 100))
                    } catch (e: Exception) {
                        if (!e.message.orEmpty().contains("Not Found", ignoreCase = true)) {
                            Log.w("TDLib", "Stopped loading chats: ${e.message}")
                        }
                        break
                    }
                }
                try {
                    chatIds.addAll(execute(TdApi.GetChats(chatList, 10000)).chatIds.toList())
                } catch (e: Exception) {
                    Log.w("TDLib", "Could not read a chat list: ${e.message}")
                }
            }
            val discovered = chatIds.mapNotNull { chatId ->
                try { mapToDiscoveredChat(execute(TdApi.GetChat(chatId))) }
                catch (e: Exception) { Log.w("TDLib", "Skipping chat $chatId: ${e.message}"); null }
            }
            Result.success(discovered)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStealthClassGroupMessages(
        sourceId: Long, 
        groupTitle: String, 
        fromMessageId: Long = 0, 
        limit: Int = 100
    ): List<GhostMessage> = withContext(Dispatchers.IO) {
        if (!isAuthorized()) return@withContext emptyList()
        try {
            Log.d("TDLib", "getStealthClassGroupMessages: Fetching for $sourceId ($groupTitle) from $fromMessageId")
            val myId = execute(TdApi.GetMe()).id
            val targetChatId = if (sourceId == -1L) myId else sourceId
            
            val history = execute(TdApi.GetChatHistory(targetChatId, fromMessageId, 0, limit, false))
            Log.d("TDLib", "getStealthClassGroupMessages: Found ${history.messages.size} messages")
            history.messages.map { msg -> mapToGhostMessage(msg, sourceId, groupTitle, myId) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("TDLib", "getStealthClassGroupMessages Error: ${e.message}")
            throw e
        }
    }

    suspend fun getStealthChannelMessages(
        sourceId: Long, 
        channelTitle: String, 
        fromMessageId: Long = 0, 
        limit: Int = 100
    ): List<GhostMessage> = withContext(Dispatchers.IO) {
        if (!isAuthorized()) return@withContext emptyList()
        try {
            Log.d("TDLib", "getStealthChannelMessages: Fetching for $sourceId ($channelTitle) from $fromMessageId")
            val myId = execute(TdApi.GetMe()).id
            val history = execute(TdApi.GetChatHistory(sourceId, fromMessageId, 0, limit, false))
            Log.d("TDLib", "getStealthChannelMessages: Found ${history.messages.size} messages")
            history.messages.map { msg -> mapToGhostMessage(msg, sourceId, channelTitle, myId) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("TDLib", "getStealthChannelMessages Error: ${e.message}")
            throw e
        }
    }

    suspend fun searchChatMessages(
        chatId: Long,
        query: String,
        filter: TdApi.SearchMessagesFilter = TdApi.SearchMessagesFilterEmpty(),
        fromMessageId: Long = 0,
        limit: Int = 50
    ): List<GhostMessage> = withContext(Dispatchers.IO) {
        if (!isAuthorized()) return@withContext emptyList()
        try {
            val myId = execute(TdApi.GetMe()).id
            val targetChatId = if (chatId == -1L) myId else chatId
            val chat = execute(TdApi.GetChat(targetChatId))
            val res = execute(TdApi.SearchChatMessages(targetChatId, null, query, null, fromMessageId, 0, limit.coerceIn(1, 100), filter))
            res.messages.map { mapToGhostMessage(it, chatId, chat.title, myId) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("TDLib", "searchChatMessages Error: ${e.message}")
            throw e
        }
    }


    suspend fun mapToGhostMessage(msg: TdApi.Message, sourceId: Long, sourceTitle: String, myUserId: Long): GhostMessage {
        var senderName = "Unknown"
        var senderHandle = ""
        var senderAvatarPath: String? = null
        
        (msg.senderId as? TdApi.MessageSenderUser)?.let { senderUser ->
            try {
                val user = execute(TdApi.GetUser(senderUser.userId))
                senderName = "${user.firstName} ${user.lastName}".trim()
                val usernames = user.usernames
                senderHandle = if (usernames != null && usernames.activeUsernames != null && usernames.activeUsernames.isNotEmpty()) "@${usernames.activeUsernames[0]}" else ""
                
                // Fetch sender profile pic
                user.profilePhoto?.small?.let { file ->
                    if (file.local.isDownloadingCompleted) {
                        senderAvatarPath = file.local.path
                    } else if (file.local.canBeDownloaded) {
                        send(TdApi.DownloadFile(file.id, 1, 0, 0, true))
                    }
                }
            } catch (e: Exception) { senderName = "Member" }
        } ?: (msg.senderId as? TdApi.MessageSenderChat)?.let { senderChat ->
            try {
                val chat = execute(TdApi.GetChat(senderChat.chatId))
                senderName = chat.title
                
                // Fetch sender chat pic
                chat.photo?.small?.let { file ->
                    if (file.local.isDownloadingCompleted) {
                        senderAvatarPath = file.local.path
                    } else if (file.local.canBeDownloaded) {
                        send(TdApi.DownloadFile(file.id, 1, 0, 0, true))
                    }
                }
            } catch (e: Exception) { senderName = "Source" }
        }

        var imageUrl: String? = null
        var photoFileId = 0
        var stickerPath: String? = null
        var stickerFileId = 0
        var stickerFormat: String? = null
        var stickerThumbnailFileId = 0
        var stickerThumbnailPath: String? = null
        var stickerEmoji: String? = null
        var videoPath: String? = null
        var audioPath: String? = null
        var voicePath: String? = null
        var attachment: FileAttachment? = null
        var animationMimeType: String? = null

        when (val content = msg.content) {
            is TdApi.MessagePhoto -> {
                val photo = content.photo.sizes.lastOrNull()
                if (photo != null) {
                    photoFileId = photo.photo.id
                    if (photo.photo.local.isDownloadingCompleted && photo.photo.local.path.isNotBlank()) {
                        imageUrl = photo.photo.local.path
                    } else {
                        // Request download in background
                        send(TdApi.DownloadFile(photo.photo.id, 1, 0, 0, true))
                    }
                }
            }
            is TdApi.MessageSticker -> {
                val media = content.sticker
                val sticker = media.sticker
                stickerFileId = sticker.id
                stickerEmoji = media.emoji
                stickerFormat = when (media.format) {
                    is TdApi.StickerFormatTgs -> "tgs"
                    is TdApi.StickerFormatWebm -> "webm"
                    else -> "webp"
                }
                media.thumbnail?.file?.let { thumbnail ->
                    stickerThumbnailFileId = thumbnail.id
                    if (thumbnail.local.isDownloadingCompleted) stickerThumbnailPath = thumbnail.local.path
                    else if (thumbnail.local.canBeDownloaded) send(TdApi.DownloadFile(thumbnail.id, 1, 0, 0, false))
                }
                if (sticker.local.isDownloadingCompleted) {
                    stickerPath = sticker.local.path
                } else if (sticker.local.canBeDownloaded) send(TdApi.DownloadFile(sticker.id, 1, 0, 0, false))
            }
            is TdApi.MessageVideo -> {
                videoPath = if (content.video.video.local.isDownloadingCompleted) content.video.video.local.path else null
                attachment = FileAttachment(
                    fileName = "Video_${msg.id}.mp4",
                    fileSizeFormatted = formatFileSize(content.video.video.expectedSize.toLong()),
                    mimeType = "video/mp4",
                    localPath = videoPath,
                    fileId = content.video.video.id
                )
            }
            is TdApi.MessageAnimation -> {
                val animation = content.animation
                val file = animation.animation
                animationMimeType = animation.mimeType
                attachment = FileAttachment(
                    fileName = animation.fileName.ifBlank { "Animation_${msg.id}.${if (animation.mimeType == "image/gif") "gif" else "mp4"}" },
                    fileSizeFormatted = formatFileSize(file.expectedSize.toLong()),
                    mimeType = animation.mimeType,
                    localPath = file.local.path.takeIf { file.local.isDownloadingCompleted && it.isNotBlank() },
                    fileId = file.id
                )
            }
            is TdApi.MessageVideoNote -> {
                val file = content.videoNote.video
                videoPath = file.local.path.takeIf { file.local.isDownloadingCompleted && it.isNotBlank() }
                attachment = FileAttachment(
                    fileName = "VideoNote_${msg.id}.mp4",
                    fileSizeFormatted = formatFileSize(file.expectedSize.toLong()),
                    mimeType = "video/mp4", localPath = videoPath, fileId = file.id
                )
            }
            is TdApi.MessageAudio -> {
                audioPath = if (content.audio.audio.local.isDownloadingCompleted) content.audio.audio.local.path else null
                attachment = FileAttachment(
                    fileName = content.audio.fileName.ifBlank { content.audio.title.ifBlank { "Audio_${msg.id}" } },
                    fileSizeFormatted = formatFileSize(content.audio.audio.expectedSize.toLong()),
                    mimeType = content.audio.mimeType.ifBlank { "audio/mpeg" },
                    localPath = audioPath,
                    fileId = content.audio.audio.id
                )
            }
            is TdApi.MessageVoiceNote -> {
                voicePath = if (content.voiceNote.voice.local.isDownloadingCompleted) content.voiceNote.voice.local.path else null
                attachment = FileAttachment(
                    fileName = "VoiceNote_${msg.id}.ogg",
                    fileSizeFormatted = formatFileSize(content.voiceNote.voice.expectedSize.toLong()),
                    mimeType = "audio/ogg",
                    localPath = voicePath,
                    fileId = content.voiceNote.voice.id
                )
            }
            is TdApi.MessageDocument -> {
                val doc = content.document.document
                attachment = FileAttachment(
                    fileName = content.document.fileName.ifBlank { "File_${msg.id}" },
                    fileSizeFormatted = formatFileSize(doc.expectedSize.toLong()),
                    mimeType = content.document.mimeType,
                    localPath = if (doc.local.isDownloadingCompleted) doc.local.path else null,
                    fileId = doc.id
                )
            }
        }

        val senderUserId = (msg.senderId as? TdApi.MessageSenderUser)?.userId ?: 0L
        val isFromMe = senderUserId == myUserId && myUserId != 0L

        val domainChatType = when {
            sourceId == -1L -> ChatSourceType.SAVED_MESSAGES
            msg.isChannelPost -> ChatSourceType.CHANNEL
            else -> {
                try {
                    val chat = execute(TdApi.GetChat(msg.chatId))
                    if (chat.type is TdApi.ChatTypePrivate) ChatSourceType.PERSONAL else ChatSourceType.GROUP
                } catch (e: Exception) {
                    ChatSourceType.GROUP
                }
            }
        }

        val replyTarget = msg.replyTo as? TdApi.MessageReplyToMessage
        // A missing remote reply must never stall a live message (or every later update).
        // Tapping the preview still retrieves the original through the navigation path.
        val repliedMessage = if (replyTarget != null && replyTarget.messageId != 0L) {
            try { execute(TdApi.GetMessageLocally(replyTarget.chatId.takeIf { it != 0L } ?: msg.chatId, replyTarget.messageId)) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { null }
        } else null
        val replyMessageId = repliedMessage?.id ?: replyTarget?.messageId ?: 0L
        val replySenderName = when (val sender = repliedMessage?.senderId) {
            is TdApi.MessageSenderUser -> {
                if (sender.userId == myUserId) "You" else try {
                    val user = execute(TdApi.GetUser(sender.userId))
                    "${user.firstName} ${user.lastName}".trim().ifBlank { "Member" }
                } catch (_: Exception) { "Member" }
            }
            is TdApi.MessageSenderChat -> try { execute(TdApi.GetChat(sender.chatId)).title }
                catch (_: Exception) { "Channel" }
            else -> null
        }

        val snapshot = interactions.get("${msg.chatId}:${msg.id}")
        val interaction = if (snapshot != null) snapshot.info else msg.interactionInfo
        return GhostMessage(
            id = msg.id,
            telegramChatId = msg.chatId,
            photoFileId = photoFileId,
            formattedEntities = TelegramMessageText.formattedText(msg.content)?.let { TelegramMessageText.encodeEntities(it) },
            commentCount = interaction?.replyInfo?.replyCount ?: -1,
            sourceId = sourceId,
            sourceTitle = sourceTitle,
            chatType = domainChatType,
            telegramMsgId = msg.id,
            senderId = senderUserId,
            senderName = if (isFromMe) "You" else senderName,
            senderHandle = senderHandle,
            text = TelegramMessageText.formattedText(msg.content)?.text ?: getMessageText(msg.content),
            timestamp = msg.date.toLong() * 1000,
            imageUrl = imageUrl,
            stickerPath = stickerPath,
            stickerFileId = stickerFileId,
            stickerFormat = stickerFormat,
            stickerThumbnailFileId = stickerThumbnailFileId,
            stickerThumbnailPath = stickerThumbnailPath,
            stickerEmoji = stickerEmoji,
            videoPath = videoPath,
            audioPath = audioPath,
            voicePath = voicePath,
            fileAttachment = attachment,
            animationMimeType = animationMimeType,
            replyToMessageId = replyMessageId,
            replyToChatId = if (replyMessageId == 0L) 0L else repliedMessage?.chatId ?: replyTarget?.chatId?.takeIf { it != 0L } ?: msg.chatId,
            replyToSenderName = replySenderName,
            replyToText = repliedMessage?.let { getMessageText(it.content) }
                ?: if (msg.replyTo != null) "Original message unavailable" else null,
            isPinned = msg.isPinned,
            views = (interaction?.viewCount ?: 0).takeIf { it > 0 }?.toString(),
            reactions = formatReactions(interaction?.reactions, resolveCustomEmoji = false),
            isBookmarked = sourceId == -1L,
            sourceAvatarPath = senderAvatarPath,
            isUrgent = false
        )
    }

    suspend fun formatReactions(reactions: TdApi.MessageReactions?, resolveCustomEmoji: Boolean = true): String? {
        if (reactions == null || reactions.reactions.isEmpty()) return null
        val missing = reactions.reactions.mapNotNull { (it.type as? TdApi.ReactionTypeCustomEmoji)?.customEmojiId }
            .filter { !customReactionEmoji.containsKey(it) }.distinct()
        if (resolveCustomEmoji && missing.isNotEmpty()) try {
            execute(TdApi.GetCustomEmojiStickers(missing.toLongArray())).stickers.forEach { sticker ->
                val type = sticker.fullType as? TdApi.StickerFullTypeCustomEmoji
                if (type != null) customReactionEmoji[type.customEmojiId] = sticker.emoji.ifBlank { "Custom emoji" }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { }
        return TelegramReactionText.format(reactions, customReactionEmoji)
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("TDLib", "Logging out...")
            execute(TdApi.LogOut())
            kotlinx.coroutines.withTimeout(20000) {
                authState.first { it is TdApi.AuthorizationStateClosed }
            }
            client = null
            authState.value = null
            setupClient()
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("Telegram took too long to finish signing out. Please retry."))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("TDLib", "Logout Error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun getMessageText(content: TdApi.MessageContent): String {
        return when (content) {
            is TdApi.MessageText -> content.text.text
            is TdApi.MessagePhoto -> {
                val caption = content.caption.text
                if (caption.isNotBlank()) caption else "[Photo]"
            }
            is TdApi.MessageVideo -> {
                val caption = content.caption.text
                if (caption.isNotBlank()) caption else "[Video]"
            }
            is TdApi.MessageAnimation -> content.caption.text.ifBlank { "[GIF]" }
            is TdApi.MessageDocument -> {
                val caption = content.caption.text
                val filename = content.document.fileName
                if (caption.isNotBlank()) "$filename: $caption" else "[File: $filename]"
            }
            is TdApi.MessageSticker -> {
                val emoji = content.sticker.emoji
                if (emoji.isNotBlank()) "Sticker $emoji" else "[Sticker]"
            }
            is TdApi.MessageAnimatedEmoji -> content.emoji
            is TdApi.MessageDice -> "[Dice: ${content.emoji}]"
            is TdApi.MessageLocation -> "[Location]"
            is TdApi.MessageVenue -> "[Venue: ${content.venue.title}]"
            is TdApi.MessageContact -> "[Contact: ${content.contact.firstName}]"
            is TdApi.MessageAudio -> content.caption.text
            is TdApi.MessageVoiceNote -> content.caption.text
            is TdApi.MessageVideoNote -> ""
            else -> "[New Update]"
        }
    }

    suspend fun getComments(chatId: Long, messageId: Long, fromMessageId: Long = 0): List<GhostMessage> {
        val thread = execute(TdApi.GetMessageThread(chatId, messageId))
        val history = execute(TdApi.GetMessageThreadHistory(chatId, messageId, fromMessageId, 0, 50))
        val myId = execute(TdApi.GetMe()).id
        return history.messages.map { mapToGhostMessage(it, thread.chatId, "Discussion", myId) }
    }

    private fun enforceOfflineStatus() {
        Log.d("TDLib", "Enforcing Stealth Presence (Offline Mode)")
        // Global options to discourage 'online' status reporting
        send(TdApi.SetOption("online", TdApi.OptionValueBoolean(false)))
        // Some TDLib versions support is_offline to block all presence telemetry
        send(TdApi.SetOption("is_offline", TdApi.OptionValueBoolean(true)))
    }
}
