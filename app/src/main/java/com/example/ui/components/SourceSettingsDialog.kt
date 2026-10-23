package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SourceConfig
import com.example.data.model.UserAccountSession
import com.example.data.local.GhostAccounts
import com.example.ui.theme.GhostSky
import com.example.ui.theme.GhostTeal

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
    val accountRegistry = remember(context) { GhostAccounts.get(context) }
    val accounts by accountRegistry.accounts.collectAsStateWithLifecycle()
    val activeAccountId by accountRegistry.activeId.collectAsStateWithLifecycle()
    var focusKeywords by remember { mutableStateOf(config.focusKeywords) }
    var silenceMode by remember { mutableStateOf(config.silenceMode) }
    var notificationsEnabled by remember { mutableStateOf(config.notificationsEnabled) }
    var showNotificationPreview by remember { mutableStateOf(config.showNotificationPreview) }
    var showPersonalTab by remember { mutableStateOf(config.showPersonalTab) }
    var includePersonalInFeed by remember { mutableStateOf(config.includePersonalInFeed) }
    var fontSizeScale by remember { mutableStateOf(config.fontSizeScale) }
    var darkMode by remember { mutableStateOf(config.darkMode) }

    var showLogoutConfirm by remember { mutableStateOf(false) }
    
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log Out?") },
            text = { Text("This account's feeds and added chats will remain. Other accounts stay signed in and continue receiving updates.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        onDismiss()
                        onLogout()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(vertical = 12.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        maxLines = 1
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp).testTag("close_settings_icon")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp)
            ) {
                // Section 1: Telegram Account Status
                SettingsSectionHeader("Profile & Accounts", Icons.Default.AccountCircle)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Circled Profile Picture with Depth
                            Surface(
                                modifier = Modifier.size(52.dp),
                                shape = CircleShape,
                                shadowElevation = 1.dp,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (session.isLoggedIn) GhostTeal.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (session.isLoggedIn && !session.profilePicPath.isNullOrBlank()) {
                                        coil.compose.AsyncImage(
                                            model = session.profilePicPath,
                                            contentDescription = "Profile",
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = null,
                                            tint = if (session.isLoggedIn) GhostTeal else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (session.isLoggedIn) session.userName else "Telegram Account",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (session.isLoggedIn) "Ghost Mode Active" else "Not connected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (session.isLoggedIn) GhostTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (session.isLoggedIn) {
                                TextButton(
                                    onClick = { showLogoutConfirm = true },
                                    modifier = Modifier.testTag("logout_settings_button"),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Log Out", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onOpenAccountLogin()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("login_settings_button"),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))

                AccountSwitcher(accounts, activeAccountId,
                    switchTo = { id -> onDismiss(); accountRegistry.switchTo(id) },
                    addAccount = { onDismiss(); accountRegistry.addAccount() })
                Spacer(modifier = Modifier.height(16.dp))

                // Section: NOTIFICATIONS
                SettingsSectionHeader("Relay Notifications", Icons.Default.Info)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsToggleItem(
                            title = "Push Notifications",
                            description = "Alerts for stealth-added chats",
                            checked = notificationsEnabled,
                            onCheckedChange = { notificationsEnabled = it },
                            tag = "switch_notifications_enabled"
                        )
                        
                        if (notificationsEnabled) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            SettingsToggleItem(
                                title = "Message Preview",
                                description = "Show content in notification center",
                                checked = showNotificationPreview,
                                onCheckedChange = { showNotificationPreview = it },
                                tag = "switch_notification_preview",
                                isSubItem = true
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Spacer(modifier = Modifier.height(20.dp))

                // Section: APPEARANCE
                SettingsSectionHeader("Appearance & Theme", Icons.Default.Tune)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeOption(
                                label = "System",
                                icon = Icons.Default.SettingsSuggest,
                                isSelected = darkMode == null,
                                onClick = { darkMode = null },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeOption(
                                label = "Light",
                                icon = Icons.Default.LightMode,
                                isSelected = darkMode == false,
                                onClick = { darkMode = false },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeOption(
                                label = "Dark",
                                icon = Icons.Default.DarkMode,
                                isSelected = darkMode == true,
                                onClick = { darkMode = true },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Text(
                            text = "Accessibility: Text Scaling",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("A", style = MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Slider(
                                value = fontSizeScale,
                                onValueChange = { fontSizeScale = it },
                                valueRange = 0.4f..2.5f,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp).testTag("setting_font_size_slider")
                            )
                            Text("A", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            text = "Current Scale: ${(fontSizeScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Section: FEED CONTROL
                SettingsSectionHeader("Feed & Stealth Management", Icons.Default.Radar)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Radar Keywords",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Highlight messages containing these terms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = focusKeywords,
                            onValueChange = { focusKeywords = it },
                            placeholder = { Text("urgent, deadline, exam...") },
                            modifier = Modifier.fillMaxWidth().testTag("setting_keywords"),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(8.dp))

                        SettingsToggleItem(
                            title = "Isolation Mode",
                            description = "Blocks all non-added Telegram chats",
                            checked = silenceMode,
                            onCheckedChange = { silenceMode = it },
                            tag = "switch_silence_mode"
                        )
                        
                        SettingsToggleItem(
                            title = "Personal Dispatch",
                            description = "Include 1-on-1 chats in your main Feed",
                            checked = includePersonalInFeed,
                            onCheckedChange = { includePersonalInFeed = it },
                            tag = "switch_personal_in_feed"
                        )

                        SettingsToggleItem(
                            title = "Stealth Contacts Tab",
                            description = "Enable direct access to 1-on-1 chats",
                            checked = showPersonalTab,
                            onCheckedChange = { showPersonalTab = it },
                            tag = "switch_personal_tab"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Section: UTILITIES
                SettingsSectionHeader("Security & Focus", Icons.Default.Shield)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onDismiss(); onOpenTimer() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Zen Timer")
                    }

                    OutlinedButton(
                        onClick = { onDismiss(); onOpenAudit() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Audit Logs")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))


                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(20.dp))

                // Section 5: ABOUT
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("about_section_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(GhostTeal, CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GhostGram v1.1",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Built by Tedros Teshome | Software Engineer",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = GhostSky,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        config.copy(
                            focusKeywords = focusKeywords,
                            silenceMode = silenceMode,
                            notificationsEnabled = notificationsEnabled,
                            showNotificationPreview = showNotificationPreview,
                            showPersonalTab = showPersonalTab,
                            includePersonalInFeed = includePersonalInFeed,
                            fontSizeScale = fontSizeScale,
                            darkMode = darkMode
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(horizontal = 8.dp).testTag("save_settings_button")
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_settings_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    isSubItem: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = if (isSubItem) 16.dp else 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (isSubItem) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun ThemeOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
