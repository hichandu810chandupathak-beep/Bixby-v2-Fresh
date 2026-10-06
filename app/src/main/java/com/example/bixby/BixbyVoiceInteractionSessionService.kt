package com.example.bixby

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class BixbyVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        BixbyVoiceInteractionSession(this)
}

private class BixbyVoiceInteractionSession(
    context: Context
) : VoiceInteractionSession(context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    override fun onShow(args: Bundle?, showFlags: Int) {
        // Audio-only mirror mode: deliberately do not call super.onShow() and
        // never launch MainActivity or any external assistant UI.
        startAudioOnlyTurn()
    }

    private fun startAudioOnlyTurn() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            finishAudioOnly()
            return
        }

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = if (Locale.getDefault().language == "hi") {
                    Locale("hi", "IN")
                } else {
                    Locale("en", "IN")
                }
            }
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) = finishAudioOnly()

            override fun onResults(results: Bundle?) {
                val command = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

                if (command == null) {
                    finishAudioOnly()
                    return
                }

                scope.launch {
                    val result = AssistantAiHandler(context).generateResponse(command)
                    result.onSuccess { response ->
                        speak(response)
                    }.onFailure {
                        val fallback = OfflineChatHandler.respond(command)
                        speak(fallback)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putExtra(
                    RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                    RecognizerIntent.LANGUAGE_SWITCH_BALANCED
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,
                    arrayListOf("hi-IN", "en-IN")
                )
            }
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            finishAudioOnly()
        }
    }

    private fun speak(response: String) {
        if (response.isBlank()) {
            finishAudioOnly()
            return
        }

        tts?.setSpeechRate(0.96f)
        tts?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "bixby_audio_response")
        finishAudioOnly()
    }

    private fun finishAudioOnly() {
        try { recognizer?.cancel() } catch (_: Exception) { }
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
        scope.cancel()
        try { hide() } catch (_: Exception) { }
    }

    override fun onHide() {
        super.onHide()
        try { recognizer?.cancel() } catch (_: Exception) { }
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
        scope.cancel()
    }
}
