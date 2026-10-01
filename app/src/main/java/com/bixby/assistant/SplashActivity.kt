package com.bixby.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.util.Log

class SplashActivity : Activity() {
    companion object {
        private const val TAG = "BixbySplash"
        private const val DELAY = 1200L
    }

    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_splash)
            try {
                findViewById<android.view.View>(R.id.splashOrb)?.startAnimation(
                    AnimationUtils.loadAnimation(this, R.anim.splash_enter)
                )
            } catch (animationError: Exception) {
                Log.e(TAG, "Splash animation failed", animationError)
            }
            handler = Handler(Looper.getMainLooper())
            runnable = Runnable {
                try { openMainSafely() }
                catch (e: Exception) { Log.e(TAG, "Navigation failed", e) }
            }
            handler?.postDelayed(runnable ?: return, DELAY)
        } catch (e: Exception) {
            Log.e(TAG, "Splash initialization failed", e)
            try {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } catch (fallback: Exception) {
                Log.e(TAG, "Splash fallback failed", fallback)
            }
        }
    }

    private fun openMainSafely() {
        try {
            if (navigated) return
            navigated = true
            startActivity(Intent(this, MainActivity::class.java))
            try { finish() }
            catch (e: Exception) { Log.e(TAG, "Finish failed", e) }
        } catch (e: Exception) {
            navigated = false
            Log.e(TAG, "MainActivity launch failed", e)
        }
    }

    override fun onDestroy() {
        try {
            handler?.let { h -> runnable?.let { r -> h.removeCallbacks(r) } }
            handler = null
            runnable = null
        } catch (e: Exception) {
            Log.e(TAG, "Splash cleanup failed", e)
        } finally {
            try { super.onDestroy() }
            catch (e: Exception) { Log.e(TAG, "super.onDestroy failed", e) }
        }
    }
}
