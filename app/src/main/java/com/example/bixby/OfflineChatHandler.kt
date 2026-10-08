package com.example.bixby

import java.util.Locale

object OfflineChatHandler {
    fun respond(prompt: String): String {
        val text = prompt.trim()
        if (text.isEmpty()) return "Please say or type something."

        val normalized = text.lowercase(Locale.ROOT)
        val hindiOrHinglish = isHindiOrHinglish(text)

        return when {
            normalized.matches(Regex("^(hi|hello|hey|namaste|namaskar)[!. ]*$")) ->
                if (hindiOrHinglish) "नमस्ते! मैं आपकी मदद के लिए तैयार हूँ।" else "Hello! How can I help you?"

            normalized.contains("how are you") ||
                normalized.contains("how r u") ||
                normalized.contains("kaise ho") ||
                normalized.contains("kaisi ho") ->
                if (hindiOrHinglish) "मैं ठीक हूँ और आपकी मदद के लिए तैयार हूँ। क्या करना है?" else "I am ready to help. What would you like me to do?"

            normalized.contains("who are you") ||
                normalized.contains("what are you") ||
                normalized.contains("tum kaun") ||
                normalized.contains("aap kaun") ->
                if (hindiOrHinglish) "मैं आपका personal voice assistant हूँ।" else "I am your personal voice assistant."

            normalized.contains("thank") ||
                normalized.contains("thanks") ||
                normalized.contains("shukriya") ||
                normalized.contains("dhanyavad") ->
                if (hindiOrHinglish) "कोई बात नहीं।" else "You're welcome."

            normalized.contains("good morning") ->
                if (hindiOrHinglish) "सुप्रभात! मैं आपकी कैसे मदद करूँ?" else "Good morning! How can I help you?"

            normalized.contains("good night") ->
                if (hindiOrHinglish) "शुभ रात्रि। अपना ख्याल रखें।" else "Good night. Take care."

            normalized.contains("time") ->
                if (hindiOrHinglish) "Current time के लिए मुझे online assistant की जरूरत है।" else "I can help with that when the online assistant is available."

            normalized.contains("weather") ||
                normalized.contains("mausam") ->
                if (hindiOrHinglish) "Current weather के लिए internet connection चाहिए।" else "I need the online assistant for current weather information."

            normalized.contains("search") ||
                normalized.contains("latest") ||
                normalized.contains("news") ->
                if (hindiOrHinglish) "Current information के लिए internet connection चाहिए।" else "I need an internet connection for current information."

            normalized.contains("help") ||
                normalized.contains("madad") ->
                if (hindiOrHinglish) "आप मुझे किसी को call करने, app खोलने, flashlight control करने या chat करने के लिए कह सकते हैं।" else "You can ask me to call someone, open an app, control the flashlight, or chat with me."

            normalized.contains("good") || normalized.contains("great") ->
                if (hindiOrHinglish) "अच्छा! बताइए, मैं क्या करूँ?" else "Glad to hear that. How can I help?"

            normalized.contains("bye") ||
                normalized.contains("goodbye") ->
                if (hindiOrHinglish) "अलविदा।" else "Goodbye."

            else ->
                if (hindiOrHinglish) "मैं अभी offline हूँ, लेकिन local commands के लिए तैयार हूँ।" else "I am offline right now, but I am still here. Try a local command or ask me something simple."
        }
    }

    private fun isHindiOrHinglish(text: String): Boolean {
        if (text.any { it.code in 0x0900..0x097F }) return true
        val padded = " " + text.lowercase(Locale.ROOT) + " "
        val markers = listOf(
            " kya ", " kaise ", " kaisi ", " aap ", " tum ", " mera ", " meri ",
            " mujhe ", " chahiye ", " karo ", " karna ", " batao ", " hai ", " ho ",
            " nahi ", " nahin ", " kyun ", " kaha ", " kaun ", " kholo ", " kholna "
        )
        return markers.any { padded.contains(it) }
    }
}
