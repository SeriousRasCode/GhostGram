package com.example.data.summary

import android.os.SystemClock
import android.util.LruCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

object GeminiMessageSummarizer {
    private val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    private val gate = Mutex()
    private val cache = LruCache<String, String>(24)
    private data class Catalog(val models: List<String>, val expires: Long)
    private val catalogs = LruCache<String, Catalog>(4)
    private val cooldowns = LruCache<String, Long>(16)
    fun clearCache() { cache.evictAll(); catalogs.evictAll() }

    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    /** Checks the key and API compatibility without sending any Telegram message. */
    internal suspend fun verify(settings: GeminiSettings, httpClient: OkHttpClient = client): String {
        validate(settings)
        return GeminiModels.choose(listModels(settings.apiKey, httpClient), settings.model)
    }

    private fun validate(settings: GeminiSettings) {
        require(settings.apiKey.isNotBlank() && settings.apiKey.length <= 256 && settings.apiKey.all { it.code in 33..126 }) {
            "Add a valid Gemini key from Google AI Studio."
        }
        require(settings.model == "auto" || GeminiSummaryProtocol.validModel(settings.model)) { "Reconnect Gemini in settings." }
    }

    internal suspend fun summarize(text: String, settings: GeminiSettings, httpClient: OkHttpClient = client): String {
        validate(settings)
        val body = GeminiSummaryProtocol.request(text, settings.language)
        val cacheKey = hash(listOf(settings.apiKey, settings.model, settings.language, text).joinToString("\u0000"))
        cache.get(cacheKey)?.let { return it }
        check(gate.tryLock()) { "A summary is already running. Please wait." }
        try {
            var model = if (settings.model == "auto") verify(settings, httpClient) else settings.model
            // Refresh model availability once if a previously available model disappears.
            val json = try { generate(model, settings.apiKey, body, httpClient) }
            catch (error: ApiException) {
                if (error.code != 404 || settings.model != "auto") throw error
                catalogs.remove(hash(settings.apiKey))
                model = GeminiModels.choose(listModels(settings.apiKey, httpClient).filterNot { it == model }, "auto")
                generate(model, settings.apiKey, body, httpClient)
            }
            return GeminiSummaryProtocol.response(json).also { cache.put(cacheKey, it) }
        } finally { gate.unlock() }
    }

    private suspend fun generate(model: String, apiKey: String, body: String, httpClient: OkHttpClient): String {
        val request = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey).post(body.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        return execute(request, hash("$apiKey\u0000$model"), httpClient, 65_536)
    }

    private suspend fun listModels(apiKey: String, httpClient: OkHttpClient): List<String> {
        val key = hash(apiKey)
        catalogs.get(key)?.takeIf { it.expires > SystemClock.elapsedRealtime() }?.let { return it.models }
        val models = mutableListOf<String>()
        var token = ""
        repeat(4) {
            val url = HttpUrl.Builder().scheme("https").host("generativelanguage.googleapis.com")
                .addPathSegments("v1beta/models").addQueryParameter("pageSize", "100")
                .apply { if (token.isNotEmpty()) addQueryParameter("pageToken", token) }.build()
            val request = Request.Builder().url(url).header("x-goog-api-key", apiKey).build()
            val json = execute(request, key, httpClient, 524_288)
            models += GeminiModels.supported(json)
            token = JSONObject(json).optString("nextPageToken")
            if (token.isBlank()) return models.distinct().also {
                catalogs.put(key, Catalog(it, SystemClock.elapsedRealtime() + 600_000))
            }
        }
        throw IOException("Couldn't check your Gemini connection. Please try again.")
    }

    private class ApiException(val code: Int, message: String, val retrySeconds: Long?) : IOException(message)

    private suspend fun execute(request: Request, quotaKey: String, httpClient: OkHttpClient, maxBytes: Long): String {
        check(SystemClock.elapsedRealtime() >= (cooldowns.get(quotaKey) ?: 0L)) {
            "You've reached your Gemini allowance. Please try again later."
        }
        repeat(3) { attempt ->
            try { return executeOnce(request, httpClient, maxBytes) }
            catch (error: ApiException) {
                if (error.code == 429) cooldowns.put(quotaKey,
                    SystemClock.elapsedRealtime() + (error.retrySeconds ?: 60).coerceIn(1, 3600) * 1000)
                if (error.code !in setOf(408, 500, 502, 503, 504) || attempt == 2) throw error
                // Bounded exponential backoff with jitter; never retry invalid keys or daily quotas.
                val wait = error.retrySeconds?.times(1000) ?: ((1L shl attempt) * 1000 + Random.nextLong(250))
                if (wait > 5000) throw error
                delay(wait.coerceAtLeast(250))
            }
        }
        error("Unreachable retry state")
    }

    private suspend fun executeOnce(request: Request, httpClient: OkHttpClient, maxBytes: Long): String =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("Couldn't reach Gemini. Check your connection and try again."))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val value = response.use {
                            val source = it.body?.source() ?: throw IOException("Gemini didn't reply. Please try again.")
                            if (source.request(maxBytes + 1)) throw IOException("Couldn't read Gemini's reply. Please try again.")
                            val body = source.readUtf8()
                            if (!it.isSuccessful) throw ApiException(it.code, GeminiSummaryProtocol.apiError(it.code, body),
                                it.header("Retry-After")?.toLongOrNull() ?: GeminiSummaryProtocol.retrySeconds(body))
                            body
                        }
                        if (continuation.isActive) continuation.resume(value)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
}
