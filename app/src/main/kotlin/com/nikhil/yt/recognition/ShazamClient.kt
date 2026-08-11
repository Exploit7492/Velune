package com.nikhil.yt.recognition

import com.nikhil.yt.recognition.models.RecognitionResult
import com.nikhil.yt.recognition.models.ShazamRequestJson
import com.nikhil.yt.recognition.models.ShazamResponseJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

object ShazamClient {

    private const val MAX_CONCURRENT_REQUESTS = 2
    private const val MIN_REQUEST_INTERVAL_MS = 1000L
    private const val MAX_RETRIES = 3
    private const val INITIAL_RETRY_DELAY_MS = 2000L
    private const val CACHE_DURATION_MS = 300000L
    private const val MAX_QUEUE_SIZE = 50

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient()
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private val userAgents = listOf(
        "Dalvik/2.1.0 (Linux; U; Android 5.0.2; VS980 4G Build/LRX22G)",
        "Dalvik/1.6.0 (Linux; U; Android 4.4.2; SM-T210 Build/KOT49H)",
        "Dalvik/2.1.0 (Linux; U; Android 5.1.1; SM-P905V Build/LMY47X)",
        "Dalvik/2.1.0 (Linux; U; Android 6.0.1; SM-G920F Build/MMB29K)",
        "Dalvik/2.1.0 (Linux; U; Android 5.0; SM-G900F Build/LRX21T)"
    )

    private val timezones = listOf(
        "Europe/Paris", "Europe/London", "America/New_York",
        "America/Los_Angeles", "Asia/Tokyo", "Asia/Dubai"
    )

    private val activeRequests = AtomicInteger(0)
    private var lastRequestTime = 0L
    private val requestMutex = Mutex()
    private val requestQueue = ConcurrentLinkedQueue<PendingRequest>()
    private val resultCache = ConcurrentHashMap<String, CachedResult>()
    private var nextRequestId = 0L
    private var isProcessingQueue = false

    suspend fun recognize(signature: String, sampleDurationMs: Long): Result<RecognitionResult> {
        val cacheKey = generateCacheKey(signature)
        getCachedResult(cacheKey)?.let { return Result.success(it) }
        return enqueueRequest(signature, sampleDurationMs)
    }

    fun getPendingRequestsCount(): Int = requestQueue.size
    fun getActiveRequestsCount(): Int = activeRequests.get()
    fun clearCache() { resultCache.clear() }
    fun cancelPendingRequests() { requestQueue.clear() }

    fun cleanup() {
        cancelPendingRequests()
        clearCache()
    }

    private suspend fun enqueueRequest(signature: String, sampleDurationMs: Long): Result<RecognitionResult> {
        val request = requestMutex.withLock {
            if (requestQueue.size >= MAX_QUEUE_SIZE) {
                return Result.failure(Exception("Request queue is full. Please wait."))
            }
            val requestId = nextRequestId++
            val req = PendingRequest(requestId, signature, sampleDurationMs)
            requestQueue.offer(req)
            if (!isProcessingQueue) {
                isProcessingQueue = true
                scope.launch { processQueue() }
            }
            req
        }
        return request.awaitResult()
    }

    private suspend fun processQueue() {
        while (true) {
            val request = requestQueue.poll() ?: break
            while (activeRequests.get() >= MAX_CONCURRENT_REQUESTS) {
                delay(100)
            }
            activeRequests.incrementAndGet()
            scope.launch {
                try {
                    val result = executeRequest(request.signature, request.sampleDurationMs)
                    request.completeWith(result)
                } catch (e: Exception) {
                    request.completeWith(Result.failure(e))
                } finally {
                    activeRequests.decrementAndGet()
                }
            }
            enforceRateLimit()
        }
        isProcessingQueue = false
    }

    private suspend fun executeRequest(signature: String, sampleDurationMs: Long): Result<RecognitionResult> {
        var lastException: Exception? = null
        for (attempt in 0 until MAX_RETRIES) {
            try {
                enforceRateLimit()
                val result = performRecognition(signature, sampleDurationMs)
                val cacheKey = generateCacheKey(signature)
                cacheResult(cacheKey, result)
                return Result.success(result)
            } catch (e: Exception) {
                lastException = e
                if (e.message?.contains("429") == true || e.message?.contains("Too many requests", ignoreCase = true) == true) {
                    if (attempt < MAX_RETRIES - 1) {
                        delay(calculateBackoffDelay(attempt))
                        continue
                    }
                } else {
                    throw e
                }
            }
        }
        throw lastException ?: Exception("Recognition failed after $MAX_RETRIES attempts")
    }

    private suspend fun performRecognition(signature: String, sampleDurationMs: Long): RecognitionResult {
        val timestamp = System.currentTimeMillis() / 1000
        val uuid1 = UUID.randomUUID().toString().uppercase()
        val uuid2 = UUID.randomUUID().toString()

        val request = ShazamRequestJson(
            geolocation = ShazamRequestJson.Geolocation(
                altitude = Random.nextDouble() * 400 + 100,
                latitude = Random.nextDouble() * 180 - 90,
                longitude = Random.nextDouble() * 360 - 180
            ),
            signature = ShazamRequestJson.Signature(
                samplems = sampleDurationMs,
                timestamp = timestamp,
                uri = signature
            ),
            timestamp = timestamp,
            timezone = timezones.random()
        )

        val requestBody = json.encodeToString(request).toRequestBody(mediaType)
        val httpRequest = Request.Builder()
            .url("https://amp.shazam.com/discovery/v5/en/US/android/-/tag/$uuid1/$uuid2?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3")
            .post(requestBody)
            .header("User-Agent", userAgents.random())
            .header("Content-Language", "en_US")
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val code = response.code
                when (code) {
                    429 -> throw Exception("Too many requests")
                    404 -> throw Exception("No match found")
                    in 500..599 -> throw Exception("Shazam service temporarily unavailable")
                    else -> throw Exception("Recognition failed (error $code)")
                }
            }
            val bodyString = response.body?.string() ?: throw Exception("Empty response")
            val shazamResponse = json.decodeFromString<ShazamResponseJson>(bodyString)
            return shazamResponse.toRecognitionResult() ?: throw Exception("No match found")
        }
    }

    private suspend fun enforceRateLimit() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastRequest = currentTime - lastRequestTime
        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
            delay(MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest)
        }
        lastRequestTime = System.currentTimeMillis()
    }

    private fun calculateBackoffDelay(attempt: Int): Long = INITIAL_RETRY_DELAY_MS * (1 shl attempt)
    private fun generateCacheKey(signature: String): String = signature.take(64)

    private fun getCachedResult(cacheKey: String): RecognitionResult? {
        val cached = resultCache[cacheKey] ?: return null
        if (System.currentTimeMillis() - cached.timestamp > CACHE_DURATION_MS) {
            resultCache.remove(cacheKey)
            return null
        }
        return cached.result
    }

    private fun cacheResult(cacheKey: String, result: RecognitionResult) {
        resultCache[cacheKey] = CachedResult(result, System.currentTimeMillis())
    }

    private class PendingRequest(
        val id: Long,
        val signature: String,
        val sampleDurationMs: Long
    ) {
        private val deferred = CompletableDeferred<Result<RecognitionResult>>()
        suspend fun awaitResult(): Result<RecognitionResult> = deferred.await()
        fun completeWith(result: Result<RecognitionResult>) = deferred.complete(result)
    }

    private class CachedResult(
        val result: RecognitionResult,
        val timestamp: Long
    )
}
