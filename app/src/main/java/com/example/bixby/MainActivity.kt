package com.example.bixby

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.animation.ScaleAnimation
import android.widget.ImageButton
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var micButton: ImageButton
    private lateinit var statusText: TextView
    private lateinit var greetingText: TextView
    private lateinit var pulseView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        micButton = findViewById(R.id.micButton)
        statusText = findViewById(R.id.statusText)
        greetingText = findViewById(R.id.greetingText)
        pulseView = findViewById(R.id.pulseView)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE), 100)
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusText.text = "Listening..."
                startPulseAnimation()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                statusText.text = "Processing..."
                stopPulseAnimation()
            }
            override fun onError(error: Int) {
                statusText.text = "Tap mic to try again"
                stopPulseAnimation()
            }
            override fun onResults(results: Bundle?) {
                stopPulseAnimation()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val cmd = matches[0].lowercase(Locale.ROOT)
                    greetingText.text = "You said: \"$cmd\""
                    executeCommand(cmd)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        micButton.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            speechRecognizer.startListening(intent)
        }
    }

    private fun startPulseAnimation() {
        val anim = ScaleAnimation(1.0f, 1.25f, 1.0f, 1.25f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f).apply {
            duration = 600
            repeatMode = ScaleAnimation.REVERSE
            repeatCount = ScaleAnimation.INFINITE
        }
        pulseView.startAnimation(anim)
    }

    private fun stopPulseAnimation() {
        pulseView.clearAnimation()
    }

    private fun executeCommand(cmd: String) {
        when {
            cmd.contains("wifi") -> {
                statusText.text = "Opening Wi-Fi Settings"
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            cmd.contains("bluetooth") -> {
                statusText.text = "Opening Bluetooth Settings"
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            cmd.contains("call") || cmd.contains("phone") -> {
                statusText.text = "Executing Call..."
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
                startActivity(intent)
            }
            cmd.contains("open") -> {
                statusText.text = "Attempting to open app..."
                val pkgIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (pkgIntent != null) startActivity(pkgIntent)
                else startActivity(Intent(Settings.ACTION_SETTINGS))
            }
            else -> {
                statusText.text = "Command: $cmd (Not mapped)"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}
