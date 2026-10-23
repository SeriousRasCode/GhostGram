package com.example.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextDecoration
import org.json.JSONArray

@Composable
internal fun RichMessageText(text: String, entities: String?, style: TextStyle = MaterialTheme.typography.bodyMedium,
    displayStart: Int = 0, displayEnd: Int = text.length) {
    val uriHandler = LocalUriHandler.current
    var revealed by remember(text, entities) { mutableStateOf(setOf<Int>()) }
    val linkColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val annotated = remember(text, entities, revealed, linkColor, textColor) {
        buildAnnotatedString {
            append(text)
            val array = try { JSONArray(entities ?: "[]") } catch (_: Exception) { JSONArray() }
            for (i in 0 until array.length()) {
                val entity = array.optJSONObject(i) ?: continue
                val start = entity.optInt("offset").coerceIn(0, text.length)
                val end = (start + entity.optInt("length")).coerceIn(start, text.length)
                if (end == start) continue
                val type = entity.optString("type")
                val span = when (type) {
                    "Bold" -> SpanStyle(fontWeight = FontWeight.Bold)
                    "Italic" -> SpanStyle(fontStyle = FontStyle.Italic)
                    "Underline" -> SpanStyle(textDecoration = TextDecoration.Underline)
                    "Strikethrough" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    "Code", "Pre", "PreCode" -> SpanStyle(fontFamily = FontFamily.Monospace, background = textColor.copy(alpha = 0.06f))
                    "BlockQuote", "ExpandableBlockQuote" -> SpanStyle(fontStyle = FontStyle.Italic, background = linkColor.copy(alpha = 0.08f))
                    "Spoiler" -> if (i in revealed) SpanStyle() else SpanStyle(color = Color.Transparent, background = textColor)
                    "Url", "TextUrl", "Mention", "MentionName", "EmailAddress", "PhoneNumber" -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                    else -> SpanStyle()
                }
                addStyle(span, start, end)
                val value = text.substring(start, end)
                val url = when (type) {
                    "TextUrl", "MentionName" -> entity.optString("url")
                    "Url" -> if (value.contains("://")) value else "https://$value"
                    "Mention" -> "https://t.me/${value.removePrefix("@") }"
                    "EmailAddress" -> "mailto:$value"
                    "PhoneNumber" -> "tel:$value"
                    else -> ""
                }
                if (url.isNotBlank()) addStringAnnotation("URL", url, start, end)
                if (type == "Spoiler" && i !in revealed) addStringAnnotation("SPOILER", i.toString(), start, end)
            }
        }
    }
    val visible = annotated.subSequence(displayStart.coerceIn(0, text.length), displayEnd.coerceIn(displayStart, text.length))
    SelectionContainer {
        ClickableText(visible, style = style.copy(color = textColor), onClick = { offset ->
            val spoiler = visible.getStringAnnotations("SPOILER", offset, offset).firstOrNull()
            if (spoiler != null) revealed = revealed + spoiler.item.toInt()
            else visible.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                try { uriHandler.openUri(it.item) } catch (_: Exception) { }
            }
        })
    }
}
