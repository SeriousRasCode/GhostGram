package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.MainGhostScreen
import com.example.ui.theme.GhostGramTheme
import com.example.ui.viewmodel.GhostViewModel
import com.example.data.local.GhostAccounts
import com.example.ui.components.LocalGhostAccountId
import com.example.ui.components.LocalOpenTelegramLink
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.util.MessageNotificationTarget
import org.drinkless.tdlib.TdApi

class MainActivity : ComponentActivity() {
    private val notificationTarget = MutableStateFlow<MessageNotificationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectNotificationAccount(intent)
        try {
            enableEdgeToEdge()
            
            setContent {
                val accounts = remember { GhostAccounts.get(application) }
                val activeId by accounts.activeId.collectAsStateWithLifecycle()
                val pendingTarget by notificationTarget.collectAsStateWithLifecycle()
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
                    }
                }
                key(activeId) {
                    val factory = remember(activeId) { object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = GhostViewModel(application, activeId) as T
                    } }
                    val viewModel: GhostViewModel = viewModel(key = "ghostgram:$activeId", factory = factory)
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(pendingTarget, uiState.userSession.isLoggedIn, uiState.telegramAuthState) {
                        val target = pendingTarget
                        if (target != null && target.accountId == activeId && uiState.userSession.isLoggedIn &&
                            uiState.telegramAuthState is TdApi.AuthorizationStateReady) {
                            viewModel.openNotificationMessage(target.chatId, target.messageId)
                            if (notificationTarget.compareAndSet(target, null)) {
                                intent?.removeExtra("chatId")
                                intent?.removeExtra("messageId")
                            }
                        }
                    }
                    val isDarkMode = uiState.config.darkMode ?: androidx.compose.foundation.isSystemInDarkTheme()
                    GhostGramTheme(darkTheme = isDarkMode) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                            CompositionLocalProvider(
                                LocalGhostAccountId provides activeId,
                                LocalOpenTelegramLink provides { link -> viewModel.openTelegramLink(link) }
                            ) {
                                MainGhostScreen(viewModel = viewModel, uiState = uiState)
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Fatal onCreate failure: ${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selectNotificationAccount(intent)
    }

    private fun selectNotificationAccount(intent: Intent?) {
        val accounts = GhostAccounts.get(application)
        val target = MessageNotificationTarget.fromIntent(intent, accounts.activeId.value) ?: return
        if (accounts.accounts.value.none { it.id == target.accountId }) return
        accounts.switchTo(target.accountId)
        notificationTarget.value = target
    }
}
