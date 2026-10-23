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
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.MainGhostScreen
import com.example.ui.theme.GhostGramTheme
import com.example.ui.viewmodel.GhostViewModel
import com.example.data.local.GhostAccounts
import com.example.ui.components.AccountSwitcher
import com.example.ui.components.LocalGhostAccountId
import androidx.compose.runtime.CompositionLocalProvider

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectNotificationAccount(intent)
        try {
            enableEdgeToEdge()
            
            setContent {
                val accounts = remember { GhostAccounts.get(application) }
                val activeId by accounts.activeId.collectAsStateWithLifecycle()
                val profiles by accounts.accounts.collectAsStateWithLifecycle()
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
                    val isDarkMode = uiState.config.darkMode ?: androidx.compose.foundation.isSystemInDarkTheme()
                    GhostGramTheme(darkTheme = isDarkMode) {
                        Column(Modifier.fillMaxSize().statusBarsPadding().consumeWindowInsets(WindowInsets.statusBars)) {
                            AccountSwitcher(profiles, activeId, accounts::switchTo, { accounts.addAccount() })
                            Box(Modifier.weight(1f)) {
                                CompositionLocalProvider(LocalGhostAccountId provides activeId) {
                                    MainGhostScreen(viewModel = viewModel, uiState = uiState)
                                }
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
        intent?.getStringExtra("accountId")?.takeIf { id -> accounts.accounts.value.any { it.id == id } }
            ?.let(accounts::switchTo)
    }
}
