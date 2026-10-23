package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** A shared, bounded dialog with a fixed header/actions and independently scrollable content. */
@Composable
fun CompactDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false)
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    Dialog(onDismissRequest, properties = properties) {
        Surface(
            modifier = modifier.padding(horizontal = 12.dp).widthIn(max = 440.dp).fillMaxWidth().heightIn(max = maxHeight),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            tonalElevation = 3.dp,
            shadowElevation = 12.dp
        ) {
            Column {
                Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 2.dp)) {
                    ProvideTextStyle(MaterialTheme.typography.titleMedium) { title() }
                }
                Box(Modifier.weight(1f, fill = false).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
internal fun DialogHeading(title: String, onDismiss: () -> Unit, tag: String = "dialog_close") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).testTag(tag)) {
            Icon(Icons.Default.Close, "Close $title", Modifier.size(18.dp))
        }
    }
}
