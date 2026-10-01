package com.bixby.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.PhoneNumberUtils
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : Activity() {
    companion object {
        private const val TAG = "BixbyMainActivity"
        private const val CAMERA_PERMISSION_REQUEST = 100
        private const val CALL_PERMISSION_REQUEST = 101
        private const val MICROPHONE_PERMISSION_REQUEST = 103
    }

    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var torchEnabled = false
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var statusText: TextView? = null
    private var micButton: Button? = null
    private var recognitionRetryCount = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            try { isListening = true; updateStatusSafely("Listening…") }
            catch (e: Exception) { logError("onReadyForSpeech failed", e) }
        }
        override fun onBeginningOfSpeech() {
            try { updateStatusSafely("Listening…") }
            catch (e: Exception) { logError("onBeginningOfSpeech failed", e) }
        }
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            try { isListening = false; updateStatusSafely("Processing…") }
            catch (e: Exception) { logError("onEndOfSpeech failed", e) }
        }
        override fun onError(error: Int) {
            try {
                isListening = false
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> {
                        if (recognitionRetryCount < 1) {
                            recognitionRetryCount++
                            updateStatusSafely("Didn't catch that — trying again…")
                            mainHandler.postDelayed({
                                try { startVoiceRecognitionSafely(resetRetry = false) }
                                catch (e: Exception) { logError("Voice retry failed", e) }
                            }, 350L)
                        } else {
                            recognitionRetryCount = 0
                            updateStatusSafely("Ready")
                            showErrorSafely("Didn't catch that. Tap the mic and try again.")
                        }
                    }
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER -> {
                        recognitionRetryCount = 0
                        updateStatusSafely("Ready")
                        showErrorSafely("Voice service unavailable. Check your internet and try again.")
                    }
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        recognitionRetryCount = 0
                        updateStatusSafely("Ready")
                        showErrorSafely("No speech detected. Tap the mic and try again.")
                    }
                    else -> {
                        recognitionRetryCount = 0
                        updateStatusSafely("Ready")
                        showErrorSafely("Voice recognition couldn't understand that.")
                    }
                }
            } catch (e: Exception) { logError("Recognition error handling failed", e) }
        }
        override fun onResults(results: Bundle?) {
            try {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val command = matches?.firstOrNull()?.trim().orEmpty()
                if (command.isBlank()) showErrorSafely("No command recognized")
                else handleRecognizedCommandSafely(command)
            } catch (e: Exception) {
                logError("Recognition results failed", e)
                showErrorSafely("Voice command failed")
            } finally { updateStatusSafely("Ready") }
        }
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)
            setupUiSafely()
            initializeHardwareSafely()
            initializeSpeechSafely()
        } catch (e: Exception) {
            logError("MainActivity initialization failed", e)
            showErrorSafely("Bixby could not start")
        }
    }

    private fun setupUiSafely() {
        try {
            statusText = findViewById(R.id.statusText)
            micButton = findViewById(R.id.micButton)
            micButton?.setOnClickListener {
                try { startVoiceRecognitionSafely() }
                catch (e: Exception) { logError("Mic click failed", e); showErrorSafely("Microphone unavailable") }
            }
            updateStatusSafely("Ready")
        } catch (e: Exception) { logError("UI setup failed", e) }
    }

    private fun initializeHardwareSafely() {
        try {
            cameraManager = getSystemService(CAMERA_SERVICE) as? CameraManager
            findTorchCameraSafely()
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        } catch (e: Exception) { logError("Hardware initialization failed", e) }
    }

    private fun initializeSpeechSafely() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                updateStatusSafely("Voice recognition unavailable")
                return
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(recognitionListener)
        } catch (e: Exception) {
            logError("Speech initialization failed", e)
            showErrorSafely("Voice recognition unavailable")
        }
    }

    private fun findTorchCameraSafely() {
        try {
            val manager = cameraManager ?: return
            for (id in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id
                    break
                }
            }
        } catch (e: Exception) { logError("Torch camera lookup failed", e) }
    }

    private fun startVoiceRecognitionSafely(resetRetry: Boolean = true) {
        try {
            if (resetRetry) recognitionRetryCount = 0
            if (isListening) return
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_PERMISSION_REQUEST)
                return
            }
            val recognizer = speechRecognizer
            if (recognizer == null) {
                showErrorSafely("Voice recognition unavailable")
                return
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }
            updateStatusSafely("Listening…")
            recognizer.startListening(intent)
        } catch (e: SecurityException) {
            logError("Speech permission error", e)
            showErrorSafely("Microphone permission required")
        } catch (e: Exception) {
            logError("Voice recognition start failed", e)
            showErrorSafely("Could not start listening")
        }
    }

    private fun stopVoiceRecognitionSafely() {
        try {
            speechRecognizer?.stopListening()
            isListening = false
            updateStatusSafely("Ready")
        } catch (e: Exception) { logError("Voice recognition stop failed", e) }
    }

    private fun handleRecognizedCommandSafely(command: String) {
        try {
            val raw = command.trim()
            if (raw.isBlank()) { showErrorSafely("Empty command"); return }
            if (handleNavigationCommandSafely(raw)) return
            if (handleTorchCommandSafely(raw)) return
            if (handleWifiCommandSafely(raw)) return
            if (handleBluetoothCommandSafely(raw)) return
            if (handleCallCommandSafely(raw)) return
            if (handleMessagingCommandSafely(raw)) return
            if (handleAppCommandSafely(raw)) return
            if (handleCloseCommandSafely(raw)) return
            showErrorSafely("Command not recognized")
        } catch (e: Exception) {
            logError("Command handling failed", e)
            showErrorSafely("Command failed")
        }
    }

    private fun normalizeCommand(command: String): String {
        return try {
            command.trim().lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ")
        } catch (e: Exception) {
            logError("Command normalization failed", e)
            command.trim().lowercase(Locale.getDefault())
        }
    }

    private fun containsAny(text: String, vararg values: String): Boolean {
        return try {
            values.any { value ->
                text == value || text.contains(" " + value + " ") ||
                    text.startsWith(value + " ") || text.endsWith(" " + value)
            }
        } catch (e: Exception) {
            logError("containsAny failed", e)
            false
        }
    }

    private fun handleNavigationCommandSafely(command: String): Boolean {
        return try {
            val normalized = normalizeCommand(command)
            when {
                containsAny(normalized, "back", "go back", "press back", "वापस", "वापस जाओ", "पीछे जाओ") -> {
                    if (!BixbyAccessibilityService.performBack()) showErrorSafely("Accessibility Service enable करें")
                    true
                }
                containsAny(normalized, "home", "go home", "press home", "होम", "होम पर जाओ") -> {
                    if (!BixbyAccessibilityService.performHome()) showErrorSafely("Accessibility Service enable करें")
                    true
                }
                containsAny(normalized, "recent", "recent apps", "open recent apps", "recents", "हाल के ऐप्स", "रीसेंट ऐप्स") -> {
                    if (!BixbyAccessibilityService.performRecents()) showErrorSafely("Accessibility Service enable करें")
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            logError("Navigation command failed", e)
            showErrorSafely("Navigation command failed")
            true
        }
    }

    private fun handleTorchCommandSafely(command: String): Boolean {
        return try {
            val normalized = normalizeCommand(command)
            when {
                containsAny(normalized, "flashlight on", "torch on", "turn on flashlight", "turn flashlight on", "टॉर्च चालू", "फ्लैशलाइट चालू") -> {
                    setTorchSafely(true); true
                }
                containsAny(normalized, "flashlight off", "torch off", "turn off flashlight", "turn flashlight off", "टॉर्च बंद", "फ्लैशलाइट बंद") -> {
                    setTorchSafely(false); true
                }
                else -> false
            }
        } catch (e: Exception) {
            logError("Torch command failed", e)
            showErrorSafely("Flashlight command failed")
            true
        }
    }

    private fun setTorchSafely(enabled: Boolean) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
                return
            }
            val manager = cameraManager
            val id = cameraId
            if (manager == null || id == null) { showErrorSafely("Flashlight unavailable"); return }
            manager.setTorchMode(id, enabled)
            torchEnabled = enabled
            updateStatusSafely(if (enabled) "Flashlight ON" else "Flashlight OFF")
        } catch (e: SecurityException) {
            logError("Torch security error", e)
            showErrorSafely("Camera permission required")
        } catch (e: Exception) {
            logError("Torch operation failed", e)
            showErrorSafely("Unable to control flashlight")
        }
    }

    private fun handleWifiCommandSafely(command: String): Boolean {
        return try {
            val normalized = normalizeCommand(command)
            when {
                containsAny(normalized, "wifi on", "turn on wifi", "turn wifi on", "वाईफाई चालू", "वाई फाई चालू") -> {
                    openWifiSettingsSafely(); true
                }
                containsAny(normalized, "wifi off", "turn off wifi", "turn wifi off", "वाईफाई बंद", "वाई फाई बंद") -> {
                    openWifiSettingsSafely(); true
                }
                else -> false
            }
        } catch (e: Exception) {
            logError("Wi-Fi command failed", e)
            showErrorSafely("Wi-Fi command failed")
            true
        }
    }

    private fun openWifiSettingsSafely() {
        try { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        catch (e: Exception) { logError("Wi-Fi settings failed", e); showErrorSafely("Wi-Fi settings unavailable") }
    }

    private fun handleBluetoothCommandSafely(command: String): Boolean {
        return try {
            val normalized = normalizeCommand(command)
            when {
                containsAny(normalized, "bluetooth on", "turn on bluetooth", "turn bluetooth on", "ब्लूटूथ चालू") -> {
                    openBluetoothSettingsSafely(); true
                }
                containsAny(normalized, "bluetooth off", "turn off bluetooth", "turn bluetooth off", "ब्लूटूथ बंद") -> {
                    openBluetoothSettingsSafely(); true
                }
                else -> false
            }
        } catch (e: Exception) {
            logError("Bluetooth command failed", e)
            showErrorSafely("Bluetooth command failed")
            true
        }
    }

    private fun openBluetoothSettingsSafely() {
        try { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        catch (e: Exception) { logError("Bluetooth settings failed", e); showErrorSafely("Bluetooth settings unavailable") }
    }

    private fun handleCallCommandSafely(command: String): Boolean {
        return try {
            val prefixes = listOf("call ", "phone ", "फोन करो ", "कॉल करो ", "कॉल ")
            val rawLower = command.lowercase(Locale.getDefault())
            val prefix = prefixes.firstOrNull { rawLower.startsWith(it) } ?: return false
            val name = command.substring(prefix.length).trim()
            if (name.isBlank()) { showErrorSafely("Contact name missing"); return true }
            callContactSafely(name)
            true
        } catch (e: Exception) {
            logError("Call command failed", e)
            showErrorSafely("Call command failed")
            true
        }
    }

    private fun callContactSafely(contactName: String) {
        try {
            val missingPermissions = ArrayList<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.READ_CONTACTS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.CALL_PHONE)
            }
            if (missingPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), CALL_PERMISSION_REQUEST)
                return
            }
            val phoneNumber = findContactPhoneNumberSafely(contactName)
            if (phoneNumber.isNullOrBlank()) { showErrorSafely("Contact not found"); return }
            showCallConfirmationSafely(contactName, phoneNumber)
        } catch (e: SecurityException) {
            logError("Call permission error", e)
            showErrorSafely("Call permission required")
        } catch (e: Exception) {
            logError("Call preparation failed", e)
            showErrorSafely("Unable to prepare call")
        }
    }

    private fun findContactPhoneNumberSafely(contactName: String): String? {
        return try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            val exactSelection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " = ?"
            val exactArgs = arrayOf(contactName.trim())

            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                exactSelection,
                exactArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numberIndex >= 0) return cursor.getString(numberIndex)
                }
            }

            val partialSelection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?"
            val partialArgs = arrayOf("%" + contactName.trim() + "%")
            var foundNumber: String? = null
            var matchCount = 0

            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                partialSelection,
                partialArgs,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numberIndex >= 0) {
                        foundNumber = cursor.getString(numberIndex)
                        matchCount++
                    }
                    if (matchCount > 1) return@use
                }
            }
            if (matchCount == 1) foundNumber else null
        } catch (e: Exception) {
            logError("Contact lookup failed", e)
            null
        }
    }

    private fun showCallConfirmationSafely(contactName: String, phoneNumber: String) {
        try {
            AlertDialog.Builder(this)
                .setTitle("Confirm call")
                .setMessage("Call " + contactName + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Call") { _, _ -> executeCallSafely(phoneNumber) }
                .show()
        } catch (e: Exception) {
            logError("Call confirmation failed", e)
            showErrorSafely("Unable to show call confirmation")
        }
    }

    private fun executeCallSafely(phoneNumber: String) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                showErrorSafely("Call permission required")
                return
            }
            val normalized = PhoneNumberUtils.normalizeNumber(phoneNumber)
            if (normalized.isBlank()) { showErrorSafely("Invalid phone number"); return }
            val intent = Intent(Intent.ACTION_CALL).apply { data = Uri.parse("tel:" + normalized) }
            startActivity(intent)
        } catch (e: SecurityException) {
            logError("Call security error", e)
            showErrorSafely("Call permission required")
        } catch (e: Exception) {
            logError("Call execution failed", e)
            showErrorSafely("Unable to place call")
        }
    }

    private fun handleMessagingCommandSafely(command: String): Boolean {
        return try {
            val prefixes = listOf("send sms to ", "send message to ", "message ", "text ", "sms ", "एसएमएस ", "मैसेज भेजो ", "मैसेज ")
            val rawLower = command.lowercase(Locale.getDefault())
            val prefix = prefixes.firstOrNull { rawLower.startsWith(it) } ?: return false
            val remainder = command.substring(prefix.length).trim()

            val indexes = listOf(remainder.indexOf(":"), remainder.indexOf(","), remainder.indexOf("-")).filter { it >= 0 }
            val separatorIndex = indexes.minOrNull() ?: -1

            if (separatorIndex <= 0) {
                showErrorSafely("Use: message Contact: Message")
                return true
            }

            val contactName = remainder.substring(0, separatorIndex).trim()
            val message = remainder.substring(separatorIndex + 1).trim()

            if (contactName.isBlank() || message.isBlank()) {
                showErrorSafely("Contact or message missing")
                return true
            }
            sendSmsSafely(contactName, message)
            true
        } catch (e: Exception) {
            logError("Messaging command failed", e)
            showErrorSafely("Message command failed")
            true
        }
    }

    private fun sendSmsSafely(contactName: String, message: String) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), CALL_PERMISSION_REQUEST)
                return
            }
            val phoneNumber = findContactPhoneNumberSafely(contactName)
            if (phoneNumber.isNullOrBlank()) { showErrorSafely("Contact not found"); return }
            val normalized = PhoneNumberUtils.normalizeNumber(phoneNumber)
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:" + normalized)
                putExtra("sms_body", message)
            }
            startActivity(intent)
        } catch (e: Exception) {
            logError("SMS launch failed", e)
            showErrorSafely("Unable to open messaging app")
        }
    }

    private fun handleAppCommandSafely(command: String): Boolean {
        return try {
            val prefixes = listOf("open app ", "launch app ", "open application ", "launch application ", "ऐप खोलो ", "एप खोलो ")
            val rawLower = command.lowercase(Locale.getDefault())
            val prefix = prefixes.firstOrNull { rawLower.startsWith(it) } ?: return false
            val appName = command.substring(prefix.length).trim()
            if (appName.isBlank()) { showErrorSafely("App name missing"); return true }
            openInstalledAppSafely(appName)
            true
        } catch (e: Exception) {
            logError("App command failed", e)
            showErrorSafely("App command failed")
            true
        }
    }

    private fun openInstalledAppSafely(appName: String) {
        try {
            val applications = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            val wanted = appName.trim().lowercase(Locale.getDefault())

            val exactMatches = applications.filter { info: ApplicationInfo ->
                packageManager.getApplicationLabel(info).toString().trim().lowercase(Locale.getDefault()) == wanted
            }

            val candidates = if (exactMatches.isNotEmpty()) exactMatches else applications.filter { info: ApplicationInfo ->
                packageManager.getApplicationLabel(info).toString().trim().lowercase(Locale.getDefault()).contains(wanted)
            }

            if (candidates.size != 1) {
                showErrorSafely(if (candidates.isEmpty()) "App not found" else "Multiple apps found")
                return
            }

            val launchIntent = packageManager.getLaunchIntentForPackage(candidates.first().packageName)
            if (launchIntent == null) { showErrorSafely("App cannot be opened"); return }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } catch (e: Exception) {
            logError("Installed app launch failed", e)
            showErrorSafely("Unable to open app")
        }
    }

    private fun handleCloseCommandSafely(command: String): Boolean {
        return try {
            val normalized = normalizeCommand(command)
            if (containsAny(normalized, "close app", "close application", "exit app", "exit application", "ऐप बंद करो", "एप बंद करो", "बंद करो")) {
                safelyFinishActivity()
                true
            } else false
        } catch (e: Exception) {
            logError("Close command failed", e)
            showErrorSafely("Close command failed")
            true
        }
    }

    private fun safelyFinishActivity() {
        try { finish() }
        catch (e: Exception) { logError("Activity finish failed", e) }
    }

    private fun updateStatusSafely(message: String) {
        try { statusText?.text = message }
        catch (e: Exception) { logError("Status update failed", e) }
    }

    private fun showErrorSafely(message: String) {
        try { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
        catch (e: Exception) { logError("Toast failed", e) }
    }

    private fun logError(message: String, error: Throwable) {
        try { Log.e(TAG, message, error) } catch (_: Exception) { }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        try {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            if (grantResults.isEmpty() || grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                showErrorSafely("Required permission denied")
                return
            }
            when (requestCode) {
                MICROPHONE_PERMISSION_REQUEST -> startVoiceRecognitionSafely()
                CAMERA_PERMISSION_REQUEST -> updateStatusSafely("Ready")
                CALL_PERMISSION_REQUEST -> showErrorSafely("Permission granted. Please repeat the command.")
            }
        } catch (e: Exception) { logError("Permission result handling failed", e) }
    }

    override fun onDestroy() {
        try {
            stopVoiceRecognitionSafely()
            speechRecognizer?.destroy()
            speechRecognizer = null
            mainHandler.removeCallbacksAndMessages(null)
            if (torchEnabled) {
                try { cameraId?.let { id -> cameraManager?.setTorchMode(id, false) } }
                catch (e: Exception) { logError("Torch cleanup failed", e) }
            }
            cameraManager = null
            cameraId = null
            bluetoothAdapter = null
        } catch (e: Exception) {
            logError("MainActivity cleanup failed", e)
        } finally {
            try { super.onDestroy() }
            catch (e: Exception) { logError("super.onDestroy failed", e) }
        }
    }
}
