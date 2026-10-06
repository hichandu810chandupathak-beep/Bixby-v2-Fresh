package com.example.bixby

import java.util.Locale

object OfflineChatHandler {
    fun respond(prompt: String): String {
        val text = prompt.trim()
        if (text.isEmpty()) return "Please say or type something."

        val normalized = text.lowercase(Locale.ROOT)

        return when {
            normalized.matches(Regex("^(hi|hello|hey|namaste|namaskar)[!. ]*$")) ->
                "Hello! How can I help you?"

            normalized.contains("how are you") ||
                normalized.contains("how r u") ||
                normalized.contains("kaise ho") ||
                normalized.contains("kaisi ho") ->
                "I am ready to help. What would you like me to do?"

            normalized.contains("who are you") ||
                normalized.contains("what are you") ||
                normalized.contains("tum kaun") ||
                normalized.contains("aap kaun") ->
                "I am your personal voice assistant."

            normalized.contains("thank") ||
                normalized.contains("thanks") ||
                normalized.contains("shukriya") ||
                normalized.contains("dhanyavad") ->
                "You're welcome."

            normalized.contains("good morning") ->
                "Good morning! How can I help you?"

            normalized.contains("good night") ->
                "Good night. Take care."

            normalized.contains("time") ->
                "I can help with that when the online assistant is available."

            normalized.contains("weather") ||
                normalized.contains("mausam") ->
                "I need the online assistant for current weather information."

            normalized.contains("search") ||
                normalized.contains("latest") ||
                normalized.contains("news") ->
                "I need an internet connection for current information."

            normalized.contains("help") ||
                normalized.contains("madad") ->
                "You can ask me to call someone, open an app, control the flashlight, or chat with me."

            normalized.contains("good") || normalized.contains("great") ->
                "Glad to hear that. How can I help?"

            normalized.contains("bye") ||
                normalized.contains("goodbye") ->
                "Goodbye."

            else ->
                "I am offline right now, but I am still here. Try a local command or ask me something simple."
        }
    }
}
