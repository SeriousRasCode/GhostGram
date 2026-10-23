package com.example.data.summary

import android.os.SystemClock
import android.util.LruCache
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object GeminiMessageSummarizer {
    private val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    private val gate = Mutex()
    private val cache = LruCache<String, String>(24)
    @Volatile private var retryAfter = 0L
    fun clearCache() { cache.evictAll() }

    internal suspend fun summarize(text: String, settings: GeminiSettings, httpClient: OkHttpClient = client): String {
        require(settings.apiKey.isNotBlank()) { "Add your Gemini API key first." }
        require(GeminiSummaryProtocol.validModel(settings.model)) { "Choose a valid Gemini model." }
        val body = GeminiSummaryProtocol.request(text, settings.language)
        val hash = MessageDigest.getInstance("SHA-256").digest((settings.apiKey + settings.model + settings.language + text).toByteArray())
            .joinToString("") { "%02x".format(it) }
        cache.get(hash)?.let { return it }
        check(SystemClock.elapsedRealtime() >= retryAfter) { "Gemini is cooling down after a quota limit. Try later or use the local option." }
        check(gate.tryLock()) { "A summary is already running. Please wait." }
        try {
            val request = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/${settings.model}:generateContent")
                .header("x-goog-api-key", settings.apiKey).post(body.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            val call = httpClient.newCall(request)
            val json = suspendCancellableCoroutine<String> { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(IOException("Couldn't reach Gemini. Check your connection or try the local option."))
                    }
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val value = response.use {
                                if (!it.isSuccessful) {
                                    if (it.code == 429) {
                                        val seconds = it.header("Retry-After")?.toLongOrNull()?.coerceIn(30, 3600) ?: 60
                                        retryAfter = SystemClock.elapsedRealtime() + seconds * 1000
                                    }
                                    throw IOException(GeminiSummaryProtocol.httpError(it.code))
                                }
                                val source = it.body?.source() ?: throw IOException("Gemini returned no summary.")
                                if (source.request(65_537)) throw IOException("Gemini returned an oversized response. Try the local option.")
                                source.readUtf8()
                            }
                            if (continuation.isActive) continuation.resume(value)
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }
                })
            }
            return GeminiSummaryProtocol.response(json).also { cache.put(hash, it) }
        } finally { gate.unlock() }
    }
}
