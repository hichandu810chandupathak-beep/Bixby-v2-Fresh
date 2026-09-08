package com.bixby.assistant

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BixbyBrain {
    suspend fun askQuestion(prompt: String, apiKey: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Using the standard Generative AI SDK with simple API Key
                val generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = apiKey
                )
                val response = generativeModel.generateContent(prompt)
                response.text ?: "I am sorry, I couldn't process that."
            } catch (e: Exception) {
                "Error: ${e.localizedMessage}"
            }
        }
    }
}
