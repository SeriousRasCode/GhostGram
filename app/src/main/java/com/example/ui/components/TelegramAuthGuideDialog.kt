package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun TelegramAuthGuideDialog(currentBotToken: String,
    onTestBotToken: (String, (Result<String>) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var token by remember { mutableStateOf(currentBotToken) }
    var advanced by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    CompactDialog(onDismissRequest = onDismiss,
        title = { DialogHeading("Telegram sign-in help", onDismiss, "close_guide_dialog_icon") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Your Telegram account", style = MaterialTheme.typography.labelLarge)
                Text("Connect in Profile to browse added groups, channels and private chats. Telegram may ask for a login code and your two-step verification password.",
                    style = MaterialTheme.typography.bodySmall)
                Text("Offline mode", style = MaterialTheme.typography.labelLarge)
                Text("GhostGram uses offline mode and keeps read receipts off while you browse. Opening another Telegram app may change your online status.",
                    style = MaterialTheme.typography.bodySmall)
                Text("Public channels", style = MaterialTheme.typography.labelLarge)
                Text("Some public posts are available without signing in. A connected account provides richer message and reaction data.",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide optional bot tools" else "Optional bot tools") }
                if (advanced) {
                    Text("Connect a Telegram bot.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = token, onValueChange = { token = it; result = null }, singleLine = true,
                        label = { Text("Bot token") }, visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("test_bot_token_input"))
                    OutlinedButton(enabled = token.isNotBlank() && !testing, modifier = Modifier.testTag("test_bot_token_button"),
                        onClick = {
                            testing = true
                            onTestBotToken(token) { response ->
                                testing = false
                                result = if (response.isSuccess) "Bot connection verified." else "Couldn't verify the bot. Check its token and your connection."
                            }
                        }) { Text(if (testing) "Checking..." else "Check bot connection") }
                    result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("auth_guide_dismiss_button")) { Text("Done") } })
}
