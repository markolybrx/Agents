package com.example.data.network

import com.example.data.prompts.ThinkingLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class GeminiUsage(
    val promptTokens: Int = 0,
    val outputTokens: Int = 0,
    val thoughtTokens: Int = 0,
    val totalTokens: Int = 0
)

sealed class GeminiCallResult {
    data class Success(
        val text: String,
        val finishReason: String,
        val usage: GeminiUsage,
        val latencyMs: Long,
        val httpStatus: Int
    ) : GeminiCallResult()

    data class Failure(
        val httpStatus: Int,
        val isRateLimited: Boolean,
        val isTimeout: Boolean,
        val isTransient: Boolean,
        val message: String,
        val latencyMs: Long
    ) : GeminiCallResult()
}

class GeminiRestClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(55, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .callTimeout(55, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateContent(
        apiKey: String,
        modelId: String,
        systemInstruction: String,
        userPayload: String,
        thinkingLevel: ThinkingLevel,
        thinkingEnabled: Boolean,
        expectJson: Boolean = true
    ): GeminiCallResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val bodyJson = JSONObject().apply {
                put(
                    "system_instruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemInstruction))
                    )
                )
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", userPayload)))
                    )
                )
                val genConfig = JSONObject()
                if (expectJson) {
                    genConfig.put("responseMimeType", "application/json")
                }
                if (thinkingEnabled && modelId.contains("3.", ignoreCase = true)) {
                    genConfig.put(
                        "thinkingConfig",
                        JSONObject().put("thinkingLevel", thinkingLevel.apiValue)
                    )
                }
                put("generationConfig", genConfig)
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("X-goog-api-key", apiKey)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                val rawBody = response.body?.string().orEmpty()
                val code = response.code

                if (!response.isSuccessful) {
                    val errMsg = extractErrorMessage(rawBody).ifBlank { "HTTP $code" }
                    return@withContext GeminiCallResult.Failure(
                        httpStatus = code,
                        isRateLimited = code == 429,
                        isTimeout = code == 408 || code == 504,
                        isTransient = code == 429 || code == 500 || code == 502 || code == 503 || code == 504,
                        message = errMsg,
                        latencyMs = latency
                    )
                }

                val root = JSONObject(rawBody)
                val candidates = root.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val finishReason = firstCandidate?.optString("finishReason", "STOP") ?: "STOP"

                val textBuilder = StringBuilder()
                val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        // Skip internal thought parts so JSON extraction stays pure
                        if (part.optBoolean("thought", false)) continue
                        val t = part.optString("text", "")
                        if (t.isNotEmpty()) textBuilder.append(t)
                    }
                }

                val usageObj = root.optJSONObject("usageMetadata")
                val promptTokens = usageObj?.optInt("promptTokenCount", 0) ?: 0
                val outputTokens = usageObj?.optInt("candidatesTokenCount", 0) ?: 0
                val thoughtTokens = usageObj?.optInt("thoughtsTokenCount", 0) ?: 0
                val totalTokens = usageObj?.optInt("totalTokenCount", promptTokens + outputTokens + thoughtTokens)
                    ?: (promptTokens + outputTokens + thoughtTokens)

                val cleanText = stripCodeFences(textBuilder.toString().trim())
                GeminiCallResult.Success(
                    text = cleanText,
                    finishReason = finishReason,
                    usage = GeminiUsage(
                        promptTokens = promptTokens,
                        outputTokens = outputTokens,
                        thoughtTokens = thoughtTokens,
                        totalTokens = totalTokens
                    ),
                    latencyMs = latency,
                    httpStatus = code
                )
            }
        } catch (e: SocketTimeoutException) {
            GeminiCallResult.Failure(
                httpStatus = 408,
                isRateLimited = false,
                isTimeout = true,
                isTransient = true,
                message = "55s HTTP timeout exceeded",
                latencyMs = System.currentTimeMillis() - start
            )
        } catch (e: InterruptedIOException) {
            GeminiCallResult.Failure(
                httpStatus = 408,
                isRateLimited = false,
                isTimeout = true,
                isTransient = true,
                message = "Request timed out: ${e.message ?: "interrupted"}",
                latencyMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            GeminiCallResult.Failure(
                httpStatus = 0,
                isRateLimited = false,
                isTimeout = false,
                isTransient = true,
                message = e.message ?: "Network error",
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    suspend fun pingKey(apiKey: String, modelId: String): GeminiCallResult {
        return generateContent(
            apiKey = apiKey,
            modelId = modelId,
            systemInstruction = "Respond with {\"ok\":true} only.",
            userPayload = "ping",
            thinkingLevel = ThinkingLevel.LOW,
            thinkingEnabled = false,
            expectJson = true
        )
    }

    private fun extractErrorMessage(raw: String): String {
        return try {
            val obj = JSONObject(raw)
            obj.optJSONObject("error")?.optString("message", "").orEmpty()
        } catch (_: Exception) {
            raw.take(180)
        }
    }

    private fun stripCodeFences(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val firstNewLine = trimmed.indexOf('\n')
        if (firstNewLine == -1) return trimmed
        val withoutFirst = trimmed.substring(firstNewLine + 1)
        val lastFence = withoutFirst.lastIndexOf("```")
        return if (lastFence >= 0) withoutFirst.substring(0, lastFence).trim() else withoutFirst.trim()
    }
}
