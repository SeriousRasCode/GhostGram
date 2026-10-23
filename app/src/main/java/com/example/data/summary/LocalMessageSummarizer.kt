package com.example.data.summary

import java.text.BreakIterator
import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt

data class MessageSummary(val sentences: List<String>, val alreadyBrief: Boolean, val truncated: Boolean)

/** Bounded, offline extractive ranking. Original sentences are retained, never invented. */
object LocalMessageSummarizer {
    private const val MAX_INPUT = 48_000
    private const val MAX_SENTENCES = 180
    private val words = Regex("[\\p{L}\\p{N}]+")
    private val stopWords = setOf("the", "a", "an", "and", "or", "to", "of", "in", "on", "is", "it", "for", "that", "this", "with", "as", "at", "be", "are", "was", "we", "you", "i")

    fun summarize(raw: String): MessageSummary {
        val cleaned = raw.trim().replace(Regex("[\\t\\r ]+"), " ")
        require(cleaned.isNotBlank()) { "This message has no text or caption to summarize." }
        // Avoid splitting a UTF-16 surrogate pair at the input boundary.
        val boundary = minOf(cleaned.length, MAX_INPUT).let { if (it < cleaned.length && cleaned[it - 1].isHighSurrogate()) it - 1 else it }
        val input = cleaned.substring(0, boundary)
        val iterator = BreakIterator.getSentenceInstance(Locale.ROOT).apply { setText(input) }
        val candidates = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            input.substring(start, end).split(Regex("\\n+")).map(String::trim).filter { it.isNotEmpty() }.forEach { candidates.add(it) }
            start = end
            end = iterator.next()
        }
        val sentences = candidates.distinct().take(MAX_SENTENCES)
        val truncated = cleaned.length > boundary || candidates.distinct().size > MAX_SENTENCES
        if (sentences.size <= 3 && input.length <= 700) return MessageSummary(sentences.ifEmpty { listOf(input) }, true, truncated)
        val tokens = sentences.map { sentence ->
            words.findAll(sentence.lowercase(Locale.ROOT)).map { it.value }.filter { it !in stopWords }.take(200).toList()
        }
        val counts = tokens.map { it.groupingBy { token -> token }.eachCount() }
        val documentFrequency = tokens.flatMap { it.toSet() }.groupingBy { it }.eachCount()
        val vectors = counts.map { count -> count.mapValues { (term, frequency) ->
            (1.0 + ln(frequency.toDouble())) * (1.0 + ln((sentences.size + 1.0) / (documentFrequency.getValue(term) + 1.0)))
        } }
        val norm = vectors.map { vector -> sqrt(vector.values.sumOf { it * it }) }
        val weights = Array(sentences.size) { DoubleArray(sentences.size) }
        for (i in sentences.indices) for (j in 0 until i) {
            val similarity = if (norm[i] == 0.0 || norm[j] == 0.0) 0.0 else
                vectors[i].entries.sumOf { (term, value) -> value * (vectors[j][term] ?: 0.0) } / (norm[i] * norm[j])
            weights[i][j] = similarity
            weights[j][i] = similarity
        }
        val outgoing = weights.map { it.sum() }
        var scores = DoubleArray(sentences.size) { 1.0 / sentences.size }
        repeat(20) {
            scores = DoubleArray(sentences.size) { i ->
                0.15 / sentences.size + 0.85 * sentences.indices.sumOf { j ->
                    if (outgoing[j] == 0.0) scores[j] / sentences.size else scores[j] * weights[j][i] / outgoing[j]
                }
            }
        }
        val selected = mutableListOf<Int>()
        for (index in sentences.indices.sortedByDescending { scores[it] }) {
            if (selected.none { weights[it][index] > 0.85 }) selected.add(index)
            if (selected.size == 3) break
        }
        val chosen = selected.sorted().map { sentences[it] }.ifEmpty { listOf(input) }
        return MessageSummary(chosen.map { sentence ->
            if (sentence.length <= 600) sentence else {
                var endIndex = sentence.lastIndexOf(' ', 600).takeIf { it >= 300 } ?: 600
                if (sentence[endIndex - 1].isHighSurrogate()) endIndex--
                sentence.substring(0, endIndex).trimEnd()
            }
        }, false, truncated || chosen.any { it.length > 600 })
    }
}
