package com.skysense.app.data.remote

import com.google.gson.Gson
import com.skysense.app.data.model.GnssSnapshot
import com.skysense.app.data.model.PromptProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Direct REST client for the Gemini API.
 * Uses OkHttp + Gson — no Firebase dependency required.
 * The API key is always user-provided and retrieved from SecurePreferencesManager.
 */
class GeminiApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val mediaType = "application/json".toMediaType()
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/"
    private var resolvedModel: String? = null

    /**
     * Sends a GNSS-contextual question to Gemini and returns the AI response.
     *
     * @param userQuestion The user's question.
     * @param snapshot Current GNSS state (auto-included as context).
     * @param profile The prompt style profile to use.
     * @param customPromptText Used only when profile = CUSTOM.
     * @param apiKey The user's Gemini API key (never hardcoded).
     * @param overrideModelName Optional explicit model choice (e.g. "models/gemini-1.5-flash")
     */
    suspend fun ask(
        userQuestion: String,
        snapshot: GnssSnapshot,
        profile: PromptProfile,
        customPromptText: String = "",
        apiKey: String,
        overrideModelName: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemStyle = when (profile) {
                PromptProfile.BEGINNER ->
                    "Explain in very simple language as if talking to a curious child. Avoid jargon. Use analogies."
                PromptProfile.STUDENT ->
                    "Explain for an engineering student familiar with basic physics. Include some technical detail."
                PromptProfile.EXPERT ->
                    "Use full GNSS technical terminology. Include signal processing, geometry, and geodesy concepts where relevant."
                PromptProfile.FUN_FACTS ->
                    "Include interesting historical, scientific, and fun facts. Make it engaging and surprising."
                PromptProfile.CUSTOM ->
                    customPromptText.ifBlank { "Explain clearly and helpfully." }
            }

            val modelName = overrideModelName ?: resolveModel(apiKey)
            if (modelName == null) {
                return@withContext Result.failure(Exception("No suitable Gemini model found for this API key. Try generating a new key from Google AI Studio."))
            }

            val gnssContext = buildGnssContext(snapshot)

            val fullPrompt = """
                You are SkySense AI, an expert GNSS educator built into a satellite visualization app.
                
                Style instruction: $systemStyle
                
                Current GNSS Context (live data from the user's device):
                $gnssContext
                
                User question: $userQuestion
                
                Provide a clear, helpful answer. When relevant, connect your answer to the live GNSS data above.
                Keep the response concise (2–4 paragraphs max).
            """.trimIndent()

            val requestBody = gson.toJson(mapOf(
                "contents" to listOf(
                    mapOf("parts" to listOf(mapOf("text" to fullPrompt)))
                ),
                "generationConfig" to mapOf(
                    "temperature" to 0.7,
                    "maxOutputTokens" to 1024
                )
            ))

            val request = Request.Builder()
                .url("$baseUrl$modelName:generateContent?key=$apiKey")
                .post(requestBody.toRequestBody(mediaType))
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful) {
                val errorMsg = parseError(body)
                return@withContext Result.failure(Exception("Gemini API error ${response.code}: $errorMsg"))
            }

            val text = parseResponse(body)
                ?: return@withContext Result.failure(Exception("Empty response from Gemini"))

            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildGnssContext(s: GnssSnapshot): String {
        if (!s.hasValidFix) return "No GPS fix yet — the device is searching for satellites."
        val constellations = s.constellationsUsed.joinToString(", ") { it.displayName }
        return """
            - Location: ${"%.6f".format(s.latitude)}°, ${"%.6f".format(s.longitude)}
            - Altitude: ${"%.1f".format(s.altitude)} m
            - Horizontal accuracy: ${"%.1f".format(s.horizontalAccuracy)} m
            - Vertical accuracy: ${"%.1f".format(s.verticalAccuracy)} m
            - Speed: ${"%.1f".format(s.speed)} m/s
            - Satellites used in fix: ${s.satellitesUsed}
            - Satellites visible: ${s.satellitesVisible}
            - Constellations active: $constellations
            - PDOP: ${"%.2f".format(s.pdop)}, HDOP: ${"%.2f".format(s.hdop)}, VDOP: ${"%.2f".format(s.vdop)}
        """.trimIndent()
    }

    private fun parseResponse(body: String?): String? {
        if (body == null) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val map = gson.fromJson(body, Map::class.java) as? Map<String, Any> ?: return null
            val candidates = map["candidates"] as? List<*> ?: return null
            val first = candidates.firstOrNull() as? Map<*, *> ?: return null
            val content = first["content"] as? Map<*, *> ?: return null
            val parts = content["parts"] as? List<*> ?: return null
            val part = parts.firstOrNull() as? Map<*, *> ?: return null
            part["text"] as? String
        } catch (e: Exception) {
            null
        }
    }

    private fun parseError(body: String?): String {
        if (body == null) return "Unknown error"
        return try {
            @Suppress("UNCHECKED_CAST")
            val map = gson.fromJson(body, Map::class.java) as? Map<String, Any> ?: return body
            val error = map["error"] as? Map<*, *>
            (error?.get("message") as? String) ?: body
        } catch (e: Exception) {
            body
        }
    }

    /**
     * Fetches all available generative text models for the given API key.
     */
    suspend fun getAvailableModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${baseUrl}models?key=$apiKey")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to fetch models: ${response.code}"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                
                @Suppress("UNCHECKED_CAST")
                val map = gson.fromJson(body, Map::class.java) as? Map<String, Any> ?: return@withContext Result.failure(Exception("Invalid JSON"))
                val models = map["models"] as? List<Map<String, Any>> ?: return@withContext Result.failure(Exception("No models array"))

                val availableModels = models.filter { model ->
                    val methods = model["supportedGenerationMethods"] as? List<String>
                    val name = (model["name"] as? String)?.lowercase() ?: return@filter false
                    
                    val hasGenerateContent = methods?.contains("generateContent") == true
                    
                    // Only explicitly include models known to have > 0 limits on the free tier, and exclude TTS/preview variants
                    val isUsable = (name.contains("gemini-1.5-flash") ||
                                   name.contains("gemini-2.5-flash") ||
                                   name.contains("gemini-3-flash") ||
                                   name.contains("gemini-3.1-flash-lite") ||
                                   name.contains("gemini-3.5-flash") ||
                                   name.contains("gemma-4")) &&
                                   !name.contains("pro") &&
                                   !name.contains("tts") &&
                                   !name.contains("preview")

                    hasGenerateContent && isUsable
                }.mapNotNull { it["name"] as? String }
                
                Result.success(availableModels)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun resolveModel(apiKey: String): String? = withContext(Dispatchers.IO) {
        if (resolvedModel != null) return@withContext resolvedModel

        getAvailableModels(apiKey).onSuccess { availableModels ->
            if (availableModels.isEmpty()) return@withContext null

                // Prefer the 3.1 Flash Lite model for highest free tier limits, fallback to other gemini models
                val chosenModel = availableModels.firstOrNull { it.contains("gemini-3.1-flash-lite") }
                    ?: availableModels.firstOrNull { it.contains("gemini-1.5-flash") }
                    ?: availableModels.firstOrNull { it.contains("gemini") }
                    ?: availableModels.firstOrNull()

                if (chosenModel != null) {
                    resolvedModel = chosenModel
                }
                return@withContext chosenModel
        }.onFailure {
            it.printStackTrace()
            return@withContext null
        }
        return@withContext null
    }
}
