package com.example.bixby

import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class BixbyVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: android.os.Bundle?): VoiceInteractionSession =
        BixbyVoiceInteractionSession(this)
}

private class BixbyVoiceInteractionSession(
    context: android.content.Context
) : VoiceInteractionSession(context)