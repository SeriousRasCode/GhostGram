package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
internal fun AccountAvatar(name: String, photoPath: String?, size: Dp = 48.dp) {
    var failed by remember(photoPath) { mutableStateOf(false) }
    val initials = remember(name) {
        name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
            .map { it.codePoints().findFirst().orElse(0).let { point -> String(Character.toChars(point)) } }
            .joinToString("").uppercase().ifBlank { "G" }
    }
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Text(initials, style = if (size >= 48.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        if (!photoPath.isNullOrBlank() && !failed) AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(photoPath).crossfade(true).build(),
            contentDescription = "$name profile photo", modifier = Modifier.size(size), contentScale = ContentScale.Crop,
            onError = { failed = true })
    }
}
