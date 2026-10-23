package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextDecoration
import org.json.JSONArray

@OptIn(ExperimentalTextApi::class)
@Composable
internal fun RichMessageText(text: String, entities: String?, style: TextStyle = MaterialTheme.typography.bodyMedium,
    displayStart: Int = 0, displayEnd: Int = text.length, onTextClick: (() -> Unit)? = null,
    maxLines: Int = Int.MAX_VALUE, overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip) {
    val uriHandler = LocalUriHandler.current
    var revealed by remember(text, entities) { mutableStateOf(setOf<Int>()) }
    val linkColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val annotated = remember(text, entities, revealed, linkColor, textColor) {
        val array = try { JSONArray(entities ?: "[]") } catch (_: Exception) { JSONArray() }
        val hidden = (0 until array.length()).mapNotNull { index ->
            val entity = array.optJSONObject(index) ?: return@mapNotNull null
            if (entity.optString("type") != "Spoiler" || index in revealed) return@mapNotNull null
            val start = entity.optInt("offset").coerceIn(0, text.length)
            val end = (start.toLong() + entity.optInt("length")).coerceIn(start.toLong(), text.length.toLong()).toInt()
            Triple(index, start, end)
        }
        val displayed = text.toCharArray().apply {
            hidden.forEach { (_, start, end) -> for (index in start until end) if (!this[index].isWhitespace()) this[index] = '•' }
        }.concatToString()
        buildAnnotatedString {
            append(displayed)
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
                    "Spoiler" -> if (i in revealed) SpanStyle() else SpanStyle(color = linkColor, background = linkColor.copy(alpha = 0.12f))
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
                if (url.isNotBlank() && hidden.none { (_, a, b) -> start < b && end > a }) {
                    addLink(LinkAnnotation.Url(url, TextLinkStyles(
                        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                        focusedStyle = SpanStyle(background = linkColor.copy(alpha = 0.18f)))) {
                        try { uriHandler.openUri(url) } catch (_: Exception) { }
                    }, start, end)
                }
                if (type == "Spoiler" && i !in revealed) {
                    addLink(LinkAnnotation.Clickable("Reveal spoiler", linkInteractionListener = { revealed = revealed + i }), start, end)
                    addTtsAnnotation(VerbatimTtsAnnotation("Hidden spoiler. Activate to reveal."), start, end)
                }
            }
        }
    }
    val visible = annotated.subSequence(displayStart.coerceIn(0, text.length), displayEnd.coerceIn(displayStart, text.length))
    SelectionContainer {
        Text(visible, style = style.copy(color = textColor), maxLines = maxLines, overflow = overflow,
            modifier = Modifier.semantics(mergeDescendants = true) {}
            .then(if (onTextClick != null) Modifier.clickable(onClickLabel = "Message options", onClick = onTextClick) else Modifier))
    }
}
