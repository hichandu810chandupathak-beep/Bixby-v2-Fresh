package com.example.bixby

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_AUDIO = 100
        private const val REQUEST_CALL = 101
        private const val REQUEST_CAMERA = 102
    }

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var micButton: ImageButton
    private lateinit var statusText: TextView
    private lateinit var greetingText: TextView
    private lateinit var pulseView: View
    private lateinit var orbView: View

    private var pendingCallNumber: String? = null
    private var flashlightOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        micButton = findViewById(R.id.micButton)
        statusText = findViewById(R.id.statusText)
        greetingText = findViewById(R.id.greetingText)
        pulseView = findViewById(R.id.pulseView)
        orbView = findViewById(R.id.orbView)

        setupOrb()
        requestAudioPermissionIfNeeded()
        setupSpeechRecognizer()

        micButton.setOnClickListener {
            startListening()
        }
    }

    private fun setupOrb() {
        val orb = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(android.graphics.Color.rgb(0, 242, 254))
        }
        orbView.background = orb

        val glow = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(android.graphics.Color.rgb(0, 242, 254))
        }
        pulseView.background = glow
        pulseView.alpha = 0.22f
    }

    private fun requestAudioPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusText.text = "Listening..."
                greetingText.text = "I'm listening"
                startPulseAnimation()
            }

            override fun onBeginningOfSpeech() {
                statusText.text = "Listening..."
            }

            override fun onRmsChanged(rmsdB: Float) {
                val scale = (1.0f + (rmsdB.coerceIn(0f, 12f) / 80f))
                orbView.scaleX = scale
                orbView.scaleY = scale
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                statusText.text = "Processing..."
                stopPulseAnimation()
            }

            override fun onError(error: Int) {
                statusText.text = "Tap mic to try again"
                stopPulseAnimation()
                orbView.scaleX = 1f
                orbView.scaleY = 1f
            }

            override fun onResults(results: Bundle?) {
                stopPulseAnimation()
                orbView.scaleX = 1f
                orbView.scaleY = 1f

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val cmd = matches[0].lowercase(Locale.ROOT).trim()
                    greetingText.text = "You said: \"$cmd\""
                    executeCommand(cmd)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer.startListening(intent)
    }

    private fun startPulseAnimation() {
        val scale = ScaleAnimation(
            0.92f, 1.18f,
            0.92f, 1.18f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 700
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }

        val alpha = AlphaAnimation(0.18f, 0.55f).apply {
            duration = 700
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }

        pulseView.startAnimation(scale)
        pulseView.startAnimation(alpha)
        orbView.startAnimation(
            ScaleAnimation(
                0.94f, 1.06f,
                0.94f, 1.06f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 700
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
        )
    }

    private fun stopPulseAnimation() {
        pulseView.clearAnimation()
        orbView.clearAnimation()
        pulseView.alpha = 0.22f
    }

    private fun executeCommand(cmd: String) {
        when {
            isFlashlightCommand(cmd) -> toggleFlashlight(cmd)

            isWifiCommand(cmd) -> {
                statusText.text = "Opening Wi-Fi controls"
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }

            isBluetoothCommand(cmd) -> {
                if (cmd.contains("turn on") || cmd.contains("enable") || cmd.contains("switch on")) {
                    statusText.text = "Opening Bluetooth enable control"
                    try {
                        startActivity(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    } catch (_: Exception) {
                        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                } else {
                    statusText.text = "Opening Bluetooth controls"
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
            }

            isCallCommand(cmd) -> handleCallCommand(cmd)

            isOpenCommand(cmd) -> openRequestedApp(cmd)

            else -> {
                statusText.text = "Command not mapped"
                Toast.makeText(this, "I couldn't find an action for that command.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isFlashlightCommand(cmd: String): Boolean =
        cmd.contains("flashlight") ||
        cmd.contains("torch") ||
        cmd.contains("flash light")

    private fun isWifiCommand(cmd: String): Boolean =
        cmd.contains("wifi") || cmd.contains("wi-fi")

    private fun isBluetoothCommand(cmd: String): Boolean =
        cmd.contains("bluetooth")

    private fun isCallCommand(cmd: String): Boolean =
        cmd.contains("call ") || cmd.startsWith("call") || cmd.contains("phone ")

    private fun isOpenCommand(cmd: String): Boolean =
        cmd.contains("open ") || cmd.startsWith("open")

    private fun toggleFlashlight(cmd: String) {
        val wantsOn = cmd.contains("turn on") ||
            cmd.contains("switch on") ||
            cmd.contains("enable") ||
            cmd.contains("on")

        val wantsOff = cmd.contains("turn off") ||
            cmd.contains("switch off") ||
            cmd.contains("disable") ||
            cmd.contains("off")

        if (!wantsOn && !wantsOff) {
            flashlightOn = !flashlightOn
        } else {
            flashlightOn = wantsOn
        }

        try {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (cameraId == null) {
                statusText.text = "Flashlight unavailable"
                Toast.makeText(this, "This device has no available flashlight.", Toast.LENGTH_SHORT).show()
                return
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                statusText.text = "Camera permission needed for flashlight"
                Toast.makeText(this, "Camera permission is required to control the flashlight.", Toast.LENGTH_SHORT).show()
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
                return
            }

            cameraManager.setTorchMode(cameraId, flashlightOn)
            statusText.text = if (flashlightOn) "Flashlight ON" else "Flashlight OFF"
        } catch (_: SecurityException) {
            statusText.text = "Flashlight permission unavailable"
            Toast.makeText(this, "Flashlight permission is unavailable.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            statusText.text = "Flashlight unavailable"
            Toast.makeText(this, "Couldn't control the flashlight.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCallCommand(cmd: String) {
        val number = extractPhoneNumber(cmd)

        if (number == null) {
            statusText.text = "No phone number found"
            Toast.makeText(this, "Please say the phone number you want to call.", Toast.LENGTH_SHORT).show()
            return
        }

        pendingCallNumber = number

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL)
            return
        }

        placeDirectCall(number)
    }

    private fun extractPhoneNumber(cmd: String): String? {
        val normalized = cmd
            .replace("plus", "+")
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")

        val match = Regex("""(?:\+?\d{10,15})""").find(normalized) ?: return null
        return match.value
    }

    private fun placeDirectCall(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Call permission required"
            return
        }

        try {
            statusText.text = "Calling $number..."
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
        } catch (_: SecurityException) {
            statusText.text = "Call permission unavailable"
            Toast.makeText(this, "Call permission is required.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openRequestedApp(cmd: String) {
        val appName = cmd
            .replaceFirst(Regex("""^.*?open\s+"""), "")
            .replace(Regex("""\b(application|app)\b"""), "")
            .trim()

        if (appName.isBlank()) {
            statusText.text = "Please say an app name"
            Toast.makeText(this, "Please tell me which app to open.", Toast.LENGTH_SHORT).show()
            return
        }

        val launchIntent = findLaunchIntentForApp(appName)

        if (launchIntent != null) {
            statusText.text = "Opening $appName"
            startActivity(launchIntent)
        } else {
            statusText.text = "App not found"
            Toast.makeText(this, "I couldn't find $appName on this phone.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun findLaunchIntentForApp(appName: String): Intent? {
        val normalizedQuery = normalizeAppName(appName)

        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        val exact = installedApps.firstOrNull { app ->
            normalizeAppName(packageManager.getApplicationLabel(app).toString()) == normalizedQuery
        }

        val partial = installedApps.firstOrNull { app ->
            val label = normalizeAppName(packageManager.getApplicationLabel(app).toString())
            label.contains(normalizedQuery) || normalizedQuery.contains(label)
        }

        val candidate = exact ?: partial ?: findKnownPackage(appName)

        return candidate?.let { packageManager.getLaunchIntentForPackage(it.packageName) }
    }

    private fun findKnownPackage(appName: String): android.content.pm.ApplicationInfo? {
        val aliases = mapOf(
            "whatsapp" to "com.whatsapp",
            "gallery" to "com.google.android.apps.photos",
            "photos" to "com.google.android.apps.photos",
            "calculator" to "com.google.android.calculator",
            "messages" to "com.google.android.apps.messaging",
            "messaging" to "com.google.android.apps.messaging",
            "chrome" to "com.android.chrome"
        )

        val packageName = aliases[normalizeAppName(appName)] ?: return null
        return try {
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun normalizeAppName(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CALL && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            pendingCallNumber?.let { placeDirectCall(it) }
            pendingCallNumber = null
        }

        if (requestCode == REQUEST_CAMERA && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Camera permission granted. Say torch again."
        }
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        super.onDestroy()
    }
}
