package com.example.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.*
import com.example.ui.components.MessageDateSeparator
import com.example.ui.components.messagesBelongTogether
import com.example.ui.components.sameMessageDay
import com.example.ui.components.ConversationEmptyState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.example.data.model.ChatSourceType
import com.example.data.model.FocusFilter
import com.example.data.model.FocusSource
import com.example.data.model.GhostMessage
import com.example.data.model.ForumTopicSummary
import com.example.data.model.MainTab
import com.example.data.model.UserAccountSession
import com.example.data.model.ChatMessageJump
import com.example.data.local.GhostAccounts
import com.example.data.local.GhostPreferences
import com.example.ui.components.AccountChatPickerSheet
import com.example.ui.components.AudioPlaybackBar
import com.example.ui.components.FocusTimerBanner
import com.example.ui.components.FocusTimerDialog
import com.example.ui.components.GhostLoadingScreen
import com.example.ui.components.GhostBottomNavBar
import com.example.ui.components.MessageItemCard
import com.example.ui.components.PersonalAccountLoginDialog
import com.example.ui.components.SourceListItem
import com.example.ui.components.SourceSettingsDialog
import com.example.ui.components.TelegramUserProfileSheet
import com.example.ui.components.TelegramMusicScreen
import com.example.ui.components.StealthAuditDialog
import com.example.ui.components.StealthTopBar
import com.example.ui.components.TelegramAuthGuideDialog
import com.example.ui.theme.GhostSky
import com.example.ui.theme.GhostTeal
import com.example.ui.viewmodel.GhostUiState
import com.example.ui.viewmodel.GhostViewModel
import kotlinx.coroutines.launch

@Composable
fun MainGhostScreen(
    viewModel: GhostViewModel,
    uiState: GhostUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, viewModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) viewModel.onForeground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val accountRegistry = remember(context) { GhostAccounts.get(context) }
    val accounts by accountRegistry.accounts.collectAsStateWithLifecycle()
    val musicLibrary by viewModel.musicLibrary.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearInfoMessage()
        }
    }

    // Safely start/stop background service based on settings when UI is active (foreground)
    LaunchedEffect(uiState.config.notificationsEnabled, uiState.userSession.isLoggedIn) {
        try {
            val intent = android.content.Intent(context, com.example.service.GhostNotificationService::class.java)
            val anyNotifications = GhostAccounts.get(context).accounts.value.any {
                it.session.isLoggedIn && GhostPreferences(context, it.id).loadConfig().notificationsEnabled
            }
            if (anyNotifications) {
                Log.d("GhostGram", "Starting foreground notification service...")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    androidx.core.content.ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } else {
                Log.d("GhostGram", "Stopping notification service...")
                context.stopService(intent)
            }
        } catch (e: Exception) {
            Log.e("GhostGram", "Foreground service control failed: ${e.message}", e)
        }
    }

    // Wait for initial session/config load to prevent flicker with cool animation
    if (!uiState.isInitialized) {
        GhostLoadingScreen()
        return
    }

    // First time launch: If no account is connected, show Login Gateway
    if (!uiState.userSession.isLoggedIn) {
        val otherAccount = accounts.firstOrNull { it.id != viewModel.accountId && it.session.isLoggedIn }
        val returnToAccount: (() -> Unit)? = otherAccount?.let { account -> { accountRegistry.switchTo(account.id) } }
        LoginGatewayScreen(
            onSendCode = { phone, callback -> viewModel.sendLoginCode(phone, callback) },
            onSignIn = { phone, code, name, username, callback ->
                viewModel.signInWithCode(phone, code, name, username, callback)
            },
            onCheckPassword = { password, phone, callback ->
                viewModel.check2FAPassword(password, phone, callback)
            },
            onResendCode = { callback ->
                viewModel.resendCode(callback)
            },
            onLogout = { viewModel.logoutUser() },
            onBackToAccount = returnToAccount,
            telegramAuthState = uiState.telegramAuthState,
            modifier = modifier
        )
        return
    }

    // Handle Android system back button when in a source detail thread
    if (uiState.selectedSource != null) {
        BackHandler {
            viewModel.closeSourceDetail()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            Surface(
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    if (uiState.selectedSource == null) {
                        StealthTopBar(
                            currentTab = uiState.currentTab,
                            filter = uiState.filter,
                            searchQuery = uiState.searchQuery,
                            onFilterSelect = { viewModel.setFilter(it) },
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            onOpenSettings = { viewModel.openSettingsDialog() },
                            onOpenProfile = {
                                if (uiState.userSession.userId > 0L) viewModel.openUserProfile(uiState.userSession.userId)
                                else viewModel.openSettingsDialog()
                            },
                            onOpenAudit = { viewModel.openStealthAudit() },
                            userSession = uiState.userSession
                        )
                    } else {
                        SourceDetailTopBar(
                            source = uiState.selectedSource,
                            chatSearchQuery = uiState.chatSearchQuery,
                            currentMediaFilter = uiState.currentMediaFilter,
                            onBack = { viewModel.closeSourceDetail() },
                            onDelete = { viewModel.removeSource(uiState.selectedSource.id) },
                            onRefresh = { viewModel.refreshCurrent() },
                            onGoToFirst = { viewModel.goToFirstMessage() },
                            onGoToLatest = { viewModel.goToLatestMessage() },
                            onSearchChange = { viewModel.searchInChat(it) },
                            onFilterMedia = { viewModel.filterByMedia(it) }
                        )
                    }

                    if (uiState.currentTab != MainTab.MUSIC) AudioPlaybackBar()

                    if (uiState.isRefreshing) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    FocusTimerBanner(
                        timerState = uiState.timerState,
                        onTogglePause = { viewModel.toggleTimerPause() },
                        onStop = { viewModel.stopTimer() }
                    )
                }
            }
        },
        bottomBar = {
            if (uiState.selectedSource == null) {
                GhostBottomNavBar(
                    currentTab = uiState.currentTab,
                    showPersonal = uiState.config.showPersonalTab,
                    onTabSelected = { viewModel.selectTab(it) }
                )
            } else {
                Spacer(Modifier.navigationBarsPadding())
            }
        },
        floatingActionButton = {
            if (uiState.selectedSource == null) {
                if (uiState.currentTab == MainTab.GROUPS) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.openChatPickerDialog(ChatSourceType.GROUP) },
                        icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Import Group", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_add_group")
                    )
                } else if (uiState.currentTab == MainTab.CHANNELS) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.openChatPickerDialog(ChatSourceType.CHANNEL) },
                        icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Import Channel", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_add_channel")
                    )
                } else if (uiState.currentTab == MainTab.PERSONAL) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.openChatPickerDialog(ChatSourceType.PERSONAL) },
                        icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Import Personal", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_add_personal")
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.selectedSource != null) {
                // Thread View for a selected Group, Channel, or Saved Messages
                SourceMessagesList(
                    source = uiState.selectedSource,
                    messages = if (uiState.selectedForumTopic != null) uiState.forumTopicMessages else uiState.chatSearchResults ?: uiState.sourceMessages,
                    isLoadingMore = uiState.isLoadingMore || (uiState.isRefreshing &&
                        if (uiState.selectedForumTopic == null) uiState.sourceMessages.isEmpty() else uiState.forumTopicMessages.isEmpty()),
                    messageJump = uiState.messageJump,
                    canLoadNewer = uiState.selectedForumTopic == null && uiState.canLoadNewerHistory,
                    onLoadNewer = { if (uiState.selectedForumTopic == null) viewModel.loadNewerMessages() },
                    onJumpHandled = { viewModel.messageJumpHandled(it) },
                    filter = uiState.filter,
                    searchQuery = uiState.chatSearchQuery.ifBlank { uiState.currentMediaFilter.orEmpty() },
                    currentUserSession = uiState.userSession,
                    fontSizeScale = uiState.config.fontSizeScale,
                    onToggleBookmark = { viewModel.toggleBookmark(it) },
                    onDownloadAttachment = { viewModel.downloadAttachment(context, it) },
                    onLoadInlineMedia = { viewModel.loadInlineMedia(it) },
                    onOpenReply = { viewModel.openReplyMessage(it) },
                    onRefreshReactions = { viewModel.refreshMessageReactions(it) },
                    onLoadReactionUsers = { message, key -> viewModel.getReactionUsers(message, key) },
                    onOpenUserProfile = { viewModel.openUserProfile(it) },
                    onLoadMore = { if (uiState.selectedForumTopic == null) viewModel.loadMoreMessages() },
                    onResetFilter = {
                        viewModel.setFilter(FocusFilter.ALL)
                        viewModel.setSearchQuery("")
                        viewModel.searchInChat("")
                        viewModel.filterByMedia(null)
                    },
                    onRefresh = { viewModel.refreshCurrent() }
                )
            } else {
                // 3 Tabs Content
                androidx.compose.animation.Crossfade(targetState = uiState.currentTab, label = "tab_fade") { tab ->
                    when (tab) {
                        MainTab.FEED -> {
                            FeedTabContent(
                                messages = uiState.feedMessages,
                                filter = uiState.filter,
                                searchQuery = uiState.searchQuery,
                                currentUserSession = uiState.userSession,
                                fontSizeScale = uiState.config.fontSizeScale,
                                onToggleBookmark = { viewModel.toggleBookmark(it) },
                                onDownloadAttachment = { viewModel.downloadAttachment(context, it) },
                                onLoadInlineMedia = { viewModel.loadInlineMedia(it) },
                                onOpenReply = { viewModel.openReplyMessage(it) },
                    onRefreshReactions = { viewModel.refreshMessageReactions(it) },
                                onLoadReactionUsers = { message, key -> viewModel.getReactionUsers(message, key) },
                                onOpenUserProfile = { viewModel.openUserProfile(it) },
                                onOpenSavedMessages = { viewModel.openSavedMessagesChat() },
                                onLoadMoreSearch = { viewModel.loadMoreFeedSearch() },
                                onResetFilter = {
                                    viewModel.setFilter(FocusFilter.ALL)
                                    viewModel.setSearchQuery("")
                                },
                                onRefresh = { viewModel.refreshCurrent() }
                            )
                        }

                        MainTab.GROUPS -> {
                            GroupsTabContent(
                                groups = uiState.groups,
                                onSelectGroup = { viewModel.openSource(it) },
                                onLoadTopics = { viewModel.loadForumTopics(it) },
                                onSelectTopic = { source, topic -> viewModel.openForumTopic(source, topic) },
                                onDeleteGroup = { viewModel.removeSource(it.id) },
                                onAddGroup = { viewModel.openChatPickerDialog(ChatSourceType.GROUP) }
                            )
                        }

                        MainTab.MUSIC -> {
                            TelegramMusicScreen(
                                state = musicLibrary,
                                accountId = viewModel.accountId,
                                onRefresh = { viewModel.refreshMusicLibrary(it) },
                                onLoadMedia = { fileId, onProgress -> viewModel.loadInlineMedia(fileId, onProgress) },
                                onSelectSource = { viewModel.selectMusicSource(it) },
                                onImportChats = { viewModel.openChatPickerDialog() }
                            )
                        }

                        MainTab.CHANNELS -> {
                            ChannelsTabContent(
                                channels = uiState.channels,
                                onSelectChannel = { viewModel.openSource(it) },
                                onDeleteChannel = { viewModel.removeSource(it.id) },
                                onAddChannel = { viewModel.openChatPickerDialog(ChatSourceType.CHANNEL) }
                            )
                        }

                        MainTab.PERSONAL -> {
                            PersonalTabContent(
                                personal = uiState.personal,
                                onSelectPersonal = { viewModel.openSource(it) },
                                onDeletePersonal = { viewModel.removeSource(it.id) },
                                onAddPersonal = { viewModel.openChatPickerDialog(ChatSourceType.PERSONAL) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (uiState.showSettingsDialog) {
        SourceSettingsDialog(
            config = uiState.config,
            session = uiState.userSession,
            onOpenAccountLogin = { viewModel.openLoginDialog() },
            onOpenTimer = { viewModel.openTimerDialog() },
            onOpenAudit = { viewModel.openStealthAudit() },
            onLogout = { viewModel.logoutUser() },
            onSave = { viewModel.saveConfig(it) },
            onDismiss = { viewModel.closeSettingsDialog() }
        )
    }

    if (uiState.showStealthAuditDialog) {
        StealthAuditDialog(
            stats = uiState.stealthStats,
            onDismiss = { viewModel.closeStealthAudit() }
        )
    }

    if (uiState.showAuthGuideDialog) {
        TelegramAuthGuideDialog(
            currentBotToken = uiState.config.botToken,
            onTestBotToken = { token, callback ->
                viewModel.testBotToken(token, callback)
            },
            onDismiss = { viewModel.closeAuthGuide() }
        )
    }

    if (uiState.showLoginDialog) {
        PersonalAccountLoginDialog(
            session = uiState.userSession,
            onSendCode = { phone, callback -> viewModel.sendLoginCode(phone, callback) },
            onSignIn = { phone, code, name, username, callback ->
                viewModel.signInWithCode(phone, code, name, username, callback)
            },
            onCheckPassword = { password, phone, callback ->
                viewModel.check2FAPassword(password, phone, callback)
            },
            onLogout = { viewModel.logoutUser() },
            onOpenChatPicker = { viewModel.openChatPickerDialog() },
            onDismiss = { viewModel.closeLoginDialog() },
            telegramAuthState = uiState.telegramAuthState
        )
    }

    if (uiState.showChatPickerDialog) {
        AccountChatPickerSheet(
            discoveredChats = uiState.discoveredChats,
            isLoading = uiState.isChatPickerLoading,
            onAddChat = { viewModel.addDiscoveredChat(it) },
            onOpenChat = { chat -> viewModel.openDiscoveredChat(chat); viewModel.closeChatPickerDialog() },
            onSearch = { viewModel.searchAccountChats(it) },
            onDismiss = { viewModel.closeChatPickerDialog() },
            initialType = uiState.addSourceType
        )
    }

    uiState.userProfileDialog?.let { profileDialog ->
        TelegramUserProfileSheet(
            state = profileDialog,
            onDismiss = { viewModel.dismissUserProfile() },
            onRetry = { viewModel.openUserProfile(profileDialog.userId) }
        )
    }

    if (uiState.showTimerDialog) {
        FocusTimerDialog(
            onStartTimer = { viewModel.startTimer(it) },
            onDismiss = { viewModel.closeTimerDialog() }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SourceDetailTopBar(
    source: FocusSource,
    chatSearchQuery: String,
    currentMediaFilter: String?,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onGoToFirst: () -> Unit,
    onGoToLatest: () -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterMedia: (String?) -> Unit
) {
    var showSearch by remember(source.id) { mutableStateOf(false) }
    var showMediaExplorer by remember(source.id) { mutableStateOf(false) }
    var showSourceInfo by remember(source.id) { mutableStateOf(false) }
    var showMenu by remember(source.id) { mutableStateOf(false) }
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(showSearch) { if (showSearch) searchFocus.requestFocus() }
    BackHandler(enabled = showSearch) { showSearch = false; onSearchChange(""); keyboard?.hide() }

    if (showSourceInfo) {
        AlertDialog(
            onDismissRequest = { showSourceInfo = false },
            icon = {
                Surface(Modifier.size(76.dp), shape = CircleShape,
                    color = if (source.type == ChatSourceType.CHANNEL) GhostSky.copy(alpha = 0.18f) else MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        if (!source.avatarPath.isNullOrBlank()) AsyncImage(source.avatarPath, null, Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        else Text(source.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            title = { Text(source.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (source.id == -1L) "Saved messages" else if (source.type == ChatSourceType.CHANNEL) "Telegram channel" else "Telegram group",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (source.handleOrId.isNotBlank()) {
                        Text(if (source.handleOrId.toLongOrNull() == null) "@${source.handleOrId.removePrefix("@")}"
                            else "Telegram ID  ${source.handleOrId}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (source.description.isNotBlank()) {
                        Text(source.description, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSourceInfo = false }) { Text("Done") } }
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(20.dp))
                }

                Box(Modifier.size(48.dp).clickable(onClickLabel = "Open ${source.title} info") { showSourceInfo = true },
                    contentAlignment = Alignment.Center) {
                    Surface(Modifier.size(40.dp), shape = CircleShape,
                        color = if (source.type == ChatSourceType.CHANNEL) GhostSky.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!source.avatarPath.isNullOrBlank()) AsyncImage(source.avatarPath, "${source.title} photo",
                                Modifier.fillMaxSize().clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            else Text(source.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Open shared media for ${source.title}") { showMediaExplorer = true }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = source.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.semantics { heading() }.basicMarquee(iterations = Int.MAX_VALUE),
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                    val subtitle = when {
                        source.id == -1L -> "Saved messages"
                        source.type == ChatSourceType.CHANNEL -> "Channel"
                        source.type == ChatSourceType.PERSONAL -> "Private chat"
                        else -> "Group"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { showSearch = !showSearch; if (!showSearch) onSearchChange("") }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Search, "Search messages", Modifier.size(20.dp))
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(48.dp).testTag("chat_options")) {
                        Icon(Icons.Default.MoreVert, "Chat options", Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Go to first message") }, onClick = { showMenu = false; showSearch = false; onGoToFirst() })
                        DropdownMenuItem(text = { Text("Go to latest") }, onClick = { showMenu = false; showSearch = false; onGoToLatest() })
                        DropdownMenuItem(text = { Text("Shared media") }, onClick = { showMenu = false; showMediaExplorer = true })
                        DropdownMenuItem(text = { Text("Refresh") }, onClick = { showMenu = false; onRefresh() })
                        if (source.id != -1L) DropdownMenuItem(text = { Text("Remove from focus", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() })
                    }
                }
            }

            if (showSearch) {
                androidx.compose.material3.OutlinedTextField(
                    value = chatSearchQuery,
                    onValueChange = onSearchChange,
                    label = { Text("Search messages") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .then(Modifier.focusRequester(searchFocus)),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keyboard?.hide() }),
                    singleLine = true,
                    trailingIcon = {
                        if (chatSearchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Close, "Clear search", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                )
            }

            if (currentMediaFilter != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)).padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Showing ${currentMediaFilter.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    IconButton(onClick = { onFilterMedia(null) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, "Clear media filter", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    if (showMediaExplorer) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showMediaExplorer = false }
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp).fillMaxWidth()) {
                Text(
                    source.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "Shared media",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                
                MediaExplorerItem("All Updates", Icons.Default.DynamicFeed, currentMediaFilter == null) {
                    onFilterMedia(null)
                    showMediaExplorer = false
                }
                MediaExplorerItem("Audio Tracks", Icons.Default.LibraryMusic, currentMediaFilter == "audio") {
                    onFilterMedia("audio")
                    showMediaExplorer = false
                }
                MediaExplorerItem("Photos", Icons.Default.Image, currentMediaFilter == "photo") {
                    onFilterMedia("photo")
                    showMediaExplorer = false
                }
                MediaExplorerItem("Links", Icons.Default.Link, currentMediaFilter == "link") {
                    onFilterMedia("link")
                    showMediaExplorer = false
                }
                MediaExplorerItem("Shared Videos", Icons.Default.VideoLibrary, currentMediaFilter == "video") {
                    onFilterMedia("video")
                    showMediaExplorer = false
                }
                MediaExplorerItem("Voice Messages", Icons.Default.Mic, currentMediaFilter == "voice") {
                    onFilterMedia("voice")
                    showMediaExplorer = false
                }
                MediaExplorerItem("Documents & Files", Icons.Default.Description, currentMediaFilter == "document") {
                    onFilterMedia("document")
                    showMediaExplorer = false
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { showMediaExplorer = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MediaExplorerItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { selected = isSelected }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (isSelected) Icon(androidx.compose.material.icons.Icons.Default.Check, "Selected", Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun FeedTabContent(
    messages: List<GhostMessage>,
    filter: FocusFilter,
    searchQuery: String,
    currentUserSession: UserAccountSession,
    fontSizeScale: Float,
    onToggleBookmark: (GhostMessage) -> Unit,
    onDownloadAttachment: (GhostMessage) -> Unit,
    onLoadInlineMedia: suspend (Int) -> String?,
    onOpenReply: (GhostMessage) -> Unit,
    onRefreshReactions: suspend (GhostMessage) -> Unit,
    onLoadReactionUsers: suspend (GhostMessage, String) -> Result<List<com.example.data.model.TelegramReactionUser>>,
    onOpenUserProfile: (Long) -> Unit,
    onOpenSavedMessages: () -> Unit,
    onLoadMoreSearch: () -> Unit,
    onResetFilter: () -> Unit,
    onRefresh: () -> Unit
) {
    val isLightMode = MaterialTheme.colorScheme.surface == Color.White
    
    Box(modifier = Modifier
        .fillMaxSize()
        .background(if (isLightMode) com.example.ui.theme.GhostNewsprint else MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("feed_messages_list")
        ) {
            // Newspaper Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "THE GHOSTGRAM GAZETTE",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = (24 * fontSizeScale).sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = java.text.SimpleDateFormat("EEEE, MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date()).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "DAILY EDITION",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 0.5.sp),
                            color = GhostTeal
                        )
                    }
                    
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(top = 4.dp),
                        thickness = 2.5.dp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(top = 3.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Pinned Saved Messages quick banner at top of feed
            if (filter == FocusFilter.ALL && searchQuery.isBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(2.dp), // Sharper corners for news style
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSavedMessages() }
                            .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            .testTag("saved_messages_feed_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(GhostTeal.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = GhostTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PERSONAL ARCHIVE",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Access your saved dispatches and notes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (messages.isEmpty()) {
                item {
                    EmptyFeedView(
                        filter = filter,
                        searchQuery = searchQuery,
                        onResetFilter = onResetFilter,
                        onRefresh = onRefresh
                    )
                }
            } else {
                items(items = messages, key = { "${it.sourceId}:${it.telegramMsgId}" }) { message ->
                    MessageItemCard(
                        message = message,
                        onToggleBookmark = onToggleBookmark,
                        currentUserSession = currentUserSession,
                        onDownloadAttachment = onDownloadAttachment,
                        onLoadInlineMedia = onLoadInlineMedia,
                        onOpenReply = onOpenReply,
                        onRefreshReactions = onRefreshReactions,
                        onLoadReactionUsers = onLoadReactionUsers,
                        onOpenUserProfile = onOpenUserProfile,
                        isFeedItem = true,
                        fontSizeScale = fontSizeScale
                    )
                    // Add a subtle spacing between articles
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (searchQuery.isNotBlank()) {
                    item {
                        OutlinedButton(onClick = onLoadMoreSearch, modifier = Modifier.fillMaxWidth()) {
                            Text("Load more search results")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupsTabContent(groups: List<FocusSource>, onSelectGroup: (FocusSource) -> Unit,
    onDeleteGroup: (FocusSource) -> Unit, onAddGroup: () -> Unit,
    onLoadTopics: suspend (FocusSource) -> List<ForumTopicSummary>,
    onSelectTopic: (FocusSource, ForumTopicSummary) -> Unit) =
    SourceCollection(groups, ChatSourceType.GROUP, "groups_list", onSelectGroup, onDeleteGroup, onAddGroup,
        onLoadTopics, onSelectTopic)

@Composable
private fun ChannelsTabContent(channels: List<FocusSource>, onSelectChannel: (FocusSource) -> Unit,
    onDeleteChannel: (FocusSource) -> Unit, onAddChannel: () -> Unit) =
    SourceCollection(channels, ChatSourceType.CHANNEL, "channels_list", onSelectChannel, onDeleteChannel, onAddChannel)

@Composable
private fun PersonalTabContent(personal: List<FocusSource>, onSelectPersonal: (FocusSource) -> Unit,
    onDeletePersonal: (FocusSource) -> Unit, onAddPersonal: () -> Unit) =
    SourceCollection(personal, ChatSourceType.PERSONAL, "personal_list", onSelectPersonal, onDeletePersonal, onAddPersonal)

@Composable
private fun SourceCollection(sources: List<FocusSource>, type: ChatSourceType, tag: String,
    onSelect: (FocusSource) -> Unit, onDelete: (FocusSource) -> Unit, onAdd: () -> Unit,
    onLoadTopics: (suspend (FocusSource) -> List<ForumTopicSummary>)? = null,
    onSelectTopic: ((FocusSource, ForumTopicSummary) -> Unit)? = null) {
    if (sources.isEmpty()) { EmptySourcesView(type, onAdd); return }
    val topicScope = androidx.compose.runtime.rememberCoroutineScope()
    val expandedTopicSources = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<Long, List<ForumTopicSummary>>() }
    val expandedSourceIds = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(setOf<Long>()) }
    val loadingTopicSources = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(setOf<Long>()) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize().testTag(tag)) {
            item {
                val noun = when (type) { ChatSourceType.GROUP -> "group"; ChatSourceType.CHANNEL -> "channel"; else -> "conversation" }
                Text("${sources.size} $noun${if (sources.size == 1) "" else "s"} in focus",
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(sources, key = { it.id }) { source ->
                Column {
                    SourceListItem(source, { onSelect(source) }, { onDelete(source) }, onShowTopics = if (onLoadTopics == null) null else ({
                        val isExpanded = source.id in expandedSourceIds.value
                        if (isExpanded) expandedSourceIds.value = expandedSourceIds.value - source.id
                        else {
                            expandedSourceIds.value = expandedSourceIds.value + source.id
                            if (source.id !in expandedTopicSources && source.id !in loadingTopicSources.value) {
                                loadingTopicSources.value = loadingTopicSources.value + source.id
                                topicScope.launch {
                                    try { expandedTopicSources[source.id] = onLoadTopics(source) }
                                    catch (_: Exception) { expandedTopicSources[source.id] = emptyList() }
                                    finally { loadingTopicSources.value = loadingTopicSources.value - source.id }
                                }
                            }
                        }
                    }))
                    if (source.id in expandedSourceIds.value) {
                        val topics = expandedTopicSources[source.id].orEmpty()
                        when {
                            source.id in loadingTopicSources.value -> Text("Loading topics...", Modifier.padding(start = 72.dp, top = 8.dp, bottom = 12.dp),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            topics.isEmpty() -> Text("No topics available", Modifier.padding(start = 72.dp, top = 8.dp, bottom = 12.dp),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> topics.forEach { topic ->
                                Surface(onClick = { onSelectTopic?.invoke(source, topic) },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.fillMaxWidth().padding(start = 58.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Forum, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                            Text(topic.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            topic.lastMessageText?.takeIf { it.isNotBlank() }?.let { preview ->
                                                Text(preview, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        if (topic.unreadCount > 0) {
                                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                                                Text(topic.unreadCount.toString(), Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
@Composable
private fun SourceMessagesList(
    source: FocusSource,
    messages: List<GhostMessage>,
    isLoadingMore: Boolean,
    messageJump: ChatMessageJump?,
    canLoadNewer: Boolean,
    onLoadNewer: () -> Unit,
    onJumpHandled: (Long) -> Unit,
    filter: FocusFilter,
    searchQuery: String,
    currentUserSession: UserAccountSession,
    fontSizeScale: Float,
    onToggleBookmark: (GhostMessage) -> Unit,
    onDownloadAttachment: (GhostMessage) -> Unit,
    onLoadInlineMedia: suspend (Int) -> String?,
    onOpenReply: (GhostMessage) -> Unit,
    onRefreshReactions: suspend (GhostMessage) -> Unit,
    onLoadReactionUsers: suspend (GhostMessage, String) -> Result<List<com.example.data.model.TelegramReactionUser>>,
    onOpenUserProfile: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onResetFilter: () -> Unit,
    onRefresh: () -> Unit
) {
    val listState = androidx.compose.runtime.key(source.id) { androidx.compose.foundation.lazy.rememberLazyListState() }
    var highlightedMessageId by remember(source.id) { mutableStateOf(0L) }
    val jumpIndex = messages.indexOfFirst { it.telegramMsgId == messageJump?.messageId }
    LaunchedEffect(messageJump?.requestId, jumpIndex) {
        if (messageJump != null && messageJump.sourceId == source.id && jumpIndex >= 0) {
            listState.scrollToItem(jumpIndex)
            if (messageJump.highlight) highlightedMessageId = messageJump.messageId
            onJumpHandled(messageJump.requestId)
        }
    }
    LaunchedEffect(highlightedMessageId) {
        if (highlightedMessageId != 0L) {
            kotlinx.coroutines.delay(2500)
            highlightedMessageId = 0L
        }
    }
    val nearNewerEdge = remember(listState) {
        androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex <= 2 }
    }
    LaunchedEffect(nearNewerEdge.value, messages.firstOrNull()?.telegramMsgId, canLoadNewer, messageJump?.requestId) {
        if (nearNewerEdge.value && canLoadNewer && !isLoadingMore && messageJump == null) onLoadNewer()
    }

    // Infinite scroll detection
    val shouldLoadMore = remember(listState) {
        androidx.compose.runtime.derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value, messages.size, isLoadingMore, messageJump?.requestId) {
        if (shouldLoadMore.value && !isLoadingMore && messageJump == null) {
            onLoadMore()
        }
    }

    if (messages.isEmpty() && !isLoadingMore) {
        val filtered = searchQuery.isNotBlank() || filter != FocusFilter.ALL
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ConversationEmptyState(if (filtered) "No matching messages" else "No messages yet",
                if (filtered) "Try another search or clear your filters." else "Messages from ${source.title} will appear here.",
                if (filtered) "Clear filters" else "Refresh", if (filtered) onResetFilter else onRefresh)
        }
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize().testTag("chat_messages_list"),
            reverseLayout = true
        ) {
            itemsIndexed(items = messages, key = { _, item -> "${item.sourceId}:${item.telegramMsgId}" }) { index, message ->
                val older = messages.getOrNull(index + 1)
                Column {
                if (older == null || !sameMessageDay(older.timestamp, message.timestamp)) MessageDateSeparator(message.timestamp)
                MessageItemCard(
                    message = message,
                    onToggleBookmark = onToggleBookmark,
                    currentUserSession = currentUserSession,
                    onDownloadAttachment = onDownloadAttachment,
                    onLoadInlineMedia = onLoadInlineMedia,
                    onOpenReply = onOpenReply,
                        onRefreshReactions = onRefreshReactions,
                        onLoadReactionUsers = onLoadReactionUsers,
                    onOpenUserProfile = onOpenUserProfile,
                    fontSizeScale = fontSizeScale,
                    startsGroup = !messagesBelongTogether(older, message),
                    endsGroup = !messagesBelongTogether(message, messages.getOrNull(index - 1)),
                    modifier = if (message.telegramMsgId == highlightedMessageId) Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)) else Modifier
                )
                }
            }
            
            if (isLoadingMore) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp).semantics { contentDescription = "Loading messages" })
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun EmptySourcesView(
    type: ChatSourceType,
    onAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (type == ChatSourceType.CHANNEL) Icons.Default.Campaign else Icons.Default.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when(type) {
                ChatSourceType.CHANNEL -> "No Channels Added"
                ChatSourceType.GROUP -> "No Groups Added"
                else -> "No Personal Chats"
            },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when(type) {
                ChatSourceType.CHANNEL -> "Import channels from your Telegram account to follow essential broadcasts."
                ChatSourceType.GROUP -> "Import groups from your Telegram account for focused updates."
                else -> "Import personal chats from your Telegram account for quick access."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.testTag("empty_add_source_button")
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(when(type) {
                ChatSourceType.CHANNEL -> "Import Channel"
                ChatSourceType.GROUP -> "Import Group"
                else -> "Import Personal Chat"
            })
        }
    }
}

@Composable
private fun EmptyFeedView(
    filter: FocusFilter,
    searchQuery: String,
    onResetFilter: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.FilterAltOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when {
                searchQuery.isNotBlank() -> "No updates match \"$searchQuery\""
                filter == FocusFilter.PINNED -> "No pinned messages in this feed"
                filter == FocusFilter.URGENT -> "No updates matching radar keywords"
                filter == FocusFilter.BOOKMARKS -> "You haven't bookmarked any updates yet"
                else -> "Feed is quiet & distraction-free"
            },
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your 3 focus feeds only show content from groups and channels you configured.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filter != FocusFilter.ALL || searchQuery.isNotBlank()) {
                OutlinedButton(
                    onClick = onResetFilter,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("reset_filter_button")
                ) {
                    Text("Show All Updates")
                }
            }

            OutlinedButton(
                onClick = onRefresh,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("empty_refresh_button")
            ) {
                Text("Check For Updates")
            }
        }
    }
}
