package com.bixby.assistant

import com.google.ai.client.generativeai.GenerativeModel

object BixbyBrain {

    suspend fun askQuestion(prompt: String, apiKey: String): String {
        return try {
            val generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            )

            val response = generativeModel.generateContent(prompt)
            response.text ?: "No response generated."
        } catch (e: Exception) {
            "Error: ${e.message ?: "Unable to get a response from Gemini."}"
        }
    }
}
