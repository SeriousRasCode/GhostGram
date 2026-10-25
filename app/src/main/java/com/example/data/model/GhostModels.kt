package com.example.data.model

enum class MainTab {
    FEED,
    GROUPS,
    MUSIC,
    CHANNELS,
    PERSONAL
}

enum class ChatSourceType {
    CHANNEL,
    GROUP,
    SAVED_MESSAGES,
    PERSONAL
}

enum class FocusFilter {
    ALL,
    PINNED,
    URGENT,
    BOOKMARKS // Saved Messages
}

enum class AuthStep {
    PHONE,
    CODE,
    PASSWORD,
    SUCCESS
}

data class UserAccountSession(
    val isLoggedIn: Boolean = false,
    val phoneNumber: String = "",
    val userName: String = "",
    val userHandle: String = "",
    val userId: Long = 0,
    val profilePicPath: String? = null,
    val passwordHint: String? = null,
    val isGhostModeActive: Boolean = true,
    val blockReadReceipts: Boolean = true,
    val blockPresencePings: Boolean = true
)

data class DiscoveredChat(
    val id: Long,
    val title: String,
    val type: ChatSourceType,
    val username: String? = null,
    val avatarPath: String? = null,
    val memberCount: Int = 0,
    val lastMessage: String? = null,
    val isAlreadyAdded: Boolean = false
)

data class ForumTopicSummary(
    val id: Int,
    val name: String,
    val unreadCount: Int = 0,
    val lastMessageText: String? = null,
    val isGeneral: Boolean = false
)

data class TelegramUserProfile(
    val userId: Long,
    val firstName: String,
    val lastName: String = "",
    val username: String? = null,
    val phoneNumber: String? = null,
    val bio: String? = null,
    val photoPath: String? = null,
    val isContact: Boolean = false,
    val isMutualContact: Boolean = false,
    val fullDetailsUnavailable: Boolean = false
)

data class TelegramReactionUser(val id: Long, val name: String, val username: String? = null)

data class FileAttachment(
    val fileName: String,
    val fileSizeFormatted: String,
    val mimeType: String,
    val downloadUrl: String? = null,
    val localPath: String? = null,
    val fileId: Int = 0
)

data class FocusSource(
    val id: Long = 0,
    val type: ChatSourceType,
    val title: String,
    val handleOrId: String,
    val description: String = "",
    val avatarColorHex: String = "#0284C7",
    val avatarPath: String? = null,
    val unreadCount: Int = 0,
    val lastMessageText: String? = null,
    val lastMessageTime: Long = System.currentTimeMillis(),
    val isMuted: Boolean = false,
    val telegramChatId: Long = 0
)

data class GhostMessage(
    val id: Long,
    val sourceId: Long = 0,
    val sourceTitle: String = "",
    val sourceAvatarPath: String? = null,
    val chatType: ChatSourceType,
    val telegramMsgId: Long,
    val senderId: Long = 0,
    val senderName: String,
    val senderHandle: String,
    val text: String,
    val timestamp: Long,
    val imageUrl: String? = null,
    val stickerPath: String? = null,
    val videoPath: String? = null,
    val audioPath: String? = null,
    val voicePath: String? = null,
    val fileAttachment: FileAttachment? = null,
    val animationMimeType: String? = null,
    val replyToMessageId: Long = 0,
    val replyToSenderName: String? = null,
    val replyToText: String? = null,
    val isPinned: Boolean = false,
    val views: String? = null,
    val reactions: String? = null,
    val isBookmarked: Boolean = false, // Saved to Saved Messages
    val isUrgent: Boolean = false,
    val telegramChatId: Long = 0,
    val photoFileId: Int = 0,
    val formattedEntities: String? = null,
    val commentCount: Int = -1, // -1: no discussion; 0: discussion exists with no replies yet
    val stickerFileId: Int = 0,
    val stickerFormat: String? = null,
    val stickerThumbnailFileId: Int = 0,
    val stickerThumbnailPath: String? = null,
    val stickerEmoji: String? = null,
    val replyToChatId: Long = 0,
    val audioTitle: String? = null,
    val audioPerformer: String? = null,
    val audioDurationSeconds: Int = 0,
    val audioCoverFileId: Int = 0
)

data class MusicLibraryUiState(
    val tracks: List<GhostMessage> = emptyList(),
    val importedSources: List<FocusSource> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingSources: Boolean = false,
    val hasSearched: Boolean = false,
    val selectedSourceChatId: Long? = null,
    val tracksSourceChatId: Long? = null,
    val searchedChats: Int = 0,
    val totalChats: Int = 0,
    val error: String? = null
)

data class SourceConfig(
    val botToken: String = "",
    val focusKeywords: String = "urgent, deadline, exam, assignment, homework, class, alert, critical",
    val silenceMode: Boolean = true,
    val stealthAccountActive: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val showNotificationPreview: Boolean = true,
    val notificationDetailLevel: Int = 1, // 0: None, 1: Basic, 2: Full
    val showPersonalTab: Boolean = false,
    val includePersonalInFeed: Boolean = false,
    val fontSizeScale: Float = 1.0f,
    val darkMode: Boolean? = null // null: System, true: Dark, false: Light
)

data class StealthStats(
    val presencePingsSent: Int = 0,
    val lastSeenFrozenText: String = "Month ago (Untouched)",
    val readReceiptsBlocked: Int = 38,
    val dmsFilteredCount: Int = 67,
    val isGhostActive: Boolean = true
)
