package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().testTag("account_switcher")) {
            Text("Accounts", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                if (expanded) "Hide accounts" else "Show accounts")
        }
        if (expanded) {
            accounts.forEach { account ->
                ListItem(
                        modifier = Modifier.clickable { switchTo(account.id) }.testTag("switch_account_${account.id}"),
                        headlineContent = {
                            Text(account.session.userName.ifBlank { "New account" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(account.session.phoneNumber.ifBlank { "Finish signing in" } +
                                if (!account.session.isLoggedIn && account.session.userId != 0L) " · Signed out" else "")
                        },
                        leadingContent = { Icon(Icons.Default.AccountCircle, null) },
                        trailingContent = { if (account.id == activeId) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            }
            TextButton(onClick = addAccount, modifier = Modifier.fillMaxWidth().testTag("add_account")) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(12.dp))
                Text("Add account", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
            }
        }
    }
}
