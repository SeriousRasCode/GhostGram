package com.example

import com.example.data.summary.LocalMessageSummarizer
import org.junit.Assert.*
import org.junit.Test

class LocalMessageSummarizerTest {
    @Test fun briefCaptionIsPreserved() {
        val text = "Meeting at 10. Bring your notes."
        val result = LocalMessageSummarizer.summarize(text)
        assertTrue(result.alreadyBrief)
        assertEquals(text, result.sentences.joinToString(" "))
    }

    @Test fun summaryContainsOnlyOriginalSentencesInOrder() {
        val sentences = listOf("The team reviewed the delivery schedule.", "The delivery date remains Friday.",
            "Lunch is available upstairs.", "Delivery requires the signed approval.", "Please send approval by Thursday.",
            "The office closes at six.")
        val result = LocalMessageSummarizer.summarize(sentences.joinToString(" "))
        assertFalse(result.alreadyBrief)
        assertTrue(result.sentences.size in 1..3)
        assertTrue(result.sentences.all { it in sentences })
        assertEquals(result.sentences.map { sentences.indexOf(it) }.sorted(), result.sentences.map { sentences.indexOf(it) })
    }

    @Test fun longUnpunctuatedTextHasBoundedOutput() {
        val result = LocalMessageSummarizer.summarize("important update ".repeat(8000))
        assertTrue(result.truncated)
        assertTrue(result.sentences.sumOf { it.length } <= 1800)
    }

    @Test fun multilingualTextAndEmojiRemainIntact() {
        val text = "ሰላም ለሁላችሁ።\nHabari za leo 😊\nمرحبا بالجميع"
        assertTrue(LocalMessageSummarizer.summarize(text).sentences.all { text.contains(it) })
    }

    @Test(expected = IllegalArgumentException::class) fun emptyMediaHasNoInventedSummary() {
        LocalMessageSummarizer.summarize(" \n ")
    }
}
