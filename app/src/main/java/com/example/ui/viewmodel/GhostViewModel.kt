package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.GhostDatabase
import com.example.data.local.GhostPreferences
import com.example.data.local.GhostAccounts
import com.example.data.model.ChatSourceType
import com.example.data.model.ChatHistoryDirection
import com.example.data.model.ChatHistoryWindow
import com.example.data.model.ChatMessageJump
import com.example.data.model.DiscoveredChat
import com.example.data.model.FocusFilter
import com.example.data.model.FocusSource
import com.example.data.model.ForumTopicSummary
import com.example.data.model.GhostMessage
import com.example.data.model.MainTab
import com.example.data.model.MusicLibraryUiState
import com.example.data.model.SourceConfig
import com.example.data.model.TelegramUserProfile
import com.example.data.model.TelegramReactionUser
import com.example.data.model.StealthStats
import com.example.data.model.UserAccountSession
import com.example.data.repository.GhostRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class FocusTimerState(
    val isRunning: Boolean = false,
    val remainingSeconds: Int = 25 * 60,
    val totalSeconds: Int = 25 * 60,
    val isPaused: Boolean = false
)

data class DialogsState(
    val addSourceType: ChatSourceType = ChatSourceType.CHANNEL,
    val showLoginDialog: Boolean = false,
    val showChatPickerDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showStealthAuditDialog: Boolean = false,
    val showAuthGuideDialog: Boolean = false,
    val showTimerDialog: Boolean = false,
    val infoMessage: String? = null
)

data class UserProfileDialogState(
    val userId: Long,
    val profile: TelegramUserProfile? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

data class GhostUiState(
    val currentTab: MainTab = MainTab.FEED,
    val selectedSource: FocusSource? = null,
    val filter: FocusFilter = FocusFilter.ALL,
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val feedMessages: List<GhostMessage> = emptyList(),
    val sourceMessages: List<GhostMessage> = emptyList(),
    val groups: List<FocusSource> = emptyList(),
    val channels: List<FocusSource> = emptyList(),
    val personal: List<FocusSource> = emptyList(),
    val userSession: UserAccountSession = UserAccountSession(),
    val discoveredChats: List<DiscoveredChat> = emptyList(),
    val isChatPickerLoading: Boolean = false,
    val config: SourceConfig = SourceConfig(),
    val stealthStats: StealthStats = StealthStats(),
    val timerState: FocusTimerState = FocusTimerState(),
    val addSourceType: ChatSourceType = ChatSourceType.CHANNEL,
    val showLoginDialog: Boolean = false,
    val showChatPickerDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showStealthAuditDialog: Boolean = false,
    val showAuthGuideDialog: Boolean = false,
    val showTimerDialog: Boolean = false,
    val infoMessage: String? = null,
    val telegramAuthState: org.drinkless.tdlib.TdApi.AuthorizationState? = null,
    val isLoadingMore: Boolean = false,
    val chatSearchQuery: String = "",
    val chatSearchResults: List<GhostMessage>? = null,
    val currentMediaFilter: String? = null, // null, "audio", "video", "voice", "document"
    val isInitialized: Boolean = false,
    val messageJump: ChatMessageJump? = null,
    val canLoadNewerHistory: Boolean = false,
    val selectedForumTopic: ForumTopicSummary? = null,
    val forumTopicMessages: List<GhostMessage> = emptyList(),
    val userProfileDialog: UserProfileDialogState? = null
)

class GhostViewModel(application: Application, val accountId: String = GhostAccounts.get(application).activeId.value) : AndroidViewModel(application) {

    private val repository: GhostRepository
    private val preferences: GhostPreferences

    private val _currentTab = MutableStateFlow(MainTab.FEED)
    private val _selectedSource = MutableStateFlow<FocusSource?>(null)
    private val _selectedForumTopic = MutableStateFlow<ForumTopicSummary?>(null)
    private val _forumTopicMessages = MutableStateFlow<List<GhostMessage>>(emptyList())
    private val _userProfileDialog = MutableStateFlow<UserProfileDialogState?>(null)
    private val _musicLibrary = MutableStateFlow(MusicLibraryUiState())
    private val musicArtworkDirectory = File(application.filesDir,
        "music-artwork/${accountId.replace(Regex("[^A-Za-z0-9_-]"), "_")}").apply { mkdirs() }
    private val musicArtworkLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private var musicLibraryJob: Job? = null
    private var musicLibraryRequestId = 0L
    val musicLibrary: StateFlow<MusicLibraryUiState> = _musicLibrary.asStateFlow()
    private var userProfileJob: Job? = null
    private val _filter = MutableStateFlow(FocusFilter.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val _isRefreshing = MutableStateFlow(false)
    private val _discoveredChats = MutableStateFlow<List<DiscoveredChat>>(emptyList())
    private val _isChatPickerLoading = MutableStateFlow(false)
    private var chatPickerJob: Job? = null
    private val _dialogsState = MutableStateFlow(DialogsState())
    private val _timerState = MutableStateFlow(FocusTimerState())
    private val _isLoadingMore = MutableStateFlow(false)
    private val _chatSearchQuery = MutableStateFlow("")
    private val _chatSearchResults = MutableStateFlow<List<GhostMessage>?>(null)
    private val _currentMediaFilter = MutableStateFlow<String?>(null)
    private var messageSearchJob: Job? = null
    private var feedSearchJob: Job? = null
    private val _feedSearchResults = MutableStateFlow<List<GhostMessage>>(emptyList())
    private var searchEndReached = false
    private var historyEndReached = false
    private var historyJob: Job? = null
    private var historyPagingJob: Job? = null
    private var historyRequestId = 0L
    private val _historyWindow = MutableStateFlow<ChatHistoryWindow?>(null)
    private val _messageJump = MutableStateFlow<ChatMessageJump?>(null)

    private var timerJob: Job? = null

    init {
        preferences = GhostPreferences(application, accountId)
        repository = GhostRepository.getInstance(application, accountId)

        // Reactive initialization based on auth state
        viewModelScope.launch {
            try {
                repository.authStateFlow.collect { state ->
                    if (state is org.drinkless.tdlib.TdApi.AuthorizationStateReady) {
                        Log.d("GhostViewModel", "TDLib Ready, triggering startup sync...")
                        repository.refreshUserSession()
                        repository.seedInitialDataIfNeeded()
                        // The repository owns startup/reconnect catch-up independently of UI state.
                        
                    }
                }
            } catch (e: Exception) {
                Log.e("GhostViewModel", "Initialization collector failed: ${e.message}")
            }
        }
    }

    private val allFeedMessages = repository.getAllFeedMessages()
    private val groupsFlow = repository.getGroups()
    private val channelsFlow = repository.getChannels()
    private val personalFlow = repository.getPersonalSources()

    private val sourceMessagesFlow = _selectedSource.flatMapLatest { src ->
        if (src != null) combine(repository.getMessagesForSource(src.id), _historyWindow) { messages, window ->
            if (window == null) messages else messages.filter { window.contains(it.telegramMsgId) }
        }
        else flowOf(emptyList())
    }

    private data class NavigationState(
        val tab: MainTab,
        val selectedSource: FocusSource?,
        val filter: FocusFilter,
        val searchQuery: String
    )

    private val navigationStateFlow = combine(
        _currentTab,
        _selectedSource,
        _filter,
        _searchQuery
    ) { tab, source, filter, query ->
        NavigationState(tab, source, filter, query)
    }

    private data class SourceLists(
        val groups: List<FocusSource>,
        val channels: List<FocusSource>,
        val personal: List<FocusSource>
    )

    private val sourceListsFlow = combine(groupsFlow, channelsFlow, personalFlow) { grps, chns, pers ->
        SourceLists(grps, chns, pers)
    }

    private data class MessagePayloads(
        val feed: List<GhostMessage>,
        val sourceMsgs: List<GhostMessage>
    )

    private val messagePayloadsFlow = combine(allFeedMessages, sourceMessagesFlow, _feedSearchResults, _searchQuery) { feed, srcMsgs, search, query ->
        MessagePayloads((filterMessages(feed, FocusFilter.ALL, query) + search).distinctBy { it.sourceId to it.telegramMsgId }.sortedByDescending { it.timestamp }, srcMsgs)
    }

    private data class CoreUiState(
        val currentTab: MainTab,
        val selectedSource: FocusSource?,
        val filter: FocusFilter,
        val searchQuery: String,
        val isRefreshing: Boolean,
        val feedMessages: List<GhostMessage>,
        val sourceMessages: List<GhostMessage>,
        val groups: List<FocusSource>,
        val channels: List<FocusSource>,
        val personal: List<FocusSource>,
        val timerState: FocusTimerState
    )

    private val coreUiStateFlow = combine(
        navigationStateFlow,
        sourceListsFlow,
        messagePayloadsFlow,
        _isRefreshing,
        _timerState
    ) { nav, sources, messages, refreshing, timerState ->
        val filteredFeed = filterMessages(messages.feed, nav.filter, "")
        val filteredSource = filterMessages(messages.sourceMsgs.filter { it.sourceId == nav.selectedSource?.id }, nav.filter, nav.searchQuery)

        CoreUiState(
            currentTab = nav.tab,
            selectedSource = nav.selectedSource,
            filter = nav.filter,
            searchQuery = nav.searchQuery,
            isRefreshing = refreshing,
            feedMessages = filteredFeed,
            sourceMessages = filteredSource,
            groups = sources.groups,
            channels = sources.channels,
            personal = sources.personal,
            timerState = timerState
        )
    }

    val uiState: StateFlow<GhostUiState> = combine(
        listOf(
            coreUiStateFlow,
            repository.userSessionFlow,
            repository.configFlow,
            _discoveredChats,
            _dialogsState,
            repository.authStateFlow,
            _isLoadingMore,
            _chatSearchQuery,
            _chatSearchResults,
            _currentMediaFilter,
            _isChatPickerLoading,
            _messageJump,
            _historyWindow,
            _selectedForumTopic,
            _forumTopicMessages,
            _userProfileDialog
        )
    ) { args ->
        val core = args[0] as CoreUiState
        val userSession = args[1] as UserAccountSession
        val config = args[2] as SourceConfig
        val discoveredChats = args[3] as List<DiscoveredChat>
        val dialogs = args[4] as DialogsState
        val authState = args[5] as org.drinkless.tdlib.TdApi.AuthorizationState?

        GhostUiState(
            currentTab = core.currentTab,
            selectedSource = core.selectedSource,
            filter = core.filter,
            searchQuery = core.searchQuery,
            isRefreshing = core.isRefreshing,
            feedMessages = core.feedMessages,
            sourceMessages = core.sourceMessages,
            groups = core.groups,
            channels = core.channels,
            personal = core.personal,
            userSession = userSession,
            discoveredChats = discoveredChats,
            isChatPickerLoading = args[10] as Boolean,
            config = config,
            stealthStats = StealthStats(
                presencePingsSent = 0,
                lastSeenFrozenText = "Month ago (Untouched)",
                readReceiptsBlocked = 38,
                dmsFilteredCount = 67,
                isGhostActive = true
            ),
            timerState = core.timerState,
            addSourceType = dialogs.addSourceType,
            showLoginDialog = dialogs.showLoginDialog,
            showChatPickerDialog = dialogs.showChatPickerDialog,
            showSettingsDialog = dialogs.showSettingsDialog,
            showStealthAuditDialog = dialogs.showStealthAuditDialog,
            showAuthGuideDialog = dialogs.showAuthGuideDialog,
            showTimerDialog = dialogs.showTimerDialog,
            infoMessage = dialogs.infoMessage,
            telegramAuthState = authState,
            isLoadingMore = args[6] as Boolean,
            chatSearchQuery = args[7] as String,
            chatSearchResults = args[8] as List<GhostMessage>?,
            currentMediaFilter = args[9] as String?,
            isInitialized = true,
            messageJump = args[11] as ChatMessageJump?,
            canLoadNewerHistory = (args[12] as ChatHistoryWindow?)?.canLoadNewer == true,
            selectedForumTopic = args[13] as ForumTopicSummary?,
            forumTopicMessages = args[14] as List<GhostMessage>,
            userProfileDialog = args[15] as UserProfileDialogState?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GhostUiState()
    )

    private fun filterMessages(list: List<GhostMessage>, filter: FocusFilter, query: String): List<GhostMessage> {
        return list.filter { msg ->
            val matchesFilter = when (filter) {
                FocusFilter.ALL -> true
                FocusFilter.PINNED -> msg.isPinned
                FocusFilter.URGENT -> msg.isUrgent
                FocusFilter.BOOKMARKS -> msg.isBookmarked
            }

            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                msg.text.contains(query, ignoreCase = true) ||
                        msg.fileAttachment?.fileName?.contains(query, ignoreCase = true) == true ||
                        msg.senderName.contains(query, ignoreCase = true) ||
                        msg.sourceTitle.contains(query, ignoreCase = true)
            }

            matchesFilter && matchesQuery
        }
    }

    fun selectTab(tab: MainTab) {
        resetHistoryWindow()
        _selectedSource.value?.let { viewModelScope.launch { repository.setChatOpen(it, false) } }
        resetChatSearch()
        _selectedForumTopic.value = null
        _forumTopicMessages.value = emptyList()
        _currentTab.value = tab
        _selectedSource.value = null
        if (tab == MainTab.MUSIC && !_musicLibrary.value.isLoadingSources) loadMusicSources()
    }

    fun loadMusicSources() {
        _musicLibrary.update { it.copy(isLoadingSources = true) }
        viewModelScope.launch {
            try {
                val sources = repository.getImportedMusicSources()
                val savedSource = preferences.selectedMusicSourceChatId()
                val selectedSource = savedSource?.takeIf { id -> sources.any { it.telegramChatId == id } }
                if (savedSource != selectedSource) preferences.setSelectedMusicSourceChatId(selectedSource)
                _musicLibrary.update { current ->
                    val sourceStillExists = current.tracksSourceChatId == null || sources.any { it.telegramChatId == current.tracksSourceChatId }
                    current.copy(importedSources = sources, isLoadingSources = false,
                        selectedSourceChatId = selectedSource,
                        tracks = current.tracks.takeIf { sourceStillExists }.orEmpty(),
                        tracksSourceChatId = current.tracksSourceChatId.takeIf { sourceStillExists },
                        hasSearched = current.hasSearched && sourceStillExists)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _musicLibrary.update { it.copy(isLoadingSources = false, error = error.message ?: "Couldn't load imported chats.") }
            }
        }
    }

    fun selectMusicSource(sourceChatId: Long) {
        if (_musicLibrary.value.importedSources.none { it.telegramChatId == sourceChatId }) return
        preferences.setSelectedMusicSourceChatId(sourceChatId)
        _musicLibrary.update { it.copy(selectedSourceChatId = sourceChatId) }
    }

    fun refreshMusicLibrary(sourceChatId: Long) {
        if (sourceChatId == 0L) return
        val requestId = ++musicLibraryRequestId
        musicLibraryJob?.cancel()
        preferences.setSelectedMusicSourceChatId(sourceChatId)
        _musicLibrary.update { it.copy(tracks = emptyList(), tracksSourceChatId = null, isLoading = true, hasSearched = false,
            selectedSourceChatId = sourceChatId, searchedChats = 0, totalChats = 0, error = null) }
        musicLibraryJob = viewModelScope.launch {
            try {
                val tracks = repository.getMusicLibrary(sourceChatId) { completed, total ->
                    _musicLibrary.update { state ->
                        if (requestId == musicLibraryRequestId) state.copy(searchedChats = completed, totalChats = total)
                        else state
                    }
                }
                if (requestId == musicLibraryRequestId) {
                    _musicLibrary.update {
                        it.copy(tracks = tracks, isLoading = false, error = null, hasSearched = true,
                            selectedSourceChatId = sourceChatId, tracksSourceChatId = sourceChatId)
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestId == musicLibraryRequestId) {
                    _musicLibrary.update {
                        it.copy(isLoading = false, error = error.message ?: "Couldn't load music from imported chats.")
                    }
                }
            }
        }
    }

    fun openSource(source: FocusSource, messageId: Long = 0): Job? {
        _selectedForumTopic.value = null
        _forumTopicMessages.value = emptyList()
        resetHistoryWindow()
        setSearchQuery("")
        _selectedSource.value?.let { viewModelScope.launch { repository.setChatOpen(it, false) } }
        resetChatSearch()
        _selectedSource.value = source
        _filter.value = FocusFilter.ALL
        viewModelScope.launch { repository.setChatOpen(source, true) }
        return navigateHistory(if (messageId > 0) ChatHistoryDirection.AROUND else ChatHistoryDirection.LATEST, messageId)
    }

    fun closeSourceDetail() {
        resetHistoryWindow()
        _selectedSource.value?.let { viewModelScope.launch { repository.setChatOpen(it, false) } }
        resetChatSearch()
        _selectedForumTopic.value = null
        _forumTopicMessages.value = emptyList()
        _selectedSource.value = null
    }

    suspend fun loadForumTopics(source: FocusSource): List<ForumTopicSummary> =
        repository.getForumTopics(source)

    fun openForumTopic(source: FocusSource, topic: ForumTopicSummary) {
        resetHistoryWindow()
        _selectedSource.value?.let { viewModelScope.launch { repository.setChatOpen(it, false) } }
        resetChatSearch()
        _filter.value = FocusFilter.ALL
        _selectedForumTopic.value = topic
        _forumTopicMessages.value = emptyList()
        _selectedSource.value = source.copy(title = "${source.title} · ${topic.name}")
        viewModelScope.launch {
            repository.setChatOpen(source, true)
            _isLoadingMore.value = true
            try {
                _forumTopicMessages.value = repository.getForumTopicMessages(source, topic.id)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                _dialogsState.update { it.copy(infoMessage = "Couldn't load topic: ${error.message}") }
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun setFilter(filter: FocusFilter) {
        _filter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        feedSearchJob?.cancel()
        _feedSearchResults.value = emptyList()
        if (query.isNotBlank()) feedSearchJob = viewModelScope.launch {
            delay(350)
            _feedSearchResults.value = repository.searchFeed(query.trim())
        }
    }

    fun loadMoreFeedSearch() {
        val query = _searchQuery.value.trim()
        if (query.isBlank() || feedSearchJob?.isActive == true) return
        feedSearchJob = viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val previous = _feedSearchResults.value
                val page = repository.searchFeed(query, previous)
                val merged = (previous + page).distinctBy { it.sourceId to it.telegramMsgId }
                _feedSearchResults.value = merged
                if (merged.size == previous.size) _dialogsState.update { it.copy(infoMessage = "All available search results loaded") }
            } finally { _isRefreshing.value = false }
        }
    }

    suspend fun refreshMessageReactions(message: GhostMessage) = repository.refreshMessageReactions(message)

    suspend fun getReactionUsers(message: GhostMessage, reactionKey: String): Result<List<TelegramReactionUser>> =
        repository.getReactionUsers(message, reactionKey)

    fun openUserProfile(userId: Long) {
        if (userId <= 0L) return
        userProfileJob?.cancel()
        _userProfileDialog.value = UserProfileDialogState(userId = userId)
        userProfileJob = viewModelScope.launch {
            try {
                val result = repository.getUserProfile(userId)
                result.fold(
                    onSuccess = { profile ->
                        if (_userProfileDialog.value?.userId == userId) {
                            _userProfileDialog.value = UserProfileDialogState(userId, profile, isLoading = false)
                        }
                    },
                    onFailure = { error ->
                        if (_userProfileDialog.value?.userId == userId) {
                            _userProfileDialog.value = UserProfileDialogState(
                                userId = userId,
                                isLoading = false,
                                error = error.message?.takeIf { it.isNotBlank() } ?: "This profile isn't available right now."
                            )
                        }
                    }
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("GhostViewModel", "Could not load Telegram user profile", error)
                if (_userProfileDialog.value?.userId == userId) {
                    _userProfileDialog.value = UserProfileDialogState(
                        userId = userId, isLoading = false,
                        error = error.message?.takeIf { it.isNotBlank() } ?: "This profile isn't available right now."
                    )
                }
            }
        }
    }

    fun openPrivateChatProfile(chatId: Long) {
        viewModelScope.launch {
            repository.getPrivateChatUserId(chatId).fold(
                onSuccess = ::openUserProfile,
                onFailure = { openUserProfile(chatId) }
            )
        }
    }

    fun dismissUserProfile() {
        userProfileJob?.cancel()
        userProfileJob = null
        _userProfileDialog.value = null
    }

    fun onForeground() {
        com.example.data.remote.TelegramUserClient.getInstance(getApplication(), accountId).requestCatchUp()
    }

    fun refreshCurrent() {
        historyEndReached = false
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val src = _selectedSource.value
                val topic = _selectedForumTopic.value
                if (src != null && topic != null) {
                    _forumTopicMessages.value = repository.getForumTopicMessages(src, topic.id)
                } else if (src != null) repository.refreshSource(src, 0).getOrThrow()
                else repository.refreshAllSources()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                _dialogsState.update { it.copy(infoMessage = "Couldn't refresh messages. Check your connection and retry.") }
            } finally { _isRefreshing.value = false }
        }
    }

    fun loadMoreMessages() {
        val src = _selectedSource.value ?: return
        if (_isLoadingMore.value) return
        if (_chatSearchResults.value != null) {
            if (!searchEndReached) runChatSearch(append = true)
            return
        }
        if (historyEndReached) return
        if (historyJob?.isActive == true) return
        _isLoadingMore.value = true
        historyPagingJob = viewModelScope.launch {
            try {
                val before = repository.getMessagesForSource(src.id).first()
                    .filter { _historyWindow.value?.contains(it.telegramMsgId) != false }
                val lastMsgId = before.minOfOrNull { it.telegramMsgId } ?: 0L
                if (lastMsgId != 0L) {
                    val page = repository.loadHistoryWindow(src, ChatHistoryDirection.OLDER, lastMsgId)
                    val oldest = page.minOfOrNull { it.telegramMsgId } ?: lastMsgId
                    historyEndReached = oldest >= lastMsgId
                    _historyWindow.value?.let { _historyWindow.value = it.copy(oldestMessageId = minOf(it.oldestMessageId, oldest)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _dialogsState.update { it.copy(infoMessage = "Couldn't load older messages: ${e.message}") }; historyEndReached = true }
            finally {
                _isLoadingMore.value = false
            }
        }
    }

    suspend fun openNotificationMessage(chatId: Long, messageId: Long) {
        val source = repository.findNotificationSource(chatId)
        if (source == null) {
            _dialogsState.update { it.copy(infoMessage = "This chat is no longer in your focus sources") }
            return
        }
        openSource(source, messageId)?.join()
    }

    fun openReplyMessage(message: GhostMessage) {
        if (message.replyToMessageId == 0L) return
        viewModelScope.launch {
            val chatId = message.replyToChatId.takeIf { it != 0L } ?: message.telegramChatId
            val source = if (chatId != 0L) repository.findNotificationSource(chatId) else null
            val fallback = _selectedSource.value?.takeIf {
                it.id == message.sourceId && (chatId == 0L || chatId == message.telegramChatId)
            }
            val targetSource = source ?: fallback
            if (targetSource == null) {
                _dialogsState.update { it.copy(infoMessage = "The original message isn't available in your added chats") }
            } else if (_selectedSource.value?.id == targetSource.id) {
                navigateHistory(ChatHistoryDirection.AROUND, message.replyToMessageId)
            } else openSource(targetSource, message.replyToMessageId)
        }
    }

    fun goToFirstMessage() { navigateHistory(ChatHistoryDirection.FIRST) }
    fun goToLatestMessage() { navigateHistory(ChatHistoryDirection.LATEST) }

    private fun resetHistoryWindow() {
        historyRequestId++
        historyJob?.cancel()
        historyPagingJob?.cancel()
        _historyWindow.value = null
        _messageJump.value = null
        _isRefreshing.value = false
    }

    private fun navigateHistory(direction: ChatHistoryDirection, messageId: Long = 0): Job? {
        val source = _selectedSource.value ?: return null
        val requestId = ++historyRequestId
        historyJob?.cancel()
        historyPagingJob?.cancel()
        resetChatSearch()
        _filter.value = FocusFilter.ALL
        _isRefreshing.value = true
        _messageJump.value = null
        historyJob = viewModelScope.launch {
            try {
                val page = repository.loadHistoryWindow(source, direction, messageId)
                if (requestId != historyRequestId) return@launch
                if (page.isEmpty()) throw IllegalStateException("No accessible messages in this chat")
                val oldest = page.minOf { it.telegramMsgId }
                val newest = page.maxOf { it.telegramMsgId }
                val target = when (direction) {
                    ChatHistoryDirection.FIRST -> oldest
                    ChatHistoryDirection.AROUND -> messageId
                    else -> newest
                }
                _historyWindow.value = ChatHistoryWindow(oldest, if (direction == ChatHistoryDirection.LATEST) Long.MAX_VALUE else newest)
                historyEndReached = direction == ChatHistoryDirection.FIRST
                _messageJump.value = ChatMessageJump(requestId, source.id, target, direction == ChatHistoryDirection.AROUND)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _dialogsState.update { it.copy(infoMessage = "Couldn't open this message or history: ${e.message}") } }
            finally { if (requestId == historyRequestId) _isRefreshing.value = false }
        }
        return historyJob
    }

    fun messageJumpHandled(requestId: Long) {
        if (_messageJump.value?.requestId == requestId) _messageJump.value = null
    }

    fun loadNewerMessages() {
        val source = _selectedSource.value ?: return
        val window = _historyWindow.value ?: return
        if (!window.canLoadNewer || _isLoadingMore.value || historyJob?.isActive == true || _messageJump.value != null || _chatSearchResults.value != null) return
        _isLoadingMore.value = true
        historyPagingJob = viewModelScope.launch {
            try {
                val page = repository.loadHistoryWindow(source, ChatHistoryDirection.NEWER, window.newestMessageId)
                val newest = page.maxOfOrNull { it.telegramMsgId } ?: window.newestMessageId
                _historyWindow.value = window.copy(newestMessageId = maxOf(window.newestMessageId, newest))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _dialogsState.update { it.copy(infoMessage = "Couldn't load newer messages: ${e.message}") } }
            finally { _isLoadingMore.value = false }
        }
    }

    fun searchInChat(query: String) {
        _chatSearchQuery.value = query
        runChatSearch()
    }

    fun filterByMedia(type: String?) {
        _currentMediaFilter.value = type
        runChatSearch()
    }

    private fun resetChatSearch() {
        messageSearchJob?.cancel()
        _chatSearchQuery.value = ""
        _currentMediaFilter.value = null
        _chatSearchResults.value = null
        _isLoadingMore.value = false
        searchEndReached = false
        historyEndReached = false
    }

    private fun runChatSearch(append: Boolean = false) {
        historyRequestId++
        historyJob?.cancel()
        historyPagingJob?.cancel()
        _messageJump.value = null
        _isRefreshing.value = false
        messageSearchJob?.cancel()
        val src = _selectedSource.value ?: return
        val query = _chatSearchQuery.value.trim()
        val type = _currentMediaFilter.value
        if (query.isBlank() && type == null) {
            _chatSearchResults.value = null
            _isLoadingMore.value = false
            return
        }
        if (!append) { _chatSearchResults.value = emptyList(); searchEndReached = false }
        val filter = when (type) {
            "audio" -> org.drinkless.tdlib.TdApi.SearchMessagesFilterAudio()
            "photo" -> org.drinkless.tdlib.TdApi.SearchMessagesFilterPhoto()
            "link" -> org.drinkless.tdlib.TdApi.SearchMessagesFilterUrl()
            "video" -> org.drinkless.tdlib.TdApi.SearchMessagesFilterVideo()
            "voice" -> org.drinkless.tdlib.TdApi.SearchMessagesFilterVoiceNote()
            "document" -> org.drinkless.tdlib.TdApi.SearchMessagesFilterDocument()
            else -> org.drinkless.tdlib.TdApi.SearchMessagesFilterEmpty()
        }

        messageSearchJob = viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                if (!append) delay(350)
                val chatId = if (src.id == -1L) -1L else src.telegramChatId.takeIf { it != 0L } ?: src.handleOrId.toLongOrNull() ?: 0L
                if (chatId == 0L) throw IllegalStateException("Refresh this chat before searching")
                val previous = if (append) _chatSearchResults.value.orEmpty() else emptyList()
                val page = repository.searchChatMessages(chatId, query, filter, previous.lastOrNull()?.telegramMsgId ?: 0L)
                val merged = (previous + page).distinctBy { it.telegramMsgId }
                searchEndReached = page.isEmpty() || (append && previous.size == merged.size)
                _chatSearchResults.value = merged
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _dialogsState.update { it.copy(infoMessage = "Search failed: ${e.message}") } }
            finally { _isLoadingMore.value = false }
        }
    }

    fun toggleBookmark(message: GhostMessage) {
        viewModelScope.launch {
            repository.toggleBookmark(message)
        }
    }

    // Dialog controls - 100% reactive state updates
    fun openSettingsDialog() {
        _dialogsState.update { it.copy(showSettingsDialog = true) }
    }

    fun closeSettingsDialog() {
        _dialogsState.update { it.copy(showSettingsDialog = false) }
    }

    fun openLoginDialog() {
        _dialogsState.update { it.copy(showLoginDialog = true) }
    }

    fun closeLoginDialog() {
        _dialogsState.update { it.copy(showLoginDialog = false) }
    }

    fun openChatPickerDialog(type: ChatSourceType = ChatSourceType.CHANNEL) {
        _dialogsState.update { it.copy(showChatPickerDialog = true, addSourceType = type) }
        chatPickerJob?.cancel()
        chatPickerJob = viewModelScope.launch {
            _isChatPickerLoading.value = true
            val res = repository.fetchAccountDialogs()
            if (res.isSuccess) {
                val importedIds = repository.importedChatIds()
                _discoveredChats.value = (res.getOrNull() ?: emptyList()).map {
                    it.copy(isAlreadyAdded = it.id in importedIds)
                }
            } else {
                _dialogsState.update { it.copy(infoMessage = "Could not load chats: ${res.exceptionOrNull()?.message}") }
            }
            _isChatPickerLoading.value = false
        }
    }

    fun closeChatPickerDialog() {
        chatPickerJob?.cancel()
        _isChatPickerLoading.value = false
        _dialogsState.update { it.copy(showChatPickerDialog = false) }
    }

    fun searchAccountChats(query: String) {
        if (query.isBlank()) {
            openChatPickerDialog(_dialogsState.value.addSourceType)
            return
        }
        chatPickerJob?.cancel()
        chatPickerJob = viewModelScope.launch {
            _isChatPickerLoading.value = true
            delay(250)
            val res = repository.searchAccountChats(query)
            if (res.isSuccess) {
                val importedIds = repository.importedChatIds()
                _discoveredChats.value = (res.getOrNull() ?: emptyList()).map {
                    it.copy(isAlreadyAdded = it.id in importedIds)
                }
            } else {
                _dialogsState.update { it.copy(infoMessage = "Could not search chats: ${res.exceptionOrNull()?.message}") }
            }
            _isChatPickerLoading.value = false
        }
    }

    fun openStealthAudit() {
        _dialogsState.update { it.copy(showStealthAuditDialog = true) }
    }

    fun closeStealthAudit() {
        _dialogsState.update { it.copy(showStealthAuditDialog = false) }
    }

    fun openAuthGuide() {
        _dialogsState.update { it.copy(showAuthGuideDialog = true) }
    }

    fun closeAuthGuide() {
        _dialogsState.update { it.copy(showAuthGuideDialog = false) }
    }

    fun openTimerDialog() {
        _dialogsState.update { it.copy(showTimerDialog = true) }
    }

    fun closeTimerDialog() {
        _dialogsState.update { it.copy(showTimerDialog = false) }
    }

    fun clearInfoMessage() {
        _dialogsState.update { it.copy(infoMessage = null) }
    }

    fun openSavedMessagesChat() {
        val savedSource = FocusSource(
            id = -1L,
            type = ChatSourceType.SAVED_MESSAGES,
            title = "Saved Messages",
            handleOrId = "me",
            description = "Personal Storage • Real-time Sync",
            avatarColorHex = "#14B8A6"
        )
        openSource(savedSource)
    }



    fun downloadAttachment(context: Context, message: GhostMessage) {
        viewModelScope.launch {
            _isLoadingMore.value = true // Show some global loading or just use snackbar
            val fileId = message.fileAttachment?.fileId ?: message.photoFileId
            var downloadUrl = message.fileAttachment?.localPath ?: message.imageUrl
            
            // If it's a Telegram file and not downloaded, download it first
            if (fileId != 0 && (downloadUrl == null || !java.io.File(downloadUrl).exists())) {
                _dialogsState.update { it.copy(infoMessage = "Fetching from Telegram...") }
                val downloadRes = repository.downloadFile(fileId)
                if (downloadRes.isSuccess) {
                    downloadUrl = downloadRes.getOrNull()
                } else {
                    _dialogsState.update { it.copy(infoMessage = "Telegram download failed: ${downloadRes.exceptionOrNull()?.message}") }
                    _isLoadingMore.value = false
                    return@launch
                }
            }

            val fileName = message.fileAttachment?.fileName
                ?: "ghostgram_media_${message.id}.jpg"
            val mimeType = message.fileAttachment?.mimeType
                ?: "image/jpeg"

            val res = com.example.util.FileDownloadHelper.downloadToDownloadsFolder(
                context = context,
                fileName = fileName,
                mimeType = mimeType,
                downloadUrl = downloadUrl
            )

            if (res.isSuccess) {
                val path = res.getOrNull() ?: fileName
                _dialogsState.update {
                    it.copy(infoMessage = "Downloaded: $path")
                }
            } else {
                _dialogsState.update {
                    it.copy(infoMessage = "Download failed: ${res.exceptionOrNull()?.message}")
                }
            }
            _isLoadingMore.value = false
        }
    }

    suspend fun loadInlineMedia(fileId: Int, onProgress: (Float) -> Unit = {}): String? =
        repository.downloadFile(fileId, onProgress).getOrNull()

    suspend fun loadMusicArtwork(trackKey: String, coverFileId: Int): String? {
        val lock = musicArtworkLocks.getOrPut(trackKey) { Mutex() }
        return lock.withLock {
            val embeddedFile = musicArtworkFile(trackKey, embedded = true)
            if (isValidArtwork(embeddedFile)) {
                embeddedFile.setLastModified(System.currentTimeMillis())
                return@withLock embeddedFile.absolutePath
            } else embeddedFile.delete()

            val telegramFile = musicArtworkFile(trackKey, embedded = false)
            if (isValidArtwork(telegramFile)) {
                telegramFile.setLastModified(System.currentTimeMillis())
                return@withLock telegramFile.absolutePath
            } else telegramFile.delete()
            if (coverFileId <= 0) return@withLock null

            val sourcePath = loadInlineMedia(coverFileId) ?: return@withLock null
            withContext(Dispatchers.IO) {
                val source = File(sourcePath)
                if (!isValidArtwork(source)) return@withContext null
                telegramFile.parentFile?.mkdirs()
                runCatching {
                    source.copyTo(telegramFile, overwrite = true)
                    telegramFile.setLastModified(System.currentTimeMillis())
                    trimMusicArtworkCache()
                    telegramFile.absolutePath.takeIf { isValidArtwork(telegramFile) }
                }.getOrNull()
            }
        }
    }

    suspend fun cacheEmbeddedMusicArtwork(trackKey: String, audioPath: String): String? {
        val lock = musicArtworkLocks.getOrPut(trackKey) { Mutex() }
        return lock.withLock {
            val target = musicArtworkFile(trackKey, embedded = true)
            if (isValidArtwork(target)) {
                target.setLastModified(System.currentTimeMillis())
                return@withLock target.absolutePath
            } else target.delete()
            withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    if (!File(audioPath).isFile) return@withContext null
                    retriever.setDataSource(audioPath)
                    val bytes = retriever.embeddedPicture?.takeIf { it.isNotEmpty() && it.size <= 12 * 1024 * 1024 }
                        ?: return@withContext null
                    if (!isValidArtwork(bytes)) return@withContext null
                    target.parentFile?.mkdirs()
                    val temporary = File(target.parentFile, "${target.name}.tmp")
                    temporary.writeBytes(bytes)
                    if (!temporary.renameTo(target)) {
                        temporary.copyTo(target, overwrite = true)
                        temporary.delete()
                    }
                    target.setLastModified(System.currentTimeMillis())
                    trimMusicArtworkCache()
                    target.absolutePath.takeIf { isValidArtwork(target) }
                } catch (error: Exception) {
                    Log.w("GhostViewModel", "Couldn't cache embedded music artwork", error)
                    null
                } finally {
                    runCatching { retriever.release() }
                }
            }
        }
    }

    private fun musicArtworkFile(trackKey: String, embedded: Boolean): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(trackKey.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(musicArtworkDirectory, "$digest-${if (embedded) "embedded" else "telegram"}.jpg")
    }

    private fun isValidArtwork(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.outWidth > 0 && options.outHeight > 0
        }.getOrDefault(false)
    }

    private fun isValidArtwork(bytes: ByteArray): Boolean = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        options.outWidth > 0 && options.outHeight > 0
    }.getOrDefault(false)

    private fun trimMusicArtworkCache() {
        val files = musicArtworkDirectory.listFiles()?.filter { it.isFile && it.name.endsWith(".jpg") }
            ?.sortedBy { it.lastModified() } ?: return
        var totalBytes = files.sumOf { it.length() }
        for (file in files) {
            if (totalBytes <= 64L * 1024L * 1024L) break
            val size = file.length()
            if (file.delete()) totalBytes -= size
        }
    }

    fun sendLoginCode(phoneNumber: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val res = repository.sendLoginCode(phoneNumber)
            onResult(res)
        }
    }

    fun signInWithCode(
        phoneNumber: String,
        code: String,
        displayName: String = "",
        username: String = "",
        onResult: (Result<UserAccountSession>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.signInWithCode(phoneNumber, code, displayName, username)
            if (res.isSuccess) {
                repository.seedInitialDataIfNeeded()
                _dialogsState.update { it.copy(showLoginDialog = false, infoMessage = "Account connected! Select chats to import") }
                openChatPickerDialog()
            }
            onResult(res)
        }
    }

    fun check2FAPassword(password: String, phone: String, onResult: (Result<UserAccountSession>) -> Unit) {
        viewModelScope.launch {
            val res = repository.check2FAPassword(password, phone)
            if (res.isSuccess) {
                repository.seedInitialDataIfNeeded()
                _dialogsState.update { it.copy(showLoginDialog = false, infoMessage = "Account connected! Select chats to import") }
                openChatPickerDialog()
            }
            onResult(res)
        }
    }

    fun logoutUser() {
        viewModelScope.launch {
            val result = repository.logoutUser()
            // No longer needed: repository.saveConfig(current.copy(isPreviewMode = false))
            _dialogsState.update {
                it.copy(infoMessage = if (result.isSuccess) "Logged out of this account. Other accounts stay signed in." else "Couldn't log out: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun resendCode(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repository.resendCode()
            onResult(res)
        }
    }

    fun removeSource(sourceId: Long) {
        viewModelScope.launch {
            val removedChatId = _musicLibrary.value.importedSources.firstOrNull { it.id == sourceId }?.telegramChatId
            repository.removeSource(sourceId)
            if (removedChatId != null && preferences.selectedMusicSourceChatId() == removedChatId) {
                preferences.setSelectedMusicSourceChatId(null)
                _musicLibrary.update { it.copy(selectedSourceChatId = null, tracksSourceChatId = null, tracks = emptyList(), hasSearched = false) }
            }
            loadMusicSources()
            if (_selectedSource.value?.id == sourceId) {
                _selectedSource.value = null
            }
            _dialogsState.update {
                it.copy(infoMessage = "Source removed from focus list")
            }
        }
    }

    fun addDiscoveredChat(chat: DiscoveredChat) {
        viewModelScope.launch {
            if (repository.isChatImported(chat.id)) {
                _dialogsState.update { it.copy(infoMessage = "Already imported: ${chat.title}") }
                return@launch
            }
            val color = if (chat.type == ChatSourceType.CHANNEL) "#0284C7" else "#10B981"
            val newSource = FocusSource(
                type = chat.type,
                title = chat.title,
                handleOrId = chat.username ?: chat.id.toString(),
                description = when (chat.type) {
                    ChatSourceType.CHANNEL -> "Channel • ${chat.memberCount} subscribers"
                    ChatSourceType.GROUP -> "Group • ${chat.memberCount} members"
                    else -> "Personal chat"
                },
                avatarColorHex = color,
                avatarPath = chat.avatarPath,
                telegramChatId = chat.id
            )
            repository.addSource(newSource)
            loadMusicSources()
            _discoveredChats.value = _discoveredChats.value.map {
                if (it.id == chat.id) it.copy(isAlreadyAdded = true) else it
            }
            _dialogsState.update {
                it.copy(infoMessage = "Added \"${chat.title}\" to your focus list")
            }
        }
    }

    fun openTelegramLink(link: String) {
        viewModelScope.launch {
            val uri = android.net.Uri.parse(link)
            val path = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
            val messageId = path.lastOrNull()?.toLongOrNull()?.takeIf { it > 0 } ?: 0L
            val query = when {
                path.firstOrNull() == "s" -> path.getOrNull(1)
                else -> path.firstOrNull()
            }?.let { "@${it}" } ?: link
            val result = repository.searchAccountChats(query)
            val chat = result.getOrElse { error ->
                _dialogsState.update { it.copy(infoMessage = "Couldn't open Telegram link: ${error.message}") }
                return@launch
            }.firstOrNull()
            if (chat == null) {
                _dialogsState.update { it.copy(infoMessage = "Couldn't find that public Telegram chat") }
                return@launch
            }
            openDiscoveredChat(chat, messageId)
        }
    }

    fun openDiscoveredChat(chat: DiscoveredChat, messageId: Long = 0) {
        viewModelScope.launch {
            val existing = repository.getAllSources().first().firstOrNull { it.telegramChatId == chat.id }
            val source = existing ?: run {
                val newSource = FocusSource(
                    type = chat.type,
                    title = chat.title,
                    handleOrId = chat.username ?: chat.id.toString(),
                    description = when (chat.type) {
                        ChatSourceType.CHANNEL -> "Channel · ${chat.memberCount} subscribers"
                        ChatSourceType.GROUP -> "Group · ${chat.memberCount} members"
                        else -> "Personal chat"
                    },
                    avatarColorHex = if (chat.type == ChatSourceType.CHANNEL) "#0284C7" else "#10B981",
                    avatarPath = chat.avatarPath,
                    telegramChatId = chat.id
                )
                newSource.copy(id = repository.addSource(newSource))
            }
            openSource(source, messageId)
        }
    }


    fun saveConfig(newConfig: SourceConfig) {
        repository.saveConfig(newConfig)
        closeSettingsDialog()
        refreshCurrent()
        _dialogsState.update {
            it.copy(infoMessage = "Settings saved")
        }
    }

    fun testBotToken(token: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val res = repository.testBotConnection(token)
            onResult(res)
        }
    }

    fun startTimer(minutes: Int) {
        timerJob?.cancel()
        val totalSecs = minutes * 60
        _timerState.value = FocusTimerState(
            isRunning = true,
            remainingSeconds = totalSecs,
            totalSeconds = totalSecs,
            isPaused = false
        )
        closeTimerDialog()

        timerJob = viewModelScope.launch {
            while (_timerState.value.remainingSeconds > 0) {
                delay(1000)
                if (!_timerState.value.isPaused) {
                    val next = _timerState.value.remainingSeconds - 1
                    _timerState.value = _timerState.value.copy(remainingSeconds = next)
                }
            }
            _timerState.value = _timerState.value.copy(isRunning = false)
            _dialogsState.update {
                it.copy(infoMessage = "Focus sprint completed! Great focus.")
            }
        }
    }

    fun toggleTimerPause() {
        val curr = _timerState.value
        _timerState.value = curr.copy(isPaused = !curr.isPaused)
    }

    fun stopTimer() {
        timerJob?.cancel()
        _timerState.value = FocusTimerState(isRunning = false)
    }

    override fun onCleared() {
        // Live synchronization is shared with the notification service for this account.
        super.onCleared()
    }
}
