package com.example.bixby

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AssistantAiHandler(private val context: android.content.Context) {
    suspend fun generateResponse(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("Gemini API key is not configured."))
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
                doOutput = true
            }
            val quotedPrompt = JSONObject.quote("You are Bixby, a concise and natural Android voice assistant. Answer naturally and briefly. If the user speaks Hindi/Hinglish, reply in Hindi/Hinglish. For current or internet-dependent questions, use Google Search grounding. User request: $prompt")
            val body = "{\"contents\":[{\"parts\":[{\"text\":$quotedPrompt}]}],\"tools\":[{\"google_search\":{}}]}"
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val responseBody = if (code in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            if (code !in 200..299) return@withContext Result.failure(IllegalStateException("Gemini API error $code"))
            val answer = JSONObject(responseBody).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")?.trim().orEmpty()
            if (answer.isBlank()) Result.failure(IllegalStateException("Gemini returned an empty response.")) else Result.success(answer)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Gemini connection failed: ${e.message ?: "unknown error"}"))
        }
    }
}