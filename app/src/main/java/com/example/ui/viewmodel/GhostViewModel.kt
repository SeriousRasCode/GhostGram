package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.GhostDatabase
import com.example.data.local.GhostPreferences
import com.example.data.model.ChatSourceType
import com.example.data.model.DiscoveredChat
import com.example.data.model.FocusFilter
import com.example.data.model.FocusSource
import com.example.data.model.GhostMessage
import com.example.data.model.MainTab
import com.example.data.model.SourceConfig
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FocusTimerState(
    val isRunning: Boolean = false,
    val remainingSeconds: Int = 25 * 60,
    val totalSeconds: Int = 25 * 60,
    val isPaused: Boolean = false
)

data class DialogsState(
    val showAddSourceDialog: Boolean = false,
    val addSourceType: ChatSourceType = ChatSourceType.CHANNEL,
    val showLoginDialog: Boolean = false,
    val showChatPickerDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showStealthAuditDialog: Boolean = false,
    val showAuthGuideDialog: Boolean = false,
    val showTimerDialog: Boolean = false,
    val infoMessage: String? = null
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
    val config: SourceConfig = SourceConfig(),
    val stealthStats: StealthStats = StealthStats(),
    val timerState: FocusTimerState = FocusTimerState(),
    val showAddSourceDialog: Boolean = false,
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
    val isInitialized: Boolean = false
)

class GhostViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: GhostRepository
    private val preferences: GhostPreferences

    private val _currentTab = MutableStateFlow(MainTab.FEED)
    private val _selectedSource = MutableStateFlow<FocusSource?>(null)
    private val _filter = MutableStateFlow(FocusFilter.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val _isRefreshing = MutableStateFlow(false)
    private val _discoveredChats = MutableStateFlow<List<DiscoveredChat>>(emptyList())
    private val _dialogsState = MutableStateFlow(DialogsState())
    private val _timerState = MutableStateFlow(FocusTimerState())
    private val _isLoadingMore = MutableStateFlow(false)
    private val _chatSearchQuery = MutableStateFlow("")
    private val _chatSearchResults = MutableStateFlow<List<GhostMessage>?>(null)
    private val _currentMediaFilter = MutableStateFlow<String?>(null)

    private var timerJob: Job? = null

    init {
        val db = GhostDatabase.getDatabase(application)
        preferences = GhostPreferences(application)
        val remoteClient = com.example.data.remote.TelegramGhostClient()
        val userClient = com.example.data.remote.TelegramUserClient.getInstance(application)
        repository = GhostRepository(db, preferences, remoteClient, userClient)

        // Reactive initialization based on auth state
        viewModelScope.launch {
            try {
                repository.authStateFlow.collect { state ->
                    if (state is org.drinkless.tdlib.TdApi.AuthorizationStateReady) {
                        Log.d("GhostViewModel", "TDLib Ready, triggering startup sync...")
                        delay(1500) // Safety delay for state propagation
                        
                        repository.refreshUserSession()
                        repository.seedInitialDataIfNeeded()
                        repository.refreshAllSources()
                        
                        val chatsRes = repository.fetchAccountDialogs()
                        if (chatsRes.isSuccess) {
                            _discoveredChats.value = chatsRes.getOrNull() ?: emptyList()
                        }
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
        if (src != null) repository.getMessagesForSource(src.id)
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

    private val messagePayloadsFlow = combine(allFeedMessages, sourceMessagesFlow) { feed, srcMsgs ->
        MessagePayloads(feed, srcMsgs)
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
        val filteredFeed = filterMessages(messages.feed, nav.filter, nav.searchQuery)
        val filteredSource = filterMessages(messages.sourceMsgs, nav.filter, nav.searchQuery)

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
            _currentMediaFilter
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
            config = config,
            stealthStats = StealthStats(
                presencePingsSent = 0,
                lastSeenFrozenText = "Month ago (Untouched)",
                readReceiptsBlocked = 38,
                dmsFilteredCount = 67,
                isGhostActive = true
            ),
            timerState = core.timerState,
            showAddSourceDialog = dialogs.showAddSourceDialog,
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
            isInitialized = true
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
                        msg.senderName.contains(query, ignoreCase = true) ||
                        msg.sourceTitle.contains(query, ignoreCase = true)
            }

            matchesFilter && matchesQuery
        }
    }

    fun selectTab(tab: MainTab) {
        _currentTab.value = tab
        _selectedSource.value = null
    }

    fun openSource(source: FocusSource) {
        _selectedSource.value = source
        refreshCurrent() // Auto-refresh to ensure latest content
    }

    fun closeSourceDetail() {
        _selectedSource.value = null
    }

    fun setFilter(filter: FocusFilter) {
        _filter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshCurrent() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val src = _selectedSource.value
            if (src != null) {
                repository.refreshSource(src, 0)
            } else {
                repository.refreshAllSources()
            }
            delay(350)
            _isRefreshing.value = false
        }
    }

    fun loadMoreMessages() {
        val src = _selectedSource.value ?: return
        if (_isLoadingMore.value) return
        
        viewModelScope.launch {
            _isLoadingMore.value = true
            val lastMsgId = uiState.value.sourceMessages.lastOrNull()?.telegramMsgId ?: 0L
            if (lastMsgId != 0L) {
                repository.refreshSource(src, lastMsgId)
            }
            delay(300)
            _isLoadingMore.value = false
        }
    }

    fun searchInChat(query: String) {
        _chatSearchQuery.value = query
        val src = _selectedSource.value ?: return
        if (query.isBlank()) {
            _chatSearchResults.value = null
            return
        }
        
        viewModelScope.launch {
            val chatId = if (src.telegramChatId != 0L) src.telegramChatId else src.handleOrId.toLongOrNull() ?: 0L
            if (chatId != 0L) {
                val results = repository.searchChatMessages(chatId, query)
                _chatSearchResults.value = results
            }
        }
    }

    fun filterByMedia(type: String?) {
        _currentMediaFilter.value = type
        val src = _selectedSource.value ?: return
        if (type == null) {
            _chatSearchResults.value = null
            return
        }

        val filter = when (type) {
            "audio" -> org.drinkless.tdlib.TdApi.SearchMessagesFilterAudio()
            "video" -> org.drinkless.tdlib.TdApi.SearchMessagesFilterVideo()
            "voice" -> org.drinkless.tdlib.TdApi.SearchMessagesFilterVoiceNote()
            "document" -> org.drinkless.tdlib.TdApi.SearchMessagesFilterDocument()
            else -> org.drinkless.tdlib.TdApi.SearchMessagesFilterEmpty()
        }

        viewModelScope.launch {
            val chatId = if (src.telegramChatId != 0L) src.telegramChatId else src.handleOrId.toLongOrNull() ?: 0L
            if (chatId != 0L) {
                val results = repository.getChatMedia(chatId, filter)
                _chatSearchResults.value = results
            }
        }
    }

    fun toggleBookmark(message: GhostMessage) {
        viewModelScope.launch {
            repository.toggleBookmark(message.telegramMsgId, message.isBookmarked)
        }
    }

    // Dialog controls - 100% reactive state updates
    fun openAddSourceDialog(type: ChatSourceType) {
        _dialogsState.update { it.copy(showAddSourceDialog = true, addSourceType = type) }
    }

    fun closeAddSourceDialog() {
        _dialogsState.update { it.copy(showAddSourceDialog = false) }
    }

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

    fun openChatPickerDialog() {
        _dialogsState.update { it.copy(showChatPickerDialog = true) }
        viewModelScope.launch {
            val res = repository.fetchAccountDialogs()
            if (res.isSuccess) {
                _discoveredChats.value = res.getOrNull() ?: emptyList<DiscoveredChat>()
            }
        }
    }

    fun closeChatPickerDialog() {
        _dialogsState.update { it.copy(showChatPickerDialog = false) }
    }

    fun searchAccountChats(query: String) {
        if (query.isBlank()) {
            openChatPickerDialog() // reload defaults
            return
        }
        viewModelScope.launch {
            val res = repository.searchAccountChats(query)
            if (res.isSuccess) {
                _discoveredChats.value = res.getOrNull() ?: emptyList()
            }
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
        _selectedSource.value = savedSource
        refreshCurrent()
    }



    fun downloadAttachment(context: Context, message: GhostMessage) {
        viewModelScope.launch {
            _isLoadingMore.value = true // Show some global loading or just use snackbar
            val fileId = message.fileAttachment?.fileId
            var downloadUrl = message.fileAttachment?.localPath ?: message.imageUrl
            
            // If it's a Telegram file and not downloaded, download it first
            if (fileId != null && fileId != 0 && (downloadUrl == null || !java.io.File(downloadUrl).exists())) {
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
                val chatsRes = repository.fetchAccountDialogs()
                if (chatsRes.isSuccess) {
                    _discoveredChats.value = chatsRes.getOrNull() ?: emptyList()
                }
                _dialogsState.update {
                    it.copy(
                        showLoginDialog = false,
                        showChatPickerDialog = true,
                        infoMessage = "Account connected! Select class groups & channels"
                    )
                }
            }
            onResult(res)
        }
    }

    fun check2FAPassword(password: String, phone: String, onResult: (Result<UserAccountSession>) -> Unit) {
        viewModelScope.launch {
            val res = repository.check2FAPassword(password, phone)
            if (res.isSuccess) {
                repository.seedInitialDataIfNeeded()
                val chatsRes = repository.fetchAccountDialogs()
                if (chatsRes.isSuccess) {
                    _discoveredChats.value = chatsRes.getOrNull() ?: emptyList()
                }
                _dialogsState.update {
                    it.copy(
                        showLoginDialog = false,
                        showChatPickerDialog = true,
                        infoMessage = "2FA Verified! Select class groups & channels"
                    )
                }
            }
            onResult(res)
        }
    }

    fun logoutUser() {
        viewModelScope.launch {
            repository.logoutUser()
            // No longer needed: repository.saveConfig(current.copy(isPreviewMode = false))
            _dialogsState.update {
                it.copy(infoMessage = "Logged out from Telegram account")
            }
        }
    }

    fun resendCode(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repository.resendCode()
            onResult(res)
        }
    }

    fun addNewSource(title: String, handleOrId: String, description: String, type: ChatSourceType) {
        viewModelScope.launch {
            val colorHex = if (type == ChatSourceType.CHANNEL) "#0284C7" else "#10B981"
            val newSource = FocusSource(
                type = type,
                title = title.trim(),
                handleOrId = handleOrId.trim().removePrefix("@"),
                description = description.trim(),
                avatarColorHex = colorHex
            )
            repository.addSource(newSource)
            closeAddSourceDialog()
            _dialogsState.update {
                it.copy(infoMessage = "Added \"$title\" to your focus feeds")
            }
        }
    }

    fun removeSource(sourceId: Long) {
        viewModelScope.launch {
            repository.removeSource(sourceId)
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
            val color = if (chat.type == ChatSourceType.CHANNEL) "#0284C7" else "#10B981"
            val newSource = FocusSource(
                type = chat.type,
                title = chat.title,
                handleOrId = chat.username ?: chat.id.toString(),
                description = if (chat.type == ChatSourceType.CHANNEL) "Channel • ${chat.memberCount} subscribers" else "Class Group • ${chat.memberCount} members",
                avatarColorHex = color,
                avatarPath = chat.avatarPath,
                telegramChatId = chat.id
            )
            repository.addSource(newSource)
            _dialogsState.update {
                it.copy(infoMessage = "Added \"${chat.title}\" to your focus list")
            }
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
}
