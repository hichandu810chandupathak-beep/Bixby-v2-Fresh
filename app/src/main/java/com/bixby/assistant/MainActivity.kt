package com.bixby.assistant

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var orbAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bixbyOrb: ImageView = findViewById(R.id.bixbyOrb)

        orbAnimator = ObjectAnimator.ofFloat(bixbyOrb, "scaleX", 1.0f, 1.2f).apply {
            duration = 800L
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val scaleYAnimator = ObjectAnimator.ofFloat(bixbyOrb, "scaleY", 1.0f, 1.2f).apply {
            duration = 800L
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        orbAnimator?.start()
        scaleYAnimator.start()
    }

    override fun onDestroy() {
        orbAnimator?.cancel()
        super.onDestroy()
    }
}
