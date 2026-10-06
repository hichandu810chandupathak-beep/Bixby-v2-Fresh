package com.example.bixby

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.SearchManager

object AiVoiceBridge {
    const val ACTION_VOICE_COMMAND = "com.example.bixby.action.VOICE_COMMAND"
    const val EXTRA_COMMAND = "com.example.bixby.extra.COMMAND"
    const val EXTRA_WELCOME = "com.example.bixby.extra.WELCOME"

    fun launchSystemConversation(context: Context, prompt: String? = null): Boolean {
        val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            prompt?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(SearchManager.QUERY, text)
            }
        }

        val resolver = context.packageManager
        if (intent.resolveActivity(resolver) == null) return false

        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

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
