package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.SourceConfig
import com.example.data.model.UserAccountSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GhostPreferences(private val context: Context, val accountId: String = GhostAccounts.get(context).activeId.value) {
    val accountRegistry: GhostAccounts get() = GhostAccounts.get(context)
    private val prefs: SharedPreferences = context.getSharedPreferences(GhostAccounts.preferencesName(accountId), Context.MODE_PRIVATE)

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<SourceConfig> = _configFlow.asStateFlow()

    private val _userSessionFlow = MutableStateFlow(loadUserSession())
    val userSessionFlow: StateFlow<UserAccountSession> = _userSessionFlow.asStateFlow()

    fun loadConfig(): SourceConfig {
        val themeStr = prefs.getString("theme_mode", "system") ?: "system"
        val darkMode = when (themeStr) {
            "dark" -> true
            "light" -> false
            else -> null
        }
        
        return SourceConfig(
            botToken = prefs.getString("bot_token", "") ?: "",
            focusKeywords = prefs.getString("focus_keywords", "urgent, deadline, exam, assignment, homework, class, alert, critical") ?: "urgent, deadline, exam, assignment, homework, class, alert, critical",
            silenceMode = prefs.getBoolean("silence_mode", true),
            stealthAccountActive = prefs.getBoolean("stealth_account_active", true),
            notificationsEnabled = prefs.getBoolean("notifications_enabled", true),
            showNotificationPreview = prefs.getBoolean("show_notification_preview", true),
            showPersonalTab = prefs.getBoolean("show_personal_tab", false),
            includePersonalInFeed = prefs.getBoolean("include_personal_in_feed", false),
            fontSizeScale = prefs.getFloat("font_size_scale", 1.0f),
            autoLoadImagesAndGifs = prefs.getBoolean("auto_load_images_and_gifs", true),
            darkMode = darkMode
        )
    }

    fun saveConfig(config: SourceConfig) {
        val themeStr = when (config.darkMode) {
            true -> "dark"
            false -> "light"
            else -> "system"
        }
        
        prefs.edit()
            .putString("bot_token", config.botToken.trim())
            .putString("focus_keywords", config.focusKeywords)
            .putBoolean("silence_mode", config.silenceMode)
            .putBoolean("stealth_account_active", config.stealthAccountActive)
            .putBoolean("notifications_enabled", config.notificationsEnabled)
            .putBoolean("show_notification_preview", config.showNotificationPreview)
            .putBoolean("show_personal_tab", config.showPersonalTab)
            .putBoolean("include_personal_in_feed", config.includePersonalInFeed)
            .putFloat("font_size_scale", config.fontSizeScale)
            .putBoolean("auto_load_images_and_gifs", config.autoLoadImagesAndGifs)
            .putString("theme_mode", themeStr)
            .apply()
        _configFlow.value = config
    }

    fun loadUserSession(): UserAccountSession {
        val isLoggedIn = prefs.getBoolean("user_is_logged_in", false)
        return UserAccountSession(
            isLoggedIn = isLoggedIn,
            phoneNumber = prefs.getString("user_phone", "") ?: "",
            userName = prefs.getString("user_name", "") ?: "",
            userHandle = prefs.getString("user_handle", "") ?: "",
            userId = prefs.getLong("user_id", 0L),
            profilePicPath = prefs.getString("user_profile_pic", null),
            passwordHint = prefs.getString("user_password_hint", null),
            isGhostModeActive = prefs.getBoolean("user_ghost_active", true),
            blockReadReceipts = prefs.getBoolean("user_block_read_receipts", true),
            blockPresencePings = prefs.getBoolean("user_block_presence_pings", true)
        )
    }

    fun saveUserSession(session: UserAccountSession) {
        prefs.edit()
            .putBoolean("user_is_logged_in", session.isLoggedIn)
            .putString("user_phone", session.phoneNumber)
            .putString("user_name", session.userName)
            .putString("user_handle", session.userHandle)
            .putLong("user_id", session.userId)
            .putString("user_profile_pic", session.profilePicPath)
            .putString("user_password_hint", session.passwordHint)
            .putBoolean("user_ghost_active", session.isGhostModeActive)
            .putBoolean("user_block_read_receipts", session.blockReadReceipts)
            .putBoolean("user_block_presence_pings", session.blockPresencePings)
            .apply()
        _userSessionFlow.value = session
        GhostAccounts.get(context).refresh()
    }

    fun logoutUser() {
        val emptySession = loadUserSession().copy(isLoggedIn = false)
        saveUserSession(emptySession)
    }

    fun isDarkTheme(): Boolean {
        return prefs.getBoolean("dark_theme", true)
    }

    fun setDarkTheme(isDark: Boolean) {
        prefs.edit().putBoolean("dark_theme", isDark).apply()
    }

    fun selectedMusicSourceChatId(): Long? = prefs.getLong("music_source_chat_id", 0L).takeIf { it != 0L }

    fun setSelectedMusicSourceChatId(chatId: Long?) {
        prefs.edit().apply {
            if (chatId == null || chatId == 0L) remove("music_source_chat_id")
            else putLong("music_source_chat_id", chatId)
        }.apply()
    }

    fun hasScannedMusicSource(chatId: Long): Boolean =
        prefs.getStringSet("music_scanned_sources", emptySet()).orEmpty().contains(chatId.toString())

    fun markMusicSourceScanned(chatId: Long) {
        val scanned = prefs.getStringSet("music_scanned_sources", emptySet()).orEmpty().toMutableSet()
        scanned.add(chatId.toString())
        prefs.edit().putStringSet("music_scanned_sources", scanned).apply()
    }

    fun clearMusicSourceScan(chatId: Long) {
        val scanned = prefs.getStringSet("music_scanned_sources", emptySet()).orEmpty().toMutableSet()
        scanned.remove(chatId.toString())
        prefs.edit().putStringSet("music_scanned_sources", scanned).apply()
    }

    fun hasRebuiltMusicArtwork(chatId: Long): Boolean =
        prefs.getStringSet("music_artwork_rebuilt_sources_v2", emptySet()).orEmpty().contains(chatId.toString())

    fun markMusicArtworkRebuilt(chatId: Long) {
        val rebuilt = prefs.getStringSet("music_artwork_rebuilt_sources_v2", emptySet()).orEmpty().toMutableSet()
        rebuilt.add(chatId.toString())
        prefs.edit().putStringSet("music_artwork_rebuilt_sources_v2", rebuilt).apply()
    }
}
