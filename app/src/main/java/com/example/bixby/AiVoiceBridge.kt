package com.example.bixby

import android.content.Context
import android.content.Intent

object AiVoiceBridge {
    const val ACTION_VOICE_COMMAND = "com.example.bixby.action.VOICE_COMMAND"
    const val EXTRA_COMMAND = "com.example.bixby.extra.COMMAND"
    const val EXTRA_WELCOME = "com.example.bixby.extra.WELCOME"

    fun extractCommand(intent: Intent?): String? {
        if (intent == null || intent.action != ACTION_VOICE_COMMAND) return null

        return intent.getStringExtra(EXTRA_COMMAND)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
    }

    fun isVoiceActivation(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_WELCOME, false) == true
}
