package com.example.bixby

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AssistantAiHandler(private val context: android.content.Context) {
    private val historyLock = Any()
    private val conversation = JSONArray()

    suspend fun generateResponse(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = "AQ.Ab8RN6IhAj84YcpR39_XhSDmAmVEtUYQY12ZSbyolUxjyJHrvA"
        
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured.")
            )
        }
        try {
            val requestBody = JSONObject().apply {
                put("system_instruction", JSONObject().put(
                    "parts", JSONArray().put(JSONObject().put(
                        "text",
                        "You are Bixby, a natural Android voice assistant. Answer concisely and helpfully. " +
                            "If the user speaks Hindi/Hinglish, reply in Hindi/Hinglish. Do not describe yourself as an AI unless asked."
                    ))
                ))
                put("contents", buildConversation(prompt))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 512)
                })
                put("tools", JSONArray().put(
                    JSONObject().put("google_search", JSONObject())
                ))
            }

            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }

            connection.outputStream.use {
                it.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val responseBody = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            connection.disconnect()

            if (code !in 200..299) {
                val detail = try {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message")
                } catch (_: Exception) {
                    null
                }
                val suffix = if (!detail.isNullOrBlank()) ": " + detail else ""
                return@withContext Result.failure(
                    IllegalStateException("Gemini API error " + code + suffix)
                )
            }

            val answer = extractAnswer(responseBody)
            if (answer.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Gemini returned an empty response.")
                )
            }

            synchronized(historyLock) {
                conversation.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                })
                conversation.put(JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().put(JSONObject().put("text", answer)))
                })
                while (conversation.length() > 8) conversation.remove(0)
            }

            Result.success(answer)
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    "Gemini connection failed: " + (e.message ?: "unknown error")
                )
            )
        }
    }

    private fun buildConversation(prompt: String): JSONArray {
        val result = JSONArray()
        synchronized(historyLock) {
            for (index in 0 until conversation.length()) {
                result.put(conversation.optJSONObject(index))
            }
        }
        result.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        })
        return result
    }

    private fun extractAnswer(responseBody: String): String {
        val candidates = JSONObject(responseBody).optJSONArray("candidates") ?: return ""
        val parts = candidates.optJSONObject(0)?.optJSONObject("content")
            ?.optJSONArray("parts") ?: return ""
        val answer = StringBuilder()
        for (index in 0 until parts.length()) {
            val text = parts.optJSONObject(index)?.optString("text").orEmpty()
            if (text.isNotBlank()) {
                if (answer.isNotEmpty()) answer.append('\n')
                answer.append(text)
            }
        }
        return answer.toString().trim()
    }
}
