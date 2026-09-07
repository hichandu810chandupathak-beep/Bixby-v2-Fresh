package com.bixby.assistant

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var orbAnimator: ObjectAnimator? = null
    private var scaleYAnimator: ObjectAnimator? = null
    private var pulseAnimator: AnimatorSet? = null
    private var textToSpeech: TextToSpeech? = null
    private lateinit var listeningText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bixbyOrb: ImageView = findViewById(R.id.bixbyOrb)
        listeningText = findViewById(R.id.listeningText)

        textToSpeech = TextToSpeech(this, this)

        orbAnimator = ObjectAnimator.ofFloat(bixbyOrb, "scaleX", 1.0f, 1.2f).apply {
            duration = 800L
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        scaleYAnimator = ObjectAnimator.ofFloat(bixbyOrb, "scaleY", 1.0f, 1.2f).apply {
            duration = 800L
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        pulseAnimator = AnimatorSet().apply {
            playTogether(orbAnimator, scaleYAnimator)
            start()
        }

        bixbyOrb.setOnClickListener {
            listeningText.text = "Thinking..."

            lifecycleScope.launch {
                val response = BixbyBrain.askQuestion(
                    "Hello, who are you and what can you do?",
                    "YOUR_API_KEY_HERE"
                )

                withContext(Dispatchers.Main) {
                    listeningText.text = response
                    if (textToSpeech?.isSpeaking == true) {
                        textToSpeech?.stop()
                    }
                    textToSpeech?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "BIXBY_RESPONSE")
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                textToSpeech?.setLanguage(Locale.getDefault())
            }
        }
    }

    override fun onDestroy() {
        pulseAnimator?.cancel()
        orbAnimator?.cancel()
        scaleYAnimator?.cancel()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}
