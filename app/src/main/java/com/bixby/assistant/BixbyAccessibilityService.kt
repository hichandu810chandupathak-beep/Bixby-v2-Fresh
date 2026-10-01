package com.bixby.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class BixbyAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "BixbyAccessibility"
        const val ACTION_HARDWARE_ASSISTANT_TRIGGER = "com.bixby.assistant.ACTION_HARDWARE_ASSISTANT_TRIGGER"
        private const val DOUBLE_PRESS_TIMEOUT = 600L

        @Volatile
        private var instance: BixbyAccessibilityService? = null

        fun isRunning(): Boolean {
            return try { instance != null }
            catch (e: Exception) { Log.e(TAG, "isRunning failed", e); false }
        }

        fun performBack(): Boolean {
            return try { instance?.safeGlobalAction(GLOBAL_ACTION_BACK) == true }
            catch (e: Exception) { Log.e(TAG, "Back action failed", e); false }
        }

        fun performHome(): Boolean {
            return try { instance?.safeGlobalAction(GLOBAL_ACTION_HOME) == true }
            catch (e: Exception) { Log.e(TAG, "Home action failed", e); false }
        }

        fun performRecents(): Boolean {
            return try { instance?.safeGlobalAction(GLOBAL_ACTION_RECENTS) == true }
            catch (e: Exception) { Log.e(TAG, "Recents action failed", e); false }
        }
    }

    private var lastVolumeDownTime = 0L
    private var serviceReady = false

    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            instance = this
            serviceReady = true
            configureServiceSafely()
            showSafeToast("Bixby accessibility service ready")
        } catch (e: Exception) {
            serviceReady = false
            Log.e(TAG, "onServiceConnected failed", e)
        }
    }

    private fun configureServiceSafely() {
        try {
            val currentInfo = serviceInfo ?: AccessibilityServiceInfo()
            currentInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            currentInfo.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            currentInfo.notificationTimeout = 100L
            currentInfo.flags = currentInfo.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            setServiceInfo(currentInfo)
        } catch (e: Exception) { Log.e(TAG, "Accessibility configuration failed", e) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try { if (!serviceReady) return }
        catch (e: Exception) { Log.e(TAG, "Accessibility event handling failed", e) }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        return try {
            if (!serviceReady || event.action != KeyEvent.ACTION_DOWN) return false
            val now = SystemClock.elapsedRealtime()
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (lastVolumeDownTime > 0L && now - lastVolumeDownTime <= DOUBLE_PRESS_TIMEOUT) {
                        lastVolumeDownTime = 0L
                        triggerAssistantSafely()
                        true
                    } else {
                        lastVolumeDownTime = now
                        false
                    }
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hardware key handling failed", e)
            false
        }
    }

    private fun triggerAssistantSafely() {
        try {
            if (!serviceReady) return
            val intent = Intent(this, SplashActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        } catch (e: SecurityException) {
            Log.e(TAG, "Hardware trigger security error", e)
            showSafeToast("Hardware shortcut unavailable")
        } catch (e: Exception) { Log.e(TAG, "Hardware trigger failed", e) }
    }

    private fun safeGlobalAction(action: Int): Boolean {
        return try {
            if (!serviceReady) return false
            performGlobalAction(action)
        } catch (e: SecurityException) {
            Log.e(TAG, "Global action security error", e)
            showSafeToast("System navigation unavailable")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Global action failed", e)
            false
        }
    }

    private fun showSafeToast(message: String) {
        try { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
        catch (e: Exception) { Log.e(TAG, "Toast failed", e) }
    }

    override fun onInterrupt() {
        try { serviceReady = false; Log.w(TAG, "Accessibility service interrupted") }
        catch (e: Exception) { Log.e(TAG, "onInterrupt failed", e) }
    }

    override fun onDestroy() {
        try {
            serviceReady = false
            if (instance === this) instance = null
            lastVolumeDownTime = 0L
            Log.i(TAG, "Accessibility service destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy cleanup failed", e)
        } finally {
            try { super.onDestroy() }
            catch (e: Exception) { Log.e(TAG, "super.onDestroy failed", e) }
        }
    }
}
