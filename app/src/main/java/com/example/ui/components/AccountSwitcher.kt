package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.GhostAccount
import com.example.data.local.GhostAccounts

val LocalGhostAccountId = compositionLocalOf { GhostAccounts.DEFAULT_ACCOUNT }

@Composable
fun AccountSwitcher(accounts: List<GhostAccount>, activeId: String, switchTo: (String) -> Unit, addAccount: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { expanded = !expanded }
            .padding(horizontal = 14.dp).testTag("account_switcher"), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ManageAccounts, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Accounts", Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.labelLarge)
            Text(accounts.size.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                if (expanded) "Hide accounts" else "Show accounts", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            accounts.forEach { account ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { switchTo(account.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp).testTag("switch_account_${account.id}"), verticalAlignment = Alignment.CenterVertically) {
                    AccountAvatar(account.session.userName.ifBlank { "Telegram account" }, account.session.profilePicPath, 36.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(account.session.userName.ifBlank { "New account" }, style = MaterialTheme.typography.labelLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (!account.session.isLoggedIn && account.session.userId != 0L) "Signed out" else account.session.phoneNumber.ifBlank { "Sign in to continue" },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (account.id == activeId) Icon(Icons.Default.CheckCircle, "Selected account", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            TextButton(onClick = addAccount, modifier = Modifier.fillMaxWidth().testTag("add_account"), contentPadding = PaddingValues(horizontal = 14.dp)) {
                Icon(Icons.Default.AddCircleOutline, null, Modifier.size(20.dp))
                Text("Add account", Modifier.weight(1f).padding(start = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
            }
        }
    }
}
