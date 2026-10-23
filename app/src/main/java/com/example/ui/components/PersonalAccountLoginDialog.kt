package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.AuthStep
import com.example.data.model.UserAccountSession
import com.example.ui.theme.GhostSky
import com.example.ui.theme.GhostTeal

@Composable
fun PersonalAccountLoginDialog(
    session: UserAccountSession,
    onSendCode: (phone: String, (Result<String>) -> Unit) -> Unit,
    onSignIn: (phone: String, code: String, name: String, username: String, (Result<UserAccountSession>) -> Unit) -> Unit,
    onCheckPassword: (password: String, phone: String, (Result<UserAccountSession>) -> Unit) -> Unit,
    onLogout: () -> Unit,
    onOpenChatPicker: () -> Unit,
    onDismiss: () -> Unit,
    telegramAuthState: org.drinkless.tdlib.TdApi.AuthorizationState? = null
) {
    var step by remember { 
        mutableStateOf(if (session.isLoggedIn) AuthStep.SUCCESS else AuthStep.PHONE) 
    }
    
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var passwordHint by remember { mutableStateOf<String?>(null) }
    var codeType by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(session.isLoggedIn, telegramAuthState) {
        if (session.isLoggedIn) {
            step = AuthStep.SUCCESS
        } else {
            when (telegramAuthState) {
                is org.drinkless.tdlib.TdApi.AuthorizationStateWaitCode -> {
                    step = AuthStep.CODE
                    codeType = when (telegramAuthState.codeInfo?.type) {
                        is org.drinkless.tdlib.TdApi.AuthenticationCodeTypeTelegramMessage -> "Telegram app"
                        is org.drinkless.tdlib.TdApi.AuthenticationCodeTypeSms -> "SMS"
                        else -> "your device"
                    }
                }
                is org.drinkless.tdlib.TdApi.AuthorizationStateWaitPassword -> {
                    step = AuthStep.PASSWORD
                    passwordHint = telegramAuthState.passwordHint
                }
                is org.drinkless.tdlib.TdApi.AuthorizationStateReady -> step = AuthStep.SUCCESS
                is org.drinkless.tdlib.TdApi.AuthorizationStateWaitPhoneNumber -> step = AuthStep.PHONE
                else -> {}
            }
        }
    }

    var phoneInput by remember { mutableStateOf(session.phoneNumber) }
    var codeInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    CompactDialog(
        onDismissRequest = onDismiss,
        title = { DialogHeading(if (session.isLoggedIn) "Telegram account" else "Sign in", onDismiss, "close_login_dialog_icon") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                if (session.isLoggedIn && step == AuthStep.SUCCESS) {
                    // Logged In Status View
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(56.dp),
                                    shape = CircleShape,
                                    shadowElevation = 2.dp,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(GhostSky.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!session.profilePicPath.isNullOrBlank()) {
                                            AsyncImage(
                                                model = session.profilePicPath,
                                                contentDescription = "Profile",
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = session.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "T",
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                color = GhostSky
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.userName.ifBlank { "Telegram User" },
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${session.userHandle.ifBlank { "@user" }} • ${session.phoneNumber}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = GhostTeal, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Focus mode active", style = MaterialTheme.typography.labelSmall, color = GhostTeal)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            onDismiss()
                            onOpenChatPicker()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("import_account_chats_button")
                    ) {
                        Text("Add Chats from Account", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    var showLogoutConfirm by remember { mutableStateOf(false) }

                    if (showLogoutConfirm) {
                        CompactDialog(
                            onDismissRequest = { showLogoutConfirm = false },
                            title = { Text("Log Out?") },
                            text = { Text("Your feeds and added chats will remain, but you won't receive new updates until you sign in again.") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showLogoutConfirm = false
                                        onLogout()
                                        step = AuthStep.PHONE
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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

                    OutlinedButton(
                        onClick = { showLogoutConfirm = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("logout_account_button")
                    ) {
                        Text("Log Out", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    AnimatedContent(targetState = step, label = "AuthSteps") { currentStep ->
                        Column {
                            when (currentStep) {
                                AuthStep.PHONE -> {
                                    Text(
                                        text = "Enter your phone number to receive a verification code.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = phoneInput,
                                        onValueChange = { phoneInput = it; errorMessage = null },
                                        label = { Text("Phone Number") },
                                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        modifier = Modifier.fillMaxWidth().testTag("login_phone_input")
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            if (phoneInput.isBlank()) {
                                                errorMessage = "Phone number is required"
                                                return@Button
                                            }
                                            val sanitizedPhone = if (phoneInput.startsWith("+")) phoneInput else "+$phoneInput"
                                            phoneInput = sanitizedPhone
                                            isLoading = true
                                            onSendCode(sanitizedPhone) { res ->
                                                isLoading = false
                                                if (res.isSuccess) step = AuthStep.CODE
                                                else errorMessage = res.exceptionOrNull()?.message ?: "Failed"
                                            }
                                        },
                                        enabled = !isLoading && phoneInput.length > 5,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("send_code_button")
                                    ) {
                                        if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        else Text("Next")
                                    }
                                }

                                AuthStep.CODE -> {
                                    Text(
                                        text = "Check your ${codeType ?: "Telegram app"} for the verification code sent to $phoneInput.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = codeInput,
                                        onValueChange = { codeInput = it; errorMessage = null },
                                        label = { Text("Code") },
                                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth().testTag("login_code_input")
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            isLoading = true
                                            onSignIn(phoneInput, codeInput, "", "") { res ->
                                                isLoading = false
                                                if (res.isSuccess) step = AuthStep.SUCCESS
                                                else {
                                                    val msg = res.exceptionOrNull()?.message ?: ""
                                                    if (msg.startsWith("SESSION_PASSWORD_NEEDED")) {
                                                        passwordHint = msg.substringAfter("|", "")
                                                        errorMessage = null
                                                        step = AuthStep.PASSWORD
                                                    } else {
                                                        errorMessage = msg
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isLoading && codeInput.isNotBlank(),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("confirm_code_button")
                                    ) {
                                        if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        else Text("Sign In")
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        TextButton(onClick = { step = AuthStep.PHONE }) {
                                            Text("Change Number", fontSize = 12.sp)
                                        }
                                        var isResending by remember { mutableStateOf(false) }
                                        TextButton(
                                            onClick = {
                                                isResending = true
                                                onSendCode(phoneInput) { res ->
                                                    isResending = false
                                                    if (res.isFailure) {
                                                        errorMessage = res.exceptionOrNull()?.message ?: "Resend failed"
                                                    }
                                                }
                                            },
                                            enabled = !isResending
                                        ) {
                                            Text("Resend", fontSize = 12.sp)
                                        }
                                    }
                                }

                                AuthStep.PASSWORD -> {
                                    Text(
                                        text = if (!passwordHint.isNullOrBlank()) "Hint: $passwordHint" else "Two-Step Verification is enabled. Enter your cloud password.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    var passwordVisible by remember { mutableStateOf(false) }

                                    OutlinedTextField(
                                        value = passwordInput,
                                        onValueChange = { passwordInput = it; errorMessage = null },
                                        label = { Text("Password") },
                                        leadingIcon = { Icon(Icons.Default.Password, null) },
                                        trailingIcon = {
                                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                Icon(
                                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                                )
                                            }
                                        },
                                        singleLine = true,
                                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        modifier = Modifier.fillMaxWidth().testTag("login_password_input")
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            if (passwordInput.isBlank()) {
                                                errorMessage = "Password is required"
                                                return@Button
                                            }
                                            isLoading = true
                                            onCheckPassword(passwordInput, phoneInput) { res ->
                                                isLoading = false
                                                if (res.isSuccess) step = AuthStep.SUCCESS
                                                else {
                                                    val msg = res.exceptionOrNull()?.message ?: "Invalid password"
                                                    errorMessage = msg.substringAfter("|") // Show hint if it's there, but usually it's just an error message here
                                                    if (errorMessage?.isBlank() == true) errorMessage = "Invalid password"
                                                }
                                            }
                                        },
                                        enabled = !isLoading && passwordInput.isNotBlank(),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("confirm_password_button")
                                    ) {
                                        if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        else Text("Verify Password")
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = errorMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {}
    )
}
