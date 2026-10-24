package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.TelegramUserProfile
import com.example.ui.viewmodel.UserProfileDialogState

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun TelegramUserProfileSheet(
    state: UserProfileDialogState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val profile = state.profile
            ProfileAvatar(profile, 76.dp)
            Spacer(Modifier.height(12.dp))
            val displayName = profile?.let { "${it.firstName} ${it.lastName}".trim() }
                ?.takeIf { it.isNotBlank() }
                ?: profile?.username?.let { "@$it" }
                ?: "Telegram user"
            Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (profile?.isContact == true) {
                Text("Contact", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))

            if (state.isLoading) {
                CircularProgressIndicator(Modifier.padding(vertical = 18.dp))
                Text("Loading profile details...", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 20.dp))
            }

            state.error?.let { error ->
                Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Retry") }
            }

            profile?.let {
                if (it.fullDetailsUnavailable) {
                    Text("Some details couldn't be loaded. Showing the information that is available.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp))
                }
                ProfileDetailRow(Icons.Default.Tag, "Username", it.username?.let { name -> "@$name" } ?: "No public username",
                    copyValue = it.username?.let { name -> "@$name" })
                ProfileDetailRow(Icons.Default.Call, "Phone", it.phoneNumber ?: "Not shared with this account",
                    copyValue = it.phoneNumber)
                ProfileDetailRow(Icons.Default.Info, "Bio", it.bio ?: "No bio available")
                ProfileDetailRow(Icons.Default.People, "Contact status", when {
                    it.isMutualContact -> "You're mutual contacts"
                    it.isContact -> "Saved in your contacts"
                    else -> "Not saved in your contacts"
                })
                Text("Telegram only shares details visible to this account.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp, bottom = 20.dp))
            }
        }
    }
}

@Composable
private fun ProfileAvatar(profile: TelegramUserProfile?, size: androidx.compose.ui.unit.Dp) {
    val name = profile?.firstName?.takeIf { it.isNotBlank() }
        ?: profile?.username?.takeIf { it.isNotBlank() }
        ?: "?"
    androidx.compose.material3.Surface(
        modifier = Modifier.size(size), shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!profile?.photoPath.isNullOrBlank()) {
                AsyncImage(profile?.photoPath, "${name}'s profile photo", Modifier.size(size).clip(CircleShape),
                    contentScale = ContentScale.Crop)
            } else Text(name.first().uppercase(), style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun ProfileDetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String,
    copyValue: String? = null) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
            if (copyValue != null) {
                IconButton(onClick = { copyToClipboard(context, label, copyValue) }) {
                    Icon(Icons.Default.ContentCopy, "Copy $label")
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    }
}
