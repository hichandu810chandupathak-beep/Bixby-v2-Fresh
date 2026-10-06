package com.example.bixby

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
    private var ttsReady = false
    private val speechBuffer = StringBuilder()
    private var finalUtteranceId: String? = null

    override fun onShow(args: Bundle?, showFlags: Int) {
        // Audio-only mirror mode: never launch MainActivity or external assistant UI.
        startAudioOnlyTurn()
    }

    private fun startAudioOnlyTurn() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            finishAudioOnly()
            return
        }

        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                val locale = if (Locale.getDefault().language == "hi") {
                    Locale("hi", "IN")
                } else {
                    Locale("en", "IN")
                }
                tts?.language = locale
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == finalUtteranceId) finishAudioOnly()
                    }
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == finalUtteranceId) finishAudioOnly()
                    }
                })
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
                    val handler = AssistantAiHandler(context)
                    val result = handler.generateResponseStream(command) { chunk ->
                        scope.launch(Dispatchers.Main.immediate) {
                            enqueueSpeechChunk(chunk)
                        }
                    }

                    result.onSuccess {
                        flushSpeechBuffer()
                    }.onFailure { error ->
                        if (isActuallyOffline()) {
                            speakFinal(OfflineChatHandler.respond(command))
                        } else {
                            speakFinal(
                                "I couldn't reach Gemini right now. " +
                                    (error.message ?: "Please try again.")
                            )
                        }
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

    private fun enqueueSpeechChunk(chunk: String) {
        speechBuffer.append(chunk)
        val current = speechBuffer.toString()

        val boundary = Regex("[.!?।]+\\s+").findAll(current).lastOrNull()
        if (boundary != null && boundary.range.last >= 40 && boundary.range.last < current.lastIndex) {
            val sentence = current.substring(0, boundary.range.last + 1).trim()
            speechBuffer.delete(0, boundary.range.last + 1)
            speakQueued(sentence)
        } else if (current.length >= 140) {
            val split = current.lastIndexOf(' ', 120)
            if (split > 20) {
                val part = current.substring(0, split).trim()
                speechBuffer.delete(0, split)
                speakQueued(part)
            }
        }
    }

    private fun flushSpeechBuffer() {
        val remaining = speechBuffer.toString().trim()
        speechBuffer.setLength(0)
        if (remaining.isNotBlank()) {
            speakFinal(remaining)
        } else if (finalUtteranceId == null) {
            finishAudioOnly()
        }
    }

    private fun speakQueued(text: String) {
        if (text.isBlank()) return
        val speaker = tts ?: return

        if (!ttsReady) {
            scope.launch {
                kotlinx.coroutines.delay(150)
                speakQueued(text)
            }
            return
        }

        speaker.setSpeechRate(0.96f)
        speaker.speak(text, TextToSpeech.QUEUE_ADD, null, "bixby_stream_${System.nanoTime()}")
    }

    private fun speakFinal(text: String) {
        if (text.isBlank()) {
            finishAudioOnly()
            return
        }

        val speaker = tts
        if (speaker == null) {
            finishAudioOnly()
            return
        }

        scope.launch(Dispatchers.Main.immediate) {
            if (!ttsReady) kotlinx.coroutines.delay(150)
            if (!ttsReady) {
                finishAudioOnly()
                return@launch
            }

            finalUtteranceId = "bixby_final_" + System.nanoTime()
            speaker.setSpeechRate(0.96f)
            speaker.speak(text, TextToSpeech.QUEUE_ADD, null, finalUtteranceId)
        }
    }

    private fun isActuallyOffline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return true
        val capabilities = manager.getNetworkCapabilities(network) ?: return true
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun finishAudioOnly() {
        try { recognizer?.cancel() } catch (_: Exception) { }
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
        ttsReady = false
        speechBuffer.setLength(0)
        finalUtteranceId = null
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
        ttsReady = false
        speechBuffer.setLength(0)
        scope.cancel()
    }
}
