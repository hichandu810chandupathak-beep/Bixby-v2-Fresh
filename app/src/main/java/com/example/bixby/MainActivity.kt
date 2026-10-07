package com.example.bixby

import android.Manifest
import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
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
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_AUDIO = 100
        private const val REQUEST_CALL = 101
        private const val REQUEST_CAMERA = 102
        private const val REQUEST_CONTACTS = 103
        private const val REQUEST_STARTUP_PERMISSIONS = 104
        private const val REQUEST_ASSISTANT_ROLE = 105
        const val EXTRA_START_LISTENING = "com.example.bixby.extra.START_LISTENING"
        const val EXTRA_BACKGROUND_LISTENING = "com.example.bixby.extra.BACKGROUND_LISTENING"
    }

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var micButton: ImageButton
    private lateinit var statusText: TextView
    private lateinit var greetingText: TextView
    private lateinit var pulseView: View
    private lateinit var orbView: View
    private lateinit var textInput: EditText
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var aiHandler: AssistantAiHandler
    private var ttsReady = false
    private var pendingWelcome = false

    private var pendingCallTarget: String? = null
    private var pendingFlashlightCommand: String? = null
    private var flashlightOn = false

    private val floatingVoiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val command = intent?.getStringExtra(BixbyFloatingAccessService.EXTRA_RECOGNIZED_COMMAND)?.trim()?.takeIf { it.isNotEmpty() } ?: return
            greetingText.text = "You said: \"$command\""
            executeCommand(command.lowercase(Locale.ROOT))
        }
    }

    private val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pulseScaleAnimator: ValueAnimator? = null
    private var pulseAlphaAnimator: ValueAnimator? = null
    private var orbScaleAnimator: ValueAnimator? = null
    private var orbState: OrbState = OrbState.IDLE

    private enum class OrbState {
        IDLE, LISTENING, PROCESSING
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startListeningFromExternal = intent?.getBooleanExtra(EXTRA_START_LISTENING, false) == true
        val backgroundListening = intent?.getBooleanExtra(EXTRA_BACKGROUND_LISTENING, false) == true

        if (backgroundListening) {
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.decorView.alpha = 0f
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )
            overridePendingTransition(0, 0)
        }

        setContentView(R.layout.activity_main)

        micButton = findViewById(R.id.micButton)
        statusText = findViewById(R.id.statusText)
        greetingText = findViewById(R.id.greetingText)
        pulseView = findViewById(R.id.pulseView)
        orbView = findViewById(R.id.orbView)
        textInput = findViewById(R.id.textInput)
        aiHandler = AssistantAiHandler(this)
        pendingWelcome = AiVoiceBridge.isVoiceActivation(intent)
        textToSpeech = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                val defaultLocale = Locale.getDefault()
                textToSpeech.language = if (defaultLocale.language == "hi") Locale("hi", "IN") else defaultLocale
                textToSpeech.setSpeechRate(0.96f)
                if (pendingWelcome) {
                    pendingWelcome = false
                    window.decorView.post { speakWelcomeSequence() }
                }
            }
        }

        setupOrb()
        startPulseAnimation()
        setupMicButton()
        setupSpeechRecognizer()
        requestRequiredPermissionsIfNeeded()
        setOrbState(OrbState.IDLE)
        registerFloatingVoiceReceiver()

        if (startListeningFromExternal) {
            window.decorView.post {
                startListening()
                if (backgroundListening) moveTaskToBack(true)
            }
        }

        micButton.setOnClickListener {
            startListening()
        }

        textInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                submitTypedCommand()
                true
            } else {
                false
            }
        }
    }

    private fun setupOrb() {
        pulseView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        orbView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        orbView.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(66, 133, 244),
                Color.rgb(26, 115, 232),
                Color.rgb(138, 180, 248)
            )
        ).apply {
            shape = GradientDrawable.OVAL
        }

        pulseView.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(26, 115, 232),
                Color.rgb(66, 133, 244)
            )
        ).apply {
            shape = GradientDrawable.OVAL
        }
        pulseView.alpha = 0.22f
    }

    private fun setupMicButton() {
        micButton.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(26, 115, 232),
                Color.rgb(66, 133, 244)
            )
        ).apply {
            shape = GradientDrawable.OVAL
        }
        micButton.clipToOutline = true
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        val startListeningFromExternal = intent?.getBooleanExtra(EXTRA_START_LISTENING, false) == true
        if (startListeningFromExternal) {
            window.decorView.post { startListening() }
        } else if (AiVoiceBridge.isVoiceActivation(intent)) {
            if (ttsReady) {
                speakWelcomeSequence()
            } else {
                pendingWelcome = true
            }
        }
    }

    private fun speakWelcomeSequence() {
        if (!ttsReady || !::textToSpeech.isInitialized) return

        textToSpeech.stop()
        textToSpeech.speak(
            "Welcome.",
            TextToSpeech.QUEUE_FLUSH,
            null,
            "welcome_1"
        )
        textToSpeech.speak(
            "Hi, I am your assistant.",
            TextToSpeech.QUEUE_ADD,
            null,
            "welcome_2"
        )
        textToSpeech.speak(
            "How can I help you?",
            TextToSpeech.QUEUE_ADD,
            null,
            "welcome_3"
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ASSISTANT_ROLE) {
            statusText.text =
                if (resultCode == RESULT_OK) "Bixby assistant ready"
                else "Ready"
        }
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
        speechRecognizer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } else {
            SpeechRecognizer.createSpeechRecognizer(this)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusText.text = "Listening..."
                greetingText.text = "I'm listening"
                setOrbState(OrbState.LISTENING)
            }

            override fun onBeginningOfSpeech() {
                statusText.text = "Listening..."
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (orbState == OrbState.LISTENING) {
                    val level = rmsdB.coerceIn(0f, 12f) / 12f
                    val scale = 1.0f + (level * 0.14f)
                    orbView.scaleX = scale
                    orbView.scaleY = scale
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                statusText.text = "Processing..."
                setOrbState(OrbState.PROCESSING)
            }

            override fun onError(error: Int) {
                statusText.text = "Tap mic to try again"
                setOrbState(OrbState.IDLE)
            }

            override fun onResults(results: Bundle?) {

                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )

                if (!matches.isNullOrEmpty()) {
                    val command = matches[0].lowercase(Locale.ROOT).trim()
                    greetingText.text = "You said: \"$command\""
                    executeCommand(command)
                }
                setOrbState(OrbState.IDLE)
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    private fun speechLocale(): Locale =
        if (Locale.getDefault().language == "hi") Locale("hi", "IN") else Locale("en", "IN")

    private fun isHindiText(text: String): Boolean {
        if (text.any { it.code in 0x0900..0x097F }) return true
        val padded = " " + text.lowercase(Locale.ROOT) + " "
        val markers = listOf(" kya ", " kaise ", " kaisi ", " aap ", " tum ", " mera ", " meri ", " mujhe ", " chahiye ", " karo ", " karna ", " batao ", " hai ", " ho ", " nahi ", " nahin ", " kyun ", " kaha ", " kaun ")
        return markers.any { padded.contains(it) }
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocale().toLanguageTag())
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
        stopPulseAnimation()

        pulseScaleAnimator = ValueAnimator.ofFloat(0.94f, 1.12f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                pulseView.scaleX = scale
                pulseView.scaleY = scale
            }
            start()
        }

        pulseAlphaAnimator = ValueAnimator.ofFloat(0.16f, 0.48f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                pulseView.alpha = animator.animatedValue as Float
            }
            start()
        }

        orbScaleAnimator = ValueAnimator.ofFloat(0.97f, 1.04f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                if (orbState != OrbState.LISTENING) {
                    val scale = animator.animatedValue as Float
                    orbView.scaleX = scale
                    orbView.scaleY = scale
                }
            }
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseScaleAnimator?.cancel()
        pulseAlphaAnimator?.cancel()
        orbScaleAnimator?.cancel()

        pulseScaleAnimator = null
        pulseAlphaAnimator = null
        orbScaleAnimator = null

        pulseView.scaleX = 1f
        pulseView.scaleY = 1f
        orbView.scaleX = 1f
        orbView.scaleY = 1f
        pulseView.alpha = 0.22f
    }

    private fun setOrbState(state: OrbState) {
        orbState = state
        when (state) {
            OrbState.IDLE -> {
                greetingText.text = if (greetingText.text.toString().startsWith("You said:")) {
                    greetingText.text
                } else {
                    "Listening for commands..."
                }
            }
            OrbState.LISTENING -> {
                greetingText.text = "I'm listening"
            }
            OrbState.PROCESSING -> {
                greetingText.text = "Thinking..."
            }
        }
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

            else -> askConversationalAi(command)
        }
    }

    private fun submitTypedCommand() {
        val command = textInput.text.toString().trim()
        if (command.isBlank()) return
        textInput.text?.clear()
        greetingText.text = "You said: \"$command\""
        executeCommand(command.lowercase(Locale.ROOT))
    }

    private fun askConversationalAi(command: String) {
        statusText.text = "Online mode"
        setOrbState(OrbState.PROCESSING)
        greetingText.text = "Thinking..."

        commandScope.launch {
            var speechBuffer = StringBuilder()
            var firstChunk = true

            val online = aiHandler.generateResponseStream(command) { chunk ->
                if (firstChunk) {
                    firstChunk = false
                    commandScope.launch(Dispatchers.Main.immediate) {
                        if (ttsReady) textToSpeech.stop()
                    }
                }

                speechBuffer.append(chunk)
                val text = speechBuffer.toString()

                val boundary = Regex("[.!?।]+\\s+").findAll(text).lastOrNull()
                if (boundary != null) {
                    val end = boundary.range.last + 1
                    val sentence = text.substring(0, end).trim()
                    speechBuffer = StringBuilder(text.substring(end))

                    if (sentence.isNotBlank()) {
                        commandScope.launch(Dispatchers.Main.immediate) {
                            enqueueGeminiSpeech(sentence)
                        }
                    }
                } else if (text.length >= 180) {
                    val split = text.lastIndexOf(' ')
                    if (split > 40) {
                        val part = text.substring(0, split).trim()
                        speechBuffer = StringBuilder(text.substring(split).trimStart())
                        commandScope.launch(Dispatchers.Main.immediate) {
                            enqueueGeminiSpeech(part)
                        }
                    }
                }
            }

            online.onSuccess { response ->
                statusText.text = "Online"
                greetingText.text = response

                val remaining = speechBuffer.toString().trim()
                if (remaining.isNotBlank()) {
                    enqueueGeminiSpeech(remaining)
                } else if (firstChunk) {
                    speakGeminiFailure("Gemini returned an empty response.")
                }
            }.onFailure { error ->
                statusText.text = "Gemini unavailable"
                greetingText.text = error.message ?: "Gemini connection failed. Please try again."

                val remaining = speechBuffer.toString().trim()
                if (remaining.isNotBlank()) {
                    enqueueGeminiSpeech(remaining)
                } else {
                    speakGeminiFailure(greetingText.text.toString())
                }
            }
        }
    }

    private fun enqueueGeminiSpeech(text: String) {
        if (!ttsReady || text.isBlank()) return

        val locale = if (isHindiText(text)) Locale("hi", "IN") else Locale("en", "IN")
        val languageResult = textToSpeech.setLanguage(locale)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            textToSpeech.language = Locale.ENGLISH
        }

        val maleVoice = textToSpeech.voices
            .asSequence()
            .filter { it.locale.language == locale.language }
            .filter {
                val name = it.name.lowercase(Locale.ROOT)
                name.contains("male") || name.contains("masculine")
            }
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.latency }))
            .firstOrNull()

        maleVoice?.let { textToSpeech.voice = it }

        val queueMode = if (textToSpeech.isSpeaking) {
            TextToSpeech.QUEUE_ADD
        } else {
            TextToSpeech.QUEUE_FLUSH
        }

        textToSpeech.speak(
            text,
            queueMode,
            null,
            "bixby_gemini_" + System.nanoTime()
        )
    }

    private fun speakGeminiFailure(message: String) {
        if (!ttsReady || message.isBlank()) return
        enqueueGeminiSpeech(message)
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
            .replaceFirst(Regex("""^\s*open\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(application|app|please)\b""", RegexOption.IGNORE_CASE), " ")
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

        commandScope.launch {
            statusText.text = "Finding $appName..."

            val launchIntent = withContext(Dispatchers.IO) {
                findLaunchIntentForApp(appName)
            }

            if (launchIntent == null) {
                statusText.text = "App not found"
                Toast.makeText(
                    this@MainActivity,
                    "I couldn't find a launchable app named $appName on this phone.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            try {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
                startActivity(launchIntent)
                statusText.text = "Opening $appName"
            } catch (_: Exception) {
                statusText.text = "Couldn't open $appName"
                Toast.makeText(
                    this@MainActivity,
                    "I found $appName, but Android couldn't launch it.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun findLaunchIntentForApp(appName: String): Intent? {
        val query = normalizeAppLookupText(appName)
        if (query.isBlank()) return null

        // Exact aliases run before universal matching.
        val exactPackageAliases = mapOf(
            "playstore" to "com.android.vending",
            "googleplay" to "com.android.vending",
            "googleplaystore" to "com.android.vending",
            "playstoregoogle" to "com.android.vending",
            "camera" to "com.sec.android.app.camera",
            "gemini" to "com.google.android.apps.bard",
            "googlegemini" to "com.google.android.apps.bard",
            "googlebard" to "com.google.android.apps.bard",
            "gpe" to "com.google.android.apps.nbu.paisa.user",
            "googlepay" to "com.google.android.apps.nbu.paisa.user",
            "gpay" to "com.google.android.apps.nbu.paisa.user",
            "samsungnotes" to "com.samsung.android.app.notes",
            "notes" to "com.samsung.android.app.notes",
            "googlekeep" to "com.google.android.keep",
            "keep" to "com.google.android.keep"
        )

        exactPackageAliases[query]?.let { packageName ->
            createPackageLaunchIntent(packageName)?.let { return it }
        }

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

        // Strict semantic aliases stay ahead of generic label/package matching.
        when (query) {
            "notes" -> {
                createPackageLaunchIntent("com.samsung.android.app.notes")?.let { return it }
                createPackageLaunchIntent("com.google.android.keep")?.let { return it }
            }
            "gemini", "googlegemini", "googlebard" -> {
                createPackageLaunchIntent("com.google.android.apps.bard")?.let { return it }
            }
            "gpe", "gpay", "googlepay" -> {
                createPackageLaunchIntent("com.google.android.apps.nbu.paisa.user")?.let { return it }
            }
        }

        // WhatsApp can exist as the original app plus a Samsung Dual Messenger/App Clone.
        if (query == "whatsapp") {
            val whatsappPackages = universalApps
                .filter { (packageName, displayLabel) ->
                    normalizeAppLookupText(displayLabel) == "whatsapp" ||
                        packageName == "com.whatsapp" ||
                        packageName.contains("whatsapp")
                }
                .keys
                .mapNotNull { createPackageLaunchIntent(it) }

            when (whatsappPackages.size) {
                0 -> Unit
                1 -> return whatsappPackages.first()
                else -> return createAppChooserIntent(whatsappPackages)
            }
        }

        // Deterministic priority: exact label/package -> full contains -> word contains.
        val exactMatches = universalApps
            .filter { (packageName, displayLabel) ->
                val normalizedLabel = normalizeAppLookupText(displayLabel)
                val normalizedPackage = normalizeAppLookupText(packageName)
                normalizedLabel == query || normalizedPackage == query
            }
            .keys
            .mapNotNull { createPackageLaunchIntent(it) }

        if (exactMatches.size == 1) return exactMatches.first()
        if (exactMatches.size > 1) return createAppChooserIntent(exactMatches)

        for ((packageName, displayLabel) in universalApps) {
            val normalizedLabel = normalizeAppLookupText(displayLabel)
            val normalizedPackage = normalizeAppLookupText(packageName)

            if (normalizedLabel.contains(query) || normalizedPackage.contains(query)) {
                createPackageLaunchIntent(packageName)?.let { return it }
            }
        }

        for ((packageName, displayLabel) in universalApps) {
            val normalizedLabel = normalizeAppLookupText(displayLabel)
            val normalizedPackage = normalizeAppLookupText(packageName)

            if (queryWords.any { word ->
                    normalizedLabel.contains(word) || normalizedPackage.contains(word)
                }
            ) {
                createPackageLaunchIntent(packageName)?.let { return it }
            }
        }

        return null
    }

    private fun createAppChooserIntent(intents: List<Intent>): Intent {
        val primary = Intent(intents.first())
        val alternatives = intents.drop(1).toTypedArray()

        if (alternatives.isNotEmpty()) {
            primary.putExtra(Intent.EXTRA_INITIAL_INTENTS, alternatives)
        }

        return Intent.createChooser(primary, "Choose WhatsApp").apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
        }
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

    private fun registerFloatingVoiceReceiver() {
        val filter = IntentFilter(BixbyFloatingAccessService.ACTION_RECOGNIZED_COMMAND)
        ContextCompat.registerReceiver(this, floatingVoiceReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        try { unregisterReceiver(floatingVoiceReceiver) } catch (_: Exception) { }
        try {
            speechRecognizer.destroy()
        } catch (_: Exception) {
        }
        commandScope.cancel()
        stopPulseAnimation()
        if (::textToSpeech.isInitialized) textToSpeech.shutdown()
        super.onDestroy()
    }
}