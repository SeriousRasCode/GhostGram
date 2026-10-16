package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatSourceType
import com.example.ui.theme.GhostAmber
import com.example.ui.theme.GhostSky
import com.example.ui.theme.GhostTeal

@Composable
fun AddSourceDialog(
    initialType: ChatSourceType,
    onAdd: (title: String, handleOrId: String, description: String, type: ChatSourceType) -> Unit,
    onDismiss: () -> Unit,
    onImportFromAccount: () -> Unit
) {
    var type by remember { mutableStateOf(initialType) }
    var title by remember { mutableStateOf("") }
    var handleOrId by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when(type) {
                            ChatSourceType.CHANNEL -> Icons.Default.Campaign
                            ChatSourceType.GROUP -> Icons.Default.Groups
                            else -> Icons.Default.AccountCircle
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when(type) {
                            ChatSourceType.CHANNEL -> "Add Focus Channel"
                            ChatSourceType.GROUP -> "Add Focus Group"
                            else -> "Add Personal Chat"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("close_add_source_icon")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Account Import Quick Entry
                Surface(
                    onClick = onImportFromAccount,
                    shape = RoundedCornerShape(12.dp),
                    color = GhostTeal.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Groups, null, tint = GhostTeal, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Import from Account",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = GhostTeal
                            )
                            Text(
                                text = "Browse your existing Telegram chats",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.AddCircle, null, tint = GhostTeal, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "OR ADD MANUALLY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = when(type) {
                        ChatSourceType.CHANNEL -> "Add a Telegram channel to follow essential broadcasts with zero presence tracking."
                        ChatSourceType.GROUP -> "Add a Telegram group for focused collaboration without DMs or outside chatter."
                        else -> "Add an important 1-on-1 contact to your focus list for quick access."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Type Toggle (Channel vs Group vs Personal)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val activeBorder = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    
                    OutlinedButton(
                        onClick = { type = ChatSourceType.CHANNEL },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        border = if (type == ChatSourceType.CHANNEL) activeBorder else null
                    ) {
                        Text("Channel", fontSize = 11.sp, fontWeight = if (type == ChatSourceType.CHANNEL) FontWeight.Bold else FontWeight.Normal)
                    }

                    OutlinedButton(
                        onClick = { type = ChatSourceType.GROUP },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        border = if (type == ChatSourceType.GROUP) activeBorder else null
                    ) {
                        Text("Group", fontSize = 11.sp, fontWeight = if (type == ChatSourceType.GROUP) FontWeight.Bold else FontWeight.Normal)
                    }

                    OutlinedButton(
                        onClick = { type = ChatSourceType.PERSONAL },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        border = if (type == ChatSourceType.PERSONAL) activeBorder else null
                    ) {
                        Text("Personal", fontSize = 11.sp, fontWeight = if (type == ChatSourceType.PERSONAL) FontWeight.Bold else FontWeight.Normal)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Display Name") },
                    placeholder = {
                        Text(if (type == ChatSourceType.CHANNEL) "e.g. Work Announcements" else "e.g. Core Dev Squad")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_source_title")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = handleOrId,
                    onValueChange = { handleOrId = it },
                    label = {
                        Text(when(type) {
                            ChatSourceType.CHANNEL -> "Channel Username"
                            ChatSourceType.GROUP -> "Chat ID or Handle"
                            else -> "Contact Username or ID"
                        })
                    },
                    placeholder = {
                        Text(when(type) {
                            ChatSourceType.CHANNEL -> "e.g. telegram, durov, androidpolice"
                            ChatSourceType.GROUP -> "e.g. -1001234567890 or @groupname"
                            else -> "e.g. @username or 123456789"
                        })
                    },
                    supportingText = {
                        Text(when(type) {
                            ChatSourceType.CHANNEL -> "Fetches via public preview. No login required."
                            ChatSourceType.GROUP -> "Bot reads group messages with zero user presence."
                            else -> "Requires account session for personal direct chats."
                        })
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_source_handle")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Focus Topic / Description (Optional)") },
                    placeholder = { Text("e.g. Production alerts, Sprint priority") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_source_desc")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Presets
                if (type == ChatSourceType.CHANNEL) {
                    Text(
                        text = "Quick Channel Presets:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                title = "Telegram News"
                                handleOrId = "telegram"
                                description = "Official Telegram releases & updates"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Telegram", fontSize = 11.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                title = "Durov Channel"
                                handleOrId = "durov"
                                description = "Pavel Durov thoughts & platform notices"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Durov", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && handleOrId.isNotBlank()) {
                        onAdd(title, handleOrId, description, type)
                    }
                },
                enabled = title.isNotBlank() && handleOrId.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_source_button")
            ) {
                Text("Add to Focus List")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_add_source_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
