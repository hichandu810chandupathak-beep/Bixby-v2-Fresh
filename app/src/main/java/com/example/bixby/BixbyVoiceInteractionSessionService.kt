package com.example.bixby

import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.content.Intent

class BixbyVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: android.os.Bundle?): VoiceInteractionSession =
        BixbyVoiceInteractionSession(this)
}

private class BixbyVoiceInteractionSession(
    context: android.content.Context
) : VoiceInteractionSession(context) {
    override fun onShow(args: android.os.Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        startVoiceActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(AiVoiceBridge.EXTRA_WELCOME, true)
            }
        )
    }
}
