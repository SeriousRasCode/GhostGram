package com.example

import com.example.data.summary.GeminiMessageSummarizer
import com.example.data.summary.GeminiSettings
import kotlinx.coroutines.runBlocking
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
    private val settings = GeminiSettings(apiKey = "test-only-not-a-real-key")

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
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("Unavailable")
                .body("private server detail must not surface".toResponseBody()).build()
        }.build()
        repeat(2) {
            try { GeminiMessageSummarizer.summarize("A failed request", settings, client); fail("Expected recoverable error") }
            catch (error: IOException) {
                assertTrue(error.message.orEmpty().contains("temporarily"))
                assertFalse(error.message.orEmpty().contains("private server"))
                assertFalse(error.message.orEmpty().contains(settings.apiKey))
            }
        }
        assertEquals(2, attempts)
    }
}
