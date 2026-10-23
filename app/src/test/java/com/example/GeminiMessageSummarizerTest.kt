package com.example

import com.example.data.summary.GeminiMessageSummarizer
import com.example.data.summary.GeminiSettings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class GeminiMessageSummarizerTest {
    private val settings = GeminiSettings(apiKey = "test-only-not-a-real-key", model = "gemini-3.1-flash-lite")

    @Test fun selectedTextUsesFixedEndpointAndRepeatedSummaryIsCached() = runBlocking {
        GeminiMessageSummarizer.clearCache()
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            assertEquals("generativelanguage.googleapis.com", chain.request().url.host)
            assertNull(chain.request().url.query)
            assertEquals(settings.apiKey, chain.request().header("x-goog-api-key"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Meeting tomorrow."}]}}]}""".toResponseBody()).build()
        }.build()
        assertEquals("Meeting tomorrow.", GeminiMessageSummarizer.summarize("Tomorrow's meeting", settings, client))
        assertEquals("Meeting tomorrow.", GeminiMessageSummarizer.summarize("Tomorrow's meeting", settings, client))
        assertEquals(1, calls)
    }

    @Test fun unavailableServerReturnsSafeErrorAndAllowsAnotherAttempt() = runBlocking {
        GeminiMessageSummarizer.clearCache()
        var attempts = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            attempts++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("Unavailable").header("Retry-After", "0")
                .body("private server detail must not surface".toResponseBody()).build()
        }.build()
        repeat(2) {
            try { GeminiMessageSummarizer.summarize("A failed request", settings, client); fail("Expected recoverable error") }
            catch (error: IOException) {
                assertTrue(error.message.orEmpty().contains("busy"))
                assertFalse(error.message.orEmpty().contains("private server"))
                assertFalse(error.message.orEmpty().contains(settings.apiKey))
            }
        }
        assertEquals(6, attempts)
    }

    @Test fun automaticSelectionUsesTheKeysListedTextModels() = runBlocking {
        GeminiMessageSummarizer.clearCache()
        var catalogs = 0
        var generations = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = if (chain.request().method == "GET") {
                catalogs++
                """{"models":[{"name":"models/gemini-pro","supportedGenerationMethods":["generateContent"]},{"name":"models/gemini-3.1-flash-lite","supportedGenerationMethods":["generateContent"]}]}"""
            } else {
                generations++
                assertTrue(chain.request().url.encodedPath.endsWith("gemini-3.1-flash-lite:generateContent"))
                """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"ስብሰባው ነገ ነው።"}]}}]}"""
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        val auto = settings.copy(model = "auto", language = "Amharic")
        assertEquals("gemini-3.1-flash-lite", GeminiMessageSummarizer.verify(auto, client))
        assertEquals("ስብሰባው ነገ ነው።", GeminiMessageSummarizer.summarize("ስብሰባው ነገ ነው።", auto, client))
        assertEquals(1, catalogs)
        assertEquals(1, generations)
    }

    @Test fun invalidKeyIsNotRetriedOrHiddenBehindAvailabilityMessage() = runBlocking {
        var attempts = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            attempts++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(400).message("Bad Request")
                .body("""{"error":{"status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}""".toResponseBody()).build()
        }.build()
        try { GeminiMessageSummarizer.summarize("Invalid key test", settings, client); fail("Expected key error") }
        catch (error: IOException) { assertTrue(error.message.orEmpty().contains("key is invalid")); assertFalse(error.message.orEmpty().contains("HTTP")) }
        assertEquals(1, attempts)
    }

    @Test fun busyServerCanRecoverWithoutAnotherUserTap() = runBlocking {
        GeminiMessageSummarizer.clearCache()
        var attempts = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            attempts++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (attempts == 1) 503 else 200).message("Result").header("Retry-After", "0")
                .body((if (attempts == 1) """{"error":{"status":"UNAVAILABLE"}}""" else
                    """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Recovered summary"}]}}]}""").toResponseBody()).build()
        }.build()
        assertEquals("Recovered summary", GeminiMessageSummarizer.summarize("Retry recovery", settings, client))
        assertEquals(2, attempts)
    }

    @Test fun removedAutomaticModelIsReplacedFromFreshCatalog() = runBlocking {
        GeminiMessageSummarizer.clearCache()
        var catalogs = 0
        val generations = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            val (code, body) = when {
                chain.request().method == "GET" -> {
                    catalogs++
                    200 to """{"models":[{"name":"models/gemini-3.1-flash-lite","supportedGenerationMethods":["generateContent"]},{"name":"models/gemini-3.5-flash-lite","supportedGenerationMethods":["generateContent"]}]}"""
                }
                path.contains("gemini-3.1-flash-lite") -> {
                    generations += path
                    404 to """{"error":{"status":"NOT_FOUND"}}"""
                }
                else -> {
                    generations += path
                    200 to """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Available model summary"}]}}]}"""
                }
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Result").body(body.toResponseBody()).build()
        }.build()
        assertEquals("Available model summary", GeminiMessageSummarizer.summarize("Model recovery", settings.copy(model = "auto"), client))
        assertEquals(2, catalogs)
        assertEquals(2, generations.size)
        assertTrue(generations.last().contains("gemini-3.5-flash-lite"))
    }

    @Test fun cancellingARequestStopsRetryAndReleasesTheSummarySlot() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var failedCalls = 0
        val busy = OkHttpClient.Builder().addInterceptor { chain ->
            failedCalls++
            entered.complete(Unit)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("Busy")
                .body("""{"error":{"status":"UNAVAILABLE"}}""".toResponseBody()).build()
        }.build()
        val job = launch { GeminiMessageSummarizer.summarize("Cancel this request", settings, busy) }
        entered.await()
        job.cancelAndJoin()
        assertEquals(1, failedCalls)
        val success = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Next request works"}]}}]}""".toResponseBody()).build()
        }.build()
        assertEquals("Next request works", GeminiMessageSummarizer.summarize("Next after cancellation", settings, success))
    }

    @Test fun quotaFailuresAreNotRetriedAndRespectCooldown() = runBlocking {
        var attempts = 0
        val limited = settings.copy(apiKey = "quota-test-key")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            attempts++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(429).message("Quota").header("Retry-After", "60")
                .body("""{"error":{"status":"RESOURCE_EXHAUSTED"}}""".toResponseBody()).build()
        }.build()
        try { GeminiMessageSummarizer.summarize("Quota test", limited, client); fail("Expected quota failure") }
        catch (error: IOException) { assertTrue(error.message.orEmpty().contains("allowance")) }
        try { GeminiMessageSummarizer.summarize("Quota test again", limited, client); fail("Expected cooldown") }
        catch (error: IllegalStateException) { assertTrue(error.message.orEmpty().contains("allowance")) }
        assertEquals(1, attempts)
    }
}
