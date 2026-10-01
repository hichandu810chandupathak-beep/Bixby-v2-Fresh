package com.example.bixby

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var micButton: ImageButton
    private lateinit var statusText: TextView
    private lateinit var greetingText: TextView
    private var isListening = false

    companion object {
        private const val REQUEST_CODE_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        micButton = findViewById(R.id.micButton)
        statusText = findViewById(R.id.statusText)
        greetingText = findViewById(R.id.greetingText)

        checkAudioPermission()
        initializeSpeechRecognizer()

        // Strict touch listener only on Mic Button to avoid full screen tap bug
        micButton.setOnClickListener {
            if (!isListening) {
                startListeningSafely()
            } else {
                stopListeningSafely()
            }
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_PERMISSION)
        }
    }

    private fun initializeSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                speechRecognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        statusText.text = "Listening..."
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListening = false
                        statusText.text = "Processing..."
                    }
                    override fun onError(error: Int) {
                        isListening = false
                        // Handled Error 7 (No match) and other speech errors gracefully without crashing
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap mic to try again."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                            else -> "Recognition error ($error)"
                        }
                        statusText.text = errorMsg
                    }
                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val command = matches[0].toLowerCase(Locale.ROOT)
                            statusText.text = "Command: $command"
                            processVoiceCommand(command)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } catch (e: Exception) {
            statusText.text = "Init Error: ${e.localizedMessage}"
        }
    }

    private fun startListeningSafely() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            statusText.text = "Mic Error"
        }
    }

    private fun stopListeningSafely() {
        try {
            speechRecognizer.stopListening()
            isListening = false
            statusText.text = "Ready"
        } catch (e: Exception) {}
    }

    private fun processVoiceCommand(command: String) {
        when {
            command.contains("torch") || command.contains("flashlight") -> {
                statusText.text = "Executing: Torch Command"
            }
            command.contains("wifi") -> {
                statusText.text = "Opening Wi-Fi Settings"
            }
            else -> {
                statusText.text = "Command not recognized: '$command'"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            speechRecognizer.destroy()
        } catch (e: Exception) {}
    }
}