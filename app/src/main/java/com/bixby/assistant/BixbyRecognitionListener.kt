package com.bixby.assistant

import android.os.Bundle
import android.speech.RecognitionListener
import android.util.Log

class BixbyRecognitionListener(
    private val onResultsReceived: (Bundle?) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit,
    private val onErrorReceived: (Int) -> Unit
) : RecognitionListener {
    companion object { private const val TAG = "BixbyRecognition" }

    override fun onReadyForSpeech(params: Bundle?) {
        try { onListeningStateChanged(true) }
        catch (e: Exception) { Log.e(TAG, "onReadyForSpeech failed", e) }
    }
    override fun onBeginningOfSpeech() {
        try { onListeningStateChanged(true) }
        catch (e: Exception) { Log.e(TAG, "onBeginningOfSpeech failed", e) }
    }
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() {
        try { onListeningStateChanged(false) }
        catch (e: Exception) { Log.e(TAG, "onEndOfSpeech failed", e) }
    }
    override fun onError(error: Int) {
        try {
            onListeningStateChanged(false)
            onErrorReceived(error)
        } catch (e: Exception) { Log.e(TAG, "onError failed", e) }
    }
    override fun onResults(results: Bundle?) {
        try {
            onListeningStateChanged(false)
            onResultsReceived(results)
        } catch (e: Exception) { Log.e(TAG, "onResults failed", e) }
    }
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
