package com.example.bixby

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
        setupMicButton()
        setupSpeechRecognizer()
        requestRequiredPermissionsIfNeeded()

        micButton.setOnClickListener {
            startListening()
        }
    }

    private fun setupOrb() {
        orbView.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(0, 242, 254))
        }

        pulseView.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(0, 242, 254))
        }
        pulseView.alpha = 0.22f
    }

    private fun setupMicButton() {
        micButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(0, 242, 254))
        }
        micButton.clipToOutline = true
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

                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )

                if (!matches.isNullOrEmpty()) {
                    val command = matches[0].lowercase(Locale.ROOT).trim()
                    greetingText.text = "You said: \"$command\""
                    executeCommand(command)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    private fun startListening() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO
            )
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer.startListening(intent)
        } catch (_: Exception) {
            statusText.text = "Couldn't start microphone"
            Toast.makeText(
                this,
                "Couldn't start voice recognition.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun startPulseAnimation() {
        val scale = ScaleAnimation(
            0.92f,
            1.18f,
            0.92f,
            1.18f,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f
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
                0.94f,
                1.06f,
                0.94f,
                1.06f,
                Animation.RELATIVE_TO_SELF,
                0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f
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

    private fun executeCommand(command: String) {
        when {
            isExitCommand(command) -> goHome()
            isFlashlightCommand(command) -> toggleFlashlight(command)

            isWifiCommand(command) -> {
                statusText.text = "Opening Wi-Fi controls"
                safeStartActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }

            isBluetoothCommand(command) -> {
                statusText.text = "Opening Bluetooth controls"
                try {
                    safeStartActivity(
                        Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    )
                } catch (_: Exception) {
                    safeStartActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
            }

            isCallCommand(command) -> handleCallCommand(command)
            isOpenCommand(command) -> openRequestedApp(command)

            else -> {
                statusText.text = "Command not mapped"
                Toast.makeText(
                    this,
                    "I couldn't find an action for that command.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun isExitCommand(command: String): Boolean {
        val normalized = command.trim()
        return normalized == "exit" ||
            normalized == "close app" ||
            normalized == "close application" ||
            normalized == "go home" ||
            normalized == "stop"
    }

    private fun goHome() {
        statusText.text = "Going home"

        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            startActivity(homeIntent)
        } catch (_: Exception) {
            finishAndRemoveTask()
        }
    }

    private fun isFlashlightCommand(command: String): Boolean =
        command.contains("flashlight") ||
            command.contains("torch") ||
            command.contains("flash light")

    private fun isWifiCommand(command: String): Boolean =
        command.contains("wifi") || command.contains("wi-fi")

    private fun isBluetoothCommand(command: String): Boolean =
        command.contains("bluetooth")

    private fun isCallCommand(command: String): Boolean =
        command.startsWith("call ") ||
            command.startsWith("call") ||
            command.startsWith("phone ")

    private fun isOpenCommand(command: String): Boolean =
        command.startsWith("open ")

    private fun toggleFlashlight(command: String) {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            pendingFlashlightCommand = command
            statusText.text = "Camera permission required"
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA
            )
            return
        }

        val wantsOn = command.contains("turn on") ||
            command.contains("switch on") ||
            command.contains("enable") ||
            Regex("\\bon\\b").containsMatchIn(command)

        val wantsOff = command.contains("turn off") ||
            command.contains("switch off") ||
            command.contains("disable") ||
            Regex("\\boff\\b").containsMatchIn(command)

        flashlightOn = if (!wantsOn && !wantsOff) !flashlightOn else wantsOn

        try {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(
                        android.hardware.camera2.CameraCharacteristics
                            .FLASH_INFO_AVAILABLE
                    ) == true
            }

            if (cameraId == null) {
                statusText.text = "Flashlight unavailable"
                Toast.makeText(
                    this,
                    "This device has no available flashlight.",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            cameraManager.setTorchMode(cameraId, flashlightOn)
            statusText.text =
                if (flashlightOn) "Flashlight ON" else "Flashlight OFF"
        } catch (_: SecurityException) {
            statusText.text = "Camera permission unavailable"
            Toast.makeText(
                this,
                "Camera permission is required to control the flashlight.",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Exception) {
            statusText.text = "Flashlight unavailable"
            Toast.makeText(
                this,
                "Couldn't control the flashlight.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleCallCommand(command: String) {
        val target = extractCallTarget(command)

        if (target.isBlank()) {
            statusText.text = "Contact or number missing"
            Toast.makeText(
                this,
                "Please say a contact name or phone number.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        pendingCallTarget = target

        val callGranted = hasPermission(Manifest.permission.CALL_PHONE)
        val contactsGranted = hasPermission(Manifest.permission.READ_CONTACTS)
        val isNumber = extractPhoneNumber(target) != null

        val missingPermissions = buildList {
            if (!callGranted) add(Manifest.permission.CALL_PHONE)
            if (!isNumber && !contactsGranted) add(Manifest.permission.READ_CONTACTS)
        }

        if (missingPermissions.isNotEmpty()) {
            statusText.text = "Permission required to place call"
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                REQUEST_CALL
            )
            return
        }

        resolveAndCall(target)
    }

    private fun extractCallTarget(command: String): String =
        command
            .replaceFirst(
                Regex("""^\s*(please\s+)?(call|phone)\s+"""),
                ""
            )
            .trim()

    private fun extractPhoneNumber(value: String): String? {
        val normalized = value
            .replace("plus", "+")
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")

        return Regex("""(?:\+?\d{10,15})""")
            .find(normalized)
            ?.value
    }

    private fun resolveAndCall(target: String) {
        val directNumber = extractPhoneNumber(target)

        if (directNumber != null) {
            placeCallSafely(directNumber)
            return
        }

        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            pendingCallTarget = target
            statusText.text = "Contacts permission required"
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                REQUEST_CONTACTS
            )
            return
        }

        val contactNumber = findContactNumberSafely(target)

        if (contactNumber == null) {
            pendingCallTarget = null
            statusText.text = "Contact not found"
            Toast.makeText(
                this,
                "I couldn't find $target in your contacts.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        placeCallSafely(contactNumber)
    }

    private fun findContactNumberSafely(contactName: String): String? {
        val normalizedTarget = normalizeContactName(contactName)
        if (normalizedTarget.isBlank()) return null

        return try {
            val displayName = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            val phoneNumber = ContactsContract.CommonDataKinds.Phone.NUMBER

            val projection = arrayOf(displayName, phoneNumber)

            val selection = "($displayName = ? COLLATE NOCASE) OR ($displayName LIKE ? COLLATE NOCASE)"
            val selectionArgs = arrayOf(contactName, "%$contactName%")

            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "$displayName COLLATE NOCASE ASC"
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(displayName)
                val numberIndex = cursor.getColumnIndex(phoneNumber)

                var partialMatch: String? = null

                while (cursor.moveToNext()) {
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                    val number = if (numberIndex >= 0) cursor.getString(numberIndex) else null

                    if (number.isNullOrBlank()) continue

                    val normalizedName = normalizeContactName(name)

                    if (normalizedName == normalizedTarget) {
                        return@use number
                    }

                    if (normalizedName.contains(normalizedTarget) ||
                        normalizedTarget.contains(normalizedName)
                    ) {
                        partialMatch = partialMatch ?: number
                    }
                }

                partialMatch
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeContactName(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    private fun placeCallSafely(number: String) {
        if (number.isBlank()) {
            pendingCallTarget = null
            statusText.text = "Phone number unavailable"
            Toast.makeText(
                this,
                "No valid phone number was found.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!hasPermission(Manifest.permission.CALL_PHONE)) {
            pendingCallTarget = number
            statusText.text = "Call permission required"
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                REQUEST_CALL
            )
            return
        }

        val callUri = Uri.parse("tel:$number")

        try {
            statusText.text = "Calling $number..."
            startActivity(Intent(Intent.ACTION_CALL, callUri))
            pendingCallTarget = null
            return
        } catch (_: SecurityException) {
        } catch (_: ActivityNotFoundException) {
        } catch (_: Exception) {
        }

        try {
            statusText.text = "Opening dialer"
            startActivity(Intent(Intent.ACTION_DIAL, callUri))
            pendingCallTarget = null
        } catch (_: Exception) {
            pendingCallTarget = null
            statusText.text = "Couldn't start phone app"
            Toast.makeText(
                this,
                "Couldn't start the phone app.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openRequestedApp(command: String) {
        val appName = command
            .trim()
            .replaceFirst(Regex("""^\\s*open\\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\\b(application|app|please)\\b""", RegexOption.IGNORE_CASE), " ")
            .trim()

        if (appName.isBlank()) {
            statusText.text = "Please say an app name"
            Toast.makeText(
                this,
                "Please tell me which app to open.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val launchIntent = findLaunchIntentForApp(appName)

        if (launchIntent == null) {
            statusText.text = "App not found"
            Toast.makeText(
                this,
                "I couldn't find a launchable app named $appName on this phone.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            startActivity(launchIntent)
            statusText.text = "Opening $appName"
        } catch (_: Exception) {
            statusText.text = "Couldn't open $appName"
            Toast.makeText(
                this,
                "I found $appName, but Android couldn't launch it.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun findLaunchIntentForApp(appName: String): Intent? {
        val query = normalizeAppLookupText(appName)
        if (query.isBlank()) return null

        // Combine launcher activities and all packages visible to PackageManager.
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val launcherActivities = try {
            packageManager.queryIntentActivities(launcherIntent, 0)
        } catch (_: Exception) {
            emptyList()
        }

        val installedApplications = try {
            packageManager.getInstalledApplications(PackageManager.MATCH_ALL)
        } catch (_: Exception) {
            emptyList()
        }

        // One entry per package: package name -> display label.
        val universalApps = linkedMapOf<String, String>()

        installedApplications.forEach { applicationInfo ->
            val packageName = applicationInfo.packageName ?: return@forEach
            val label = try {
                packageManager.getApplicationLabel(applicationInfo).toString()
            } catch (_: Exception) {
                packageName
            }
            universalApps.putIfAbsent(packageName, label)
        }

        launcherActivities.forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@forEach
            val label = try {
                resolveInfo.loadLabel(packageManager).toString()
            } catch (_: Exception) {
                packageName
            }
            universalApps.putIfAbsent(packageName, label)
        }

        val queryWords = appLookupWords(appName)

        // Match the full spoken query first, then meaningful individual words.
        for ((packageName, displayLabel) in universalApps) {
            val normalizedLabel = normalizeAppLookupText(displayLabel)
            val normalizedPackage = normalizeAppLookupText(packageName)

            val matchesFullQuery =
                normalizedLabel.contains(query) || normalizedPackage.contains(query)

            val matchesQueryWord = queryWords.any { word ->
                normalizedLabel.contains(word) || normalizedPackage.contains(word)
            }

            if (matchesFullQuery || matchesQueryWord) {
                createPackageLaunchIntent(packageName)?.let { return it }
            }
        }

        return null
    }

    private fun createPackageLaunchIntent(packageName: String): Intent? {
        if (packageName.isBlank()) return null

        try {
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
            }?.let { return it }
        } catch (_: Exception) {
        }

        return try {
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeAppLookupText(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    private fun appLookupWords(value: String): List<String> {
        val ignoredWords = setOf("open", "launch", "start", "application", "app", "please", "the")
        return value.lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .map { normalizeAppLookupText(it) }
            .filter { it.length >= 3 && it !in ignoredWords }
            .distinct()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED

    private fun safeStartActivity(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            statusText.text = "Action unavailable"
            Toast.makeText(
                this,
                "That action is unavailable on this phone.",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Exception) {
            statusText.text = "Couldn't open"
            Toast.makeText(
                this,
                "Couldn't open that action.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_AUDIO -> {
                statusText.text =
                    if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                        "Microphone permission granted"
                    } else {
                        "Microphone permission required"
                    }
            }

            REQUEST_CAMERA -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    val pending = pendingFlashlightCommand
                    pendingFlashlightCommand = null

                    if (pending != null) {
                        toggleFlashlight(pending)
                    } else {
                        statusText.text = "Camera permission granted"
                    }
                } else {
                    statusText.text = "Camera permission required for flashlight"
                }
            }

            REQUEST_CALL, REQUEST_CONTACTS -> {
                val target = pendingCallTarget ?: return
                val number = extractPhoneNumber(target)

                if (hasPermission(Manifest.permission.CALL_PHONE) &&
                    (number != null || hasPermission(Manifest.permission.READ_CONTACTS))
                ) {
                    resolveAndCall(target)
                } else {
                    statusText.text = "Call permission required"
                    Toast.makeText(
                        this,
                        "Phone or contacts permission is required.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            REQUEST_STARTUP_PERMISSIONS -> {
                val missing = arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_CONTACTS
                ).count { !hasPermission(it) }

                statusText.text =
                    if (missing == 0) "Ready"
                    else "Some permissions are still required"
            }
        }
    }

    override fun onDestroy() {
        try {
            speechRecognizer.destroy()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}