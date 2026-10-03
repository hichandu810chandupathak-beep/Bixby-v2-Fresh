package com.example.bixby

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
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
        private const val REQUEST_CONTACTS = 103
        private const val REQUEST_STARTUP_PERMISSIONS = 104
    }

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var micButton: ImageButton
    private lateinit var statusText: TextView
    private lateinit var greetingText: TextView
    private lateinit var pulseView: View
    private lateinit var orbView: View

    private var pendingCallTarget: String? = null
    private var pendingFlashlightCommand: String? = null
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
        setupSpeechRecognizer()
        requestRequiredPermissionsIfNeeded()

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

    private fun requestRequiredPermissionsIfNeeded() {
        val required = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missing.toTypedArray(),
                REQUEST_STARTUP_PERMISSIONS
            )
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
                val scale = 1.0f + (rmsdB.coerceIn(0f, 12f) / 80f)
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
        cmd.startsWith("call ") ||
            cmd.startsWith("call") ||
            cmd.startsWith("phone ") ||
            cmd.contains("call ")

    private fun isOpenCommand(cmd: String): Boolean =
        cmd.startsWith("open ") || cmd.startsWith("open")

    private fun toggleFlashlight(cmd: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingFlashlightCommand = cmd
            statusText.text = "Camera permission required"
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
            return
        }

        val wantsOn = cmd.contains("turn on") ||
            cmd.contains("switch on") ||
            cmd.contains("enable") ||
            Regex("\\bon\\b").containsMatchIn(cmd)

        val wantsOff = cmd.contains("turn off") ||
            cmd.contains("switch off") ||
            cmd.contains("disable") ||
            Regex("\\boff\\b").containsMatchIn(cmd)

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

            cameraManager.setTorchMode(cameraId, flashlightOn)
            statusText.text = if (flashlightOn) "Flashlight ON" else "Flashlight OFF"
        } catch (_: SecurityException) {
            statusText.text = "Camera permission unavailable"
            Toast.makeText(this, "Camera permission is required to control the flashlight.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            statusText.text = "Flashlight unavailable"
            Toast.makeText(this, "Couldn't control the flashlight.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCallCommand(cmd: String) {
        val target = extractCallTarget(cmd)

        if (target.isBlank()) {
            statusText.text = "Contact or number missing"
            Toast.makeText(this, "Please say a contact name or phone number.", Toast.LENGTH_SHORT).show()
            return
        }

        pendingCallTarget = target

        val hasCallPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val hasContactsPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val looksLikeNumber = extractPhoneNumber(target) != null

        val missing = buildList {
            if (!hasCallPermission) add(Manifest.permission.CALL_PHONE)
            if (!looksLikeNumber && !hasContactsPermission) add(Manifest.permission.READ_CONTACTS)
        }

        if (missing.isNotEmpty()) {
            statusText.text = "Permission required to place call"
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_CALL)
            return
        }

        resolveAndCall(target)
    }

    private fun extractCallTarget(cmd: String): String {
        return cmd
            .replaceFirst(Regex("""^\s*(please\s+)?(call|phone)\s+"""), "")
            .replaceFirst(Regex("""^\s*(please\s+)?(call|phone)\s*$"""), "")
            .trim()
    }

    private fun extractPhoneNumber(value: String): String? {
        val normalized = value
            .replace("plus", "+")
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")

        val match = Regex("""(?:\+?\d{10,15})""").find(normalized) ?: return null
        return match.value
    }

    private fun resolveAndCall(target: String) {
        val number = extractPhoneNumber(target)
        if (number != null) {
            placeDirectCall(number)
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Contacts permission required"
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS)
            return
        }

        val contactNumber = findContactNumber(target)
        if (contactNumber == null) {
            statusText.text = "Contact not found"
            Toast.makeText(this, "I couldn't find $target in your contacts.", Toast.LENGTH_SHORT).show()
            pendingCallTarget = null
            return
        }

        placeDirectCall(contactNumber)
    }

    private fun findContactNumber(contactName: String): String? {
        val normalizedTarget = normalizeContactName(contactName)
        if (normalizedTarget.isBlank()) return null

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val selection = "\${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} LIKE ?"
        val selectionArgs = arrayOf("%$contactName%")

        return try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "\${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
            )?.use { cursor ->
                var fallbackNumber: String? = null

                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else ""
                    val number = if (numberIndex >= 0) cursor.getString(numberIndex) else null

                    if (number.isNullOrBlank()) continue
                    if (normalizeContactName(name) == normalizedTarget) return@use number
                    if (fallbackNumber == null) fallbackNumber = number
                }

                fallbackNumber
            }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun normalizeContactName(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    private fun placeDirectCall(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingCallTarget = number
            statusText.text = "Call permission required"
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL)
            return
        }

        try {
            statusText.text = "Calling $number..."
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
            pendingCallTarget = null
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
        if (normalizedQuery.isBlank()) return null

        val packageCandidates = knownPackageCandidates(normalizedQuery)
        for (packageName in packageCandidates) {
            packageManager.getLaunchIntentForPackage(packageName)?.let { return it }
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val launchableApps = packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.MATCH_ALL
        )

        val exact = launchableApps.firstOrNull { info ->
            val label = info.loadLabel(packageManager).toString()
            normalizeAppName(label) == normalizedQuery
        }

        val partial = launchableApps.firstOrNull { info ->
            val label = normalizeAppName(info.loadLabel(packageManager).toString())
            label.contains(normalizedQuery) || normalizedQuery.contains(label)
        }

        return (exact ?: partial)?.activityInfo?.let { activityInfo ->
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(activityInfo.packageName, activityInfo.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
        }
    }

    private fun knownPackageCandidates(normalizedAppName: String): List<String> {
        val aliases = mapOf(
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "whatsappbusiness" to listOf("com.whatsapp.w4b", "com.whatsapp"),
            "calculator" to listOf(
                "com.sec.android.app.popupcalculator",
                "com.google.android.calculator",
                "com.android.calculator2"
            ),
            "gallery" to listOf(
                "com.sec.android.gallery3d",
                "com.google.android.apps.photos"
            ),
            "photos" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d"),
            "messages" to listOf(
                "com.samsung.android.messaging",
                "com.google.android.apps.messaging"
            ),
            "messaging" to listOf(
                "com.samsung.android.messaging",
                "com.google.android.apps.messaging"
            ),
            "chrome" to listOf("com.android.chrome"),
            "youtube" to listOf("com.google.android.youtube")
        )

        return aliases[normalizedAppName].orEmpty()
    }

    private fun normalizeAppName(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_AUDIO -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    statusText.text = "Microphone permission granted"
                } else {
                    statusText.text = "Microphone permission required"
                }
            }

            REQUEST_CAMERA -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    pendingFlashlightCommand?.let {
                        pendingFlashlightCommand = null
                        toggleFlashlight(it)
                    } ?: run {
                        statusText.text = "Camera permission granted"
                    }
                } else {
                    statusText.text = "Camera permission required for flashlight"
                }
            }

            REQUEST_CALL, REQUEST_CONTACTS -> {
                val target = pendingCallTarget ?: return
                val number = extractPhoneNumber(target)
                val callGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                val contactsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

                if (callGranted && (number != null || contactsGranted)) {
                    resolveAndCall(target)
                } else {
                    statusText.text = "Call permission required"
                    Toast.makeText(this, "Please allow phone and contacts permissions for contact calling.", Toast.LENGTH_SHORT).show()
                }
            }

            REQUEST_STARTUP_PERMISSIONS -> {
                val missing = arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_CONTACTS
                ).filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }

                statusText.text = if (missing.isEmpty()) {
                    "Ready"
                } else {
                    "Some permissions are still required"
                }
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        super.onDestroy()
    }
}