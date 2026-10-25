package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.GhostAccounts
import com.example.data.model.SourceConfig
import com.example.data.model.UserAccountSession

@Composable
fun SourceSettingsDialog(
    config: SourceConfig,
    session: UserAccountSession,
    onOpenAccountLogin: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenAudit: () -> Unit,
    onLogout: () -> Unit,
    onSave: (SourceConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val registry = remember(context) { GhostAccounts.get(context) }
    val accounts by registry.accounts.collectAsStateWithLifecycle()
    val activeId by registry.activeId.collectAsStateWithLifecycle()
    var draft by remember(config) { mutableStateOf(config) }
    var tab by remember { mutableStateOf(0) }
    var logout by remember { mutableStateOf(false) }
    var aiSettings by remember { mutableStateOf(false) }
    val accountDisplayName = session.userName.trim().takeIf { it.isNotBlank() }
        ?: session.userHandle.trim().removePrefix("@").takeIf { it.isNotBlank() }
        ?: session.phoneNumber.takeIf { it.isNotBlank() }
        ?: if (session.isLoggedIn) "Telegram account" else "Connect Telegram"
    val accountSubtitle = session.userHandle.trim().takeIf { it.isNotBlank() }?.let { "@${it.removePrefix("@")}" }
        ?: session.phoneNumber.takeIf { it.isNotBlank() }
        ?: if (session.isLoggedIn) "Connected account" else "Sign in to connect your account"
    if (aiSettings) GeminiSettingsDialog(onDismiss = { aiSettings = false })
    if (logout) CompactDialog(onDismissRequest = { logout = false },
        title = { Text("Log out of Telegram?") },
        text = { Text("You can sign in again from your profile.") },
        dismissButton = { TextButton(onClick = { logout = false }) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = { logout = false; onDismiss(); onLogout() }) { Text("Log out", color = MaterialTheme.colorScheme.error) } })
    CompactDialog(onDismissRequest = onDismiss,
        title = { DialogHeading("Settings", onDismiss, "close_settings_icon") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // Equal-width tabs avoid horizontal scrolling and oversized labels.
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf("Profile", "Alerts", "Display", "Focus").forEachIndexed { index, label ->
                        Surface(onClick = { tab = index }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (tab == index) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow,
                            shadowElevation = if (tab == index) 1.dp else 0.dp,
                            border = if (tab == index) BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) else null) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (tab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1)
                            }
                        }
                    }
                }
                }
                key(tab) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (tab) {
                            0 -> {
                                Surface(shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))) {
                                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AccountAvatar(accountDisplayName, session.profilePicPath, 60.dp)
                                        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(accountDisplayName, style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(accountSubtitle,
                                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(if (session.isLoggedIn && session.isGhostModeActive) "●  Connected · Offline mode"
                                                else if (session.isLoggedIn) "●  Connected to Telegram" else "Not connected",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (session.isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = { if (session.isLoggedIn) logout = true else { onDismiss(); onOpenAccountLogin() } },
                                            modifier = Modifier.testTag(if (session.isLoggedIn) "logout_settings_button" else "login_settings_button")) {
                                            Icon(if (session.isLoggedIn) Icons.Default.Logout else Icons.Default.Login,
                                                if (session.isLoggedIn) "Log out" else "Connect Telegram", Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                SettingsGroup {
                                    AccountSwitcher(accounts, activeId,
                                        switchTo = { onDismiss(); registry.switchTo(it) }, addAccount = { onDismiss(); registry.addAccount() })
                                }
                                SettingsAction("Gemini summaries", "Understand messages in Amharic or English", { aiSettings = true })
                                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth().testTag("about_section_card")) {
                                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                            Text("GhostGram", style = MaterialTheme.typography.labelLarge)
                                            Text("Tedros Teshome", style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text("Version 1.1", style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            1 -> SettingsGroup {
                                SettingsToggleItem("Notifications", "Alerts for added chats", draft.notificationsEnabled,
                                    { draft = draft.copy(notificationsEnabled = it) }, "switch_notifications_enabled")
                                if (draft.notificationsEnabled) SettingsToggleItem("Message preview", "Include text in alerts", draft.showNotificationPreview,
                                    { draft = draft.copy(showNotificationPreview = it) }, "switch_notification_preview")
                            }
                            2 -> SettingsGroup {
                                Text("Theme", Modifier.padding(start = 12.dp, top = 10.dp), style = MaterialTheme.typography.labelMedium)
                                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("System" to null, "Light" to false, "Dark" to true).forEach { (label, value) ->
                                        FilterChip(selected = draft.darkMode == value, onClick = { draft = draft.copy(darkMode = value) },
                                            label = { Text(label, style = MaterialTheme.typography.labelMedium) }, modifier = Modifier.weight(1f))
                                    }
                                }
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Text size", style = MaterialTheme.typography.labelMedium)
                                    Text("${(draft.fontSizeScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                                }
                                Slider(value = draft.fontSizeScale, onValueChange = { draft = draft.copy(fontSizeScale = it) },
                                    valueRange = 0.4f..2.5f, modifier = Modifier.padding(horizontal = 12.dp).testTag("setting_font_size_slider"))
                                Text("Preview your reading size", Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize * draft.fontSizeScale))
                            }
                            3 -> {
                                OutlinedTextField(value = draft.focusKeywords, onValueChange = { draft = draft.copy(focusKeywords = it) },
                                    label = { Text("Focus keywords") }, supportingText = { Text("Separate with commas") },
                                    maxLines = 2, modifier = Modifier.fillMaxWidth().testTag("focus_keywords_input"), shape = RoundedCornerShape(12.dp))
                                SettingsGroup {
                                    SettingsToggleItem("Isolation mode", "Show only added chats", draft.silenceMode,
                                        { draft = draft.copy(silenceMode = it) }, "switch_silence_mode")
                                    SettingsToggleItem("Personal messages in feed", "Include added private chats", draft.includePersonalInFeed,
                                        { draft = draft.copy(includePersonalInFeed = it) }, "switch_personal_in_feed")
                                    SettingsToggleItem("Personal tab", "Direct access to private chats", draft.showPersonalTab,
                                        { draft = draft.copy(showPersonalTab = it) }, "switch_personal_tab")
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { onDismiss(); onOpenTimer() }, modifier = Modifier.weight(1f)) { Text("Focus timer") }
                                    TextButton(onClick = { onDismiss(); onOpenAudit() }, modifier = Modifier.weight(1f)) { Text("Offline & privacy") }
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_settings_button")) { Text("Cancel") } },
        confirmButton = { Button(onClick = { onSave(draft) }, modifier = Modifier.testTag("save_settings_button"),
            shape = RoundedCornerShape(12.dp)) { Text("Save") } })
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), content = content)
    }
}

@Composable
private fun SettingsAction(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsToggleItem(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    tag: String, isSubItem: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(tag))
    }
}
