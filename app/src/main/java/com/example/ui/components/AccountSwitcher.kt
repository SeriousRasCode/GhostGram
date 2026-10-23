package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.GhostAccount
import com.example.data.local.GhostAccounts

val LocalGhostAccountId = compositionLocalOf { GhostAccounts.DEFAULT_ACCOUNT }

@Composable
fun AccountSwitcher(accounts: List<GhostAccount>, activeId: String, switchTo: (String) -> Unit, addAccount: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val active = accounts.firstOrNull { it.id == activeId }
    Surface(tonalElevation = 1.dp) {
        Box(Modifier.fillMaxWidth()) {
            TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag("account_switcher")) {
                Icon(Icons.Default.AccountCircle, null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(active?.session?.userName?.ifBlank { "Telegram account" } ?: "Telegram account", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (active?.session?.isLoggedIn == true) "Offline presence" else "Sign in", style = MaterialTheme.typography.labelSmall)
                }
                Icon(Icons.Default.ExpandMore, "Switch or add account")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        modifier = Modifier.testTag("switch_account_${account.id}"),
                        text = {
                            Column {
                                Text(account.session.userName.ifBlank { "New account" })
                                Text(account.session.phoneNumber.ifBlank { "Finish signing in" } +
                                    if (!account.session.isLoggedIn && account.session.userId != 0L) " · Signed out" else "",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        leadingIcon = { Icon(Icons.Default.AccountCircle, null) },
                        trailingIcon = { if (account.id == activeId) Icon(Icons.Default.Check, "Selected") },
                        onClick = { expanded = false; switchTo(account.id) }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Add account") }, leadingIcon = { Icon(Icons.Default.Add, null) },
                    modifier = Modifier.testTag("add_account"), onClick = { expanded = false; addAccount() })
            }
        }
    }
}
