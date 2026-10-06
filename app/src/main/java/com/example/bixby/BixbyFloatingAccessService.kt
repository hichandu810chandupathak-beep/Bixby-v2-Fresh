package com.example.bixby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageButton
import java.util.Locale

class BixbyFloatingAccessService : Service() {

    companion object {
        private const val CHANNEL_ID = "bixby_floating_access"
        private const val NOTIFICATION_ID = 2202
        private const val PREFS = "bixby_floating_access"
        private const val PREF_ENABLED = "enabled"
        const val ACTION_RECOGNIZED_COMMAND = "com.example.bixby.action.FLOATING_RECOGNIZED_COMMAND"
        const val EXTRA_RECOGNIZED_COMMAND = "com.example.bixby.extra.FLOATING_RECOGNIZED_COMMAND"
    }

    private var windowManager: WindowManager? = null
    private var windowContext: Context? = null
    private var floatingButton: ImageButton? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var listening = false

    override fun onCreate() {
        super.onCreate()

        if (!Settings.canDrawOverlays(this)) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(PREF_ENABLED, false)
                .apply()
            stopSelf()
            return
        }

        createNotificationChannel()
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = notificationBuilder
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Bixby floating access")
            .setContentText("Bixby is ready from any screen")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        setupSpeechRecognizer()
        showFloatingButton()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(PREF_ENABLED, true)
            .apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        floatingButton?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        speechRecognizer?.let { recognizer ->
            try { recognizer.cancel() } catch (_: Exception) { }
            try { recognizer.destroy() } catch (_: Exception) { }
        }
        speechRecognizer = null
        listening = false
        floatingButton = null
        windowManager = null
        windowContext = null

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(PREF_ENABLED, false)
            .apply()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showFloatingButton() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val contextForWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = getSystemService(android.hardware.display.DisplayManager::class.java)
                .getDisplay(android.view.Display.DEFAULT_DISPLAY)
            display?.let {
                createDisplayContext(it).createWindowContext(overlayType, null)
            } ?: this
        } else {
            this
        }

        windowContext = contextForWindow
        windowManager = contextForWindow.getSystemService(WINDOW_SERVICE) as WindowManager

        val size = (56 * resources.displayMetrics.density).toInt()
        val button = ImageButton(contextForWindow).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Bixby microphone"
            isClickable = true
            isFocusable = false
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(37, 99, 235), Color.rgb(124, 58, 237))
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(
                    (1.5f * resources.displayMetrics.density).toInt(),
                    Color.argb(180, 255, 255, 255)
                )
            }
            elevation = 12f
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (12 * resources.displayMetrics.density).toInt()
            y = (220 * resources.displayMetrics.density).toInt()
        }

        var downRawX = 0f
        var downRawY = 0f
        var downX = 0
        var downY = 0
        var moved = false
        val touchSlop = (8 * resources.displayMetrics.density).toInt()

        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downX = params.x
                    downY = params.y
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()

                    if (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) {
                        moved = true
                    }

                    if (moved) {
                        params.x = (downX - dx).coerceAtLeast(0)
                        params.y = (downY + dy).coerceAtLeast(0)
                        try {
                            windowManager?.updateViewLayout(view, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        triggerAssistant()
                    }
                    true
                }

                else -> true
            }
        }

        layoutParams = params
        floatingButton = button

        try {
            windowManager?.addView(button, params)
        } catch (_: SecurityException) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(PREF_ENABLED, false)
                .apply()
            stopSelf()
        } catch (_: Exception) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(PREF_ENABLED, false)
                .apply()
            stopSelf()
        }
    }

    private fun triggerAssistant() {
        startBackgroundListening()
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                listening = true
                updateNotification("Listening for your command")
            }

            override fun onBeginningOfSpeech() {
                updateNotification("Listening…")
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                listening = false
                updateNotification("Processing command")
            }

            override fun onError(error: Int) {
                listening = false
                updateNotification("Bixby is ready from any screen")
            }

            override fun onResults(results: android.os.Bundle?) {
                listening = false
                val command = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

                if (command != null) sendRecognizedCommand(command)
                updateNotification("Bixby is ready from any screen")
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
    }

    private fun startBackgroundListening() {
        if (listening) {
            try { speechRecognizer?.cancel() } catch (_: Exception) { }
            listening = false
            updateNotification("Bixby is ready from any screen")
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateNotification("Microphone permission required")
            return
        }

        val recognizer = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, arrayListOf("hi-IN", "en-IN"))
            }
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

        try {
            recognizer.startListening(intent)
        } catch (_: Exception) {
            listening = false
            updateNotification("Couldn't start microphone")
        }
    }

    private fun sendRecognizedCommand(command: String) {
        val broadcast = Intent(ACTION_RECOGNIZED_COMMAND).apply {
            setPackage(packageName)
            putExtra(EXTRA_RECOGNIZED_COMMAND, command)
        }
        sendBroadcast(broadcast)

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(EXTRA_RECOGNIZED_COMMAND, command)
            .apply()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        manager.notify(
            NOTIFICATION_ID,
            notificationBuilder
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Bixby floating access")
                .setContentText(text)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Bixby floating access",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Bixby floating access button available."
                setShowBadge(false)
            }
        )
    }
}
