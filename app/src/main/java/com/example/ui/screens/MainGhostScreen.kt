package com.example.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.example.data.model.ChatSourceType
import com.example.data.model.FocusFilter
import com.example.data.model.FocusSource
import com.example.data.model.GhostMessage
import com.example.data.model.MainTab
import com.example.data.model.UserAccountSession
import com.example.ui.components.AccountChatPickerSheet
import com.example.ui.components.AddSourceDialog
import com.example.ui.components.FocusTimerBanner
import com.example.ui.components.FocusTimerDialog
import com.example.ui.components.GhostLoadingScreen
import com.example.ui.components.GhostBottomNavBar
import com.example.ui.components.MessageItemCard
import com.example.ui.components.PersonalAccountLoginDialog
import com.example.ui.components.SourceListItem
import com.example.ui.components.SourceSettingsDialog
import com.example.ui.components.StealthAuditDialog
import com.example.ui.components.StealthTopBar
import com.example.ui.components.TelegramAuthGuideDialog
import com.example.ui.theme.GhostSky
import com.example.ui.theme.GhostTeal
import com.example.ui.viewmodel.GhostUiState
import com.example.ui.viewmodel.GhostViewModel

@Composable
fun MainGhostScreen(
    viewModel: GhostViewModel,
    uiState: GhostUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearInfoMessage()
        }
    }

    // Safely start/stop background service based on settings when UI is active (foreground)
    LaunchedEffect(uiState.config.notificationsEnabled) {
        try {
            val intent = android.content.Intent(context, com.example.service.GhostNotificationService::class.java)
            if (uiState.config.notificationsEnabled) {
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
                tonalElevation = 3.dp,
                shadowElevation = 2.dp,
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
                            onSearchChange = { viewModel.searchInChat(it) },
                            onFilterMedia = { viewModel.filterByMedia(it) }
                        )
                    }

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
                Surface(
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = GhostTeal.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Read-only Stealth Mode • Presence Protected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (uiState.selectedSource == null) {
                if (uiState.currentTab == MainTab.GROUPS) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.openAddSourceDialog(ChatSourceType.GROUP) },
                        icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Add Group", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_add_group")
                    )
                } else if (uiState.currentTab == MainTab.CHANNELS) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.openAddSourceDialog(ChatSourceType.CHANNEL) },
                        icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Add Channel", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_add_channel")
                    )
                } else if (uiState.currentTab == MainTab.PERSONAL) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.openAddSourceDialog(ChatSourceType.PERSONAL) },
                        icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Add Personal", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
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
                    messages = uiState.chatSearchResults ?: uiState.sourceMessages,
                    isLoadingMore = uiState.isLoadingMore,
                    filter = uiState.filter,
                    searchQuery = uiState.searchQuery,
                    currentUserSession = uiState.userSession,
                    fontSizeScale = uiState.config.fontSizeScale,
                    onToggleBookmark = { viewModel.toggleBookmark(it) },
                    onDownloadAttachment = { viewModel.downloadAttachment(context, it) },
                    onLoadMore = { viewModel.loadMoreMessages() },
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
                                onOpenSavedMessages = { viewModel.openSavedMessagesChat() },
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
                                onDeleteGroup = { viewModel.removeSource(it.id) },
                                onAddGroup = { viewModel.openAddSourceDialog(ChatSourceType.GROUP) }
                            )
                        }

                        MainTab.CHANNELS -> {
                            ChannelsTabContent(
                                channels = uiState.channels,
                                onSelectChannel = { viewModel.openSource(it) },
                                onDeleteChannel = { viewModel.removeSource(it.id) },
                                onAddChannel = { viewModel.openAddSourceDialog(ChatSourceType.CHANNEL) }
                            )
                        }

                        MainTab.PERSONAL -> {
                            PersonalTabContent(
                                personal = uiState.personal,
                                onSelectPersonal = { viewModel.openSource(it) },
                                onDeletePersonal = { viewModel.removeSource(it.id) },
                                onAddPersonal = { viewModel.openAddSourceDialog(ChatSourceType.PERSONAL) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (uiState.showAddSourceDialog) {
        AddSourceDialog(
            initialType = uiState.addSourceType,
            onAdd = { title, handle, desc, type ->
                viewModel.addNewSource(title, handle, desc, type)
            },
            onDismiss = { viewModel.closeAddSourceDialog() },
            onImportFromAccount = {
                viewModel.closeAddSourceDialog()
                viewModel.openChatPickerDialog()
            }
        )
    }

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
            onAddChat = { viewModel.addDiscoveredChat(it) },
            onSearch = { viewModel.searchAccountChats(it) },
            onDismiss = { viewModel.closeChatPickerDialog() },
            initialType = uiState.addSourceType
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
private fun SourceDetailTopBar(
    source: FocusSource,
    chatSearchQuery: String,
    currentMediaFilter: String?,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterMedia: (String?) -> Unit
) {
    var showSearch by remember { mutableStateOf(false) }
    var showMediaExplorer by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }

                // Profile Circle in Detail View for consistency
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = if (source.type == ChatSourceType.CHANNEL) GhostSky.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (!source.avatarPath.isNullOrBlank()) {
                            AsyncImage(
                                model = source.avatarPath,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = source.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showMediaExplorer = true }
                ) {
                    Text(
                        text = source.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = when {
                        source.id == -1L -> "Encrypted Personal Storage"
                        source.type == ChatSourceType.CHANNEL -> {
                            if (source.handleOrId.contains("-") || source.handleOrId.all { it.isDigit() }) "Broadcast Channel"
                            else "@${source.handleOrId}"
                        }
                        source.type == ChatSourceType.PERSONAL -> "Stealth Contact"
                        else -> "Focus Group"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (source.type == ChatSourceType.PERSONAL) GhostTeal else GhostSky
                    )
                }

                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(Icons.Default.Search, "Search")
                }

                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }

                if (source.id != -1L) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (showSearch) {
                androidx.compose.material3.OutlinedTextField(
                    value = chatSearchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search messages...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    trailingIcon = {
                        if (chatSearchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )
            }

            if (currentMediaFilter != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filtering by: ${currentMediaFilter.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onFilterMedia(null) }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, null)
                    }
                }
            }
        }
    }

    if (showMediaExplorer) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showMediaExplorer = false }
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(
                    "Media Explorer", 
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "Browse shared content categories", 
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun MediaChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.height(28.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    onOpenSavedMessages: () -> Unit,
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
                            fontSize = (20 * fontSizeScale).sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                        ),
                        color = MaterialTheme.colorScheme.onBackground
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
                            text = "LATE EDITION",
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
                items(items = messages, key = { it.telegramMsgId }) { message ->
                    MessageItemCard(
                        message = message,
                        onToggleBookmark = onToggleBookmark,
                        currentUserSession = currentUserSession,
                        onDownloadAttachment = onDownloadAttachment,
                        isFeedItem = true,
                        fontSizeScale = fontSizeScale
                    )
                    // Add a subtle spacing between articles
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun GroupsTabContent(
    groups: List<FocusSource>,
    onSelectGroup: (FocusSource) -> Unit,
    onDeleteGroup: (FocusSource) -> Unit,
    onAddGroup: () -> Unit
) {
    if (groups.isEmpty()) {
        EmptySourcesView(
            type = ChatSourceType.GROUP,
            onAdd = onAddGroup
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("groups_list")
        ) {
            item {
                Text(
                    text = "${groups.size} ALLOWED GROUPS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(items = groups, key = { it.id }) { group ->
                SourceListItem(
                    source = group,
                    onClick = { onSelectGroup(group) },
                    onDelete = { onDeleteGroup(group) }
                )
            }
        }
    }
}

@Composable
private fun ChannelsTabContent(
    channels: List<FocusSource>,
    onSelectChannel: (FocusSource) -> Unit,
    onDeleteChannel: (FocusSource) -> Unit,
    onAddChannel: () -> Unit
) {
    if (channels.isEmpty()) {
        EmptySourcesView(
            type = ChatSourceType.CHANNEL,
            onAdd = onAddChannel
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("channels_list")
        ) {
            item {
                Text(
                    text = "${channels.size} ALLOWED CHANNELS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(items = channels, key = { it.id }) { channel ->
                SourceListItem(
                    source = channel,
                    onClick = { onSelectChannel(channel) },
                    onDelete = { onDeleteChannel(channel) }
                )
            }
        }
    }
}

@Composable
private fun PersonalTabContent(
    personal: List<FocusSource>,
    onSelectPersonal: (FocusSource) -> Unit,
    onDeletePersonal: (FocusSource) -> Unit,
    onAddPersonal: () -> Unit
) {
    if (personal.isEmpty()) {
        EmptySourcesView(
            type = ChatSourceType.PERSONAL,
            onAdd = onAddPersonal
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("personal_list")
        ) {
            item {
                Text(
                    text = "${personal.size} PERSONAL CHATS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(items = personal, key = { it.id }) { person ->
                SourceListItem(
                    source = person,
                    onClick = { onSelectPersonal(person) },
                    onDelete = { onDeletePersonal(person) }
                )
            }
        }
    }
}

@Composable
private fun SourceMessagesList(
    source: FocusSource,
    messages: List<GhostMessage>,
    isLoadingMore: Boolean,
    filter: FocusFilter,
    searchQuery: String,
    currentUserSession: UserAccountSession,
    fontSizeScale: Float,
    onToggleBookmark: (GhostMessage) -> Unit,
    onDownloadAttachment: (GhostMessage) -> Unit,
    onLoadMore: () -> Unit,
    onResetFilter: () -> Unit,
    onRefresh: () -> Unit
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Infinite scroll detection
    val shouldLoadMore = remember {
        androidx.compose.runtime.derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex > 0 && lastVisibleItemIndex >= totalItems - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoadingMore) {
            onLoadMore()
        }
    }

    if (messages.isEmpty() && !isLoadingMore) {
        EmptyFeedView(
            filter = filter,
            searchQuery = searchQuery,
            onResetFilter = onResetFilter,
            onRefresh = onRefresh
        )
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
            reverseLayout = true
        ) {
            items(items = messages, key = { it.telegramMsgId }) { message ->
                MessageItemCard(
                    message = message,
                    onToggleBookmark = onToggleBookmark,
                    currentUserSession = currentUserSession,
                    onDownloadAttachment = onDownloadAttachment,
                    fontSizeScale = fontSizeScale
                )
            }
            
            if (isLoadingMore) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
                ChatSourceType.CHANNEL -> "Add your essential Telegram broadcast channels to receive updates without online presence."
                ChatSourceType.GROUP -> "Add your core collaborative groups here. All other chats and DMs will stay 100% blocked."
                else -> "Add important 1-on-1 contacts to your focus list for quick access and priority notifications."
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
                ChatSourceType.CHANNEL -> "Add Channel"
                ChatSourceType.GROUP -> "Add Group"
                else -> "Add Personal Chat"
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
