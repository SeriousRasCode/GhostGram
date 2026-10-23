package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AuthStep
import com.example.data.model.UserAccountSession
import com.example.ui.theme.GhostSky
import com.example.ui.theme.GhostTeal

@Composable
fun LoginGatewayScreen(
    onSendCode: (phone: String, (Result<String>) -> Unit) -> Unit,
    onSignIn: (phone: String, code: String, name: String, username: String, (Result<UserAccountSession>) -> Unit) -> Unit,
    onCheckPassword: (password: String, phone: String, (Result<UserAccountSession>) -> Unit) -> Unit,
    onResendCode: ((Result<Unit>) -> Unit) -> Unit,
    onLogout: () -> Unit,
    onBackToAccount: (() -> Unit)? = null,
    telegramAuthState: org.drinkless.tdlib.TdApi.AuthorizationState? = null,
    modifier: Modifier = Modifier
) {
    if (onBackToAccount != null) BackHandler(onBack = onBackToAccount)
    var step by remember { mutableStateOf(AuthStep.PHONE) }
    
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var passwordHint by remember { mutableStateOf<String?>(null) }
    var codeType by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(telegramAuthState) {
        when (telegramAuthState) {
            is org.drinkless.tdlib.TdApi.AuthorizationStateWaitCode -> {
                step = AuthStep.CODE
                codeType = when (telegramAuthState.codeInfo?.type) {
                    is org.drinkless.tdlib.TdApi.AuthenticationCodeTypeTelegramMessage -> "Telegram app"
                    is org.drinkless.tdlib.TdApi.AuthenticationCodeTypeSms -> "SMS"
                    is org.drinkless.tdlib.TdApi.AuthenticationCodeTypeCall -> "Phone call"
                    is org.drinkless.tdlib.TdApi.AuthenticationCodeTypeFlashCall -> "Flash call"
                    is org.drinkless.tdlib.TdApi.AuthenticationCodeTypeMissedCall -> "Missed call"
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

    var phoneInput by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (onBackToAccount != null) {
            TextButton(onClick = onBackToAccount, modifier = Modifier.align(Alignment.Start)) {
                Text("Back to signed-in account")
            }
        } else Spacer(modifier = Modifier.height(32.dp))

        // Telegram Logo
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(GhostSky.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Telegram",
                tint = GhostSky,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "LoginSteps"
        ) { currentStep ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (currentStep) {
                    AuthStep.PHONE -> {
                        Text(
                            text = "Your Phone",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Please enter your number in international format.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                OutlinedTextField(
                                    value = phoneInput,
                                    onValueChange = {
                                        phoneInput = it
                                        errorMessage = null
                                    },
                                    label = { Text("Phone Number") },
                                    placeholder = { Text("+1 555 123 4567") },
                                    leadingIcon = { Icon(Icons.Default.Phone, null) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                                    modifier = Modifier.fillMaxWidth().testTag("gateway_phone_input")
                                )

                                Spacer(modifier = Modifier.height(20.dp))

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
                                            else errorMessage = res.exceptionOrNull()?.message ?: "Connection failed"
                                        }
                                    },
                                    enabled = !isLoading && phoneInput.length > 5,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("gateway_send_code_button")
                                ) {
                                    if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    else Text("Next", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    AuthStep.CODE -> {
                        Text(
                            text = "Verify Code",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Check your ${codeType ?: "Telegram app"} for the verification code sent to $phoneInput.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                OutlinedTextField(
                                    value = codeInput,
                                    onValueChange = {
                                        codeInput = it
                                        errorMessage = null
                                    },
                                    label = { Text("Code") },
                                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    modifier = Modifier.fillMaxWidth().testTag("gateway_code_input")
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        if (codeInput.isBlank()) {
                                            errorMessage = "Enter the code"
                                            return@Button
                                        }
                                        isLoading = true
                                        onSignIn(phoneInput, codeInput, "", "") { res ->
                                            isLoading = false
                                            if (res.isFailure) {
                                                val msg = res.exceptionOrNull()?.message ?: ""
                                                if (msg.startsWith("SESSION_PASSWORD_NEEDED")) {
                                                    passwordHint = msg.substringAfter("|", "")
                                                    errorMessage = null
                                                    step = AuthStep.PASSWORD
                                                } else {
                                                    errorMessage = msg.ifBlank { "Invalid code" }
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isLoading && codeInput.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("gateway_confirm_button")
                                ) {
                                    if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    else Text("Sign In", fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(onClick = { 
                                        onLogout()
                                        step = AuthStep.PHONE 
                                    }) {
                                        Text("Change Number", fontSize = 13.sp)
                                    }
                                    TextButton(
                                        onClick = {
                                            isLoading = true
                                            onResendCode { res ->
                                                isLoading = false
                                                if (res.isFailure) {
                                                    errorMessage = res.exceptionOrNull()?.message ?: "Resend failed"
                                                }
                                            }
                                        },
                                        enabled = !isLoading
                                    ) {
                                        Text("Resend Code", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    AuthStep.PASSWORD -> {
                        Text(
                            text = "Cloud Password",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (!passwordHint.isNullOrBlank()) "Hint: $passwordHint" else "Two-Step Verification is enabled. Please enter your cloud password.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                var passwordVisible by remember { mutableStateOf(false) }

                                OutlinedTextField(
                                    value = passwordInput,
                                    onValueChange = {
                                        passwordInput = it
                                        errorMessage = null
                                    },
                                    label = { Text("Cloud Password") },
                                    leadingIcon = { Icon(Icons.Default.Lock, null) },
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
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                    modifier = Modifier.fillMaxWidth().testTag("gateway_password_input")
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        if (passwordInput.isBlank()) {
                                            errorMessage = "Password is required"
                                            return@Button
                                        }
                                        isLoading = true
                                        onCheckPassword(passwordInput, phoneInput) { res ->
                                            isLoading = false
                                            if (res.isFailure) {
                                                errorMessage = res.exceptionOrNull()?.message ?: "Invalid password"
                                            }
                                        }
                                    },
                                    enabled = !isLoading && passwordInput.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("gateway_password_button")
                                ) {
                                    if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    else Text("Verify Password", fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                TextButton(onClick = { step = AuthStep.CODE }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Back to Code")
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMessage!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }


        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Official Open-Source Telegram Access",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
